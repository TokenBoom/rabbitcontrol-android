//! Android Host-mode capture, passthrough H.264 hardware encoder bridge, and remote input queue.

use std::collections::VecDeque;
use std::sync::{Arc, Condvar, Mutex, OnceLock};
use std::time::{Duration, Instant};

use rc_capture::{CaptureBackend, CapturedFrame, DisplayInfo, FrameTexture, InvalidationReason};
use rc_encode::{BitstreamFormat, Codec, EncodedFrame, Encoder, EncoderCaps};
use rc_input::{InputEvent, InputInjector, MouseButton};
use rc_pipeline::PipelineConfig;
use rc_types::{CaptureSeq, CpuFrame, DisplayId, FrameKind, PixelFormat, Resolution, Result, Timestamp};
use serde::Serialize;

const MAX_FRAME_QUEUE: usize = 4;
const MAX_CMD_QUEUE: usize = 32;
const MAX_INPUT_QUEUE: usize = 256;

static CLOCK_ORIGIN: OnceLock<Instant> = OnceLock::new();
static SCREEN_METRICS: OnceLock<Mutex<(u32, u32)>> = OnceLock::new();
static FRAME_QUEUE: OnceLock<(Mutex<VecDeque<AndroidEncodedPacket>>, Condvar)> = OnceLock::new();
static ENCODER_CMDS: OnceLock<Mutex<VecDeque<HostEncoderCommand>>> = OnceLock::new();
static INPUT_EVENTS: OnceLock<Mutex<VecDeque<HostInputCommand>>> = OnceLock::new();

#[derive(Debug, Clone)]
pub struct AndroidEncodedPacket {
    pub data: Vec<u8>,
    pub is_keyframe: bool,
    pub timestamp_us: u64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum HostEncoderCommand {
    Start {
        width: u32,
        height: u32,
        fps: u32,
        bitrate_bps: u32,
    },
    Stop,
    SetBitrate {
        bps: u32,
    },
    RequestIdr,
}

#[derive(Debug, Clone, Serialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum HostInputCommand {
    MouseMove { x: u16, y: u16 },
    MouseButton { button: u8, pressed: bool },
    MouseWheel { delta: i16, horizontal: bool },
    Key { scancode: u16, pressed: bool, modifiers: u8 },
    LockScreen,
}

fn frame_queue() -> &'static (Mutex<VecDeque<AndroidEncodedPacket>>, Condvar) {
    FRAME_QUEUE.get_or_init(|| (Mutex::new(VecDeque::with_capacity(MAX_FRAME_QUEUE)), Condvar::new()))
}

fn encoder_cmds() -> &'static Mutex<VecDeque<HostEncoderCommand>> {
    ENCODER_CMDS.get_or_init(|| Mutex::new(VecDeque::with_capacity(MAX_CMD_QUEUE)))
}

fn input_events() -> &'static Mutex<VecDeque<HostInputCommand>> {
    INPUT_EVENTS.get_or_init(|| Mutex::new(VecDeque::with_capacity(MAX_INPUT_QUEUE)))
}

pub fn host_clock_us() -> u64 {
    let origin = CLOCK_ORIGIN.get_or_init(Instant::now);
    origin.elapsed().as_micros() as u64
}

pub fn set_host_screen_metrics(width: u32, height: u32) {
    let w = (width.max(2)) & !1;
    let h = (height.max(2)) & !1;
    let lock = SCREEN_METRICS.get_or_init(|| Mutex::new((1080, 2400)));
    *lock.lock().unwrap_or_else(|e| e.into_inner()) = (w, h);
}

pub fn current_screen_resolution() -> Resolution {
    let lock = SCREEN_METRICS.get_or_init(|| Mutex::new((1080, 2400)));
    let (w, h) = *lock.lock().unwrap_or_else(|e| e.into_inner());
    Resolution::new(w.max(2), h.max(2))
}

pub fn reset_host_queues() {
    frame_queue().0.lock().unwrap_or_else(|e| e.into_inner()).clear();
    encoder_cmds().lock().unwrap_or_else(|e| e.into_inner()).clear();
    input_events().lock().unwrap_or_else(|e| e.into_inner()).clear();
}

fn push_encoder_command(cmd: HostEncoderCommand) {
    let mut q = encoder_cmds().lock().unwrap_or_else(|e| e.into_inner());
    while q.len() >= MAX_CMD_QUEUE {
        q.pop_front();
    }
    q.push_back(cmd);
}

pub fn drain_host_encoder_commands() -> Vec<HostEncoderCommand> {
    encoder_cmds()
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .drain(..)
        .collect()
}

pub fn drain_host_input_events() -> Vec<HostInputCommand> {
    input_events()
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .drain(..)
        .collect()
}

pub fn push_lock_screen() {
    let mut q = input_events().lock().unwrap_or_else(|e| e.into_inner());
    while q.len() >= MAX_INPUT_QUEUE {
        q.pop_front();
    }
    q.push_back(HostInputCommand::LockScreen);
}

pub fn push_host_encoded_frame(data: Vec<u8>, is_keyframe: bool) {
    if data.is_empty() {
        return;
    }
    let pkt = AndroidEncodedPacket {
        data,
        is_keyframe,
        timestamp_us: host_clock_us(),
    };
    let (lock, cvar) = frame_queue();
    let mut q = lock.lock().unwrap_or_else(|e| e.into_inner());
    while q.len() >= MAX_FRAME_QUEUE {
        if let Some(pos) = q.iter().position(|p| !p.is_keyframe) {
            q.remove(pos);
        } else {
            q.pop_front();
        }
    }
    q.push_back(pkt);
    cvar.notify_all();
}

/// Host capture backend fed by Android MediaProjection + hardware MediaCodec H.264 encoder.
pub struct AndroidCaptureBackend {
    resolution: Resolution,
    clock_origin_us: u64,
    last_rel_us: u64,
    seq: u64,
}

impl AndroidCaptureBackend {
    pub fn new(cfg: &PipelineConfig) -> Self {
        frame_queue().0.lock().unwrap_or_else(|e| e.into_inner()).clear();
        push_encoder_command(HostEncoderCommand::Start {
            width: cfg.resolution.width.max(2),
            height: cfg.resolution.height.max(2),
            fps: cfg.fps.clamp(1, 120),
            bitrate_bps: cfg.bitrate_bps.max(500_000),
        });
        Self {
            resolution: cfg.resolution,
            clock_origin_us: cfg.clock_origin_us.unwrap_or_else(host_clock_us),
            last_rel_us: 0,
            seq: 0,
        }
    }
}

impl Drop for AndroidCaptureBackend {
    fn drop(&mut self) {
        push_encoder_command(HostEncoderCommand::Stop);
    }
}

impl CaptureBackend for AndroidCaptureBackend {
    fn next_frame(&mut self, timeout: Duration) -> Result<Option<CapturedFrame>> {
        let (lock, cvar) = frame_queue();
        let guard = lock.lock().unwrap_or_else(|e| e.into_inner());
        let (mut guard, _) = cvar
            .wait_timeout_while(guard, timeout, |q| q.is_empty())
            .unwrap_or_else(|e| e.into_inner());
        let Some(pkt) = guard.pop_front() else {
            return Ok(None);
        };
        drop(guard);

        let raw_rel = pkt.timestamp_us.saturating_sub(self.clock_origin_us);
        let rel_us = if self.seq == 0 {
            raw_rel
        } else {
            raw_rel.max(self.last_rel_us.saturating_add(1))
        };
        self.last_rel_us = rel_us;
        self.seq = self.seq.saturating_add(1);

        let ts = Timestamp(u32::try_from(rel_us).unwrap_or(u32::MAX));
        Ok(Some(CapturedFrame {
            texture: FrameTexture::Cpu(CpuFrame {
                width: 1,
                height: 1,
                format: PixelFormat::Bgra8,
                stride: 4,
                data: vec![0; 4],
            }),
            timestamp: ts,
            dirty: None,
            seq: CaptureSeq(self.seq),
            is_repeat: false,
            resource_guard: Some(Arc::new(pkt)),
        }))
    }

    fn poll_invalidation(&mut self) -> Option<InvalidationReason> {
        None
    }

    fn rebuild(&mut self) -> Result<()> {
        push_encoder_command(HostEncoderCommand::RequestIdr);
        Ok(())
    }

    fn display_info(&self) -> DisplayInfo {
        DisplayInfo {
            id: DisplayId(0),
            resolution: self.resolution,
            scale: 1.0,
            hdr: false,
        }
    }
}

/// Zero-copy pass-through encoder: Android MediaCodec Surface already emitted Annex-B H.264.
pub struct AndroidPassthroughEncoder;

impl Encoder for AndroidPassthroughEncoder {
    fn encode(
        &mut self,
        frame: &CapturedFrame,
        force_idr: bool,
        _deadline_ts: Timestamp,
    ) -> Result<EncodedFrame> {
        let Some(pkt) = frame
            .resource_guard
            .as_ref()
            .and_then(|g| g.downcast_ref::<AndroidEncodedPacket>())
        else {
            return Err(rc_types::Error::Encoder(
                "missing AndroidEncodedPacket on host frame".to_string(),
            ));
        };
        if force_idr && !pkt.is_keyframe {
            push_encoder_command(HostEncoderCommand::RequestIdr);
        }
        Ok(EncodedFrame {
            data: pkt.data.clone(),
            kind: if pkt.is_keyframe {
                FrameKind::Idr
            } else {
                FrameKind::P
            },
            is_keyframe: pkt.is_keyframe,
            encode_done_ts: frame.timestamp,
        })
    }

    fn capabilities(&self) -> EncoderCaps {
        EncoderCaps {
            codec: Codec::H264,
            hardware: true,
            max_resolution: Resolution::new(3840, 2160),
            bitstream_format: BitstreamFormat::AnnexB,
            profiles: &[66, 77, 100],
        }
    }

    fn set_bitrate(&mut self, bps: u32) -> Result<()> {
        if bps == 0 {
            return Err(rc_types::Error::InvalidConfig(
                "bitrate must be > 0".to_string(),
            ));
        }
        push_encoder_command(HostEncoderCommand::SetBitrate { bps });
        Ok(())
    }

    fn request_idr(&mut self) {
        push_encoder_command(HostEncoderCommand::RequestIdr);
    }

    fn flush(&mut self) -> Result<Vec<EncodedFrame>> {
        Ok(Vec::new())
    }
}

pub struct AndroidInputInjector;

impl InputInjector for AndroidInputInjector {
    fn inject(&mut self, events: &[InputEvent]) -> Result<()> {
        let mut q = input_events().lock().unwrap_or_else(|e| e.into_inner());
        for ev in events {
            let mapped = match *ev {
                InputEvent::MouseMove { x, y } => Some(HostInputCommand::MouseMove { x, y }),
                InputEvent::MouseButton { button, pressed } => {
                    let code = match button {
                        MouseButton::Left => 0,
                        MouseButton::Right => 1,
                        MouseButton::Middle => 2,
                        MouseButton::X1 => 3,
                        MouseButton::X2 => 4,
                    };
                    Some(HostInputCommand::MouseButton {
                        button: code,
                        pressed,
                    })
                }
                InputEvent::MouseWheel { delta, horizontal } => {
                    Some(HostInputCommand::MouseWheel { delta, horizontal })
                }
                InputEvent::Key {
                    scancode,
                    pressed,
                    modifiers,
                } => Some(HostInputCommand::Key {
                    scancode,
                    pressed,
                    modifiers,
                }),
                InputEvent::Sync => None,
            };
            if let Some(cmd) = mapped {
                while q.len() >= MAX_INPUT_QUEUE {
                    q.pop_front();
                }
                q.push_back(cmd);
            }
        }
        Ok(())
    }

    fn set_screen_size(&mut self, _w: u32, _h: u32) {}
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn host_capture_and_passthrough_encode_roundtrip() {
        reset_host_queues();
        let cfg = PipelineConfig {
            resolution: Resolution::new(1080, 2400),
            fps: 60,
            bitrate_bps: 8_000_000,
            ..PipelineConfig::default()
        };
        let mut cap = AndroidCaptureBackend::new(&cfg);
        let cmds = drain_host_encoder_commands();
        assert!(matches!(
            cmds.first(),
            Some(HostEncoderCommand::Start {
                width: 1080,
                height: 2400,
                fps: 60,
                bitrate_bps: 8_000_000
            })
        ));

        let payload = vec![0, 0, 0, 1, 0x65, 0x88, 0x84];
        push_host_encoded_frame(payload.clone(), true);
        let frame = cap
            .next_frame(Duration::from_millis(50))
            .expect("next_frame ok")
            .expect("frame present");

        let mut enc = AndroidPassthroughEncoder;
        let encoded = enc
            .encode(&frame, false, Timestamp(u32::MAX))
            .expect("encode ok");
        assert!(encoded.is_keyframe);
        assert_eq!(encoded.kind, FrameKind::Idr);
        assert_eq!(encoded.data, payload);

        drop(cap);
        let stop_cmds = drain_host_encoder_commands();
        assert!(matches!(stop_cmds.last(), Some(HostEncoderCommand::Stop)));
    }

    #[test]
    fn host_input_injector_enqueues_events() {
        reset_host_queues();
        let mut inj = AndroidInputInjector;
        inj.inject(&[
            InputEvent::MouseMove { x: 100, y: 200 },
            InputEvent::MouseButton {
                button: MouseButton::Left,
                pressed: true,
            },
            InputEvent::Sync,
        ])
        .expect("inject ok");
        let drained = drain_host_input_events();
        assert_eq!(drained.len(), 2);
        assert!(matches!(
            drained[0],
            HostInputCommand::MouseMove { x: 100, y: 200 }
        ));
        assert!(matches!(
            drained[1],
            HostInputCommand::MouseButton {
                button: 0,
                pressed: true
            }
        ));
    }
}
