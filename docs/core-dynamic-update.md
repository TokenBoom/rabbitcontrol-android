# `rabbitcontrol-core` 动态更新方案与发版指南

本文档说明 Android 端独立更新 `rabbitcontrol-core`（预编译 `librabbitcontrol_android.so`）的架构、密钥管理、发版流水线和端侧验收方法。

---

## 1. 整体架构

- **APK 基线内核**：正常构建 APK 时，依然会把当前 `rabbitcontrol-core` 编译一份内置到 APK 中，用于离线首次启动和异常回滚兜底。如果安装了更新的 APK 且其内置内核版本 ≥ 本地动态内核版本，App 会自动切换回 APK 内置内核。
- **动态内核包**：独立发布在 GitHub Releases 的 `core-v*` 标签下（`make_latest: false`，不会干扰旧版 APK 检查 `v*` 整包更新），包含：
  - `librabbitcontrol_android-arm64-v8a.so`（强制 16 KB ELF 页对齐）
  - `core-manifest.json`（含 `coreVersion`、`coreGitSha`、`abiVersion`、`minAppVersion`、`sha256`、`sizeBytes`）
  - `core-manifest.json.sig`（ECDSA P-256 `SHA256withECDSA` 离线签名）
- **安全与合规（`targetSdk = 36`）**：
  - 所有动态内核保存在应用私有内部目录 `filesDir/core_runtime/<version>/librabbitcontrol_android.so`。
  - 加载前强制通过 ECDSA P-256 验签 + SHA-256/文件大小校验，并在 `System.load()` 前剥离写权限（`chmod 555`）。
  - 加载前同步 `fsync` 写入启动标记；若连续启动失败（含原生闪退），下次冷启动自动拉黑该版本并回滚到上一可用版本或 APK 内置内核。

---

## 2. 一次性生成 ECDSA P-256 签名密钥

在仓库根目录执行：

```bash
chmod +x scripts/gen-core-signing-key.sh scripts/build-core-manifest.sh
./scripts/gen-core-signing-key.sh
```

脚本会生成：
1. `core-signing-private.pem`（已被 `.gitignore` 忽略，**切勿提交到仓库**）：完整内容粘贴到 GitHub Repo Settings → Secrets and variables → Actions → **`CORE_SIGNING_PRIVATE_KEY_PEM`**。
2. `app/core-signing-public.pem`：**提交到仓库**，Gradle 会在构建时自动读取并注入 `BuildConfig.CORE_SIGNING_PUBLIC_KEY_B64`。

> 也可通过 GitHub Secret `CORE_SIGNING_PUBLIC_KEY_B64` 或 `-PcoreSigningPublicKey=...` 覆盖公钥。如果公钥为空，App 会拒绝安装任何在线内核（fail-closed）。

---

## 3. 如何发布新版内核（无需发新版 APK）

### 方式 A：GitHub Actions 手动触发
在 `rabbitcontrol-android` 仓库的 Actions → **Release Core Native Runtime** → Run workflow：
- `core_ref`：填写 `rabbitcontrol-core` 的 tag / 分支 / SHA（如 `v0.1.19`）
- `core_version`：可选覆盖版本号（留空则自动取 `core_ref` 的 tag）
- `abi_version`：当前为 `1`（仅当修改了 `Core.kt` JNI 接口或跨端 JSON 契约时才递增）

### 方式 B：推送 `core-v*` Tag
```bash
git tag core-v0.1.19
git push origin core-v0.1.19
```

### 方式 C：从 `rabbitcontrol-core` 仓库发版联动触发
`release-core.yml` 已监听 `repository_dispatch` 事件 `rabbitcontrol-core-release`，`rabbitcontrol-core` 发版流水线可通过 PAT 发送 `client_payload`（`core_ref`、`core_version`、`abi_version`、`min_app_version`、`release_notes`）自动触发发布。

---

## 4. ABI 版本升级约定

- `CORE_ABI_VERSION` 同时定义在 [native/src/lib.rs](../native/src/lib.rs) 与 [app/build.gradle.kts](../app/build.gradle.kts)。
- **仅修复/优化 `rabbitcontrol-core` 或 Rust 运行时内部逻辑**：保持 `abi_version = 1` 不变，旧版 App 可直接下载生效。
- **修改了 [Core.kt](../app/src/main/java/io/rabbitcontrol/android/session/Core.kt) 的 JNI 签名或改变了 Kotlin 侧依赖的 JSON 结构**：必须递增 `CORE_ABI_VERSION`（如 `1 -> 2`），并在新内核的 `abi_version` 中声明为 `2`。旧版 App 会继续匹配最新的 ABI v1 内核，同时提示用户「另有更新内核，需升级 App 后可用」。

---

## 5. 真机与本地验证清单

1. **16 KB 页对齐检查**：
   ```bash
   "$ANDROID_HOME/ndk/28.2.13676358/toolchains/llvm/prebuilt/"*"/bin/llvm-readelf" -l \
     app/build/generated/rustJniLibs/arm64-v8a/librabbitcontrol_android.so | grep -E "LOAD|Align"
   ```
   确认所有 `LOAD` 段对齐均为 `0x4000`。
2. **功能自检**：
   - 在真机「我的 → 内核更新」或「关于我们 → 内核版本」检查更新并手动下载，确认弹窗提示「立即重启生效 / 下次启动生效」；
   - 重启后确认来源标签从 `内置` 变为 `已热更新`，且远控会话/投屏功能正常；
   - 点击「恢复内置内核」并重启，确认切回 `内置`；
   - 模拟崩溃回滚：将动态 `.so` 替换为无效字节或未对齐文件，连续重启两次应自动回滚至内置内核并展示回滚提示。
