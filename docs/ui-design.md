# RabbitControl Android 界面设计规范

本规范定义 RabbitControl Android 的视觉语言、明暗双色板、低阴影扁平层级及远控交互布局。设计以**简洁、克制、高频触手可及**为核心原则，保留 `#3370FF` 品牌蓝识别，支持**跟随系统 / 浅色 / 深色**三种外观模式，并在远程桌面会话中使用固定深色沉浸式 Chrome。

---

## 1. 核心设计原则

1. **低阴影扁平层级（Flat Hairline Hierarchy）**
   - 所有常规卡片、设备列表项、表单容器、开关分组采用 **`0.dp` 阴影（`shadowElevation = 0.dp`）** 与 **`0.dp` 色调叠加（`tonalElevation = 0.dp`）**，通过 `1.dp` 极细描边（`palette.line`）与页面画布区分层次。
   - 仅浮层控件（FAB 悬浮按钮、会话边缘胶囊、悬浮键盘条、弹窗与抽屉面板）允许使用不超过 **`2.dp`** 的微弱投影，并配合 `1.dp` 描边保持边缘利落。
2. **亮暗双模式高对比一致性**
   - 所有界面颜色均通过语义 Token（`LocalRabbitPalette`）驱动，禁止在业务页面硬编码颜色。
   - 浅色与深色模式下正文及次要说明文字均保证 `>= 4.5:1` 对比度；品牌文字与链接色区分填充色（浅色 `#285EDB`、深色 `#7CA6FF`），确保在浅底、深底与半透明标签底上均清晰可读。
3. **远控人体工学优先**
   - 手机竖屏采用「上画面 + 中段快捷操控条 + 下半屏精密触控板」单手操作布局；横屏采用全屏无遮挡沉浸画布 + 可自动淡出的边缘微胶囊。
   - 会话控制面板按**操控 / 画质 / 工具**三段式分段收敛，消除长列表滚动疲劳；底部固定「锁屏」与「断开连接」快捷操作。
   - 键盘扩展条左右固定「软键盘」与「收起」按钮，中间按修饰键 / 系统热键 / 导航键分组横向滚动，避免滚动后找不到关闭按钮。
4. **触控与节律规范**
   - 严格遵循 `4.dp` 间距网格（`4 / 8 / 12 / 16 / 20 / 24.dp`）。
   - 主要点击区域（按钮、顶部返回/收藏图标、列表项、开关行）高度/尺寸 `>= 44–48.dp`，相邻可点击芯片间距 `>= 6–8.dp`。

---

## 2. 色彩系统（Tokens）

### 2.1 主应用亮/暗模式（`RabbitPalette`）

| 语义角色 | 浅色模式 (Light) | 深色模式 (Dark) | 用途说明 |
| --- | --- | --- | --- |
| `primary` | `#3370FF` | `#3370FF` | 品牌主按钮填充、激活态描边 |
| `primaryText` | `#285EDB` | `#7CA6FF` | 文本链接、高亮数值、选中图标 |
| `primarySoft` | `#EBF1FF` | `#1B2B4B` | 选中态浅底、徽章底色 |
| `canvas` | `#F4F6F9` | `#0F131A` | 页面底层画布 |
| `surface` | `#FFFFFF` | `#171C26` | 卡片、底栏、弹层背景 |
| `surfaceSubtle` | `#F8F9FB` | `#1C222E` | 次级容器、表头区块 |
| `field` | `#F3F5F8` | `#202735` | 输入框底色、分段控件轨道 |
| `line` | `#E2E7EF` | `#2A3343` | 卡片 `1.dp` 描边、分割线 |
| `lineStrong` | `#CFD7E3` | `#3A465C` | 按钮/聚焦强化描边 |
| `text` | `#151922` | `#F1F4FA` | 一级标题与正文 |
| `muted` | `#556173` | `#A6B3C7` | 二级说明与元数据 |
| `success` / `successSoft` | `#156B41` / `#E7F6EE` | `#6EE7B7` / `#133126` | 在线、连接成功 |
| `warning` / `warningSoft` | `#B45309` / `#FEF3C7` | `#FBBF24` / `#332510` | 连接中、状态提醒 |
| `danger` / `dangerSoft` | `#B92530` / `#FCECEE` | `#FF8E96` / `#3A1A20` | 断开、删除、错误提示 |

### 2.2 远控会话固定深色（`SessionPalette`）

| 语义角色 | 色值 | 用途说明 |
| --- | --- | --- |
| `canvas` | `#07090D` | 远端桌面画布与黑边 |
| `chrome` | `#12161F` | 竖屏中段操作栏、控制面板底色 |
| `chromeElevated` | `#181E29` | 悬浮键盘条、边缘胶囊、状态弹窗 |
| `surface` | `#1E2533` | 磁贴与按键芯片常态底色 |
| `surfaceActive` | `#22355A` | 激活态磁贴底色 |
| `trackpad` | `#0D1117` | 竖屏玻璃触控板与终端视图底色 |
| `border` | `#263042` | 面板与控件 `1.dp` 细描边 |
| `text` / `muted` | `#F1F5FB` / `#9AA8BD` | 主要文字 / 次要标签 |
| `accent` / `accentText` | `#3370FF` / `#7CA6FF` | 选中按钮、激活修饰键 |
| `danger` / `dangerText` | `#C9303B` / `#FF8C94` | 断开会话按钮与警示文字 |

---

## 3. 阴影、圆角与排版

- **阴影层级（`RabbitElevation`）：** `Card = 0.dp`、`Floating = 2.dp`、`Sheet = 2.dp`。
- **圆角体系（`RabbitRadius`）：** 小芯片 `8.dp`、按钮/输入框/内嵌磁贴 `12.dp`、标准卡片 `16.dp`、Hero 卡片与底部弹层 `20.dp`、胶囊 `999.dp`。
- **字体排版（`RabbitTypography`）：**
  - 页面大标题：`24.sp / SemiBold`
  - 弹层/区块标题：`18.sp / SemiBold`
  - 卡片标题与表单标题：`15–16.sp / SemiBold–Medium`
  - 正文与输入：`14–15.sp / Normal`
  - 辅助说明与标签：`12–13.sp`，帧率、分辨率、设备码与终端使用等宽字体（`FontFamily.Monospace`）。

---

## 4. 页面架构与重构说明

1. **首页（`HomeScreen`）**：底部三栏（我的设备 / 远程协助 / 我的），`NavigationBar` 使用 `0.dp` 色调阴影 + 顶部 `1.dp` 分割线。
   - **我的设备**：简洁页眉 + 服务器切换胶囊 + 扁平设备卡片（缩略图 + 名称 + 系统 + 状态点）。
   - **远程协助**：输入卡片下直接内嵌「最近连接 / 我的收藏」分段列表，一键回填，减少二次弹窗。
   - **我的**：账号与服务器卡片、**外观主题分段切换（跟随系统 / 浅色 / 深色）**、全宽交互模式与屏幕方向分段控件、统一开关列表。
2. **设备详情（`DeviceDetailScreen`）**：紧凑 Hero 卡片（`188.dp`）+ 2×2 常用工具网格（遵循自定义排序）+ 设备信息卡片 + 低阴影底部工具/电源管理面板（`ModalBottomSheet`）。
3. **远控会话（`DesktopSessionScreen` + `SessionSidebars` + `SessionKeyMappingOverlay` + `SessionControlPanel` + `SessionKeyboardBar`）**：
   - **竖屏单手**：上画面（45%）+ 中段 52dp 快捷操控条 + 下半屏玻璃触控板（55%）。
   - **横屏双侧边栏（UU 风格黑边工具栏）**：利用全面屏左右黑边空间布置 `68.dp` 纯黑侧边栏（可通过控制面板切换为纯全屏悬浮胶囊模式）：
     - **右侧 3 个固定核心按钮**：`操作`（控制中心）、`键盘`（扩展键位与软键盘）、`按键`（点击开关虚拟按键映射，长按直接进入按键拖拽编辑模式）。
     - **左侧 3 个可自定义快捷槽位**：默认提供 `放大`、`缩小`、`显示桌面`，长按任意槽位（或在控制中心点击「左侧快捷栏配置」）可替换为缩放（`放大/缩小/1:1`）、系统快捷键（`显示桌面/所有窗口/切换窗口/截屏/任务管理/锁屏`）、编辑热键（`复制/粘贴/撤销/全选/回车/Esc`）、鼠标（`左/右/中键/拖动/滚轮`）或置空。
   - **虚拟按键映射浮层**：支持 `WASD 键位`、`办公快捷`、`方向与确认` 三套预设方案，支持多指按住不放（含 8 向虚拟摇杆）、自定义透明度，以及可视化拖拽/缩放/增删按键编辑器。
   - 点击右上角 HUD 可直达控制面板「画质」分页；「操控 / 画质 / 工具」三分页结构清晰，底部常驻「锁屏 / 断开连接」。
4. **认证与工具页（`AuthScreen`、`FileTransferScreen`、`TerminalScreen`、`PortMapScreen`）**：统一使用 `RabbitTopBar`、`RabbitCard`、`RabbitSegmentedControl` 与低阴影交互控件，修复系统栏内边距叠加问题并确保横屏/平板居中宽度收敛。

---

## 5. 代码映射

- 主题与 Token：`app/src/main/java/io/rabbitcontrol/android/ui/theme/RabbitTheme.kt`
- 公共扁平组件：`app/src/main/java/io/rabbitcontrol/android/ui/components/RabbitComponents.kt`
- 主题偏好持久化：`app/src/main/java/io/rabbitcontrol/android/core/data/InteractionPrefsStore.kt`
- 远控会话：`app/src/main/java/io/rabbitcontrol/android/ui/session/`
- 页面实现：`app/src/main/java/io/rabbitcontrol/android/ui/screens/`
