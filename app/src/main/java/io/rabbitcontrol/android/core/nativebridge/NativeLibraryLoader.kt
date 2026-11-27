package io.rabbitcontrol.android.core.nativebridge

import android.content.Context
import android.util.Log
import io.rabbitcontrol.android.BuildConfig
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.update.CoreAbiArtifact
import io.rabbitcontrol.android.core.update.CoreManifest
import io.rabbitcontrol.android.core.update.CoreManifestVerifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

data class LoadedCoreInfo(
    val version: String = BuildConfig.BUNDLED_CORE_VERSION,
    val abiVersion: Int = BuildConfig.CORE_ABI_VERSION,
    val coreGitSha: String = BuildConfig.BUNDLED_CORE_GIT_SHA,
    val isDynamic: Boolean = false,
    val builtInVersion: String = BuildConfig.BUNDLED_CORE_VERSION,
    val lastFallbackReason: String? = null,
    val pendingRestartVersion: String? = null,
)

internal object NativeLibraryLoader {
    private const val TAG = "RC_CORE_LOADER"
    private const val LIB_NAME = "rabbitcontrol_android"

    private val loadLock = Any()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var store: CoreRuntimeStore? = null

    @Volatile
    private var libraryLoaded = false

    @Volatile
    private var loadedDynamicEntry: InstalledCoreEntry? = null

    private val _state = MutableStateFlow(LoadedCoreInfo())
    val state: StateFlow<LoadedCoreInfo> = _state.asStateFlow()

    fun bindApplication(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (store == null) {
            val s = CoreRuntimeStore(File(ctx.filesDir, "core_runtime"))
            store = s
            val runtimeState = s.loadState()
            _state.value = _state.value.copy(
                lastFallbackReason = runtimeState.lastFallbackReason,
            )
        }
    }

    fun runtimeStore(): CoreRuntimeStore? = store

    fun ensureLoaded() {
        if (libraryLoaded) return
        synchronized(loadLock) {
            if (libraryLoaded) return
            val s = store
            if (s == null) {
                System.loadLibrary(LIB_NAME)
                libraryLoaded = true
                loadedDynamicEntry = null
                _state.value = LoadedCoreInfo(
                    version = BuildConfig.BUNDLED_CORE_VERSION,
                    abiVersion = BuildConfig.CORE_ABI_VERSION,
                    coreGitSha = BuildConfig.BUNDLED_CORE_GIT_SHA,
                    isDynamic = false,
                    builtInVersion = BuildConfig.BUNDLED_CORE_VERSION,
                )
                return
            }

            repeat(3) {
                when (val candidate = s.selectCandidateForBoot(
                    builtInVersion = BuildConfig.BUNDLED_CORE_VERSION,
                    supportedAbi = BuildConfig.CORE_ABI_VERSION,
                )) {
                    is BootCandidate.Dynamic -> {
                        val entry = candidate.entry
                        val soFile = candidate.soFile
                        val integrityOk = CoreManifestVerifier.verifyFileIntegrity(
                            file = soFile,
                            expectedSha256 = entry.sha256,
                            expectedSizeBytes = entry.sizeBytes,
                        )
                        if (!integrityOk) {
                            Log.e(TAG, "Integrity mismatch for dynamic core v${entry.version}")
                            s.blacklistAndFallback(
                                version = entry.version,
                                reason = AppStrings.get(
                                    R.string.core_fallback_integrity,
                                    "内核 v${entry.version} 完整性校验失败，已自动回退",
                                    entry.version,
                                ),
                                builtInVersion = BuildConfig.BUNDLED_CORE_VERSION,
                                supportedAbi = BuildConfig.CORE_ABI_VERSION,
                            )
                            return@repeat
                        }
                        if (!CoreRuntimeStore.enforceReadOnlyExecutable(soFile)) {
                            Log.e(TAG, "Failed to enforce read-only permissions on ${soFile.absolutePath}")
                            s.blacklistAndFallback(
                                version = entry.version,
                                reason = AppStrings.get(
                                    R.string.core_fallback_perms,
                                    "内核 v${entry.version} 权限设置失败，已自动回退",
                                    entry.version,
                                ),
                                builtInVersion = BuildConfig.BUNDLED_CORE_VERSION,
                                supportedAbi = BuildConfig.CORE_ABI_VERSION,
                            )
                            return@repeat
                        }

                        s.recordBootAttempt(entry.version)
                        try {
                            System.load(soFile.absolutePath)
                            libraryLoaded = true
                            loadedDynamicEntry = entry
                            val runtimeState = s.loadState()
                            _state.value = LoadedCoreInfo(
                                version = entry.version,
                                abiVersion = entry.abiVersion,
                                coreGitSha = entry.coreGitSha,
                                isDynamic = true,
                                builtInVersion = BuildConfig.BUNDLED_CORE_VERSION,
                                lastFallbackReason = runtimeState.lastFallbackReason,
                            )
                            Log.i(TAG, "Loaded dynamic core v${entry.version} (${entry.coreGitSha})")
                            return
                        } catch (t: Throwable) {
                            Log.e(TAG, "System.load failed for dynamic core v${entry.version}", t)
                            s.blacklistAndFallback(
                                version = entry.version,
                                reason = AppStrings.get(
                                    R.string.core_fallback_load,
                                    "内核 v${entry.version} 加载失败，已自动回退",
                                    entry.version,
                                ),
                                builtInVersion = BuildConfig.BUNDLED_CORE_VERSION,
                                supportedAbi = BuildConfig.CORE_ABI_VERSION,
                            )
                        }
                    }

                    BootCandidate.BuiltIn -> {
                        loadBuiltIn(s)
                        return
                    }
                }
            }

            loadBuiltIn(s)
        }
    }

    private fun loadBuiltIn(s: CoreRuntimeStore) {
        System.loadLibrary(LIB_NAME)
        libraryLoaded = true
        loadedDynamicEntry = null
        val runtimeState = s.loadState()
        _state.value = LoadedCoreInfo(
            version = BuildConfig.BUNDLED_CORE_VERSION,
            abiVersion = BuildConfig.CORE_ABI_VERSION,
            coreGitSha = BuildConfig.BUNDLED_CORE_GIT_SHA,
            isDynamic = false,
            builtInVersion = BuildConfig.BUNDLED_CORE_VERSION,
            lastFallbackReason = runtimeState.lastFallbackReason,
        )
        Log.i(TAG, "Loaded built-in core v${BuildConfig.BUNDLED_CORE_VERSION}")
    }

    fun onRuntimeVerified(rawCoreInfoJson: String?): Boolean {
        val parsed = rawCoreInfoJson?.let { raw ->
            runCatching { JSONObject(raw) }.getOrNull()
        }
        val nativeVer = parsed?.optString("coreVersion", "")?.trim()?.takeIf { it.isNotBlank() }
        val nativeAbi = parsed?.optInt("abiVersion", -1) ?: -1
        val nativeSha = parsed?.optString("coreGitSha", "")?.trim()?.takeIf { it.isNotBlank() }

        val dynamic = loadedDynamicEntry
        val s = store
        if (dynamic != null && nativeAbi > 0 && nativeAbi != BuildConfig.CORE_ABI_VERSION) {
            val reason = AppStrings.get(
                R.string.core_fallback_abi,
                "内核 ABI 不兼容（需要 v${BuildConfig.CORE_ABI_VERSION}，实际 v$nativeAbi），下次重启将自动恢复",
                BuildConfig.CORE_ABI_VERSION,
                nativeAbi,
            )
            s?.blacklistAndFallback(
                version = dynamic.version,
                reason = reason,
                builtInVersion = BuildConfig.BUNDLED_CORE_VERSION,
                supportedAbi = BuildConfig.CORE_ABI_VERSION,
            )
            _state.value = _state.value.copy(
                abiVersion = nativeAbi,
                lastFallbackReason = reason,
            )
            return false
        }

        val currentVer = dynamic?.version ?: nativeVer ?: BuildConfig.BUNDLED_CORE_VERSION
        s?.markBootSuccess(
            loadedVersion = currentVer,
            isDynamic = dynamic != null,
        )
        val runtimeState = s?.loadState()
        _state.value = _state.value.copy(
            version = currentVer,
            abiVersion = if (nativeAbi > 0) nativeAbi else BuildConfig.CORE_ABI_VERSION,
            coreGitSha = dynamic?.coreGitSha ?: nativeSha ?: BuildConfig.BUNDLED_CORE_GIT_SHA,
            isDynamic = dynamic != null,
            builtInVersion = BuildConfig.BUNDLED_CORE_VERSION,
            lastFallbackReason = runtimeState?.lastFallbackReason,
        )
        refreshPendingState()
        return true
    }

    fun onRuntimeInitFailed(err: Throwable) {
        val dynamic = loadedDynamicEntry ?: return
        val reason = AppStrings.get(
            R.string.core_fallback_init,
            "内核 v${dynamic.version} 初始化失败，下次启动将自动回滚",
            dynamic.version,
        )
        Log.e(TAG, reason, err)
        store?.blacklistAndFallback(
            version = dynamic.version,
            reason = reason,
            builtInVersion = BuildConfig.BUNDLED_CORE_VERSION,
            supportedAbi = BuildConfig.CORE_ABI_VERSION,
        )
        _state.value = _state.value.copy(lastFallbackReason = reason)
        refreshPendingState()
    }

    fun installVerifiedCore(
        manifest: CoreManifest,
        artifact: CoreAbiArtifact,
        stagedFile: File,
    ): InstalledCoreEntry {
        val s = store ?: throw IllegalStateException("运行时存储尚未初始化")
        val entry = s.installVerifiedBinary(manifest, artifact, stagedFile)
        refreshPendingState()
        return entry
    }

    fun revertToBuiltIn() {
        store?.revertToBuiltIn()
        refreshPendingState()
    }

    fun isVersionBlacklisted(version: String): Boolean {
        val s = store ?: return false
        return version in s.loadState().blacklistedVersions
    }

    fun refreshPendingState() {
        val s = store ?: return
        val runtimeState = s.loadState()
        val running = _state.value
        val desiredVersion = runtimeState.activeVersion ?: BuildConfig.BUNDLED_CORE_VERSION
        val desiredIsDynamic = runtimeState.activeVersion != null
        val pending = if (desiredVersion != running.version || desiredIsDynamic != running.isDynamic) {
            desiredVersion
        } else {
            null
        }
        _state.value = running.copy(
            lastFallbackReason = runtimeState.lastFallbackReason,
            pendingRestartVersion = pending,
        )
    }
}
