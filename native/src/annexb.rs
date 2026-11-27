#![cfg_attr(not(target_os = "android"), allow(dead_code))]
//! H.264 / HEVC Annex-B NAL unit inspector for MediaCodec CSD extraction and IDR detection.

/// Parsed summary of a single H.264 Annex-B access unit.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AccessUnitInfo {
    pub sps: Option<Vec<u8>>,
    pub pps: Option<Vec<u8>>,
    pub has_idr: bool,
    pub has_vcl: bool,
}

impl AccessUnitInfo {
    pub fn has_csd(&self) -> bool {
        self.sps.is_some() && self.pps.is_some()
    }
}

/// Parsed summary of a single HEVC (H.265) Annex-B access unit.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HevcAccessUnitInfo {
    pub vps: Option<Vec<u8>>,
    pub sps: Option<Vec<u8>>,
    pub pps: Option<Vec<u8>>,
    pub has_idr: bool,
    pub has_vcl: bool,
}

impl HevcAccessUnitInfo {
    pub fn has_csd(&self) -> bool {
        self.vps.is_some() && self.sps.is_some() && self.pps.is_some()
    }

    #[allow(dead_code)]
    pub fn combined_csd0(&self) -> Option<Vec<u8>> {
        let (Some(vps), Some(sps), Some(pps)) = (&self.vps, &self.sps, &self.pps) else {
            return None;
        };
        let mut out = Vec::with_capacity(vps.len() + sps.len() + pps.len());
        out.extend_from_slice(vps);
        out.extend_from_slice(sps);
        out.extend_from_slice(pps);
        Some(out)
    }
}

fn split_annexb_nals(data: &[u8]) -> Vec<(usize, usize)> {
    let len = data.len();
    let mut i = 0usize;
    let mut nal_ranges: Vec<(usize, usize)> = Vec::new();
    let mut cur_start: Option<usize> = None;

    while i < len {
        let sc_len = if i + 4 <= len
            && data[i] == 0
            && data[i + 1] == 0
            && data[i + 2] == 0
            && data[i + 3] == 1
        {
            4
        } else if i + 3 <= len && data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 1 {
            3
        } else {
            0
        };

        if sc_len > 0 {
            if let Some(start) = cur_start {
                if start < i {
                    nal_ranges.push((start, i));
                }
            }
            cur_start = Some(i + sc_len);
            i += sc_len;
        } else {
            i += 1;
        }
    }
    if let Some(start) = cur_start {
        if start < len {
            nal_ranges.push((start, len));
        }
    }
    nal_ranges
}

/// Inspects an Annex-B byte stream and extracts the first SPS/PPS (including start code
/// `00 00 00 01` as expected by Android MediaCodec `csd-0` / `csd-1`) plus IDR/VCL flags.
pub fn inspect_annexb(data: &[u8]) -> AccessUnitInfo {
    let mut info = AccessUnitInfo {
        sps: None,
        pps: None,
        has_idr: false,
        has_vcl: false,
    };

    for (start, end) in split_annexb_nals(data) {
        let nal = &data[start..end];
        if nal.is_empty() {
            continue;
        }
        let nal_type = nal[0] & 0x1F;
        match nal_type {
            1..=4 => {
                info.has_vcl = true;
            }
            5 => {
                info.has_idr = true;
                info.has_vcl = true;
            }
            7 => {
                if info.sps.is_none() {
                    let mut buf = Vec::with_capacity(4 + nal.len());
                    buf.extend_from_slice(&[0, 0, 0, 1]);
                    buf.extend_from_slice(nal);
                    info.sps = Some(buf);
                }
            }
            8 => {
                if info.pps.is_none() {
                    let mut buf = Vec::with_capacity(4 + nal.len());
                    buf.extend_from_slice(&[0, 0, 0, 1]);
                    buf.extend_from_slice(nal);
                    info.pps = Some(buf);
                }
            }
            _ => {}
        }
    }
    info
}

/// Inspects an HEVC Annex-B byte stream and extracts VPS/SPS/PPS plus IRAP/VCL flags.
pub fn inspect_hevc_annexb(data: &[u8]) -> HevcAccessUnitInfo {
    let mut info = HevcAccessUnitInfo {
        vps: None,
        sps: None,
        pps: None,
        has_idr: false,
        has_vcl: false,
    };

    for (start, end) in split_annexb_nals(data) {
        let nal = &data[start..end];
        if nal.len() < 2 {
            continue;
        }
        let nal_type = (nal[0] >> 1) & 0x3F;
        match nal_type {
            0..=15 => {
                info.has_vcl = true;
            }
            16..=21 => {
                info.has_idr = true;
                info.has_vcl = true;
            }
            22..=31 => {
                info.has_vcl = true;
            }
            32 => {
                if info.vps.is_none() {
                    let mut buf = Vec::with_capacity(4 + nal.len());
                    buf.extend_from_slice(&[0, 0, 0, 1]);
                    buf.extend_from_slice(nal);
                    info.vps = Some(buf);
                }
            }
            33 => {
                if info.sps.is_none() {
                    let mut buf = Vec::with_capacity(4 + nal.len());
                    buf.extend_from_slice(&[0, 0, 0, 1]);
                    buf.extend_from_slice(nal);
                    info.sps = Some(buf);
                }
            }
            34 => {
                if info.pps.is_none() {
                    let mut buf = Vec::with_capacity(4 + nal.len());
                    buf.extend_from_slice(&[0, 0, 0, 1]);
                    buf.extend_from_slice(nal);
                    info.pps = Some(buf);
                }
            }
            _ => {}
        }
    }
    info
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_sps_pps_and_idr_from_annexb() {
        let mut au = Vec::new();
        au.extend_from_slice(&[0, 0, 0, 1, 0x67, 0x42, 0x00, 0x28]);
        au.extend_from_slice(&[0, 0, 1, 0x68, 0xCE, 0x3C, 0x80]);
        au.extend_from_slice(&[0, 0, 0, 1, 0x65, 0x88, 0x84, 0x21]);
        let info = inspect_annexb(&au);
        assert!(info.has_idr);
        assert!(info.has_vcl);
        assert_eq!(
            info.sps.as_deref(),
            Some(&[0, 0, 0, 1, 0x67, 0x42, 0x00, 0x28][..])
        );
        assert_eq!(
            info.pps.as_deref(),
            Some(&[0, 0, 0, 1, 0x68, 0xCE, 0x3C, 0x80][..])
        );
    }

    #[test]
    fn distinguishes_p_frame_and_garbage() {
        let p_frame = [0, 0, 0, 1, 0x41, 0x9A, 0x22];
        let info = inspect_annexb(&p_frame);
        assert!(!info.has_idr);
        assert!(info.has_vcl);
        assert!(!info.has_csd());

        let fake_payload = b"FAKE_FRAME_DATA_12345";
        let fake = inspect_annexb(fake_payload);
        assert!(!fake.has_idr);
        assert!(!fake.has_vcl);
        assert!(!fake.has_csd());
    }

    #[test]
    fn parses_hevc_vps_sps_pps_and_idr() {
        let mut au = Vec::new();
        // VPS (32 << 1 = 0x40), SPS (33 << 1 = 0x42), PPS (34 << 1 = 0x44), IDR_W_RADL (19 << 1 = 0x26)
        au.extend_from_slice(&[0, 0, 0, 1, 0x40, 0x01, 0x0C]);
        au.extend_from_slice(&[0, 0, 0, 1, 0x42, 0x01, 0x01]);
        au.extend_from_slice(&[0, 0, 0, 1, 0x44, 0x01, 0xC0]);
        au.extend_from_slice(&[0, 0, 0, 1, 0x26, 0x01, 0xAF]);
        let info = inspect_hevc_annexb(&au);
        assert!(info.has_idr);
        assert!(info.has_vcl);
        assert!(info.has_csd());
        assert!(info.combined_csd0().is_some());
    }
}
