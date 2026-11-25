//! Process-wide Android controller runtime bridging Auth, Signal, LAN Discovery, SessionHandle,
//! and File / Terminal / PortMap tool engines.

use std::collections::VecDeque;
use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock, RwLock};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use rc_client_core::auth_client::{
    save_auth_session, AuthHttpClient, AuthSessionManager, StoredAuthSession,
};
use rc_client_core::cert_manager::{CertManager, RcOwnerTicketVerifier};
use rc_client_core::config_paths::acquire_file_lock;
use rc_client_core::host_bridge::{
    generate_temp_code, HostHandle, UiHostConfig, DEFAULT_HOST_PORT,
};
use rc_client_core::lan_discovery::{discover_local_lan_ipv4s, LanDiscoveryHandle};
use rc_client_core::model::parse_connect_target;
use rc_client_core::server_config::{
    format_host_port, parse_device_id_input, ServerConfig, ServerConnectionState,
    ServerStatusSnapshot,
};
use rc_client_core::session_bridge::{
    SessionCommand, SessionHandle, SessionMtlsBundle, SessionUpdate, UiSessionConfig,
};
use rc_client_core::signal_client::{
    ConnectGrant, DeviceStatusEvent, SignalClientHandle, SignalClientOptions,
};
use rc_input::{InputEvent, MouseButton};
use rc_session::auth::{OwnerTicketSigner, OwnerTicketVerifier};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

use crate::host_android;

use crate::ndk_media::{
    check_unreported_first_frame, media_diagnostics, request_keyframe, reset_media_diagnostics,
    set_codec_candidates, CodecCandidate,
};
use crate::platform_android::{
    drain_inbound_clipboard_texts, push_local_clipboard_text, push_silent_ime_text,
    register_android_hooks,
};

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OpenSessionRequest {
    /// Either a 9-digit device code or `ip:port`.
    pub target: String,
    pub password: Option<String>,
    pub watch_only: bool,
    #[serde(default)]
    pub force_relay: bool,
    #[serde(default = "default_true")]
    pub encrypt_connection: bool,
    #[serde(default = "default_true")]
    pub audio_enabled: bool,
    #[serde(default = "default_true")]
    pub clipboard_enabled: bool,
    /// Optional explicit stream override; when 0, route-aware defaults are chosen.
    #[serde(default)]
    pub width: u32,
    #[serde(default)]
    pub height: u32,
    #[serde(default)]
    pub fps: u32,
    #[serde(default)]
    pub bitrate_bps: u32,
}

#[derive(Debug, Clone, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StartHostRequest {
    #[serde(default)]
    pub allow_remote_assist: bool,
    #[serde(default = "default_true")]
    pub require_approval: bool,
    pub password: Option<String>,
}

fn default_true() -> bool {
    true
}

#[derive(Debug, Clone, Serialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
enum SignalUiEvent {
    Status(ServerStatusSnapshot),
    DeviceStatus(DeviceStatusEvent),
    Kicked { reason: String },
}

pub struct AndroidRuntime {
    device_name: Mutex<String>,
    auth_path: PathBuf,
    pub auth: AuthSessionManager,
    cert_mgr: Arc<CertManager>,
    signal: Mutex<Option<Arc<SignalClientHandle>>>,
    signal_events: Arc<Mutex<VecDeque<SignalUiEvent>>>,
    signal_snapshot: Arc<Mutex<ServerStatusSnapshot>>,
    signal_dirty: Arc<AtomicBool>,
    last_reported_refresh: Mutex<String>,
    lan: Mutex<Option<LanDiscoveryHandle>>,
    session: RwLock<Option<SessionHandle>>,
    last_session_running: AtomicBool,
    session_epoch: AtomicU64,
    host: Mutex<Option<HostHandle>>,
    last_host_running: AtomicBool,
    host_allow_remote_assist: AtomicBool,
}

static RUNTIME: OnceLock<Arc<AndroidRuntime>> = OnceLock::new();

pub fn global_runtime() -> Option<Arc<AndroidRuntime>> {
    RUNTIME.get().cloned()
}

pub fn init_runtime(
    files_dir: PathBuf,
    device_name: String,
    codecs: Vec<CodecCandidate>,
) -> Result<Arc<AndroidRuntime>, String> {
    register_android_hooks();
    set_codec_candidates(codecs);

    if let Some(rt) = RUNTIME.get() {
        *rt.device_name.lock().unwrap_or_else(|e| e.into_inner()) = device_name;
        return Ok(Arc::clone(rt));
    }

    let certs_dir = files_dir.join("certs");
    let cert_mgr = Arc::new(CertManager::load_or_init(certs_dir)?);
    let auth_path = files_dir.join("auth.json");
    let auth = AuthSessionManager::new(ServerConfig::default(), auth_path.clone(), Arc::clone(&cert_mgr));
    let instance_id = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_nanos() as u64)
        .unwrap_or(1);
    let lan = LanDiscoveryHandle::spawn(instance_id, device_name.clone());

    let rt = Arc::new(AndroidRuntime {
        device_name: Mutex::new(device_name),
        auth_path,
        auth,
        cert_mgr,
        signal: Mutex::new(None),
        signal_events: Arc::new(Mutex::new(VecDeque::with_capacity(64))),
        signal_snapshot: Arc::new(Mutex::new(ServerConfig::default().disabled_snapshot())),
        signal_dirty: Arc::new(AtomicBool::new(true)),
        last_reported_refresh: Mutex::new(String::new()),
        lan: Mutex::new(Some(lan)),
        session: RwLock::new(None),
        last_session_running: AtomicBool::new(false),
        session_epoch: AtomicU64::new(0),
        host: Mutex::new(None),
        last_host_running: AtomicBool::new(false),
        host_allow_remote_assist: AtomicBool::new(false),
    });

    let _ = RUNTIME.set(Arc::clone(&rt));
    Ok(RUNTIME.get().cloned().unwrap_or(rt))
}

impl AndroidRuntime {
    pub fn cert_manager(&self) -> Arc<CertManager> {
        Arc::clone(&self.cert_mgr)
    }

    pub fn device_name(&self) -> String {
        self.device_name
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .clone()
    }

    /// Stable 64-char hex device key derived deterministically from this device's Ed25519 pubkey.
    pub fn device_key(&self) -> String {
        self.cert_mgr.device_pubkey_hex()
    }

    /// Persists an externally obtained/restored session to `auth.json` and updates in-memory state
    /// so `AuthSessionManager::ensure_valid_access_token()` can safely refresh tokens on Android.
    pub fn apply_and_persist_session(&self, stored: StoredAuthSession) {
        let _file_lock = acquire_file_lock(&self.auth_path, Duration::from_secs(5));
        let _ = save_auth_session(&self.auth_path, &stored);
        self.auth.apply_external_session(stored);
        self.signal_dirty.store(true, Ordering::Release);
    }

    pub fn ensure_signal_started(&self) {
        let cfg = self.auth.server_config();
        if !cfg.is_configured() {
            return;
        }
        let Some(sess) = self.auth.current_session() else {
            return;
        };
        if sess.must_change_password {
            return;
        }

        let mut sig_guard = self.signal.lock().unwrap_or_else(|e| e.into_inner());
        if let Some(existing) = sig_guard.as_ref() {
            let st = existing.status();
            if st.enabled
                && st.server_host == cfg.server_host
                && st.signal_port == cfg.signal_port
                && st.relay_port == cfg.relay_port
                && matches!(
                    st.state,
                    ServerConnectionState::Connected | ServerConnectionState::Connecting
                )
            {
                return;
            }
        }
        if let Some(old) = sig_guard.take() {
            old.request_stop();
        }

        let preferred: Vec<SocketAddr> = cfg
            .signal_addr()
            .and_then(|s| s.parse::<SocketAddr>().ok())
            .into_iter()
            .collect();
        let lan_addrs: Vec<String> = discover_local_lan_ipv4s(&preferred)
            .into_iter()
            .map(|ip| ip.to_string())
            .collect();

        // Clone an Arc-backed token provider that queries the singleton runtime.
        let token_provider = Arc::new(|| {
            let rt = global_runtime().ok_or_else(|| "runtime uninitialized".to_string())?;
            rt.auth
                .ensure_valid_access_token()
                .map_err(|e| e.to_string())
        });
        let token_refresher = Arc::new(|failed_token: &str| {
            let rt = global_runtime().ok_or_else(|| "runtime uninitialized".to_string())?;
            rt.auth
                .refresh_after_unauthorized(failed_token)
                .map_err(|e| e.to_string())
        });

        let opts = SignalClientOptions {
            token_provider: Some(token_provider),
            token_refresher: Some(token_refresher),
            device_pubkey: Some(self.cert_mgr.device_pubkey_hex()),
            allow_remote_assist: self.host_allow_remote_assist.load(Ordering::Acquire),
            dynamic_lan_discovery: true,
        };

        let events_tx = Arc::clone(&self.signal_events);
        let snap_store = Arc::clone(&self.signal_snapshot);
        let dirty_flag = Arc::clone(&self.signal_dirty);
        let handle = SignalClientHandle::spawn_with_options(
            cfg,
            self.device_name(),
            lan_addrs,
            opts,
            move |snapshot| {
                *snap_store.lock().unwrap_or_else(|e| e.into_inner()) = snapshot.clone();
                dirty_flag.store(true, Ordering::Release);
                if snapshot.state == ServerConnectionState::Connected {
                    if let Some(ref did) = snapshot.device_id {
                        let did_bg = did.clone();
                        let _ = std::thread::Builder::new()
                            .name("rc-cert-ensure".to_string())
                            .spawn(move || {
                                if let Some(rt) = global_runtime() {
                                    let _ = rt.auth.ensure_device_cert(&did_bg);
                                }
                            });
                    }
                }
                let mut q = events_tx.lock().unwrap_or_else(|e| e.into_inner());
                if q.len() >= 128 {
                    q.pop_front();
                }
                q.push_back(SignalUiEvent::Status(snapshot));
            },
        );
        *sig_guard = Some(Arc::new(handle));
        self.signal_dirty.store(true, Ordering::Release);
    }

    pub fn stop_signal_and_session(&self) {
        self.stop_host();
        self.disconnect_session(false);
        let mut sig_guard = self.signal.lock().unwrap_or_else(|e| e.into_inner());
        if let Some(old) = sig_guard.take() {
            old.request_stop();
        }
        *self.signal_snapshot.lock().unwrap_or_else(|e| e.into_inner()) =
            ServerConfig::default().disabled_snapshot();
        self.signal_dirty.store(true, Ordering::Release);
    }

    fn pump_incoming_relay(&self) {
        let mut incoming_list = Vec::new();
        {
            let sig_guard = self.signal.lock().unwrap_or_else(|e| e.into_inner());
            if let Some(sig) = sig_guard.as_ref() {
                while let Some(inc) = sig.try_recv_incoming() {
                    incoming_list.push(inc);
                }
            }
        }
        if incoming_list.is_empty() {
            return;
        }
        let host_guard = self.host.lock().unwrap_or_else(|e| e.into_inner());
        if let Some(host) = host_guard.as_ref() {
            for inc in incoming_list {
                host.offer_relay_incoming(inc);
            }
        } else {
            drop(host_guard);
            for inc in incoming_list {
                let _ = std::thread::Builder::new()
                    .name("rc-relay-reject".to_string())
                    .spawn(move || {
                        rc_client_core::relay_transport::reject_relay_incoming_fast(inc, None);
                    });
            }
        }
    }

    pub fn poll_signal_events(&self) -> Option<Value> {
        self.pump_incoming_relay();
        let mut out = Vec::new();
        {
            let mut q = self.signal_events.lock().unwrap_or_else(|e| e.into_inner());
            while let Some(ev) = q.pop_front() {
                out.push(ev);
            }
        }
        {
            let sig_guard = self.signal.lock().unwrap_or_else(|e| e.into_inner());
            if let Some(sig) = sig_guard.as_ref() {
                while let Some(dev) = sig.try_recv_device_status() {
                    out.push(SignalUiEvent::DeviceStatus(dev));
                }
                if let Some(kicked) = sig.try_recv_kicked_reason() {
                    out.push(SignalUiEvent::Kicked { reason: kicked });
                }
            }
        }

        let cur_session = self.auth.current_session();
        let cur_refresh = cur_session
            .as_ref()
            .map(|s| s.refresh_token.as_str())
            .unwrap_or("");
        let refresh_changed = {
            let mut last_ref = self
                .last_reported_refresh
                .lock()
                .unwrap_or_else(|e| e.into_inner());
            if last_ref.as_str() != cur_refresh {
                *last_ref = cur_refresh.to_string();
                true
            } else {
                false
            }
        };

        let dirty = self.signal_dirty.swap(false, Ordering::AcqRel);
        if !dirty && out.is_empty() && !refresh_changed {
            return None;
        }

        let snapshot = self
            .signal_snapshot
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .clone();
        if let Some(ref did) = snapshot.device_id {
            if let Some(lan) = self.lan.lock().unwrap_or_else(|e| e.into_inner()).as_ref() {
                lan.set_local_device_id(Some(did.clone()));
            }
        }
        let session_tokens = cur_session.map(|s| {
            json!({
                "access_token": s.access_token,
                "refresh_token": s.refresh_token,
                "access_expires_at_unix_sec": s.access_expires_at_unix_sec,
                "user": {
                    "id": s.user_id,
                    "email": s.email,
                    "display_name": s.display_name,
                    "role": s.role,
                    "must_change_password": s.must_change_password,
                }
            })
        });
        Some(json!({
            "snapshot": snapshot,
            "events": out,
            "sessionTokens": session_tokens,
        }))
    }

    fn build_mtls_bundle(
        &self,
        expected_peer_device_id: Option<String>,
        expected_peer_user_id: Option<String>,
    ) -> Option<SessionMtlsBundle> {
        let cfg = self.auth.server_config();
        let ca_cert_pem = cfg.ca_cert_pem?;
        let current_uid = self.auth.current_session()?.user_id;
        let (cert_pem, meta) = self.cert_mgr.load_device_cert()?;
        if !meta.user_id.eq_ignore_ascii_case(current_uid.trim()) {
            return None;
        }
        let key_pem = self.cert_mgr.tls_key_pem().ok()?;
        Some(SessionMtlsBundle {
            ca_cert_pem,
            cert_pem,
            key_pem,
            local_user_id: meta.user_id,
            expected_peer_device_id,
            expected_peer_user_id,
        })
    }

    pub fn open_session(&self, req: OpenSessionRequest) -> Result<Value, String> {
        let my_epoch = self.session_epoch.fetch_add(1, Ordering::AcqRel) + 1;
        let trimmed = req.target.trim();

        let (connect_addr, relay_grant, lan_direct_addr, target_device_id): (
            SocketAddr,
            Option<ConnectGrant>,
            Option<SocketAddr>,
            Option<String>,
        ) = if let Some(did) = parse_device_id_input(trimmed) {
            // Ensure signal is running and wait briefly if currently connecting.
            self.ensure_signal_started();
            let sig_clone = {
                let g = self.signal.lock().unwrap_or_else(|e| e.into_inner());
                g.clone()
            };
            let sig = sig_clone.ok_or_else(|| "信令尚未连接，请先登录并等待服务器连接就绪".to_string())?;
            let wait_deadline = std::time::Instant::now() + Duration::from_secs(5);
            while sig.status().state != ServerConnectionState::Connected {
                if self.session_epoch.load(Ordering::Acquire) != my_epoch {
                    return Ok(json!({ "cancelled": true }));
                }
                if std::time::Instant::now() >= wait_deadline {
                    let st = sig.status();
                    let detail = st
                        .error_message
                        .filter(|s| !s.is_empty())
                        .unwrap_or_else(|| format!("{:?}", st.state));
                    return Err(format!("服务器信令未就绪（{detail}）"));
                }
                std::thread::sleep(Duration::from_millis(150));
            }

            let grant = sig.request_connect(&did, Duration::from_secs(12))?;
            if self.session_epoch.load(Ordering::Acquire) != my_epoch {
                return Ok(json!({ "cancelled": true }));
            }

            let lan_addr = if !req.force_relay {
                self.lan
                    .lock()
                    .unwrap_or_else(|e| e.into_inner())
                    .as_ref()
                    .and_then(|h| h.lookup_lan_addr(&did))
            } else {
                None
            };
            (
                SocketAddr::from(([0, 0, 0, 0], 0)),
                Some(grant),
                lan_addr,
                Some(did),
            )
        } else {
            let addr = parse_connect_target(trimmed)?;
            (addr, None, None, None)
        };

        let is_direct_ip = target_device_id.is_none();
        let encrypt_connection = if is_direct_ip {
            false
        } else {
            req.encrypt_connection
        };

        let owner_grant = relay_grant.as_ref().and_then(|g| g.owner_grant.clone());
        let owner_signer = match owner_grant.as_ref() {
            Some(grant) => Some(Arc::new(
                self.cert_mgr
                    .create_owner_ticket_signer(grant.clone())
                    .map_err(|e| format!("构造自控签名器失败：{e}"))?,
            ) as Arc<dyn OwnerTicketSigner>),
            None => None,
        };

        let expected_peer_user_id = if owner_grant.is_some() {
            self.auth.current_session().map(|s| s.user_id)
        } else {
            None
        };

        // Ensure device cert is issued before building mTLS bundle when possible.
        let mut cert_err: Option<String> = None;
        if let Some(did) = self
            .signal_snapshot
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .device_id
            .clone()
        {
            if let Err(e) = self.auth.ensure_device_cert(&did) {
                cert_err = Some(e.to_string());
            }
        }

        let mtls_config = self.build_mtls_bundle(target_device_id.clone(), expected_peer_user_id);
        if encrypt_connection && mtls_config.is_none() {
            return Err(match cert_err {
                Some(e) => format!("本机设备证书签发失败：{e}"),
                None => "本机设备证书未就绪，请重新登录或检查服务器证书".to_string(),
            });
        }

        // Ultra-low-latency route-aware defaults: LAN / direct IP defaults to 1080p@120fps,
        // P2P / Relay starts at 1080p@60fps; higher resolutions can be selected anytime in Display.
        let is_lan_route = is_direct_ip || lan_direct_addr.is_some();
        let (default_w, default_h, default_fps, default_bps) = if is_lan_route {
            (1920, 1080, 120, 20_000_000)
        } else {
            (1920, 1080, 60, 10_000_000)
        };

        let ui_cfg = UiSessionConfig {
            connect_addr,
            password: req.password.filter(|p| !p.is_empty()),
            watch_only: req.watch_only,
            width: if req.width > 0 { req.width } else { default_w },
            height: if req.height > 0 { req.height } else { default_h },
            fps: if req.fps > 0 { req.fps } else { default_fps },
            bitrate_bps: if req.bitrate_bps > 0 {
                req.bitrate_bps
            } else {
                default_bps
            },
            prefer_hardware: true,
            clipboard_enabled: req.clipboard_enabled,
            audio_enabled: req.audio_enabled,
            relay_grant,
            force_relay: req.force_relay,
            encrypt_connection,
            mtls_config,
            owner_grant,
            owner_signer,
            lan_direct_addr,
        };

        // Disconnect any previous session cleanly before spawning the new one.
        self.close_active_session_only(false);
        if self.session_epoch.load(Ordering::Acquire) != my_epoch {
            return Ok(json!({ "cancelled": true }));
        }

        reset_media_diagnostics();
        let handle = SessionHandle::spawn_with_surface(ui_cfg, None);
        *self.session.write().unwrap_or_else(|e| e.into_inner()) = Some(handle);
        self.last_session_running.store(true, Ordering::Release);

        Ok(json!({
            "started": true,
            "epoch": my_epoch,
            "lanHint": is_lan_route,
            "targetLabel": target_device_id
                .unwrap_or_else(|| format_host_port(&connect_addr.ip().to_string(), connect_addr.port())),
        }))
    }

    fn close_active_session_only(&self, lock_remote: bool) {
        let old = self
            .session
            .write()
            .unwrap_or_else(|e| e.into_inner())
            .take();
        self.last_session_running.store(false, Ordering::Release);
        if let Some(handle) = old {
            if lock_remote {
                handle.request_lock_on_disconnect();
            }
            let _ = handle.tools().detach_all_terminals();
            handle.join();
        }
    }

    pub fn disconnect_session(&self, lock_remote: bool) {
        self.session_epoch.fetch_add(1, Ordering::AcqRel);
        self.close_active_session_only(lock_remote);
    }

    pub fn send_command(&self, cmd_json: &Value) -> Result<(), String> {
        let g = self.session.read().unwrap_or_else(|e| e.into_inner());
        let Some(handle) = g.as_ref() else {
            return Err("当前无活动会话".to_string());
        };
        let kind = cmd_json["type"].as_str().unwrap_or("");
        match kind {
            "disconnect" => {
                drop(g);
                self.disconnect_session(cmd_json["lockRemote"].as_bool().unwrap_or(false));
            }
            "set_watch_only" => {
                let enabled = cmd_json["enabled"].as_bool().unwrap_or(false);
                handle.set_watch_only(enabled);
            }
            "set_bitrate" => {
                let bps = cmd_json["bps"].as_u64().unwrap_or(0) as u32;
                handle.send(SessionCommand::SetBitrate { bps });
            }
            "set_stream_params" => {
                let width = cmd_json["width"].as_u64().unwrap_or(0) as u32;
                let height = cmd_json["height"].as_u64().unwrap_or(0) as u32;
                let fps = cmd_json["fps"].as_u64().unwrap_or(0) as u32;
                handle.send(SessionCommand::SetStreamParams { width, height, fps });
            }
            "set_clipboard_enabled" => {
                let enabled = cmd_json["enabled"].as_bool().unwrap_or(true);
                handle.send(SessionCommand::SetClipboardEnabled { enabled });
            }
            "set_audio_muted" => {
                let muted = cmd_json["muted"].as_bool().unwrap_or(false);
                handle.send(SessionCommand::SetAudioMuted { muted });
            }
            "lock_remote_screen" => {
                handle.send(SessionCommand::LockRemoteScreen);
            }
            "request_idr" => {
                request_keyframe();
            }
            other => return Err(format!("未知会话指令: {other}")),
        }
        Ok(())
    }

    #[inline]
    pub fn send_input_event(&self, ev: InputEvent) {
        let g = self.session.read().unwrap_or_else(|e| e.into_inner());
        if let Some(handle) = g.as_ref() {
            handle.send_input(ev);
        }
    }

    pub fn poll_session_updates(&self, include_media: bool) -> Option<Value> {
        let mut updates: Vec<Value> = Vec::new();
        let mut file_events: Vec<Value> = Vec::new();
        let mut terminal_events: Vec<Value> = Vec::new();
        let mut portmap_rules = None;
        let running;
        {
            let g = self.session.read().unwrap_or_else(|e| e.into_inner());
            if let Some(handle) = g.as_ref() {
                running = handle.is_running();
                while let Some(u) = handle.try_recv_update() {
                    if let Ok(v) = serde_json::to_value(&u) {
                        updates.push(v);
                    }
                    if matches!(u, SessionUpdate::Ended { .. }) {
                        break;
                    }
                }
                while let Some(ev) = handle.try_recv_file_event() {
                    if let Ok(v) = serde_json::to_value(ev) {
                        file_events.push(v);
                    }
                }
                while let Some(ev) = handle.try_recv_terminal_event() {
                    if let Ok(v) = serde_json::to_value(ev) {
                        terminal_events.push(v);
                    }
                }
                portmap_rules = handle.take_portmap_update();
            } else {
                running = false;
            }
        }
        let clipboard_texts = drain_inbound_clipboard_texts();
        let prev_running = self.last_session_running.swap(running, Ordering::AcqRel);
        let running_changed = prev_running != running;
        let emit_media = include_media || check_unreported_first_frame();

        if !running_changed
            && updates.is_empty()
            && clipboard_texts.is_empty()
            && file_events.is_empty()
            && terminal_events.is_empty()
            && portmap_rules.is_none()
            && !emit_media
        {
            return None;
        }

        let mut out = serde_json::Map::with_capacity(7);
        out.insert("running".to_string(), Value::Bool(running));
        if !updates.is_empty() {
            out.insert("updates".to_string(), Value::Array(updates));
        }
        if !clipboard_texts.is_empty() {
            out.insert(
                "clipboardTexts".to_string(),
                serde_json::to_value(clipboard_texts).unwrap_or(Value::Null),
            );
        }
        if !file_events.is_empty() {
            out.insert("fileEvents".to_string(), Value::Array(file_events));
        }
        if !terminal_events.is_empty() {
            out.insert("terminalEvents".to_string(), Value::Array(terminal_events));
        }
        if let Some(rules) = portmap_rules {
            out.insert(
                "portmapRules".to_string(),
                serde_json::to_value(rules).unwrap_or(Value::Null),
            );
        }
        if emit_media {
            out.insert(
                "media".to_string(),
                serde_json::to_value(media_diagnostics()).unwrap_or(Value::Null),
            );
        }
        Some(Value::Object(out))
    }

    pub fn push_clipboard_local(&self, text: String) {
        push_local_clipboard_text(text);
    }

    pub fn push_ime_silent(&self, text: String) {
        push_silent_ime_text(text);
    }

    pub fn tool_call(&self, req: &Value) -> Result<Value, String> {
        let g = self.session.read().unwrap_or_else(|e| e.into_inner());
        let handle = g.as_ref().ok_or_else(|| "请先建立远控会话".to_string())?;
        let tools = handle.tools();
        let action = req["action"].as_str().unwrap_or("");
        match action {
            // ── File Transfer ──
            "file_list_remote" => {
                let path = req["path"].as_str().map(str::to_string);
                tools.file_list_remote(path)?;
                Ok(json!({ "ok": true }))
            }
            "file_mkdir_remote" => {
                let parent = req["parent"].as_str().unwrap_or("").to_string();
                let name = req["name"].as_str().unwrap_or("").to_string();
                tools.file_mkdir_remote(parent, name)?;
                Ok(json!({ "ok": true }))
            }
            "file_upload" => {
                let local_path = req["localPath"].as_str().unwrap_or("");
                let remote_dir = req["remoteDir"].as_str().unwrap_or("");
                let id = tools.file_upload(local_path, remote_dir)?;
                Ok(json!({ "id": id }))
            }
            "file_download" => {
                let remote_path = req["remotePath"].as_str().unwrap_or("");
                let local_dir = req["localDir"].as_str().unwrap_or("");
                let id = tools.file_download(remote_path, local_dir)?;
                Ok(json!({ "id": id }))
            }
            "file_cancel" => {
                let id = req["id"].as_u64().unwrap_or(0);
                tools.file_cancel(id)?;
                Ok(json!({ "ok": true }))
            }
            "file_list_transfers" => Ok(json!({ "transfers": tools.file_list_transfers() })),
            "file_clear_finished" => {
                tools.file_clear_finished()?;
                Ok(json!({ "ok": true }))
            }
            "file_remove_transfer" => {
                let id = req["id"].as_u64().unwrap_or(0);
                tools.file_remove_transfer(id)?;
                Ok(json!({ "ok": true }))
            }
            "file_poll_events" => {
                let mut events = Vec::new();
                while let Some(ev) = handle.try_recv_file_event() {
                    if let Ok(v) = serde_json::to_value(ev) {
                        events.push(v);
                    }
                }
                Ok(json!({ "events": events }))
            }

            // ── Terminal ──
            "terminal_list" => {
                tools.terminal_list()?;
                Ok(json!({ "ok": true }))
            }
            "terminal_open" => {
                let cols = req["cols"].as_u64().unwrap_or(80) as u16;
                let rows = req["rows"].as_u64().unwrap_or(24) as u16;
                let name = req["name"].as_str().map(str::to_string);
                let attach = req["attach"].as_bool().unwrap_or(true);
                let id = tools.terminal_open_with_options(cols, rows, name, attach)?;
                Ok(json!({ "termId": id }))
            }
            "terminal_attach" => {
                let term_id = req["termId"].as_u64().unwrap_or(0) as u32;
                let cols = req["cols"].as_u64().unwrap_or(80) as u16;
                let rows = req["rows"].as_u64().unwrap_or(24) as u16;
                tools.terminal_attach(term_id, cols, rows)?;
                Ok(json!({ "ok": true }))
            }
            "terminal_detach" => {
                let term_id = req["termId"].as_u64().unwrap_or(0) as u32;
                tools.terminal_detach(term_id)?;
                Ok(json!({ "ok": true }))
            }
            "terminal_rename" => {
                let term_id = req["termId"].as_u64().unwrap_or(0) as u32;
                let name = req["name"].as_str().unwrap_or("");
                tools.terminal_rename(term_id, name)?;
                Ok(json!({ "ok": true }))
            }
            "terminal_input" => {
                let term_id = req["termId"].as_u64().unwrap_or(0) as u32;
                let b64 = req["dataBase64"].as_str().unwrap_or("");
                use base64::Engine as _;
                let bytes = base64::engine::general_purpose::STANDARD
                    .decode(b64)
                    .map_err(|e| format!("无效终端输入编码: {e}"))?;
                tools.terminal_input(term_id, &bytes)?;
                Ok(json!({ "ok": true }))
            }
            "terminal_resize" => {
                let term_id = req["termId"].as_u64().unwrap_or(0) as u32;
                let cols = req["cols"].as_u64().unwrap_or(80) as u16;
                let rows = req["rows"].as_u64().unwrap_or(24) as u16;
                tools.terminal_resize(term_id, cols, rows)?;
                Ok(json!({ "ok": true }))
            }
            "terminal_close" => {
                let term_id = req["termId"].as_u64().unwrap_or(0) as u32;
                tools.terminal_close(term_id)?;
                Ok(json!({ "ok": true }))
            }
            "terminal_poll_events" => {
                let mut events = Vec::new();
                while let Some(ev) = handle.try_recv_terminal_event() {
                    if let Ok(v) = serde_json::to_value(ev) {
                        events.push(v);
                    }
                }
                Ok(json!({ "events": events }))
            }

            // ── PortMap ──
            "portmap_list" => Ok(json!({ "rules": tools.portmap_list() })),
            "portmap_add" => {
                let name = req["name"].as_str().unwrap_or("").to_string();
                let local_port = req["localPort"].as_u64().unwrap_or(0) as u16;
                let target_addr = req["targetAddr"].as_str().unwrap_or("127.0.0.1").to_string();
                let target_port = req["targetPort"].as_u64().unwrap_or(0) as u16;
                let (rule, all) = tools.portmap_add(name, local_port, target_addr, target_port)?;
                Ok(json!({ "rule": rule, "rules": all }))
            }
            "portmap_toggle" => {
                let id = req["id"].as_u64().unwrap_or(0);
                let enabled = req["enabled"].as_bool().unwrap_or(true);
                let (rule, all) = tools.portmap_toggle(id, enabled)?;
                Ok(json!({ "rule": rule, "rules": all }))
            }
            "portmap_delete" => {
                let id = req["id"].as_u64().unwrap_or(0);
                let all = tools.portmap_delete(id)?;
                Ok(json!({ "rules": all }))
            }
            "portmap_poll_updates" => {
                let update = handle.take_portmap_update();
                Ok(json!({ "rules": update }))
            }
            other => Err(format!("未知工具命令: {other}")),
        }
    }

    fn build_owner_verifier(&self) -> Option<Arc<dyn OwnerTicketVerifier>> {
        let mut cfg = self.auth.server_config();
        let pubkey_hex = match cfg.server_auth_pubkey_hex.clone() {
            Some(pk) if !pk.trim().is_empty() => pk,
            _ => {
                if !cfg.is_configured() {
                    return None;
                }
                let client = AuthHttpClient::new(
                    cfg.server_host.clone(),
                    cfg.api_port,
                    cfg.use_https,
                    cfg.ca_cert_pem.clone(),
                )
                .ok()?;
                let bundle = client.fetch_ca_bundle().ok()?;
                cfg.server_auth_pubkey_hex = Some(bundle.auth_pubkey_hex.clone());
                self.auth.update_server_config(cfg.clone());
                bundle.auth_pubkey_hex
            }
        };
        let user_id = self.auth.current_session()?.user_id;
        let did = self
            .signal_snapshot
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .device_id
            .clone()
            .or(cfg.cached_device_id)?;
        RcOwnerTicketVerifier::new(&pubkey_hex, user_id, did)
            .ok()
            .map(|v| Arc::new(v) as Arc<dyn OwnerTicketVerifier>)
    }

    pub fn start_host(&self, req: StartHostRequest) -> Result<Value, String> {
        self.stop_host();

        if let Some(did) = self
            .signal_snapshot
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .device_id
            .clone()
        {
            let _ = self.auth.ensure_device_cert(&did);
        }

        let mtls_config = self.build_mtls_bundle(None, None);
        let has_mtls = mtls_config.is_some();
        let owner_verifier = self.build_owner_verifier();
        let owner_ready = owner_verifier.is_some();

        self.host_allow_remote_assist
            .store(req.allow_remote_assist, Ordering::Release);
        if let Some(sig) = self.signal.lock().unwrap_or_else(|e| e.into_inner()).as_ref() {
            let _ = sig.set_allow_remote_assist(req.allow_remote_assist);
        }

        let password = if let Some(custom) = req.password.filter(|p| !p.trim().is_empty()) {
            Some(custom)
        } else if req.allow_remote_assist {
            Some(generate_temp_code())
        } else {
            None
        };

        let ui_cfg = UiHostConfig {
            listen_addr: SocketAddr::from(([0, 0, 0, 0], DEFAULT_HOST_PORT)),
            password: password.clone(),
            prefer_hardware: true,
            require_approval: req.require_approval,
            auto_accept_with_password: true,
            auto_rotate_password: req.allow_remote_assist,
            clipboard_enabled: true,
            audio_enabled: false,
            allow_remote_assist: req.allow_remote_assist,
            owner_verifier,
            mtls_config,
            accept_mtls_lan: has_mtls,
            forbid_passwordless_auto_approve: false,
            ..Default::default()
        };

        host_android::reset_host_queues();
        let handle = HostHandle::spawn(ui_cfg)?;
        let listen_addr = handle.listen_addr().to_string();
        *self.host.lock().unwrap_or_else(|e| e.into_inner()) = Some(handle);
        self.last_host_running.store(true, Ordering::Release);

        Ok(json!({
            "started": true,
            "listenAddr": listen_addr,
            "password": password,
            "allowRemoteAssist": req.allow_remote_assist,
            "ownerSelfControlReady": owner_ready,
        }))
    }

    pub fn stop_host(&self) {
        self.host_allow_remote_assist.store(false, Ordering::Release);
        self.last_host_running.store(false, Ordering::Release);
        if let Some(sig) = self.signal.lock().unwrap_or_else(|e| e.into_inner()).as_ref() {
            let _ = sig.set_allow_remote_assist(false);
        }
        let old = self.host.lock().unwrap_or_else(|e| e.into_inner()).take();
        if let Some(mut handle) = old {
            handle.stop();
        }
        host_android::reset_host_queues();
    }

    pub fn host_command(&self, cmd: &Value) -> Result<Value, String> {
        let kind = cmd["type"].as_str().unwrap_or("");
        match kind {
            "set_allow_remote_assist" => {
                let enabled = cmd["enabled"].as_bool().unwrap_or(false);
                self.host_allow_remote_assist
                    .store(enabled, Ordering::Release);
                if let Some(sig) = self.signal.lock().unwrap_or_else(|e| e.into_inner()).as_ref() {
                    let _ = sig.set_allow_remote_assist(enabled);
                }
                let new_pw = if enabled {
                    Some(generate_temp_code())
                } else {
                    None
                };
                if let Some(host) = self.host.lock().unwrap_or_else(|e| e.into_inner()).as_ref() {
                    host.set_password_mode(new_pw.clone(), enabled);
                    host.set_allow_remote_assist(enabled);
                }
                Ok(json!({
                    "ok": true,
                    "allowRemoteAssist": enabled,
                    "password": new_pw,
                }))
            }
            "respond_approval" => {
                let generation = cmd["generation"].as_u64().unwrap_or(0);
                let accepted = cmd["accepted"].as_bool().unwrap_or(false);
                let applied = self
                    .host
                    .lock()
                    .unwrap_or_else(|e| e.into_inner())
                    .as_ref()
                    .is_some_and(|h| h.respond_approval(generation, accepted));
                Ok(json!({ "ok": applied }))
            }
            "disconnect_client" => {
                if let Some(host) = self.host.lock().unwrap_or_else(|e| e.into_inner()).as_ref() {
                    host.disconnect_client();
                }
                Ok(json!({ "ok": true }))
            }
            "rotate_password" => {
                let new_pw = generate_temp_code();
                if let Some(host) = self.host.lock().unwrap_or_else(|e| e.into_inner()).as_ref() {
                    host.set_password_mode(Some(new_pw.clone()), true);
                }
                Ok(json!({ "password": new_pw }))
            }
            other => Err(format!("未知被控端指令: {other}")),
        }
    }

    pub fn poll_host_events(&self) -> Option<Value> {
        self.pump_incoming_relay();
        let mut updates = Vec::new();
        let running;
        {
            let g = self.host.lock().unwrap_or_else(|e| e.into_inner());
            if let Some(handle) = g.as_ref() {
                running = handle.is_running();
                while let Some(u) = handle.try_recv_update() {
                    if let Ok(v) = serde_json::to_value(&u) {
                        updates.push(v);
                    }
                }
            } else {
                running = false;
            }
        }
        let encoder_cmds = host_android::drain_host_encoder_commands();
        let input_events = host_android::drain_host_input_events();
        let prev_running = self.last_host_running.swap(running, Ordering::AcqRel);
        if prev_running == running
            && updates.is_empty()
            && encoder_cmds.is_empty()
            && input_events.is_empty()
        {
            return None;
        }
        Some(json!({
            "running": running,
            "updates": updates,
            "encoderCommands": encoder_cmds,
            "inputEvents": input_events,
        }))
    }
}

pub fn decode_mouse_button(code: i32) -> Option<MouseButton> {
    match code {
        0 => Some(MouseButton::Left),
        1 => Some(MouseButton::Right),
        2 => Some(MouseButton::Middle),
        3 => Some(MouseButton::X1),
        4 => Some(MouseButton::X2),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use rc_client_core::auth_client::load_auth_session;

    #[test]
    fn close_active_session_only_preserves_epoch() {
        let temp_dir = std::env::temp_dir().join(format!(
            "rc-runtime-epoch-test-{}",
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map(|d| d.as_nanos())
                .unwrap_or(0)
        ));
        let _ = std::fs::create_dir_all(&temp_dir);
        let rt = init_runtime(temp_dir.clone(), "test-android".to_string(), Vec::new())
            .expect("init_runtime");
        let my_epoch = rt.session_epoch.fetch_add(1, Ordering::AcqRel) + 1;
        rt.close_active_session_only(false);
        assert_eq!(rt.session_epoch.load(Ordering::Acquire), my_epoch);

        rt.disconnect_session(false);
        assert_eq!(rt.session_epoch.load(Ordering::Acquire), my_epoch + 1);
        let _ = std::fs::remove_dir_all(&temp_dir);
    }

    #[test]
    fn apply_and_persist_session_writes_auth_json_and_updates_memory() {
        let temp_dir = std::env::temp_dir().join(format!(
            "rc-runtime-auth-persist-test-{}",
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map(|d| d.as_nanos())
                .unwrap_or(0)
        ));
        let certs_dir = temp_dir.join("certs");
        let auth_path = temp_dir.join("auth.json");
        let cert_mgr = Arc::new(CertManager::load_or_init(certs_dir).expect("cert_mgr"));
        let auth = AuthSessionManager::new(
            ServerConfig::default(),
            auth_path.clone(),
            Arc::clone(&cert_mgr),
        );
        let rt = AndroidRuntime {
            device_name: Mutex::new("test-android".to_string()),
            auth_path: auth_path.clone(),
            auth,
            cert_mgr,
            signal: Mutex::new(None),
            signal_events: Arc::new(Mutex::new(VecDeque::new())),
            signal_snapshot: Arc::new(Mutex::new(ServerConfig::default().disabled_snapshot())),
            signal_dirty: Arc::new(AtomicBool::new(true)),
            last_reported_refresh: Mutex::new(String::new()),
            lan: Mutex::new(None),
            session: RwLock::new(None),
            last_session_running: AtomicBool::new(false),
            session_epoch: AtomicU64::new(0),
            host: Mutex::new(None),
            last_host_running: AtomicBool::new(false),
            host_allow_remote_assist: AtomicBool::new(false),
        };

        let stored = StoredAuthSession {
            user_id: "user-123".to_string(),
            email: "user@example.com".to_string(),
            display_name: "Test".to_string(),
            role: "user".to_string(),
            must_change_password: false,
            access_token: "acc-token".to_string(),
            refresh_token: "ref-token".to_string(),
            access_expires_at_unix_sec: 1234567890,
            server_host: "example.com".to_string(),
            api_port: 21118,
        };

        rt.apply_and_persist_session(stored.clone());

        let disk = load_auth_session(&auth_path).expect("auth.json should exist on disk");
        assert_eq!(disk.user_id, stored.user_id);
        assert_eq!(disk.refresh_token, stored.refresh_token);
        let mem = rt
            .auth
            .current_session()
            .expect("in-memory session should be set");
        assert_eq!(mem.user_id, stored.user_id);

        let _ = std::fs::remove_dir_all(&temp_dir);
    }
}
