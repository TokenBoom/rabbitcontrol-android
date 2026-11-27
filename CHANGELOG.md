# Changelog

English | [简体中文](CHANGELOG_zh.md)

All notable user-facing changes to **RabbitControl Android** are documented in this file in natural language, serving as the single source of truth for GitHub Releases:
- `v*`: Full Android APK releases
- `core-v*`: Standalone `rabbitcontrol-core` hot-update releases

---

## [Unreleased]

<!-- Move items into a version heading before releasing, e.g. ## [v0.2.2] - 2026-12-01 or ## [core-v0.1.24] - 2026-12-01 -->

---

## [v0.2.2] - 2026-11-27

This release upgrades the bundled engine to `rabbitcontrol-core v0.2.2` with `rabbitcontrol-spec v1.0.0`, brings full alignment for connection strategies, multi-display & virtual displays, live codec/decode switching, privacy curtain, host mute, mTLS verification, remote file operations, multi-language support, a full virtual keyboard, and key fixes for Android host mode.

### Added
- **Multi-Language Localization & Full Virtual Keyboard**: Real-time switching across System Default, English, Simplified Chinese, and Traditional Chinese, plus a full virtual desktop keyboard and modifier combos during remote sessions.
- **Connection Mode, Outbound Proxy, UPnP & Wake-on-LAN**:
  - Added Connection Mode selection (`Auto` / `P2P Only` / `TCP Relay`) in both **Me → Connection Mode** and the in-session **Display** panel, persisted globally and reconnecting immediately when changed mid-session.
  - Added SOCKS5 / HTTP outbound proxy settings, UPnP / NAT-PMP port mapping diagnostics, and Wake-on-LAN support.
  - Added Android default network route monitoring (`NetworkRouteMonitor`) to refresh LAN discovery, reconnect signaling, and probe active QUIC sessions immediately on Wi-Fi/cellular transitions.
- **Live Codec / Decode Control & Multi-Display**:
  - Live HUD and Control Panel display the active video codec (`H.264` / `HEVC` / `AV1`) and hardware/software decode state, with in-session codec switching and hot-switchable HW/SW decoding.
  - Added High-Refresh Advisory banners (`DisplayConfig.advisory`) with one-tap HEVC or recommended resolution switching.
  - Added remote display switching as well as Virtual Display creation (presets, phone aspect-ratio match, custom size, HiDPI 2x) and removal.
- **Privacy Curtain, Host Mute & mTLS Verification**:
  - Added Privacy Curtain (`Off` / `Privacy` / `Black Screen`) and Remote Host Mute (auto-restored on exit) in the **Controls** tab.
  - Added an mTLS status badge in the session control header to inspect and copy the peer device certificate's SHA-256 fingerprint.
- **Remote File Rename & Delete**: Added rename and confirmed delete actions (`⋮` menu) on remote files and folders in File Transfer, alongside live tool-permission enforcement.

### Changed & Fixed
- **Core & Spec Upgrade**: Upgraded bundled `rabbitcontrol-core` to `v0.2.2` and integrated `rabbitcontrol-spec v1.0.0` unified error codes and error presentation.
- **Android Host Fixes**:
  - Fixed Android host mode not reporting `allow_remote_control` state to the signaling server on startup, toggle, and stop.
  - Fixed Android host black-screen regression after upgrading to v0.2.2 by declaring H.264-only host encode capabilities and replaying early encoder start commands.

---

## [v0.2.1] - 2026-11-26

This release decouples `rabbitcontrol-core` updates from Android APK releases so users can safely check for, verify, and apply precompiled native core updates directly inside the app without reinstalling the full APK, while also improving device identity stability and upstream core compatibility.

### Added
- **Dynamic Core Hot-Updates**: The app continues to bundle a baseline core for offline startup and fallback, and now allows manually checking and downloading newer `core-v*` core releases under **Me → Core Update** or **About → Core Version**. Verified updates can be applied immediately via app restart or on next launch, and users can revert to the built-in core at any time.
- **Offline Signature Verification & Android 14–16 Compliance**: Downloaded core manifests are verified offline with ECDSA P-256 (`SHA256withECDSA`), binaries are checked against SHA-256 and exact byte size, stored in private internal storage with read-only permissions prior to `System.load()`, and built with 16 KB ELF page alignment.
- **Automatic Crash-Loop Rollback**: Boot attempts are persisted synchronously before loading a dynamic core; if a new core fails ABI validation or crashes repeatedly during startup, the next launch automatically blacklists it and rolls back to the previous working version or the built-in baseline.
- **Optional Download Mirror Support**: Added an optional mirror prefix setting in the core update dialog to speed up GitHub Releases downloads where needed.
- **Device Removal**: Added support for removing a device from the device detail screen.

### Changed & Fixed
- **Device Identity Stability**: Migrated to a stable Android device key scheme with automatic cleanup of legacy duplicate device entries.
- **Upstream Core Alignment**: Aligned native JNI bridges with `rabbitcontrol-core` (v0.1.23) registration/login metadata (`app_version`, `arch`) and signaling options, and bumped the APK to `0.2.1` (`versionCode = 3`).

---

## [v0.2.0] - 2026-11-25

A major Jetpack Compose UI overhaul for RabbitControl Android, delivering full remote desktop controls, built-in remote tools, Android host mode, and in-app updates.

### Added
- **Jetpack Compose + Material 3 Redesign**: Rebuilt the device list, device detail, remote assistance, profile/settings screens, light/dark themes, and native splash screen with Compose.
- **Low-Latency Remote Desktop Control**:
  - Adaptive portrait and landscape layouts, quick sidebars, bottom keyboard bar, customizable key-mapping overlay, and precision trackpad/direct-touch modes.
  - Real-time floating HUD pill showing frame rate, resolution, latency, and P2P/relay route status.
- **Remote Tools & Android Host Mode**:
  - Built-in File Transfer, ANSI Terminal, and Port Mapping screens.
  - Android host mode via `MediaProjection` screen capture encoding and `AccessibilityService` input injection.
  - In-app GitHub Release update checker and APK installer.

### Changed
- **Core & Build Pipeline**: Aligned with upstream `rabbitcontrol-core` session/signaling APIs (including automatic token refresh and media bridge improvements), enabled Release R8 shrinking/obfuscation, and added GitHub Actions CI & signed release workflows.
