#![cfg_attr(not(target_os = "android"), allow(dead_code))]
//! Zero-copy Android NDK AMediaCodec + ANativeWindow decoder and surface renderer
//! with a dedicated low-latency output-drain worker thread.

use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
#[cfg(target_os = "android")]
use std::sync::{Arc, Condvar};
use std::sync::{Mutex, OnceLock};

use rc_capture::FrameTexture;
use rc_decode::{DecodeOutput, DecodedFrame, Decoder};
use rc_render::Renderer;
#[cfg(target_os = "android")]
use rc_types::Error;
use rc_types::{CpuFrame, FrameKind, PixelFormat, Result, Timestamp};

#[cfg(target_os = "android")]
use crate::annexb::inspect_annexb;

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct CodecCandidate {
    pub name: String,
    pub hardware: bool,
    pub low_latency: bool,
}

#[derive(Debug, Clone, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MediaDiagnostics {
    pub codec_name: String,
    pub hardware: bool,
    pub low_latency: bool,
    pub decoded_frames: u64,
    pub rendered_frames: u64,
    pub dropped_frames: u64,
    pub surface_attached: bool,
}

static CODEC_CANDIDATES: OnceLock<Mutex<Vec<CodecCandidate>>> = OnceLock::new();
static DIAG_CODEC_NAME: OnceLock<Mutex<String>> = OnceLock::new();
static DIAG_HARDWARE: AtomicBool = AtomicBool::new(false);
static DIAG_LOW_LATENCY: AtomicBool = AtomicBool::new(false);
static DIAG_DECODED: AtomicU64 = AtomicU64::new(0);
static DIAG_RENDERED: AtomicU64 = AtomicU64::new(0);
static DIAG_DROPPED: AtomicU64 = AtomicU64::new(0);
static FIRST_FRAME_REPORTED: AtomicBool = AtomicBool::new(false);
static FORCE_KEYFRAME: AtomicBool = AtomicBool::new(false);

pub fn set_codec_candidates(candidates: Vec<CodecCandidate>) {
    let lock = CODEC_CANDIDATES.get_or_init(|| Mutex::new(Vec::new()));
    *lock.lock().unwrap_or_else(|e| e.into_inner()) = candidates;
}

fn get_codec_candidates() -> Vec<CodecCandidate> {
    CODEC_CANDIDATES
        .get_or_init(|| Mutex::new(Vec::new()))
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .clone()
}

pub fn request_keyframe() {
    FORCE_KEYFRAME.store(true, Ordering::Release);
}

pub fn reset_media_diagnostics() {
    if let Some(m) = DIAG_CODEC_NAME.get() {
        *m.lock().unwrap_or_else(|e| e.into_inner()) = String::new();
    }
    DIAG_HARDWARE.store(false, Ordering::Release);
    DIAG_LOW_LATENCY.store(false, Ordering::Release);
    DIAG_DECODED.store(0, Ordering::Release);
    DIAG_RENDERED.store(0, Ordering::Release);
    DIAG_DROPPED.store(0, Ordering::Release);
    FIRST_FRAME_REPORTED.store(false, Ordering::Release);
}

/// Returns true exactly once per session when the first decoded frame is rendered onto SurfaceView.
pub fn check_unreported_first_frame() -> bool {
    if DIAG_RENDERED.load(Ordering::Acquire) > 0
        && !FIRST_FRAME_REPORTED.swap(true, Ordering::AcqRel)
    {
        return true;
    }
    false
}

pub fn media_diagnostics() -> MediaDiagnostics {
    let rendered = DIAG_RENDERED.load(Ordering::Acquire);
    if rendered > 0 {
        FIRST_FRAME_REPORTED.store(true, Ordering::Release);
    }
    let codec_name = DIAG_CODEC_NAME
        .get_or_init(|| Mutex::new(String::new()))
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .clone();
    MediaDiagnostics {
        codec_name,
        hardware: DIAG_HARDWARE.load(Ordering::Acquire),
        low_latency: DIAG_LOW_LATENCY.load(Ordering::Acquire),
        decoded_frames: DIAG_DECODED.load(Ordering::Acquire),
        rendered_frames: rendered,
        dropped_frames: DIAG_DROPPED.load(Ordering::Acquire),
        surface_attached: has_attached_surface(),
    }
}

fn set_diag_codec(name: String, hardware: bool, low_latency: bool) {
    let lock = DIAG_CODEC_NAME.get_or_init(|| Mutex::new(String::new()));
    *lock.lock().unwrap_or_else(|e| e.into_inner()) = name;
    DIAG_HARDWARE.store(hardware, Ordering::Release);
    DIAG_LOW_LATENCY.store(low_latency, Ordering::Release);
}

#[cfg(target_os = "android")]
mod sys {
    use std::ffi::{c_char, c_void};

    #[repr(C)]
    pub struct ANativeWindow {
        _private: [u8; 0],
    }

    #[repr(C)]
    pub struct AMediaCodec {
        _private: [u8; 0],
    }

    #[repr(C)]
    pub struct AMediaFormat {
        _private: [u8; 0],
    }

    #[repr(C)]
    pub struct AMediaCrypto {
        _private: [u8; 0],
    }

    #[repr(C)]
    #[derive(Debug, Clone, Copy, Default)]
    pub struct AMediaCodecBufferInfo {
        pub offset: i32,
        pub size: i32,
        pub presentation_time_us: i64,
        pub flags: u32,
    }

    pub const AMEDIA_OK: i32 = 0;
    pub const AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED: isize = -2;
    pub const AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED: isize = -3;
    pub const AMEDIACODEC_BUFFER_FLAG_KEY_FRAME: u32 = 1;

    #[link(name = "android")]
    extern "C" {
        pub fn ANativeWindow_fromSurface(
            env: *mut jni::sys::JNIEnv,
            surface: jni::sys::jobject,
        ) -> *mut ANativeWindow;
        pub fn ANativeWindow_acquire(window: *mut ANativeWindow);
        pub fn ANativeWindow_release(window: *mut ANativeWindow);
    }

    #[link(name = "mediandk")]
    extern "C" {
        pub fn AMediaCodec_createCodecByName(name: *const c_char) -> *mut AMediaCodec;
        pub fn AMediaCodec_createDecoderByType(mime_type: *const c_char) -> *mut AMediaCodec;
        pub fn AMediaCodec_delete(codec: *mut AMediaCodec) -> i32;
        pub fn AMediaCodec_configure(
            codec: *mut AMediaCodec,
            format: *const AMediaFormat,
            surface: *mut ANativeWindow,
            crypto: *mut AMediaCrypto,
            flags: u32,
        ) -> i32;
        pub fn AMediaCodec_start(codec: *mut AMediaCodec) -> i32;
        pub fn AMediaCodec_stop(codec: *mut AMediaCodec) -> i32;
        pub fn AMediaCodec_flush(codec: *mut AMediaCodec) -> i32;
        pub fn AMediaCodec_getInputBuffer(
            codec: *mut AMediaCodec,
            idx: usize,
            out_size: *mut usize,
        ) -> *mut u8;
        pub fn AMediaCodec_dequeueInputBuffer(codec: *mut AMediaCodec, timeout_us: i64) -> isize;
        pub fn AMediaCodec_queueInputBuffer(
            codec: *mut AMediaCodec,
            idx: usize,
            offset: std::os::raw::c_long,
            size: usize,
            time: u64,
            flags: u32,
        ) -> i32;
        pub fn AMediaCodec_dequeueOutputBuffer(
            codec: *mut AMediaCodec,
            info: *mut AMediaCodecBufferInfo,
            timeout_us: i64,
        ) -> isize;
        pub fn AMediaCodec_releaseOutputBuffer(
            codec: *mut AMediaCodec,
            idx: usize,
            render: bool,
        ) -> i32;
        pub fn AMediaCodec_getName(
            codec: *mut AMediaCodec,
            out_name: *mut *mut c_char,
        ) -> i32;
        pub fn AMediaCodec_releaseName(codec: *mut AMediaCodec, name: *mut c_char);

        pub fn AMediaFormat_new() -> *mut AMediaFormat;
        pub fn AMediaFormat_delete(format: *mut AMediaFormat) -> i32;
        pub fn AMediaFormat_setString(
            format: *mut AMediaFormat,
            name: *const c_char,
            value: *const c_char,
        );
        pub fn AMediaFormat_setInt32(
            format: *mut AMediaFormat,
            name: *const c_char,
            value: i32,
        );
        pub fn AMediaFormat_setBuffer(
            format: *mut AMediaFormat,
            name: *const c_char,
            data: *const c_void,
            size: usize,
        );
    }
}

#[cfg(target_os = "android")]
struct SurfaceState {
    window: usize, // *mut sys::ANativeWindow
    generation: u64,
}

#[cfg(target_os = "android")]
static SURFACE_SLOT: OnceLock<Mutex<SurfaceState>> = OnceLock::new();

#[cfg(target_os = "android")]
fn surface_slot() -> &'static Mutex<SurfaceState> {
    SURFACE_SLOT.get_or_init(|| {
        Mutex::new(SurfaceState {
            window: 0,
            generation: 0,
        })
    })
}

#[cfg(target_os = "android")]
pub fn attach_surface(env: &mut jni::JNIEnv, surface: jni::objects::JObject) {
    let win = unsafe { sys::ANativeWindow_fromSurface(env.get_raw(), surface.as_raw()) };
    let mut slot = surface_slot().lock().unwrap_or_else(|e| e.into_inner());
    if slot.window != 0 {
        unsafe {
            sys::ANativeWindow_release(slot.window as *mut sys::ANativeWindow);
        }
        slot.window = 0;
    }
    if !win.is_null() {
        slot.window = win as usize;
        slot.generation = slot.generation.wrapping_add(1);
        FORCE_KEYFRAME.store(true, Ordering::Release);
    }
}

#[cfg(target_os = "android")]
pub fn detach_surface() {
    let mut slot = surface_slot().lock().unwrap_or_else(|e| e.into_inner());
    if slot.window != 0 {
        unsafe {
            sys::ANativeWindow_release(slot.window as *mut sys::ANativeWindow);
        }
        slot.window = 0;
        slot.generation = slot.generation.wrapping_add(1);
    }
}

#[cfg(target_os = "android")]
pub fn has_attached_surface() -> bool {
    surface_slot()
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .window
        != 0
}

#[cfg(not(target_os = "android"))]
pub fn detach_surface() {}

#[cfg(not(target_os = "android"))]
pub fn has_attached_surface() -> bool {
    false
}

/// Dedicated background output drainer for `AMediaCodec`.
/// Immediately releases the newest decoded frame onto `ANativeWindow` as soon as hardware
/// decoding completes, preventing 1-frame pipeline lag and stranded final frames on static screens,
/// while sleeping at 0% CPU when idle and joining cleanly before `flush`/`stop`/`delete`.
#[cfg(target_os = "android")]
struct CodecOutputDrainer {
    stop: Arc<AtomicBool>,
    wake: Arc<(Mutex<u64>, Condvar)>,
    handle: Option<std::thread::JoinHandle<()>>,
}

#[cfg(target_os = "android")]
impl CodecOutputDrainer {
    fn spawn(codec: *mut sys::AMediaCodec) -> Self {
        let stop = Arc::new(AtomicBool::new(false));
        let wake = Arc::new((Mutex::new(0u64), Condvar::new()));
        let stop_bg = Arc::clone(&stop);
        let wake_bg = Arc::clone(&wake);
        let codec_addr = codec as usize;

        let handle = std::thread::Builder::new()
            .name("rc-avc-drain".to_string())
            .spawn(move || {
                let codec_ptr = codec_addr as *mut sys::AMediaCodec;
                let mut seen_seq = 0u64;
                let mut idle_polls_left = 0u32;
                let mut info = sys::AMediaCodecBufferInfo::default();

                while !stop_bg.load(Ordering::Acquire) {
                    if idle_polls_left == 0 {
                        let (lock, cvar) = &*wake_bg;
                        let guard = lock.lock().unwrap_or_else(|e| e.into_inner());
                        let guard = cvar
                            .wait_while(guard, |seq| {
                                *seq == seen_seq && !stop_bg.load(Ordering::Acquire)
                            })
                            .unwrap_or_else(|e| e.into_inner());
                        if stop_bg.load(Ordering::Acquire) {
                            break;
                        }
                        seen_seq = *guard;
                        idle_polls_left = 20; // ~60ms active drain window after last queued input
                    }

                    let idx = unsafe {
                        sys::AMediaCodec_dequeueOutputBuffer(codec_ptr, &mut info, 3_000)
                    };
                    if idx >= 0 {
                        let mut newest_idx: Option<usize> = None;
                        let mut dropped = 0u64;

                        if info.size > 0 {
                            newest_idx = Some(idx as usize);
                        } else {
                            unsafe {
                                let _ =
                                    sys::AMediaCodec_releaseOutputBuffer(codec_ptr, idx as usize, false);
                            }
                        }

                        // Drain any additional ready buffers non-blockingly so only the freshest frame renders
                        loop {
                            let next_idx = unsafe {
                                sys::AMediaCodec_dequeueOutputBuffer(codec_ptr, &mut info, 0)
                            };
                            if next_idx >= 0 {
                                let u_idx = next_idx as usize;
                                if info.size > 0 {
                                    if let Some(prev) = newest_idx.replace(u_idx) {
                                        unsafe {
                                            let _ = sys::AMediaCodec_releaseOutputBuffer(
                                                codec_ptr, prev, false,
                                            );
                                        }
                                        dropped += 1;
                                    }
                                } else {
                                    unsafe {
                                        let _ = sys::AMediaCodec_releaseOutputBuffer(
                                            codec_ptr, u_idx, false,
                                        );
                                    }
                                }
                            } else if next_idx == sys::AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED
                                || next_idx == sys::AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED
                            {
                                continue;
                            } else {
                                break;
                            }
                        }

                        if dropped > 0 {
                            DIAG_DROPPED.fetch_add(dropped, Ordering::Relaxed);
                        }
                        if let Some(render_idx) = newest_idx {
                            unsafe {
                                let _ = sys::AMediaCodec_releaseOutputBuffer(
                                    codec_ptr, render_idx, true,
                                );
                            }
                            DIAG_RENDERED.fetch_add(1, Ordering::Release);
                        }

                        let cur_seq = *wake_bg.0.lock().unwrap_or_else(|e| e.into_inner());
                        if cur_seq != seen_seq {
                            seen_seq = cur_seq;
                            idle_polls_left = 20;
                        } else {
                            idle_polls_left = idle_polls_left.max(6);
                        }
                    } else if idx == sys::AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED
                        || idx == sys::AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED
                    {
                        continue;
                    } else {
                        let cur_seq = *wake_bg.0.lock().unwrap_or_else(|e| e.into_inner());
                        if cur_seq != seen_seq {
                            seen_seq = cur_seq;
                            idle_polls_left = 20;
                        } else {
                            idle_polls_left = idle_polls_left.saturating_sub(1);
                        }
                    }
                }
            })
            .ok();

        Self { stop, wake, handle }
    }

    #[inline]
    fn notify_input_queued(&self) {
        let (lock, cvar) = &*self.wake;
        let mut seq = lock.lock().unwrap_or_else(|e| e.into_inner());
        *seq = seq.wrapping_add(1);
        drop(seq);
        cvar.notify_one();
    }

    fn stop_and_join(&mut self) {
        self.stop.store(true, Ordering::Release);
        self.wake.1.notify_all();
        if let Some(h) = self.handle.take() {
            let _ = h.join();
        }
    }
}

#[cfg(target_os = "android")]
impl Drop for CodecOutputDrainer {
    fn drop(&mut self) {
        self.stop_and_join();
    }
}

#[cfg(target_os = "android")]
pub struct AndroidSurfaceDecoder {
    width: u32,
    height: u32,
    codec: *mut sys::AMediaCodec,
    drainer: Option<CodecOutputDrainer>,
    active_generation: u64,
    last_sps: Option<Vec<u8>>,
    last_pps: Option<Vec<u8>>,
    needs_keyframe: bool,
    pts_counter: u64,
}

#[cfg(target_os = "android")]
unsafe impl Send for AndroidSurfaceDecoder {}

#[cfg(target_os = "android")]
impl AndroidSurfaceDecoder {
    pub fn new(width: u32, height: u32) -> Self {
        Self {
            width: width.max(2),
            height: height.max(2),
            codec: std::ptr::null_mut(),
            drainer: None,
            active_generation: 0,
            last_sps: None,
            last_pps: None,
            needs_keyframe: true,
            pts_counter: 0,
        }
    }

    fn release_codec(&mut self) {
        if let Some(mut d) = self.drainer.take() {
            d.stop_and_join();
        }
        if !self.codec.is_null() {
            unsafe {
                let _ = sys::AMediaCodec_stop(self.codec);
                let _ = sys::AMediaCodec_delete(self.codec);
            }
            self.codec = std::ptr::null_mut();
        }
    }

    fn configure_codec(
        &mut self,
        window: *mut sys::ANativeWindow,
        sps: &[u8],
        pps: &[u8],
    ) -> std::result::Result<(), String> {
        use std::ffi::{CStr, CString};

        self.release_codec();
        let mime = CString::new("video/avc").unwrap();
        let mut candidates = get_codec_candidates();
        if candidates.is_empty() {
            candidates.push(CodecCandidate {
                name: String::new(),
                hardware: true,
                low_latency: true,
            });
        }

        let mut last_err = "no compatible AVC decoder".to_string();
        for cand in candidates {
            let mut try_modes = Vec::new();
            if cand.low_latency {
                try_modes.push((true, true));
                try_modes.push((true, false));
            }
            try_modes.push((false, false));

            for (low_lat, vendor_keys) in try_modes {
                let codec = if cand.name.is_empty() {
                    unsafe { sys::AMediaCodec_createDecoderByType(mime.as_ptr()) }
                } else {
                    let cname = CString::new(cand.name.as_str()).unwrap();
                    unsafe { sys::AMediaCodec_createCodecByName(cname.as_ptr()) }
                };
                if codec.is_null() {
                    continue;
                }

                let fmt = unsafe { sys::AMediaFormat_new() };
                if fmt.is_null() {
                    unsafe {
                        let _ = sys::AMediaCodec_delete(codec);
                    }
                    continue;
                }

                unsafe {
                    let k_mime = CString::new("mime").unwrap();
                    let k_w = CString::new("width").unwrap();
                    let k_h = CString::new("height").unwrap();
                    let k_max_w = CString::new("max-width").unwrap();
                    let k_max_h = CString::new("max-height").unwrap();
                    let k_csd0 = CString::new("csd-0").unwrap();
                    let k_csd1 = CString::new("csd-1").unwrap();
                    sys::AMediaFormat_setString(fmt, k_mime.as_ptr(), mime.as_ptr());
                    sys::AMediaFormat_setInt32(fmt, k_w.as_ptr(), self.width as i32);
                    sys::AMediaFormat_setInt32(fmt, k_h.as_ptr(), self.height as i32);
                    sys::AMediaFormat_setInt32(fmt, k_max_w.as_ptr(), self.width.max(3840) as i32);
                    sys::AMediaFormat_setInt32(fmt, k_max_h.as_ptr(), self.height.max(2160) as i32);
                    sys::AMediaFormat_setBuffer(
                        fmt,
                        k_csd0.as_ptr(),
                        sps.as_ptr() as *const _,
                        sps.len(),
                    );
                    sys::AMediaFormat_setBuffer(
                        fmt,
                        k_csd1.as_ptr(),
                        pps.as_ptr() as *const _,
                        pps.len(),
                    );

                    if low_lat {
                        let k_low = CString::new("low-latency").unwrap();
                        let k_prio = CString::new("priority").unwrap();
                        let k_rate = CString::new("operating-rate").unwrap();
                        sys::AMediaFormat_setInt32(fmt, k_low.as_ptr(), 1);
                        sys::AMediaFormat_setInt32(fmt, k_prio.as_ptr(), 0);
                        sys::AMediaFormat_setInt32(fmt, k_rate.as_ptr(), 120);
                        if vendor_keys {
                            for key_str in [
                                "vendor.qti-ext-dec-low-latency.enable",
                                "vendor.mtk-ext-dec-low-latency.enable",
                                "vendor.rtc-ext-dec-low-latency.enable",
                                "vendor.low-latency.enable",
                                "vendor.hisi-ext-low-latency-video-dec.video-scene-for-low-latency-req",
                            ] {
                                let k = CString::new(key_str).unwrap();
                                sys::AMediaFormat_setInt32(fmt, k.as_ptr(), 1);
                            }
                        }
                    }
                }

                let cfg_res = unsafe {
                    sys::AMediaCodec_configure(codec, fmt, window, std::ptr::null_mut(), 0)
                };
                unsafe {
                    let _ = sys::AMediaFormat_delete(fmt);
                }
                if cfg_res != sys::AMEDIA_OK {
                    last_err = format!("AMediaCodec_configure({}): {cfg_res}", cand.name);
                    unsafe {
                        let _ = sys::AMediaCodec_delete(codec);
                    }
                    continue;
                }

                let start_res = unsafe { sys::AMediaCodec_start(codec) };
                if start_res != sys::AMEDIA_OK {
                    last_err = format!("AMediaCodec_start({}): {start_res}", cand.name);
                    unsafe {
                        let _ = sys::AMediaCodec_delete(codec);
                    }
                    continue;
                }

                let actual_name = if cand.name.is_empty() {
                    let mut raw_name: *mut std::ffi::c_char = std::ptr::null_mut();
                    let name_res = unsafe { sys::AMediaCodec_getName(codec, &mut raw_name) };
                    if name_res == sys::AMEDIA_OK && !raw_name.is_null() {
                        let s = unsafe { CStr::from_ptr(raw_name).to_string_lossy().into_owned() };
                        unsafe { sys::AMediaCodec_releaseName(codec, raw_name) };
                        s
                    } else {
                        "AMediaCodec".to_string()
                    }
                } else {
                    cand.name.clone()
                };

                self.codec = codec;
                self.drainer = Some(CodecOutputDrainer::spawn(codec));
                set_diag_codec(actual_name, cand.hardware, low_lat);
                return Ok(());
            }
        }
        Err(last_err)
    }

    #[inline]
    fn dummy_frame(&self, decode_done_ts: Timestamp) -> DecodedFrame {
        DecodedFrame {
            texture: FrameTexture::Cpu(CpuFrame {
                width: 0,
                height: 0,
                format: PixelFormat::Bgra8,
                stride: 0,
                data: Vec::new(), // Zero heap allocation
            }),
            decode_done_ts,
            width: self.width,
            height: self.height,
        }
    }
}

#[cfg(target_os = "android")]
impl Drop for AndroidSurfaceDecoder {
    fn drop(&mut self) {
        self.release_codec();
    }
}

#[cfg(target_os = "android")]
struct AcquiredWindow(*mut sys::ANativeWindow);

#[cfg(target_os = "android")]
impl Drop for AcquiredWindow {
    fn drop(&mut self) {
        if !self.0.is_null() {
            unsafe {
                sys::ANativeWindow_release(self.0);
            }
        }
    }
}

#[cfg(target_os = "android")]
impl Decoder for AndroidSurfaceDecoder {
    fn decode(
        &mut self,
        data: &[u8],
        kind: FrameKind,
        deadline_ts: Timestamp,
    ) -> Result<DecodeOutput> {
        let info = inspect_annexb(data);
        if !info.has_vcl && !info.has_csd() {
            self.needs_keyframe = true;
            return Ok(DecodeOutput::Nothing);
        }

        if FORCE_KEYFRAME.swap(false, Ordering::AcqRel) {
            self.needs_keyframe = true;
        }

        let (window_guard, generation) = {
            let slot = surface_slot().lock().unwrap_or_else(|e| e.into_inner());
            let ptr = slot.window as *mut sys::ANativeWindow;
            if !ptr.is_null() {
                unsafe {
                    sys::ANativeWindow_acquire(ptr);
                }
            }
            (AcquiredWindow(ptr), slot.generation)
        };
        let window_ptr = window_guard.0;

        if window_ptr.is_null() {
            if !self.codec.is_null() {
                self.release_codec();
            }
            self.active_generation = generation;
            self.needs_keyframe = true;
            return Ok(DecodeOutput::Frame(self.dummy_frame(deadline_ts)));
        }

        if self.active_generation != generation {
            self.release_codec();
            self.active_generation = generation;
            self.needs_keyframe = true;
        }

        let sps_changed = info
            .sps
            .as_ref()
            .is_some_and(|s| self.last_sps.as_ref() != Some(s));
        let pps_changed = info
            .pps
            .as_ref()
            .is_some_and(|p| self.last_pps.as_ref() != Some(p));
        if let Some(sps) = info.sps.clone() {
            self.last_sps = Some(sps);
        }
        if let Some(pps) = info.pps.clone() {
            self.last_pps = Some(pps);
        }

        if self.codec.is_null() || (info.has_idr && (sps_changed || pps_changed)) {
            if let (Some(sps), Some(pps)) = (self.last_sps.clone(), self.last_pps.clone()) {
                if let Err(err) = self.configure_codec(window_ptr, &sps, &pps) {
                    return Err(Error::InvalidConfig(err));
                }
            } else {
                self.needs_keyframe = true;
                return Ok(DecodeOutput::NeedKeyframe);
            }
        }

        if self.needs_keyframe && kind != FrameKind::Idr && !info.has_idr {
            return Ok(DecodeOutput::NeedKeyframe);
        }

        // Fast non-blocking / micro-timeout input dequeue; wake output drainer and retry once if full
        let mut in_idx = unsafe { sys::AMediaCodec_dequeueInputBuffer(self.codec, 1_500) };
        if in_idx < 0 {
            if let Some(ref d) = self.drainer {
                d.notify_input_queued();
            }
            in_idx = unsafe { sys::AMediaCodec_dequeueInputBuffer(self.codec, 3_500) };
            if in_idx < 0 {
                self.needs_keyframe = true;
                DIAG_DROPPED.fetch_add(1, Ordering::Relaxed);
                return Ok(DecodeOutput::NeedKeyframe);
            }
        }

        let mut cap = 0usize;
        let buf_ptr =
            unsafe { sys::AMediaCodec_getInputBuffer(self.codec, in_idx as usize, &mut cap) };
        if buf_ptr.is_null() || cap < data.len() {
            self.needs_keyframe = true;
            return Ok(DecodeOutput::NeedKeyframe);
        }

        unsafe {
            std::ptr::copy_nonoverlapping(data.as_ptr(), buf_ptr, data.len());
        }
        let pts = self.pts_counter.saturating_mul(8_333);
        self.pts_counter = self.pts_counter.wrapping_add(1);
        let flags = if kind == FrameKind::Idr || info.has_idr {
            sys::AMEDIACODEC_BUFFER_FLAG_KEY_FRAME
        } else {
            0
        };

        let q_res = unsafe {
            sys::AMediaCodec_queueInputBuffer(
                self.codec,
                in_idx as usize,
                0,
                data.len(),
                pts,
                flags,
            )
        };
        if q_res != sys::AMEDIA_OK {
            self.release_codec();
            self.needs_keyframe = true;
            return Ok(DecodeOutput::NeedKeyframe);
        }

        if kind == FrameKind::Idr || info.has_idr {
            self.needs_keyframe = false;
        }
        DIAG_DECODED.fetch_add(1, Ordering::Relaxed);
        if let Some(ref d) = self.drainer {
            d.notify_input_queued();
        }

        Ok(DecodeOutput::Frame(self.dummy_frame(deadline_ts)))
    }

    fn needs_keyframe(&self) -> bool {
        self.needs_keyframe || FORCE_KEYFRAME.load(Ordering::Acquire)
    }

    fn flush(&mut self) -> Result<()> {
        if !self.codec.is_null() {
            if let Some(mut d) = self.drainer.take() {
                d.stop_and_join();
            }
            unsafe {
                let _ = sys::AMediaCodec_flush(self.codec);
            }
            self.drainer = Some(CodecOutputDrainer::spawn(self.codec));
        }
        self.needs_keyframe = true;
        Ok(())
    }
}

#[cfg(not(target_os = "android"))]
pub struct AndroidSurfaceDecoder {
    width: u32,
    height: u32,
}

#[cfg(not(target_os = "android"))]
impl AndroidSurfaceDecoder {
    pub fn new(width: u32, height: u32) -> Self {
        Self { width, height }
    }
}

#[cfg(not(target_os = "android"))]
impl Decoder for AndroidSurfaceDecoder {
    fn decode(
        &mut self,
        _data: &[u8],
        _kind: FrameKind,
        deadline_ts: Timestamp,
    ) -> Result<DecodeOutput> {
        Ok(DecodeOutput::Frame(DecodedFrame {
            texture: FrameTexture::Cpu(CpuFrame {
                width: 0,
                height: 0,
                format: PixelFormat::Bgra8,
                stride: 0,
                data: Vec::new(),
            }),
            decode_done_ts: deadline_ts,
            width: self.width,
            height: self.height,
        }))
    }
    fn needs_keyframe(&self) -> bool {
        false
    }
    fn flush(&mut self) -> Result<()> {
        Ok(())
    }
}

/// Zero-copy pass-through renderer: AMediaCodec already queued the decoded buffer
/// directly onto the Android SurfaceView ANativeWindow.
pub struct AndroidSurfaceRenderer;

impl Renderer for AndroidSurfaceRenderer {
    fn present(&mut self, frame: DecodedFrame) -> Result<Timestamp> {
        Ok(frame.decode_done_ts)
    }

    fn resize(&mut self, _size: (u32, u32)) -> Result<()> {
        Ok(())
    }

    fn set_vsync(&mut self, _on: bool) {}

    fn set_cursor(&mut self, _pos: (i32, i32), _visible: bool) {}
}
