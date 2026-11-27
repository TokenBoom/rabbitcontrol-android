//! Registration of Android hardware backends into rabbitcontrol-core hooks.

use std::collections::VecDeque;
use std::sync::{Arc, Mutex, OnceLock};
use std::time::Instant;

use rc_audio::{AudioBackendProvider, AudioCapture, AudioPlayer};
use rc_capture::CaptureBackend;
use rc_client_core::platform::{
    ClientPlatformHooks, HardwareRendererOutput, PlatformDisplayMode, PreallocatedRenderSurface,
};
use rc_decode::Decoder;
use rc_encode::Encoder;
use rc_pipeline::{CodecCapability, PipelineConfig, PlatformMediaProvider};
use rc_render::Renderer;
use rc_session::ClipboardBackend;
use rc_types::{Resolution, Result};

use crate::host_android::{
    self, AndroidCaptureBackend, AndroidInputInjector, AndroidPassthroughEncoder,
};
use crate::ndk_audio::AAudioPlayer;
use crate::ndk_media::{
    probed_decode_capabilities, supports_hardware_hevc, AndroidSurfaceDecoder, AndroidSurfaceRenderer,
};

#[derive(Default)]
struct ClipboardState {
    change_count: u64,
    outbound_text: Option<String>,
    inbound_queue: VecDeque<String>,
}

static CLIPBOARD_STATE: OnceLock<Mutex<ClipboardState>> = OnceLock::new();

fn clipboard_state() -> &'static Mutex<ClipboardState> {
    CLIPBOARD_STATE.get_or_init(|| Mutex::new(ClipboardState::default()))
}

/// Pushes text from Android's system ClipboardManager into Rust's ClipboardChannel.
pub fn push_local_clipboard_text(text: String) {
    let mut state = clipboard_state().lock().unwrap_or_else(|e| e.into_inner());
    state.outbound_text = Some(text);
    state.change_count = state.change_count.wrapping_add(1);
}

/// Pushes silent IME committed text (e.g. Chinese/Emoji phrase) into the remote clipboard channel
/// without touching Android's system clipboard.
pub fn push_silent_ime_text(text: String) {
    let mut state = clipboard_state().lock().unwrap_or_else(|e| e.into_inner());
    state.outbound_text = Some(text);
    state.change_count = state.change_count.wrapping_add(1);
}

/// Drains text received from the remote host so Android can write it to system ClipboardManager.
pub fn drain_inbound_clipboard_texts() -> Vec<String> {
    let mut state = clipboard_state().lock().unwrap_or_else(|e| e.into_inner());
    state.inbound_queue.drain(..).collect()
}

struct AndroidClipboardBackend;

impl ClipboardBackend for AndroidClipboardBackend {
    fn change_count(&mut self) -> Result<u64> {
        Ok(clipboard_state()
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .change_count)
    }

    fn read_text(&mut self) -> Result<Option<String>> {
        Ok(clipboard_state()
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .outbound_text
            .clone())
    }

    fn write_text(&mut self, text: &str) -> Result<()> {
        let mut state = clipboard_state().lock().unwrap_or_else(|e| e.into_inner());
        state.outbound_text = Some(text.to_string());
        if state.inbound_queue.len() >= 16 {
            state.inbound_queue.pop_front();
        }
        state.inbound_queue.push_back(text.to_string());
        Ok(())
    }
}

struct AndroidMediaProvider;

impl PlatformMediaProvider for AndroidMediaProvider {
    fn host_clock_us(&self) -> Option<u64> {
        Some(host_android::host_clock_us())
    }

    fn supports_hevc(&self) -> bool {
        supports_hardware_hevc()
    }

    fn decode_capabilities(&self) -> Vec<CodecCapability> {
        probed_decode_capabilities()
    }

    fn create_capture(&self, cfg: &PipelineConfig) -> Result<(Box<dyn CaptureBackend>, &'static str)> {
        let cap = AndroidCaptureBackend::new(cfg);
        Ok((Box::new(cap), "mediaprojection"))
    }

    fn create_encoder(&self, _cfg: &PipelineConfig) -> Result<(Box<dyn Encoder>, &'static str)> {
        Ok((Box::new(AndroidPassthroughEncoder), "mediacodec-enc"))
    }

    fn create_decoder(&self, cfg: &PipelineConfig) -> Result<(Box<dyn Decoder>, &'static str)> {
        let dec = AndroidSurfaceDecoder::new(cfg.resolution.width, cfg.resolution.height, cfg.codec);
        Ok((Box::new(dec), "mediacodec"))
    }

    fn create_renderer(&self, _cfg: &PipelineConfig) -> Result<(Box<dyn Renderer>, &'static str)> {
        Ok((Box::new(AndroidSurfaceRenderer), "surfaceview"))
    }
}

struct AndroidPlatformHooks;

impl ClientPlatformHooks for AndroidPlatformHooks {
    fn main_display_logical_resolution(&self) -> Option<Resolution> {
        Some(host_android::current_screen_resolution())
    }

    fn main_display_backing_resolution(&self) -> Option<Resolution> {
        Some(host_android::current_screen_resolution())
    }

    fn list_main_display_modes(&self) -> Vec<PlatformDisplayMode> {
        let res = host_android::current_screen_resolution();
        let mut modes = vec![PlatformDisplayMode {
            width: res.width,
            height: res.height,
            is_hidpi: true,
        }];
        if res.width > 800 {
            let scaled_w = 720u32;
            let scaled_h = (((u64::from(res.height) * u64::from(scaled_w)) / u64::from(res.width)) as u32)
                .max(2)
                & !1;
            modes.push(PlatformDisplayMode {
                width: scaled_w,
                height: scaled_h,
                is_hidpi: false,
            });
        }
        modes
    }

    fn lock_screen_now(&self) -> bool {
        host_android::push_lock_screen();
        true
    }

    fn create_main_display_injector(&self) -> Option<Box<dyn rc_input::InputInjector>> {
        Some(Box::new(AndroidInputInjector))
    }

    fn create_hardware_renderer(
        &self,
        _width: u32,
        _height: u32,
        _session_start: Instant,
        _preallocated: Option<&PreallocatedRenderSurface>,
    ) -> std::result::Result<HardwareRendererOutput, String> {
        Ok(HardwareRendererOutput {
            renderer: Box::new(AndroidSurfaceRenderer),
            layer_addr: 0,
            label: "surfaceview",
        })
    }
}

struct AndroidAudioProvider;

impl AudioBackendProvider for AndroidAudioProvider {
    fn create_system_audio_capture(&self) -> std::result::Result<Box<dyn AudioCapture>, String> {
        Err("audio capture is not supported on Android controller".to_string())
    }

    fn create_audio_player(
        &self,
        sample_rate: u32,
        channels: u8,
    ) -> std::result::Result<Box<dyn AudioPlayer>, String> {
        let player = AAudioPlayer::open(sample_rate, channels)?;
        Ok(Box::new(player))
    }
}

static REGISTERED: OnceLock<()> = OnceLock::new();

pub fn register_android_hooks() {
    REGISTERED.get_or_init(|| {
        let _ = rc_pipeline::register_platform_provider(Arc::new(AndroidMediaProvider));
        let _ = rc_client_core::platform::register_platform_hooks(Arc::new(AndroidPlatformHooks));
        let _ = rc_audio::register_audio_provider(Arc::new(AndroidAudioProvider));
        let _ = rc_session::register_clipboard_provider(Arc::new(|| {
            Ok(Box::new(AndroidClipboardBackend))
        }));
    });
}
