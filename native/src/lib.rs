//! Android-facing controller primitives & full native session runtime JNI bridge.

use jni::objects::{JObject, JString};
use jni::sys::{jboolean, jbyteArray, jint, jstring};
use jni::JNIEnv;
use rc_client_core::input_bridge::normalize_pointer;
use rc_input::{encode_events, InputEvent, MouseButton};
use serde_json::{json, Value};
use std::path::PathBuf;
use std::ptr;

mod account;
mod annexb;
mod ndk_audio;
mod ndk_media;
mod platform_android;
mod runtime;

fn throw_illegal_state(env: &mut JNIEnv, msg: &str) {
    let _ = env.throw_new("java/lang/IllegalStateException", msg);
}

fn jstr_to_string(env: &mut JNIEnv, s: &JString) -> Option<String> {
    env.get_string(s).ok().map(Into::into)
}

fn string_to_jstr(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s)
        .map_or(ptr::null_mut(), JString::into_raw)
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_initRuntime(
    mut env: JNIEnv,
    _: JObject,
    files_dir: JString,
    device_name: JString,
    codecs_json: JString,
) -> jstring {
    let Some(dir) = jstr_to_string(&mut env, &files_dir) else {
        throw_illegal_state(&mut env, "无效的 filesDir");
        return ptr::null_mut();
    };
    let name = jstr_to_string(&mut env, &device_name).unwrap_or_else(|| "Android".to_string());
    let codecs_raw = jstr_to_string(&mut env, &codecs_json).unwrap_or_else(|| "[]".to_string());
    let codecs: Vec<ndk_media::CodecCandidate> =
        serde_json::from_str(&codecs_raw).unwrap_or_default();

    match runtime::init_runtime(PathBuf::from(dir), name, codecs) {
        Ok(rt) => string_to_jstr(
            &mut env,
            &json!({
                "deviceKey": rt.device_key(),
                "devicePubkey": rt.cert_manager().device_pubkey_hex(),
            })
            .to_string(),
        ),
        Err(err) => {
            throw_illegal_state(&mut env, &err);
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
        return string_to_jstr(&mut env, "{\"snapshot\":{\"enabled\":false,\"state\":\"disabled\"},\"events\":[]}");
    };
    string_to_jstr(&mut env, &rt.poll_signal_events().to_string())
}

#[no_mangle]
pub extern "system" fn Java_io_rabbitcontrol_android_session_Core_openSession(
    mut env: JNIEnv,
    _: JObject,
    request_json: JString,
) -> jstring {
    let Some(rt) = runtime::global_runtime() else {
        throw_illegal_state(&mut env, "运行时尚未初始化");
        return ptr::null_mut();
    };
    let Some(raw) = jstr_to_string(&mut env, &request_json) else {
        throw_illegal_state(&mut env, "无效的会话请求");
        return ptr::null_mut();
    };
    let req: runtime::OpenSessionRequest = match serde_json::from_str(&raw) {
        Ok(r) => r,
        Err(e) => {
            throw_illegal_state(&mut env, &format!("解析会话请求失败: {e}"));
            return ptr::null_mut();
        }
    };
    match rt.open_session(req) {
        Ok(val) => string_to_jstr(&mut env, &val.to_string()),
        Err(err) => {
            throw_illegal_state(&mut env, &err);
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
) -> jstring {
    let Some(rt) = runtime::global_runtime() else {
        return string_to_jstr(&mut env, "{\"running\":false,\"updates\":[],\"clipboardTexts\":[]}");
    };
    string_to_jstr(&mut env, &rt.poll_session_updates().to_string())
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
        throw_illegal_state(&mut env, "运行时尚未初始化");
        return ptr::null_mut();
    };
    let Some(raw) = jstr_to_string(&mut env, &request_json) else {
        throw_illegal_state(&mut env, "无效的工具请求");
        return ptr::null_mut();
    };
    let req: Value = match serde_json::from_str(&raw) {
        Ok(v) => v,
        Err(e) => {
            throw_illegal_state(&mut env, &format!("解析工具请求失败: {e}"));
            return ptr::null_mut();
        }
    };
    match rt.tool_call(&req) {
        Ok(val) => string_to_jstr(&mut env, &val.to_string()),
        Err(err) => {
            throw_illegal_state(&mut env, &err);
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
}
