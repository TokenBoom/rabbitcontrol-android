//! Android-facing controller primitives & full native session runtime JNI bridge.

use jni::objects::{JObject, JString};
use jni::sys::{jboolean, jbyteArray, jint, jstring};
use jni::JNIEnv;
use rc_client_core::input_bridge::normalize_pointer;
use rc_client_core::proxy::ProxyConfig;
use rc_client_core::ui_error::{codes, resolve_ui_error, ErrorPlatform, IntoUiError, UiError};
use rc_client_core::version::core_build_info;
use rc_input::{encode_events, InputEvent, MouseButton};
use serde_json::{json, Value};
use std::path::PathBuf;
use std::ptr;

mod account;
mod annexb;
mod host_android;
mod ndk_audio;
mod ndk_media;
mod platform_android;
mod runtime;

pub const CORE_ABI_VERSION: u32 = rc_client_core::version::CORE_ABI_VERSION;

pub fn core_info_json() -> Value {
    let info = core_build_info();
    json!({
        "coreVersion": info.core_version,
        "gitCommit": info.git_commit,
        "buildTimestamp": info.build_timestamp,
        "sessionProtocolVersion": info.session_protocol_version,
        "punchVersion": info.punch_version,
        "beaconVersion": info.beacon_version,
        "abiVersion": info.abi_version,
        // Backward-compatible aliases
        "coreGitSha": info.git_commit,
        "builtAt": info.build_timestamp,
    })
}

pub(crate) fn throw_ui_error(env: &mut JNIEnv, class: &str, err: &UiError) {
    let payload = serde_json::to_string(err).unwrap_or_else(|_| err.message.clone());
    let _ = env.throw_new(class, payload);
}

pub(crate) fn throw_resolved_ui_error(env: &mut JNIEnv, code: &str, detail: Option<String>) {
    let err = resolve_ui_error(code, ErrorPlatform::Android, &[], None, detail);
    throw_ui_error(env, "java/lang/IllegalStateException", &err);
}

fn jstr_to_string(env: &mut JNIEnv, s: &JString) -> Option<String> {
    if s.is_null() {
        return None;
    }
    env.get_string(s).ok().map(Into::into)
}

fn string_to_jstr(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s)
        .map_or(ptr::null_mut(), JString::into_raw)
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_coreInfo(
    mut env: JNIEnv,
    _: JObject,
) -> jstring {
    string_to_jstr(&mut env, &core_info_json().to_string())
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_resolveUiError(
    mut env: JNIEnv,
    _: JObject,
    code: JString,
    detail: JString,
) -> jstring {
    let raw_code = jstr_to_string(&mut env, &code).unwrap_or_else(|| codes::COMMON_UNKNOWN.to_string());
    let raw_detail = jstr_to_string(&mut env, &detail).filter(|s| !s.trim().is_empty());
    let ui_err = resolve_ui_error(&raw_code, ErrorPlatform::Android, &[], None, raw_detail);
    let json_str = serde_json::to_string(&ui_err).unwrap_or_else(|_| "{}".to_string());
    string_to_jstr(&mut env, &json_str)
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_initRuntime(
    mut env: JNIEnv,
    _: JObject,
    files_dir: JString,
    device_name: JString,
    device_key: JString,
    codecs_json: JString,
) -> jstring {
    let Some(dir) = jstr_to_string(&mut env, &files_dir) else {
        throw_resolved_ui_error(
            &mut env,
            codes::VALIDATION_INVALID_INPUT,
            Some("无效的 filesDir".to_string()),
        );
        return ptr::null_mut();
    };
    let name = jstr_to_string(&mut env, &device_name).unwrap_or_else(|| "Android".to_string());
    let stable_device_key = jstr_to_string(&mut env, &device_key);
    let codecs_raw = jstr_to_string(&mut env, &codecs_json).unwrap_or_else(|| "[]".to_string());
    let codecs: Vec<ndk_media::CodecCandidate> =
        serde_json::from_str(&codecs_raw).unwrap_or_default();

    match runtime::init_runtime(PathBuf::from(dir), name, stable_device_key, codecs) {
        Ok(rt) => string_to_jstr(
            &mut env,
            &json!({
                "deviceKey": rt.device_key(),
                "devicePubkey": rt.cert_manager().device_pubkey_hex(),
            })
            .to_string(),
        ),
        Err(err) => {
            throw_ui_error(&mut env, "java/lang/IllegalStateException", &err);
            ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_deviceId(
    mut env: JNIEnv,
    _: JObject,
    raw: JString,
) -> jstring {
    let Some(input) = jstr_to_string(&mut env, &raw) else {
        return ptr::null_mut();
    };
    rc_client_core::server_config::parse_device_id_input(&input)
        .and_then(|id| env.new_string(id).ok())
        .map_or(ptr::null_mut(), JString::into_raw)
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_pointer(
    _: JNIEnv,
    _: JObject,
    x: f32,
    y: f32,
    width: jint,
    height: jint,
) -> jint {
    let (x, y) = normalize_pointer(x.into(), y.into(), width.into(), height.into());
    (u32::from(x) << 16 | u32::from(y)) as jint
}

fn input(kind: jint, a: jint, b: jint) -> Option<InputEvent> {
    match kind {
        0 => Some(InputEvent::MouseMove {
            x: a.try_into().ok()?,
            y: b.try_into().ok()?,
        }),
        1 => Some(InputEvent::MouseButton {
            button: match a {
                0 => MouseButton::Left,
                1 => MouseButton::Right,
                2 => MouseButton::Middle,
                3 => MouseButton::X1,
                4 => MouseButton::X2,
                _ => return None,
            },
            pressed: match b {
                0 => false,
                1 => true,
                _ => return None,
            },
        }),
        2 => Some(InputEvent::MouseWheel {
            delta: a.try_into().ok()?,
            horizontal: b != 0,
        }),
        _ => None,
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_encodeInput(
    mut env: JNIEnv,
    _: JObject,
    kind: jint,
    a: jint,
    b: jint,
) -> jbyteArray {
    let Some(event) = input(kind, a, b) else {
        let _ = env.throw_new("java/lang/IllegalArgumentException", "无效的输入事件");
        return ptr::null_mut();
    };
    let mut bytes = Vec::with_capacity(8);
    encode_events(&[event], &mut bytes);
    match env.byte_array_from_slice(&bytes) {
        Ok(result) => result.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_attachSurface(
    mut env: JNIEnv,
    _: JObject,
    surface: JObject,
) {
    #[cfg(target_os = "android")]
    {
        if surface.is_null() {
            ndk_media::detach_surface();
        } else {
            ndk_media::attach_surface(&mut env, surface);
        }
    }
    #[cfg(not(target_os = "android"))]
    let _ = (&mut env, surface);
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_detachSurface(
    _: JNIEnv,
    _: JObject,
) {
    ndk_media::detach_surface();
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_pollSignalEvents(
    mut env: JNIEnv,
    _: JObject,
) -> jstring {
    let Some(rt) = runtime::global_runtime() else {
        return ptr::null_mut();
    };
    match rt.poll_signal_events() {
        Some(val) => string_to_jstr(&mut env, &val.to_string()),
        None => ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_onNetworkChanged(
    _: JNIEnv,
    _: JObject,
) {
    if let Some(rt) = runtime::global_runtime() {
        rt.on_network_changed();
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_getUpnpStatus(
    mut env: JNIEnv,
    _: JObject,
) -> jstring {
    let Some(rt) = runtime::global_runtime() else {
        return string_to_jstr(&mut env, r#"{"enabled":false,"status":{"state":"disabled"}}"#);
    };
    string_to_jstr(&mut env, &rt.upnp_status_payload().to_string())
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_setUpnpEnabled(
    mut env: JNIEnv,
    _: JObject,
    enabled: jboolean,
) -> jstring {
    let Some(rt) = runtime::global_runtime() else {
        return string_to_jstr(&mut env, r#"{"enabled":false,"status":{"state":"disabled"}}"#);
    };
    string_to_jstr(&mut env, &rt.set_upnp_enabled(enabled != 0).to_string())
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_openSession(
    mut env: JNIEnv,
    _: JObject,
    request_json: JString,
) -> jstring {
    let Some(rt) = runtime::global_runtime() else {
        throw_resolved_ui_error(&mut env, codes::SERVICE_RUNTIME_NOT_INITIALIZED, None);
        return ptr::null_mut();
    };
    let Some(raw) = jstr_to_string(&mut env, &request_json) else {
        throw_resolved_ui_error(
            &mut env,
            codes::VALIDATION_INVALID_INPUT,
            Some("无效的会话请求".to_string()),
        );
        return ptr::null_mut();
    };
    let req: runtime::OpenSessionRequest = match serde_json::from_str(&raw) {
        Ok(r) => r,
        Err(e) => {
            throw_resolved_ui_error(
                &mut env,
                codes::VALIDATION_INVALID_JSON,
                Some(format!("解析会话请求失败: {e}")),
            );
            return ptr::null_mut();
        }
    };
    match rt.open_session(req) {
        Ok(val) => string_to_jstr(&mut env, &val.to_string()),
        Err(err) => {
            throw_ui_error(&mut env, "java/lang/IllegalStateException", &err);
            ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_disconnectSession(
    _: JNIEnv,
    _: JObject,
    lock_remote: jboolean,
) {
    if let Some(rt) = runtime::global_runtime() {
        rt.disconnect_session(lock_remote != 0);
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_setPreferHardwareDecode(
    _: JNIEnv,
    _: JObject,
    prefer_hardware: jboolean,
) {
    crate::ndk_media::set_prefer_hardware_decode(prefer_hardware != 0);
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_sendSessionCommand(
    mut env: JNIEnv,
    _: JObject,
    command_json: JString,
) {
    let Some(rt) = runtime::global_runtime() else {
        return;
    };
    let Some(raw) = jstr_to_string(&mut env, &command_json) else {
        return;
    };
    if let Ok(val) = serde_json::from_str::<Value>(&raw) {
        let _ = rt.send_command(&val);
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_pollSessionUpdates(
    mut env: JNIEnv,
    _: JObject,
    include_media: jboolean,
) -> jstring {
    let Some(rt) = runtime::global_runtime() else {
        return ptr::null_mut();
    };
    match rt.poll_session_updates(include_media != 0) {
        Some(val) => string_to_jstr(&mut env, &val.to_string()),
        None => ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_sendMouseMove(
    _: JNIEnv,
    _: JObject,
    x: jint,
    y: jint,
) {
    if let (Some(rt), Ok(nx), Ok(ny)) = (
        runtime::global_runtime(),
        u16::try_from(x),
        u16::try_from(y),
    ) {
        rt.send_input_event(InputEvent::MouseMove { x: nx, y: ny });
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_sendMouseButton(
    _: JNIEnv,
    _: JObject,
    button: jint,
    pressed: jboolean,
) {
    if let (Some(rt), Some(btn)) = (
        runtime::global_runtime(),
        runtime::decode_mouse_button(button),
    ) {
        rt.send_input_event(InputEvent::MouseButton {
            button: btn,
            pressed: pressed != 0,
        });
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_sendMouseWheel(
    _: JNIEnv,
    _: JObject,
    delta: jint,
    horizontal: jboolean,
) {
    if let Some(rt) = runtime::global_runtime() {
        let clamped = delta.clamp(i32::from(i16::MIN), i32::from(i16::MAX)) as i16;
        if clamped != 0 {
            rt.send_input_event(InputEvent::MouseWheel {
                delta: clamped,
                horizontal: horizontal != 0,
            });
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_sendKey(
    _: JNIEnv,
    _: JObject,
    scancode: jint,
    pressed: jboolean,
    modifiers: jint,
) {
    if let (Some(rt), Ok(code), Ok(mods)) = (
        runtime::global_runtime(),
        u16::try_from(scancode),
        u8::try_from(modifiers & 0xFF),
    ) {
        rt.send_input_event(InputEvent::Key {
            scancode: code,
            pressed: pressed != 0,
            modifiers: mods,
        });
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_pushClipboardLocal(
    mut env: JNIEnv,
    _: JObject,
    text: JString,
) {
    if let (Some(rt), Some(s)) = (runtime::global_runtime(), jstr_to_string(&mut env, &text)) {
        rt.push_clipboard_local(s);
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_pushImeSilent(
    mut env: JNIEnv,
    _: JObject,
    text: JString,
) {
    if let (Some(rt), Some(s)) = (runtime::global_runtime(), jstr_to_string(&mut env, &text)) {
        rt.push_ime_silent(s);
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_toolCall(
    mut env: JNIEnv,
    _: JObject,
    request_json: JString,
) -> jstring {
    let Some(rt) = runtime::global_runtime() else {
        throw_resolved_ui_error(&mut env, codes::SERVICE_RUNTIME_NOT_INITIALIZED, None);
        return ptr::null_mut();
    };
    let Some(raw) = jstr_to_string(&mut env, &request_json) else {
        throw_resolved_ui_error(
            &mut env,
            codes::VALIDATION_INVALID_INPUT,
            Some("无效的工具请求".to_string()),
        );
        return ptr::null_mut();
    };
    let req: Value = match serde_json::from_str(&raw) {
        Ok(v) => v,
        Err(e) => {
            throw_resolved_ui_error(
                &mut env,
                codes::VALIDATION_INVALID_JSON,
                Some(format!("解析工具请求失败: {e}")),
            );
            return ptr::null_mut();
        }
    };
    match rt.tool_call(&req) {
        Ok(val) => string_to_jstr(&mut env, &val.to_string()),
        Err(err) => {
            throw_ui_error(&mut env, "java/lang/IllegalStateException", &err);
            ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_setHostScreenMetrics(
    _: JNIEnv,
    _: JObject,
    width: jint,
    height: jint,
) {
    if width > 0 && height > 0 {
        host_android::set_host_screen_metrics(width as u32, height as u32);
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_startHost(
    mut env: JNIEnv,
    _: JObject,
    config_json: JString,
) -> jstring {
    let Some(rt) = runtime::global_runtime() else {
        throw_resolved_ui_error(&mut env, codes::SERVICE_RUNTIME_NOT_INITIALIZED, None);
        return ptr::null_mut();
    };
    let raw = jstr_to_string(&mut env, &config_json).unwrap_or_else(|| "{}".to_string());
    let req: runtime::StartHostRequest = serde_json::from_str(&raw).unwrap_or_default();
    match rt.start_host(req) {
        Ok(val) => string_to_jstr(&mut env, &val.to_string()),
        Err(err) => {
            throw_ui_error(&mut env, "java/lang/IllegalStateException", &err);
            ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_stopHost(
    _: JNIEnv,
    _: JObject,
) {
    if let Some(rt) = runtime::global_runtime() {
        rt.stop_host();
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_hostCommand(
    mut env: JNIEnv,
    _: JObject,
    command_json: JString,
) -> jstring {
    let Some(rt) = runtime::global_runtime() else {
        throw_resolved_ui_error(&mut env, codes::SERVICE_RUNTIME_NOT_INITIALIZED, None);
        return ptr::null_mut();
    };
    let Some(raw) = jstr_to_string(&mut env, &command_json) else {
        throw_resolved_ui_error(
            &mut env,
            codes::VALIDATION_INVALID_INPUT,
            Some("无效被控端指令".to_string()),
        );
        return ptr::null_mut();
    };
    let cmd: Value = match serde_json::from_str(&raw) {
        Ok(v) => v,
        Err(e) => {
            throw_resolved_ui_error(
                &mut env,
                codes::VALIDATION_INVALID_JSON,
                Some(format!("解析被控端指令失败: {e}")),
            );
            return ptr::null_mut();
        }
    };
    match rt.host_command(&cmd) {
        Ok(val) => string_to_jstr(&mut env, &val.to_string()),
        Err(err) => {
            throw_ui_error(&mut env, "java/lang/IllegalStateException", &err);
            ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_pollHostEvents(
    mut env: JNIEnv,
    _: JObject,
) -> jstring {
    let Some(rt) = runtime::global_runtime() else {
        return ptr::null_mut();
    };
    match rt.poll_host_events() {
        Some(val) => string_to_jstr(&mut env, &val.to_string()),
        None => ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_pushHostEncodedFrame(
    env: JNIEnv,
    _: JObject,
    data: jni::objects::JByteArray,
    is_keyframe: jboolean,
) {
    if data.is_null() {
        return;
    }
    if let Ok(bytes) = env.convert_byte_array(&data) {
        host_android::push_host_encoded_frame(bytes, is_keyframe != 0);
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_setProxyConfig(
    mut env: JNIEnv,
    _: JObject,
    config_json: JString,
) -> jstring {
    let Some(raw) = jstr_to_string(&mut env, &config_json) else {
        throw_resolved_ui_error(
            &mut env,
            codes::VALIDATION_INVALID_INPUT,
            Some("无效代理配置".to_string()),
        );
        return ptr::null_mut();
    };
    let cfg: ProxyConfig = match serde_json::from_str(&raw) {
        Ok(c) => c,
        Err(e) => {
            throw_resolved_ui_error(
                &mut env,
                codes::VALIDATION_INVALID_JSON,
                Some(format!("解析代理配置失败: {e}")),
            );
            return ptr::null_mut();
        }
    };
    if let Err(err) = cfg.validate() {
        let ui_err = err.to_ui_error(ErrorPlatform::Android);
        throw_ui_error(&mut env, "java/lang/IllegalStateException", &ui_err);
        return ptr::null_mut();
    }
    let result = if let Some(rt) = runtime::global_runtime() {
        rt.set_proxy_config(cfg)
    } else {
        rc_client_core::proxy::set_global_proxy_config(cfg);
        Ok(json!({ "ok": true }))
    };
    match result {
        Ok(val) => string_to_jstr(&mut env, &val.to_string()),
        Err(err) => {
            throw_ui_error(&mut env, "java/lang/IllegalStateException", &err);
            ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_requestWol(
    mut env: JNIEnv,
    _: JObject,
    device_id: JString,
) -> jstring {
    let Some(rt) = runtime::global_runtime() else {
        throw_resolved_ui_error(&mut env, codes::SERVICE_RUNTIME_NOT_INITIALIZED, None);
        return ptr::null_mut();
    };
    let Some(did) = jstr_to_string(&mut env, &device_id) else {
        throw_resolved_ui_error(
            &mut env,
            codes::VALIDATION_INVALID_INPUT,
            Some("无效目标设备码".to_string()),
        );
        return ptr::null_mut();
    };
    match rt.request_wol(&did) {
        Ok(val) => string_to_jstr(&mut env, &val.to_string()),
        Err(err) => {
            throw_ui_error(&mut env, "java/lang/IllegalStateException", &err);
            ptr::null_mut()
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn android_gestures_roundtrip_through_core_wire_format() {
        let events = [
            (0, 65535, 0),
            (1, 0, 1),
            (0, 4, 5),
            (1, 0, 0),
            (1, 1, 1),
            (1, 1, 0),
            (2, -3, 0),
            (2, 2, 1),
        ]
        .map(|(kind, a, b)| input(kind, a, b).unwrap());
        let mut wire = Vec::new();
        encode_events(&events, &mut wire);
        assert_eq!(rc_input::decode_events(&wire).unwrap(), events);
        assert!(input(0, -1, 0).is_none());
        assert!(input(1, 5, 1).is_none());
        assert!(input(1, 1, 2).is_none());
    }

    #[test]
    fn core_info_json_exposes_real_core_build_info_and_abi_v2() {
        let info = core_info_json();
        assert_eq!(CORE_ABI_VERSION, 2);
        assert_eq!(info["abiVersion"].as_u64(), Some(2));
        assert_eq!(info["coreVersion"].as_str(), Some("0.2.2"));
        assert!(!info["gitCommit"].as_str().unwrap_or_default().is_empty());
        assert!(!info["buildTimestamp"].as_str().unwrap_or_default().is_empty());
        assert!(info["sessionProtocolVersion"].as_u64().is_some());
        assert!(info["punchVersion"].as_u64().is_some());
        assert!(info["beaconVersion"].as_u64().is_some());
    }

    #[test]
    fn resolve_ui_error_uses_android_platform_overrides() {
        let mp = resolve_ui_error(
            codes::PLATFORM_ANDROID_MEDIA_PROJECTION_DENIED,
            ErrorPlatform::Android,
            &[],
            None,
            Some("user cancelled".to_string()),
        );
        assert_eq!(mp.code, "platform.android.media_projection_denied");
        assert!(!mp.title.is_empty());
        assert!(!mp.suggestion.is_empty());
        assert_eq!(mp.detail.as_deref(), Some("user cancelled"));

        let acc = resolve_ui_error(
            codes::PLATFORM_ANDROID_ACCESSIBILITY_DENIED,
            ErrorPlatform::Android,
            &[],
            None,
            None,
        );
        assert_eq!(acc.code, "platform.android.accessibility_denied");
        assert!(!acc.message.is_empty());
    }
}
