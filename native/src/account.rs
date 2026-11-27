//! Server trust probing, authentication, device registration, and device cert issuance.

use jni::objects::{JObject, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use rc_client_core::auth_client::{
    AuthClientError, AuthHttpClient, AuthUserInfo, LoginRequest, RegisterRequest, StoredAuthSession,
};
use rc_client_core::server_config::{
    parse_server_input, ServerConfig, DEFAULT_API_PORT, DEFAULT_RELAY_PORT, DEFAULT_SIGNAL_PORT,
};
use rc_client_core::ui_error::{ErrorPlatform, IntoUiError};
use serde_json::{json, Value};
use std::ptr;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::runtime;

fn field<'a>(args: &'a Value, key: &str) -> Result<&'a str, AuthClientError> {
    args[key]
        .as_str()
        .ok_or_else(|| AuthClientError::Config(format!("缺少字段 {key}")))
}

fn opt_field<'a>(args: &'a Value, key: &str) -> Option<&'a str> {
    args[key].as_str().map(str::trim).filter(|s| !s.is_empty())
}

fn opt_port(args: &Value, key: &str) -> Result<Option<u16>, AuthClientError> {
    let val = &args[key];
    if val.is_null() {
        return Ok(None);
    }
    let port = val
        .as_u64()
        .ok_or_else(|| AuthClientError::Config(format!("无效端口号：{key}")))?;
    if port == 0 || port > u16::MAX as u64 {
        return Err(AuthClientError::Config(format!("端口号超出范围：{key}")));
    }
    Ok(Some(port as u16))
}

fn now_unix_sec() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

#[allow(clippy::too_many_arguments)]
fn build_server_config(
    address: &str,
    ca_pem: &str,
    ca_pin: Option<&str>,
    auth_pubkey_hex: Option<String>,
    api_port_override: Option<u16>,
    signal_port_override: Option<u16>,
    relay_port_override: Option<u16>,
    device_key: String,
    cached_device_id: Option<String>,
) -> Result<ServerConfig, AuthClientError> {
    let (host, parsed_api_port) =
        parse_server_input(address, DEFAULT_API_PORT).map_err(AuthClientError::Config)?;
    Ok(ServerConfig {
        enabled: true,
        server_host: host,
        signal_port: signal_port_override.unwrap_or(DEFAULT_SIGNAL_PORT),
        relay_port: relay_port_override.unwrap_or(DEFAULT_RELAY_PORT),
        api_port: api_port_override.unwrap_or(parsed_api_port),
        use_https: true,
        ca_fingerprint_sha256: ca_pin.map(|s| s.to_ascii_lowercase()),
        ca_cert_pem: Some(ca_pem.to_string()),
        server_auth_pubkey_hex: auth_pubkey_hex,
        device_key,
        cached_device_id,
    })
}

fn to_stored_session(
    host: &str,
    api_port: u16,
    access_token: &str,
    refresh_token: &str,
    access_expires_at_unix_sec: u64,
    user: &AuthUserInfo,
) -> StoredAuthSession {
    StoredAuthSession {
        user_id: user.id.trim().to_ascii_lowercase(),
        email: user.email.trim().to_ascii_lowercase(),
        display_name: user.display_name.clone(),
        role: user.role.clone(),
        must_change_password: user.must_change_password,
        access_token: access_token.to_string(),
        refresh_token: refresh_token.to_string(),
        access_expires_at_unix_sec,
        server_host: host.to_string(),
        api_port,
    }
}

#[allow(clippy::too_many_arguments)]
fn sync_runtime_session(
    address: &str,
    ca_pem: &str,
    ca_pin: Option<&str>,
    auth_pubkey_hex: Option<String>,
    api_port_override: Option<u16>,
    signal_port_override: Option<u16>,
    relay_port_override: Option<u16>,
    tokens: &Value,
    cached_device_id: Option<String>,
    is_restore: bool,
) {
    let Some(rt) = runtime::global_runtime() else {
        return;
    };
    let Ok((host, parsed_api_port)) = parse_server_input(address, DEFAULT_API_PORT) else {
        return;
    };
    let api_port = api_port_override.unwrap_or(parsed_api_port);
    let existing_cfg = rt.auth.server_config();
    let dev_id = tokens["device"]["device_id"]
        .as_str()
        .map(str::to_string)
        .or(cached_device_id)
        .or(existing_cfg.cached_device_id);
    let resolved_auth_pubkey = auth_pubkey_hex.or(existing_cfg.server_auth_pubkey_hex);
    let device_key = rt.device_key();
    let Ok(cfg) = build_server_config(
        address,
        ca_pem,
        ca_pin,
        resolved_auth_pubkey,
        Some(api_port),
        signal_port_override,
        relay_port_override,
        device_key,
        dev_id.clone(),
    ) else {
        return;
    };
    rt.auth.update_server_config(cfg);

    let fallback_user = rt.auth.current_session().map(|s| AuthUserInfo {
        id: s.user_id,
        email: s.email,
        display_name: s.display_name,
        role: s.role,
        status: "active".to_string(),
        must_change_password: s.must_change_password,
        token_version: 0,
    });

    let parsed_user = serde_json::from_value::<AuthUserInfo>(tokens["user"].clone())
        .ok()
        .or(fallback_user);

    if let (Some(access), Some(refresh), Some(user)) = (
        tokens["access_token"].as_str(),
        tokens["refresh_token"].as_str(),
        parsed_user,
    ) {
        let access_expires_at = tokens["access_expires_at_unix_sec"]
            .as_u64()
            .unwrap_or_else(|| {
                if is_restore {
                    // Force ensure_valid_access_token() to refresh if legacy stored tokens lacked expiry.
                    0
                } else {
                    now_unix_sec().saturating_add(tokens["expires_in_sec"].as_u64().unwrap_or(900))
                }
            });
        let stored = to_stored_session(&host, api_port, access, refresh, access_expires_at, &user);
        rt.apply_and_persist_session(stored);
        if !user.must_change_password {
            if is_restore {
                let _ = std::thread::Builder::new()
                    .name("rc-restore-bg".to_string())
                    .spawn(move || {
                        if let Some(bg_rt) = runtime::global_runtime() {
                            if let Some(ref did) = dev_id {
                                let _ = bg_rt.auth.ensure_device_cert(did);
                            }
                            bg_rt.ensure_signal_started();
                        }
                    });
            } else {
                if let Some(ref did) = dev_id {
                    let _ = rt.auth.ensure_device_cert(did);
                }
                rt.ensure_signal_started();
            }
        }
    }
}

fn probe(
    address: &str,
    fingerprint: &str,
    api_port_override: Option<u16>,
) -> Result<Value, AuthClientError> {
    let (host, parsed_port) =
        parse_server_input(address, DEFAULT_API_PORT).map_err(AuthClientError::Config)?;
    let port = api_port_override.unwrap_or(parsed_port);
    let pin = fingerprint.trim();
    if !pin.is_empty() && (pin.len() != 64 || !pin.bytes().all(|b| b.is_ascii_hexdigit())) {
        return Err(AuthClientError::Config(
            "CA 指纹应为 64 位十六进制 SHA-256".into(),
        ));
    }
    let client = AuthHttpClient::new(host, port, true, None)?;
    let result = client.probe_server(Some(pin))?;
    let ca = result
        .ca_bundle
        .ok_or_else(|| AuthClientError::Tls("服务端没有返回 CA".into()))?;
    Ok(json!({
        "status": result.health.status,
        "version": result.health.version,
        "elapsedMs": result.latency_ms,
        "apiPort": port,
        "caSha256": ca.ca_fingerprint_sha256,
        "caPem": ca.ca_cert_pem,
        "authPubkeyHex": ca.auth_pubkey_hex,
        "allowRegistration": result.allow_registration,
    }))
}

fn request(args: &Value) -> Result<Value, AuthClientError> {
    let action = field(args, "action")?;
    if action == "disconnect" {
        if let Some(rt) = runtime::global_runtime() {
            rt.stop_signal_and_session();
            rt.auth.wipe_local_credentials();
        }
        return Ok(json!({ "ok": true }));
    }
    let address = field(args, "address")?;
    let api_port_override = opt_port(args, "apiPort")?;
    let signal_port_override = opt_port(args, "signalPort")?;
    let relay_port_override = opt_port(args, "relayPort")?;
    if action == "probe" {
        return probe(address, field(args, "pin")?, api_port_override);
    }
    let ca_pem = field(args, "ca")?;
    let ca_pin = opt_field(args, "pin");
    let auth_pubkey_hex = opt_field(args, "authPubkeyHex").map(str::to_string);
    let (host, parsed_port) =
        parse_server_input(address, DEFAULT_API_PORT).map_err(AuthClientError::Config)?;
    let port = api_port_override.unwrap_or(parsed_port);
    let client = AuthHttpClient::new(host, port, true, Some(ca_pem.into()))?;

    let rt_info = runtime::global_runtime().map(|rt| {
        (
            rt.device_key(),
            rt.device_name(),
            rt.cert_manager().device_pubkey_hex(),
        )
    });

    match action {
        "auth_config" => {
            let cfg = client.fetch_auth_config()?;
            let fetched_pubkey = client
                .fetch_ca_bundle()
                .ok()
                .map(|b| b.auth_pubkey_hex);
            Ok(json!({
                "allowRegistration": cfg.allow_registration,
                "authPubkeyHex": fetched_pubkey,
            }))
        }
        "register" => {
            let (device_key, hostname, device_pubkey) = match rt_info {
                Some((dk, hn, pk)) => (Some(dk), Some(hn), Some(pk)),
                None => (None, None, None),
            };
            let tokens = client.register(&RegisterRequest {
                email: field(args, "email")?.into(),
                password: field(args, "password")?.into(),
                display_name: opt_field(args, "displayName").map(str::to_string),
                device_key,
                hostname,
                os: Some("Android".to_string()),
                app_version: opt_field(args, "appVersion").map(str::to_string),
                arch: Some(std::env::consts::ARCH.to_string()),
                device_pubkey,
            })?;
            let mut val = json!(tokens);
            let expires_at = now_unix_sec().saturating_add(tokens.expires_in_sec);
            val["access_expires_at_unix_sec"] = json!(expires_at);
            sync_runtime_session(
                address,
                ca_pem,
                ca_pin,
                auth_pubkey_hex,
                api_port_override,
                signal_port_override,
                relay_port_override,
                &val,
                None,
                false,
            );
            Ok(val)
        }
        "login" => {
            let (device_key, hostname, device_pubkey) = match rt_info {
                Some((dk, hn, pk)) => (Some(dk), Some(hn), Some(pk)),
                None => (None, None, None),
            };
            let tokens = client.login(&LoginRequest {
                email: field(args, "email")?.into(),
                password: field(args, "password")?.into(),
                device_key,
                hostname,
                os: Some("Android".to_string()),
                app_version: opt_field(args, "appVersion").map(str::to_string),
                arch: Some(std::env::consts::ARCH.to_string()),
                device_pubkey,
            })?;
            let mut val = json!(tokens);
            let expires_at = now_unix_sec().saturating_add(tokens.expires_in_sec);
            val["access_expires_at_unix_sec"] = json!(expires_at);
            sync_runtime_session(
                address,
                ca_pem,
                ca_pin,
                auth_pubkey_hex,
                api_port_override,
                signal_port_override,
                relay_port_override,
                &val,
                None,
                false,
            );
            Ok(val)
        }
        "restore" => {
            let tokens = &args["tokens"];
            let cached_did = opt_field(args, "deviceId").map(str::to_string);
            sync_runtime_session(
                address,
                ca_pem,
                ca_pin,
                auth_pubkey_hex,
                api_port_override,
                signal_port_override,
                relay_port_override,
                tokens,
                cached_did,
                true,
            );
            Ok(json!({ "ok": true }))
        }
        "refresh" => {
            let tokens = client.refresh(field(args, "refresh")?)?;
            let mut val = json!(tokens);
            let expires_at = now_unix_sec().saturating_add(tokens.expires_in_sec);
            val["access_expires_at_unix_sec"] = json!(expires_at);
            sync_runtime_session(
                address,
                ca_pem,
                ca_pin,
                auth_pubkey_hex,
                api_port_override,
                signal_port_override,
                relay_port_override,
                &val,
                opt_field(args, "deviceId").map(str::to_string),
                false,
            );
            Ok(val)
        }
        "password" => {
            let tokens = client.change_password(
                field(args, "access")?,
                field(args, "password")?,
                field(args, "replacement")?,
            )?;
            let mut val = json!(tokens);
            let expires_at = now_unix_sec().saturating_add(tokens.expires_in_sec);
            val["access_expires_at_unix_sec"] = json!(expires_at);
            sync_runtime_session(
                address,
                ca_pem,
                ca_pin,
                auth_pubkey_hex,
                api_port_override,
                signal_port_override,
                relay_port_override,
                &val,
                opt_field(args, "deviceId").map(str::to_string),
                false,
            );
            Ok(val)
        }
        "devices" => {
            let access = if let Some(rt) = runtime::global_runtime() {
                if rt.auth.current_session().is_some() {
                    rt.auth.ensure_valid_access_token()?
                } else {
                    field(args, "access")?.to_string()
                }
            } else {
                field(args, "access")?.to_string()
            };
            Ok(json!({ "devices": client.list_my_devices(&access)? }))
        }
        "remove_device" => {
            let access = if let Some(rt) = runtime::global_runtime() {
                if rt.auth.current_session().is_some() {
                    rt.auth.ensure_valid_access_token()?
                } else {
                    field(args, "access")?.to_string()
                }
            } else {
                field(args, "access")?.to_string()
            };
            let target_id = field(args, "targetDeviceId")?;
            client.remove_device(&access, target_id)?;
            Ok(json!({ "ok": true }))
        }
        "logout" => {
            if let Some(rt) = runtime::global_runtime() {
                rt.stop_signal_and_session();
                rt.auth.wipe_local_credentials();
            }
            let _ = client.logout(field(args, "access")?, Some(field(args, "refresh")?), None);
            Ok(json!({}))
        }
        _ => Err(AuthClientError::Config("不支持的账号操作".into())),
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_account(
    mut env: JNIEnv,
    _: JObject,
    raw: JString,
) -> jstring {
    let Ok(input) = env.get_string(&raw) else {
        return ptr::null_mut();
    };
    let input: String = input.into();
    let result = serde_json::from_str::<Value>(&input)
        .map_err(|_| AuthClientError::Config("无效的账号请求".into()))
        .and_then(|args| request(&args));
    match result {
        Ok(response) => env
            .new_string(response.to_string())
            .map_or(ptr::null_mut(), JString::into_raw),
        Err(error) => {
            let class = if error.is_unauthorized()
                || error.is_token_reuse()
                || matches!(error, AuthClientError::ReloginRequired(_))
            {
                "java/lang/SecurityException"
            } else {
                "java/lang/IllegalStateException"
            };
            let ui_err = error.to_ui_error(ErrorPlatform::Android);
            crate::throw_ui_error(&mut env, class, &ui_err);
            ptr::null_mut()
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn malformed_pin_is_rejected_before_network_io() {
        assert!(probe("localhost", "12", None)
            .unwrap_err()
            .to_string()
            .contains("64"));
    }

    #[test]
    fn build_server_config_applies_custom_ports_and_defaults() {
        let cfg = build_server_config(
            "example.com",
            "ca-pem",
            None,
            None,
            Some(31118),
            Some(31116),
            None,
            "dev-key".to_string(),
            None,
        )
        .unwrap();
        assert_eq!(cfg.server_host, "example.com");
        assert_eq!(cfg.api_port, 31118);
        assert_eq!(cfg.signal_port, 31116);
        assert_eq!(cfg.relay_port, DEFAULT_RELAY_PORT);
    }
}
