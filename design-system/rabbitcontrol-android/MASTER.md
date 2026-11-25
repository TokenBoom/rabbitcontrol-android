# RabbitControl Android Design System — MASTER

> **Platform:** Android (Jetpack Compose + Material 3)  
> **Style:** Minimalism & Swiss Precision · Flat Hairline Hierarchy · Ergonomic Remote Control  
> **Brand Identity:** Rabbit Blue (`#3370FF`)  
> **Theme Modes:** Light · Dark · System (`AppThemeMode`), plus Fixed-Dark Session Chrome (`SessionPalette`)

---

## 1. Core Principles

1. **Flat Hairline Hierarchy (Zero Heavy Card Shadows)**
   - Standard cards, list items, and grouped containers use **`0.dp` shadow elevation**, **`0.dp` tonal elevation**, and a crisp **`1.dp` hairline border** (`palette.line`) on top of `palette.canvas`.
   - Floating overlays (FAB, session micro-pill, floating keyboard bar, bottom/side sheets, active session banner) are capped at **`2.dp` shadow elevation** max.
   - No Material 3 default purple/indigo tonal wash (`surfaceContainer*` mapped explicitly to neutral slate-tinted surfaces).

2. **Light & Dark Mode Contrast Parity**
   - Every surface, border, and semantic state is driven exclusively by `LocalRabbitPalette` / `SessionPalette` tokens—never ad-hoc hex colors in screens.
   - Body and secondary text maintain `>= 4.5:1` contrast in both Light and Dark themes.
   - Brand text/links use `palette.primaryText` (`#285EDB` in Light, `#7CA6FF` in Dark) for high legibility on soft and dark surfaces, while filled primary CTAs use `palette.primary` (`#3370FF`) with `#FFFFFF` label.

3. **Ergonomic Remote Control First**
   - **Portrait One-Handed Mode:** Top video viewport (`45%`), middle `52.dp` ergonomic action dock (`左键`, `拖动` toggle, `右键`, `键盘`, `控制`), bottom precision glass trackpad (`55%`).
   - **Landscape Immersive Mode:** Fullscreen viewport, edge-snapped `44.dp` micro-pill (auto-dims when idle) with 3 instant actions (Keyboard, Touch Mode toggle, Control Panel).
   - **Segmented Control Panel:** Replaces long scrolling menus with 3 focused tabs (`操控`, `画质`, `工具`) and a pinned footer (`锁屏`, `断开连接`).
   - **Pinned-Anchor Keyboard Bar:** Soft-keyboard button pinned left, close button pinned right, grouped scrollable shortcut keys in the center.

4. **Touch Targets & Spacing Rhythm**
   - Strictly `4.dp` grid (`4 / 8 / 12 / 16 / 20 / 24.dp`).
   - Interactive controls and icon buttons maintain `>= 44–48.dp` touch target (`>= 38.dp` height with `6–8.dp` gap for dense keyboard/chip strips).

---

## 2. Color Tokens

### Light & Dark App Tokens (`RabbitPalette`)

| Token | Light | Dark | Role |
| --- | --- | --- | --- |
| `primary` | `#3370FF` | `#3370FF` | Primary CTA fill, active border/indicator |
| `primaryText` | `#285EDB` | `#7CA6FF` | High-contrast link/accent text & active icon |
| `primarySoft` | `#EBF1FF` | `#1B2B4B` | Selected pill tint, icon badge background |
| `canvas` | `#F4F6F9` | `#0F131A` | Page canvas |
| `surface` | `#FFFFFF` | `#171C26` | Flat card & navigation bar surface |
| `surfaceSubtle` | `#F8F9FB` | `#1C222E` | Secondary/nested surface |
| `field` | `#F3F5F8` | `#202735` | Input field & segmented track background |
| `line` | `#E2E7EF` | `#2A3343` | Card `1.dp` hairline border & dividers |
| `lineStrong` | `#CFD7E3` | `#3A465C` | Emphasized border |
| `text` | `#151922` | `#F1F4FA` | Primary text |
| `muted` | `#556173` | `#A6B3C7` | Secondary text & metadata (`>= 4.5:1`) |
| `success` / `successSoft` | `#156B41` / `#E7F6EE` | `#6EE7B7` / `#133126` | Online / connected status |
| `warning` / `warningSoft` | `#B45309` / `#FEF3C7` | `#FBBF24` / `#332510` | Connecting / warning notice |
| `danger` / `dangerSoft` | `#B92530` / `#FCECEE` | `#FF8E96` / `#3A1A20` | Destructive CTA & error banners |

### Remote Session Fixed-Dark Chrome (`SessionPalette`)

| Token | Hex | Role |
| --- | --- | --- |
| `canvas` | `#07090D` | Video letterbox stage |
| `chrome` | `#12161F` | Portrait action dock, sheet backdrop |
| `chromeElevated` | `#181E29` | Floating keyboard bar, micro-pill, dialogs |
| `surface` | `#1E2533` | Control tiles, key chips |
| `surfaceActive` | `#22355A` | Active control tile |
| `trackpad` | `#0D1117` | Portrait glass trackpad & PTY background |
| `border` | `#263042` | Hairline border (`1.dp`) |
| `text` / `muted` | `#F1F5FB` / `#9AA8BD` | High-contrast labels |
| `accent` / `accentText` | `#3370FF` / `#7CA6FF` | Active controls & telemetry highlight |
| `danger` / `dangerText` | `#C9303B` / `#FF8C94` | Disconnect / error |

---

## 3. Elevation, Radius & Spacing Scales

- **Elevation (`RabbitElevation`):** `None = 0.dp`, `Card = 0.dp`, `Floating = 2.dp`, `Sheet = 2.dp`.
- **Radius (`RabbitRadius`):** `Sm = 8.dp` (chips), `Md = 12.dp` (buttons, inputs, tiles), `Lg = 16.dp` (cards), `Xl = 20.dp` (hero card, bottom sheets), `Pill = 999.dp` (capsules).
- **Spacing (`RabbitSpacing`):** `Xs = 4.dp`, `Sm = 8.dp`, `Md = 12.dp`, `Lg = 16.dp`, `Xl = 20.dp`, `Xxl = 24.dp`.

---

## 4. Component Conventions

- **Cards:** Always use `RabbitCard` (`0.dp` shadow, `1.dp` `palette.line` border, `16.dp` radius).
- **Buttons:** `RabbitPrimaryButton`, `RabbitSecondaryButton`, `RabbitDangerButton` (`48.dp` height, `12.dp` radius, `0.dp` shadow).
- **Segmented Switchers:** Use `RabbitSegmentedControl` for 2–3 mutually exclusive choices (Login/Register, Recent/Favorites, Theme mode, Touch mode, Orientation, Control Panel tabs).
- **Bottom Sheets:** Use `ModalBottomSheet(tonalElevation = 0.dp, containerColor = palette.surface)` instead of heavy floating `Dialog`s for tool pickers and server switchers.
