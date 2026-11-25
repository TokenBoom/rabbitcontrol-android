//! Low-latency non-blocking AAudio PCM16 player for Android.

use rc_audio::{AudioPlayer, PcmFrame};

#[cfg(target_os = "android")]
mod sys {
    use std::ffi::c_void;

    #[repr(C)]
    pub struct AAudioStreamBuilder {
        _private: [u8; 0],
    }

    #[repr(C)]
    pub struct AAudioStream {
        _private: [u8; 0],
    }

    pub const AAUDIO_OK: i32 = 0;
    pub const AAUDIO_DIRECTION_OUTPUT: i32 = 0;
    pub const AAUDIO_FORMAT_PCM_I16: i32 = 1;
    pub const AAUDIO_SHARING_MODE_EXCLUSIVE: i32 = 0;
    pub const AAUDIO_SHARING_MODE_SHARED: i32 = 1;
    pub const AAUDIO_PERFORMANCE_MODE_LOW_LATENCY: i32 = 12;
    pub const AAUDIO_USAGE_GAME: i32 = 14;
    pub const AAUDIO_CONTENT_TYPE_MUSIC: i32 = 2;

    #[link(name = "aaudio")]
    extern "C" {
        pub fn AAudio_createStreamBuilder(builder: *mut *mut AAudioStreamBuilder) -> i32;
        pub fn AAudioStreamBuilder_delete(builder: *mut AAudioStreamBuilder) -> i32;
        pub fn AAudioStreamBuilder_setDirection(builder: *mut AAudioStreamBuilder, direction: i32);
        pub fn AAudioStreamBuilder_setSampleRate(builder: *mut AAudioStreamBuilder, sample_rate: i32);
        pub fn AAudioStreamBuilder_setChannelCount(builder: *mut AAudioStreamBuilder, channel_count: i32);
        pub fn AAudioStreamBuilder_setFormat(builder: *mut AAudioStreamBuilder, format: i32);
        pub fn AAudioStreamBuilder_setSharingMode(builder: *mut AAudioStreamBuilder, sharing_mode: i32);
        pub fn AAudioStreamBuilder_setPerformanceMode(builder: *mut AAudioStreamBuilder, mode: i32);
        pub fn AAudioStreamBuilder_setUsage(builder: *mut AAudioStreamBuilder, usage: i32);
        pub fn AAudioStreamBuilder_setContentType(builder: *mut AAudioStreamBuilder, content_type: i32);
        pub fn AAudioStreamBuilder_openStream(
            builder: *mut AAudioStreamBuilder,
            stream: *mut *mut AAudioStream,
        ) -> i32;
        pub fn AAudioStream_requestStart(stream: *mut AAudioStream) -> i32;
        pub fn AAudioStream_requestStop(stream: *mut AAudioStream) -> i32;
        pub fn AAudioStream_close(stream: *mut AAudioStream) -> i32;
        pub fn AAudioStream_write(
            stream: *mut AAudioStream,
            buffer: *const c_void,
            num_frames: i32,
            timeout_nanoseconds: i64,
        ) -> i32;
        pub fn AAudioStream_getFramesPerBurst(stream: *mut AAudioStream) -> i32;
        pub fn AAudioStream_setBufferSizeInFrames(
            stream: *mut AAudioStream,
            num_frames: i32,
        ) -> i32;
    }
}

#[cfg(target_os = "android")]
pub struct AAudioPlayer {
    stream: *mut sys::AAudioStream,
    sample_rate: u32,
    channels: u8,
    muted: bool,
    stopped: bool,
}

#[cfg(target_os = "android")]
unsafe impl Send for AAudioPlayer {}

#[cfg(target_os = "android")]
impl AAudioPlayer {
    pub fn open(sample_rate: u32, channels: u8) -> Result<Self, String> {
        let safe_sr = i32::try_from(sample_rate.clamp(8_000, 192_000))
            .map_err(|_| "invalid audio sample rate".to_string())?;
        let safe_ch = if channels <= 1 { 1i32 } else { 2i32 };

        for sharing_mode in [
            sys::AAUDIO_SHARING_MODE_EXCLUSIVE,
            sys::AAUDIO_SHARING_MODE_SHARED,
        ] {
            let mut builder: *mut sys::AAudioStreamBuilder = std::ptr::null_mut();
            let res = unsafe { sys::AAudio_createStreamBuilder(&mut builder) };
            if res != sys::AAUDIO_OK || builder.is_null() {
                return Err(format!("AAudio_createStreamBuilder failed: {res}"));
            }
            unsafe {
                sys::AAudioStreamBuilder_setDirection(builder, sys::AAUDIO_DIRECTION_OUTPUT);
                sys::AAudioStreamBuilder_setSampleRate(builder, safe_sr);
                sys::AAudioStreamBuilder_setChannelCount(builder, safe_ch);
                sys::AAudioStreamBuilder_setFormat(builder, sys::AAUDIO_FORMAT_PCM_I16);
                sys::AAudioStreamBuilder_setSharingMode(builder, sharing_mode);
                sys::AAudioStreamBuilder_setPerformanceMode(
                    builder,
                    sys::AAUDIO_PERFORMANCE_MODE_LOW_LATENCY,
                );
                sys::AAudioStreamBuilder_setUsage(builder, sys::AAUDIO_USAGE_GAME);
                sys::AAudioStreamBuilder_setContentType(builder, sys::AAUDIO_CONTENT_TYPE_MUSIC);
            }
            let mut stream: *mut sys::AAudioStream = std::ptr::null_mut();
            let open_res = unsafe { sys::AAudioStreamBuilder_openStream(builder, &mut stream) };
            unsafe {
                sys::AAudioStreamBuilder_delete(builder);
            }
            if open_res == sys::AAUDIO_OK && !stream.is_null() {
                unsafe {
                    let burst = sys::AAudioStream_getFramesPerBurst(stream);
                    if burst > 0 {
                        let _ = sys::AAudioStream_setBufferSizeInFrames(stream, burst * 2);
                    }
                    let start_res = sys::AAudioStream_requestStart(stream);
                    if start_res != sys::AAUDIO_OK {
                        let _ = sys::AAudioStream_close(stream);
                        continue;
                    }
                }
                return Ok(Self {
                    stream,
                    sample_rate,
                    channels: safe_ch as u8,
                    muted: false,
                    stopped: false,
                });
            }
        }
        Err("AAudio failed to open output stream".to_string())
    }
}

#[cfg(target_os = "android")]
impl AudioPlayer for AAudioPlayer {
    fn push_frame(&mut self, frame: &PcmFrame) -> Result<(), String> {
        if self.stopped || self.muted || frame.samples.is_empty() || self.stream.is_null() {
            return Ok(());
        }
        if frame.sample_rate != self.sample_rate || frame.channels != self.channels {
            return Ok(());
        }
        let ch = usize::from(self.channels.max(1));
        let num_frames = (frame.samples.len() / ch) as i32;
        if num_frames <= 0 {
            return Ok(());
        }
        let written = unsafe {
            sys::AAudioStream_write(
                self.stream,
                frame.samples.as_ptr() as *const std::ffi::c_void,
                num_frames,
                0,
            )
        };
        if written < 0 {
            // Non-fatal underflow/disconnect: drop frame so video/input never stalls.
            return Ok(());
        }
        Ok(())
    }

    fn set_muted(&mut self, muted: bool) {
        self.muted = muted;
    }

    fn is_muted(&self) -> bool {
        self.muted
    }

    fn stop(&mut self) {
        if self.stopped {
            return;
        }
        self.stopped = true;
        if !self.stream.is_null() {
            unsafe {
                let _ = sys::AAudioStream_requestStop(self.stream);
                let _ = sys::AAudioStream_close(self.stream);
            }
            self.stream = std::ptr::null_mut();
        }
    }
}

#[cfg(target_os = "android")]
impl Drop for AAudioPlayer {
    fn drop(&mut self) {
        self.stop();
    }
}

#[cfg(not(target_os = "android"))]
pub struct AAudioPlayer;

#[cfg(not(target_os = "android"))]
impl AAudioPlayer {
    pub fn open(_sample_rate: u32, _channels: u8) -> Result<Self, String> {
        Err("AAudio is only available on Android".to_string())
    }
}

#[cfg(not(target_os = "android"))]
impl AudioPlayer for AAudioPlayer {
    fn push_frame(&mut self, _frame: &PcmFrame) -> Result<(), String> {
        Ok(())
    }
    fn set_muted(&mut self, _muted: bool) {}
    fn is_muted(&self) -> bool {
        false
    }
    fn stop(&mut self) {}
}
