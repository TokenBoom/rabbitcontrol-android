# Android 控制端架构

## 选型决定

Android 负责界面、手势、生命周期与硬件解码，Rust 负责服务器身份、会话协议、网络路由和输入顺序。首期优先适配 Android 10+、arm64、H.264。这个范围覆盖当前选用的解码器硬件能力查询接口，也便于在同一套真机样本上比较性能。

| 候选 | 对本项目的影响 | 取舍 |
| --- | --- | --- |
| Kotlin 原生 Views + Rust JNI | SurfaceView、MotionEvent 和系统生命周期可直接使用。现有 Rust 核心继续维护协议，界面更新与视频线程独立 | 采用。当前界面规模只需两个 Activity |
| Tauri / WebView 移植桌面界面 | 桌面命令和页面有复用价值，但仍需重做 Android 解码表面、触控、软键盘和前后台管理 | 没有足够的界面复用收益 |
| Flutter + 平台插件 | 可以实现同样的硬解，但仍需 Surface 或纹理插件、手势桥和 Rust 桥。当前交付只有 Android | 后续如共享移动 UI 成为目标再评估 |
| Kotlin 重写传输或改用 WebRTC | 需要重新实现或改造既有主机的握手、关键帧恢复、输入优先级和中继互通 | 首期沿用现有线协议 |

### 工程基线

| 层次 | 首版定案 | 实现位置 |
| --- | --- | --- |
| 界面与输入 | Kotlin、平台 Activity、原生 Views 与 XML 资源主题；MotionEvent 处理触控 | [Android 应用](../app/src/main/java/io/rabbitcontrol/android) · [界面规范](ui-design.md) |
| 视频 | H.264 Annex-B、MediaCodec、SurfaceView；按能力选择硬解与低延迟模式 | [视频模块](../app/src/main/java/io/rabbitcontrol/android/video) |
| 协议与身份 | 本地路径依赖 Rust core，通过 JNI 向 Kotlin 暴露平台所需操作；会话采用现有 QUIC、信令和 mTLS 中继 | [JNI](../native/src/lib.rs) · [会话接线进度](#当前工程与真会话接线) |
| 构建与设备 | Gradle 9.1.0、AGP 9.0.1、JDK 17、NDK 28.2.13676358、cargo-ndk；minSdk 29、targetSdk 36、arm64-v8a | [应用构建配置](../app/build.gradle.kts) |

## 三个代码库给出的约束

| 来源与版本 | 实现事实 | Android 接入点 |
| --- | --- | --- |
| core · `f1dc361` | `SessionHandle` 接收会话命令和输入，发布连接、协商、HUD、显示配置与结束事件。QUIC 提供媒体 datagram、可靠控制和独立的高优先级可靠输入流。现有重组、关键帧请求和重连均有实现 | 会话用 Rust 工作者线程，UI 只消费状态。后续用协商尺寸更新显示区。[会话桥](../rabbitcontrol-core/crates/rc-client-core/src/session_bridge.rs) · [输入批处理](../rabbitcontrol-core/crates/rc-input/src/batch.rs) |
| server · `57ca00d` | API 默认 21118 提供登录、证书签发及本账号设备。21116/TCP 是长度前缀 JSON 信令，21116/UDP 负责地址探测，21117/TCP 提供 mTLS 中继 | 复用 core 的证书及信令客户端。设备码路径获取授权后依次尝试 LAN、P2P、中继，并显示实际路由。[API](../rabbitcontrol-server/backend/src/api.rs) · [信令](../rabbitcontrol-server/backend/src/signal.rs) · [中继](../rabbitcontrol-server/backend/src/relay/mod.rs) |
| macOS · `8834d79` | 被控端把 VideoToolbox 输出转成 H.264 Annex-B，IDR 携带 SPS/PPS。控制端在主线程准备显示层，再将其交给会话。输入与像素分开传递 | 原样接收 AVC 完整访问单元。Surface 的有效期由 Android 管理；本地光标使用单独的 View。[编码输出](../rabbitcontrol-macos/crates/rc-encode-vt/src/ffi/mod.rs) · [平台注册](../rabbitcontrol-macos/crates/rc-platform-macos/src/lib.rs) · [会话入口](../rabbitcontrol-macos/crates/rc-ui/src/commands.rs) |

## 当前工程与真会话接线

当前代码跑通了服务器探测和本地解码两条路径。下图的虚线是接下来需要接通的真会话接口。

```mermaid
flowchart LR
    UI[Kotlin 首页] --> JNI[JNI / rc-client-core] --> API[HTTPS API / PKI]
    MAC[macOS 主机] -. QUIC 或中继 .-> S[Rust 会话 / 重组 / IDR 恢复]
    S -. 完整 H.264 访问单元 .-> D[MediaCodec]
    P[本地 Annex-B 样片] --> D --> V[SurfaceView]
    T[手势 / 本地光标] --> I[rc-input]
    I -. 高优先级输入流 .-> MAC
```

**视频接口是第一个集成点。** core 的 `Decoder::decode` 和 `Renderer::present` 有同步返回与帧时序约定。`FrameTexture` 在 Android 目标上目前只有 CPU 载体。直接调用 `SessionHandle::spawn_with_surface` 仍可能走到 fake 降级。接真会话前应在 core 的控制端管线增加异步 Surface 输出适配，包含提交完整帧、取回带帧号的呈现结果、请求 IDR 与关闭四种动作，保持现有桌面后端可用。选择硬解失败时应明确报告降级。普通软件 MediaCodec 仍可输出真实画面。[解码契约](../rabbitcontrol-core/crates/rc-decode/src/lib.rs) · [纹理载体](../rabbitcontrol-core/crates/rc-capture/src/lib.rs) · [控制端管线](../rabbitcontrol-core/crates/rc-pipeline/src/controller.rs)

- 每个会话由单一媒体线程操作 codec。通过 JNI 移交完整压缩帧及其所有权，向 codec 的输入缓冲做一次有界拷贝。解码后的像素由系统直接送到 Surface。JNI 不逐分片回调，UI 不分配 Bitmap。Android 11+ 先查询所选解码器的低延迟能力，支持时才开启。当前样片路径已使用该选择逻辑。[Android MediaCodec](https://developer.android.com/reference/android/media/MediaCodec) · [Android 低延迟解码](https://developer.android.com/about/versions/11/features#low-latency-decoding)
- 实时路径沿用 core 的有界重组。建议压缩帧交接槽首版限为两帧；溢出导致参考链断裂时丢弃待解依赖帧、等待携带 SPS/PPS 的 IDR，并通过 core 的看门狗去重请求。已经解码的积压输出可以只显示最新一帧。切分辨率或 Surface 失效时建立新一代解码器，旧一代的回调不能更新新会话。
- 正常启动依次经历授权、路由、握手、协商、等待 IDR、首帧送显。首帧到达后再显示“正在控制”。HUD 分开记接收帧、解码输出、送到 Surface 的帧和渲染回调。当前校验页明确标出“送显”，按秒刷新。锁屏或切后台时优先释放按键，再暂停媒体和释放 Surface；恢复时按策略重新授权或恢复原会话并请求 IDR。
- 控制链路使用设备证书、预期对端设备 ID 和主机的密码/审批或同账号 OwnerTicket。注册、证书续期与 refresh 轮换走现有 core 流程。Android 初始化时将 core 配置目录指向应用私有目录，规划用 Keystore 管理包裹密钥以加密静态凭据。现有 CertManager 使用可读取的密钥材料；要使用硬件不可导出的私钥，还需接入平台签名接口。[证书管理](../rabbitcontrol-core/crates/rc-client-core/src/cert_manager.rs) · [路径约定](../rabbitcontrol-core/crates/rc-client-core/src/config_paths.rs)

## 手机上如何操控

当前实现把触控层与等比显示的 Surface 放进同一矩形。上下或左右黑边不接收手势。单击左键、长按右键、单指拖动、双指滚动，手指移动立即更新本地光标。坐标由 core 归一化到 0–65535。拖动取消或页面失焦时释放已经按下的键。

真会话把手势生成的 `InputEvent` 直接交给 `SessionHandle::send_input`。继续使用 core 的移动合并、按键顺序和高优先级通道。小屏下一步增加相对触控板模式与双指缩放，缩放后的点击按同一视口变换逆映射。外接键盘转换为 Set 1 扫描码，维护修饰键快照；中文 IME 的提交文本需要独立协商的文本输入或显式粘贴能力。[输入事件定义](../rabbitcontrol-core/crates/rc-input/src/lib.rs)

## 实施顺序和性能验收

| 阶段 | 交付与通过条件 |
| --- | --- |
| 工程基线（本次） | 构建 arm64 APK；真实 API 探测入口可调用；本地固定片源可揭示硬解/软解和送显帧率；校验触控编码和返回清理 |
| LAN 真机闭环 | 先接入带身份校验的单台 Mac 与 Android 真机，完成“批准连接 → 首帧 → 点击远端按钮 → 看到按钮反馈”。验证动态分辨率、旋转、回前台与双方断开 |
| 设备码与弱网 | 接入账号、证书、9 位设备码、P2P 和中继。分开测试强制中继、网络切换、丢包后 IDR 恢复和按键松开 |
| 交互扩展 | 在已测的视频预算内增加触控板、缩放、外接键盘和低延迟音频，再评估剪贴板与其他桌面工具 |

建议以 **同一 Wi-Fi、RTT ≤ 10 ms、持续运动的 1080p60 主机画面、两台不同芯片的 arm64 真机** 为首轮验收环境。稳态 5 分钟送显平均 ≥ 55 fps，帧间隔 P95 ≤ 33 ms，手机触控到远端反馈出现在手机上的延迟 P95 ≤ 100 ms。再连续跑 20 分钟记录温控、峰值内存和掉帧。720p60 作为体验退档。这里的数字是首轮工程目标，等待真机测量。

每次记录手机型号、解码器名、硬解标志、分辨率、码率和实际路由。用 core HUD 取 RTT、吞吐和丢包，用 Android 渲染回调与 Perfetto 观察帧间隔，用高速摄影测触控到画面反馈。人工注入 1% / 3% 丢包后检查 IDR 恢复时间；另外单测持续拖动中断网、视口边缘和前后台连续切换。中继与公网路径单独报告分位数。
