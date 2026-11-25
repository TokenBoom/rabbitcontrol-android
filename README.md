# RabbitControl Android 控制端

首版面向用手机或平板控制 Mac 的场景。工程采用 **Kotlin 原生 Views + Rust 共享内核 + MediaCodec / SurfaceView**。当前提供设备、远程协助与我的三个入口，支持自建服务器校验、真实账号登录和设备同步，也可离线播放样片并校验触控。真实桌面会话的接入设计和验收条件见 [架构设计](docs/architecture.md)。

应用主题、颜色、字体、控件和手势规则见 [Android 界面规范](docs/ui-design.md)。

## 构建

需要 JDK 17、Android SDK Platform 36、Build Tools 36.0.0、NDK 28.2.13676358、Rust stable 和 cargo-ndk 4。设置 `ANDROID_HOME` 或用 Android Studio 创建 `local.properties`。当前 APK 面向 Android 10 及以上的 arm64 设备，arm64 模拟器也可安装。

本次工作区已经放好 `rabbitcontrol-core/`。从新的 Android 工程检出构建时，将 core 仓放在项目根目录下的同名目录，当前验证版本为 `f1dc361`。Gradle 通过本地 Cargo 路径依赖编译它。macOS 与 server 仓仅作协议和行为参考。

```sh
rustup target add aarch64-linux-android
cargo install cargo-ndk --version 4.1.2 --locked
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Gradle 会构建优化版 Rust 动态库并放入 APK。用 `cargo test --locked --manifest-path native/Cargo.toml` 可在开发机检查触控事件编码和指纹格式校验。

## 在设备上验证

- **我的 → 服务器设置**。填写 Android 能访问的服务端主机名或 IP，API 默认端口为 21118，支持 `主机:端口`。用管理员提供的 64 位 CA 指纹核对，确认后保存。地址必须与服务端证书匹配。同一台 Mac 上的 Android 模拟器可以用本地端口转发测试。
- **我的 → 登录**。使用 RabbitControl 服务端账号。首次登录如需修改初始密码，按页面提示设置新密码。登录令牌使用 Android Keystore 加密后保存。
- **设备**。列表来自账号设备接口，可按在线状态筛选，点开设备详情后可将设备码带入远程协助。
- **远程协助**。输入并确认 9 位设备码，可以收藏和复用最近确认过的设备码。实时桌面会话仍在接入阶段。
- **我的 → 画面与手势校验**。播放本地样片，核对解码器、送显帧率和触控映射。

## 代码入口

| 位置 | 职责 |
| --- | --- |
| [Android 首页](app/src/main/java/io/rabbitcontrol/android/MainActivity.kt) | 三栏导航、设备列表、服务器校验和账号交互 |
| [Rust JNI](native/src/lib.rs) | 复用证书探测、账号 API、坐标归一化和输入编码 |
| [会话画面](app/src/main/java/io/rabbitcontrol/android/PreviewActivity.kt) | 等比画面区域、Surface 生命周期与触控层 |
| [视频](app/src/main/java/io/rabbitcontrol/android/video) | Annex-B 样片、按能力选择 AVC 解码器、直接送显 |
| [输入](app/src/main/java/io/rabbitcontrol/android/input/TouchPad.kt) | 手势识别、本地光标和取消时释放按键 |

## 已验证

API 36 arm64 模拟器已安装并启动调试 APK。用 server 仓现有 HTTPS 路由、PKI 与内存测试存储启动临时实例，Android 端正确指纹探测成功，错误指纹被拒。样片通过 MediaCodec 出画，连续截取的画面像素有变化；点按、长按、拖动松开、旋转和返回前台均已验证。尚待物理设备与真实 macOS 主机联调，尤其是端到端输入延迟、长时间温控和中继弱网表现。
