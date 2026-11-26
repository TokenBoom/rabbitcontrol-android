package io.rabbitcontrol.android.core.update

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import io.rabbitcontrol.android.BuildConfig
import io.rabbitcontrol.android.core.nativebridge.LoadedCoreInfo
import io.rabbitcontrol.android.core.nativebridge.NativeLibraryLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class CoreReleaseCandidate(
    val manifest: CoreManifest,
    val artifact: CoreAbiArtifact,
    val soDownloadUrl: String,
    val htmlUrl: String,
)

data class CoreIncompatibleNotice(
    val coreVersion: String,
    val requiredAbiVersion: Int,
    val minAppVersion: String,
    val htmlUrl: String,
)

sealed interface CoreCheckStatus {
    data object Idle : CoreCheckStatus
    data object Checking : CoreCheckStatus
    data class UpToDate(
        val currentVersion: String,
        val incompatibleNotice: CoreIncompatibleNotice? = null,
    ) : CoreCheckStatus
    data class UpdateAvailable(
        val candidate: CoreReleaseCandidate,
        val incompatibleNotice: CoreIncompatibleNotice? = null,
    ) : CoreCheckStatus
    data class Error(val message: String) : CoreCheckStatus
}

sealed interface CoreDownloadState {
    data object Idle : CoreDownloadState
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
    ) : CoreDownloadState
    data object Verifying : CoreDownloadState
    data class ReadyToRestart(
        val targetVersion: String,
        val isRevert: Boolean = false,
    ) : CoreDownloadState
    data class Error(val message: String) : CoreDownloadState
}

data class CoreUpdateUiState(
    val loadedCore: LoadedCoreInfo = LoadedCoreInfo(),
    val checkStatus: CoreCheckStatus = CoreCheckStatus.Idle,
    val downloadState: CoreDownloadState = CoreDownloadState.Idle,
    val availableUpdate: CoreReleaseCandidate? = null,
    val incompatibleNotice: CoreIncompatibleNotice? = null,
    val mirrorPrefix: String = "",
    val publicKeyConfigured: Boolean = false,
) {
    val hasUpdate: Boolean
        get() = availableUpdate != null
}

internal data class VerifiedCoreReleaseEntry(
    val manifest: CoreManifest,
    val htmlUrl: String,
    val assetUrlOverrides: Map<String, String> = emptyMap(),
)

internal data class CoreReleaseSelection(
    val bestCompatible: CoreReleaseCandidate?,
    val incompatibleNotice: CoreIncompatibleNotice?,
)

internal fun extractCoreReleaseTagsFromHtml(html: String): List<String> {
    val regex = Regex("""href="[^"]*/releases/tag/(core-v[^"/?#]+)"""")
    val tags = LinkedHashSet<String>()
    for (match in regex.findAll(html)) {
        val raw = match.groupValues[1]
        val decoded = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw).trim()
        if (decoded.startsWith("core-v")) {
            tags += decoded
        }
    }
    return tags.toList()
}

internal fun normalizeMirrorPrefix(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val lower = trimmed.lowercase()
    if (!lower.startsWith("https://") && !lower.startsWith("http://")) return ""
    return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
}

internal fun applyMirrorPrefix(url: String, mirrorPrefix: String): String {
    val normalized = normalizeMirrorPrefix(mirrorPrefix)
    if (normalized.isEmpty()) return url
    if (url.startsWith(normalized)) return url
    return normalized + url
}

internal fun selectCoreUpdateFromManifests(
    releases: List<VerifiedCoreReleaseEntry>,
    effectiveCurrentVersion: String,
    supportedAbiVersion: Int,
    supportedAbis: List<String>,
    blacklistedVersions: Set<String>,
): CoreReleaseSelection {
    var bestCompatible: CoreReleaseCandidate? = null
    var bestIncompatible: CoreIncompatibleNotice? = null

    for (entry in releases) {
        val manifest = entry.manifest
        if (manifest.abiVersion == supportedAbiVersion) {
            if (manifest.coreVersion in blacklistedVersions) continue
            val matchedAbi = supportedAbis.firstOrNull { it in manifest.abis } ?: continue
            val artifact = manifest.abis[matchedAbi] ?: continue
            if (compareSemVer(manifest.coreVersion, effectiveCurrentVersion) > 0) {
                val curBest = bestCompatible
                if (curBest == null || compareSemVer(manifest.coreVersion, curBest.manifest.coreVersion) > 0) {
                    val downloadUrl = entry.assetUrlOverrides[artifact.fileName]
                        ?: "${AppUpdateManager.GITHUB_REPO_URL}/releases/download/${manifest.tagName}/${artifact.fileName}"
                    bestCompatible = CoreReleaseCandidate(
                        manifest = manifest,
                        artifact = artifact,
                        soDownloadUrl = downloadUrl,
                        htmlUrl = entry.htmlUrl,
                    )
                }
            }
        } else if (manifest.abiVersion > supportedAbiVersion) {
            val curIncompat = bestIncompatible
            if (curIncompat == null || compareSemVer(manifest.coreVersion, curIncompat.coreVersion) > 0) {
                bestIncompatible = CoreIncompatibleNotice(
                    coreVersion = manifest.coreVersion,
                    requiredAbiVersion = manifest.abiVersion,
                    minAppVersion = manifest.minAppVersion,
                    htmlUrl = entry.htmlUrl,
                )
            }
        }
    }

    return CoreReleaseSelection(
        bestCompatible = bestCompatible,
        incompatibleNotice = bestIncompatible,
    )
}

private class CoreHttpStatusException(val code: Int, override val message: String) : Exception(message)

class CoreUpdateManager(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences("rabbitcontrol_core_update", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val publicKeyConfigured = CoreManifestVerifier.isPublicKeyConfigured(BuildConfig.CORE_SIGNING_PUBLIC_KEY_B64)

    private val _state = MutableStateFlow(
        CoreUpdateUiState(
            loadedCore = NativeLibraryLoader.state.value,
            mirrorPrefix = normalizeMirrorPrefix(prefs.getString(KEY_MIRROR_PREFIX, "").orEmpty()),
            publicKeyConfigured = publicKeyConfigured,
        ),
    )
    val state: StateFlow<CoreUpdateUiState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    init {
        scope.launch {
            NativeLibraryLoader.state.collect { loaded ->
                _state.value = _state.value.copy(loadedCore = loaded)
            }
        }
    }

    fun setMirrorPrefix(raw: String) {
        val normalized = normalizeMirrorPrefix(raw)
        prefs.edit().putString(KEY_MIRROR_PREFIX, normalized).apply()
        _state.value = _state.value.copy(mirrorPrefix = normalized)
    }

    fun checkForUpdate(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force) {
            val lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
            if (now - lastCheck < AUTO_CHECK_INTERVAL_MS) return
        }
        if (checkJob?.isActive == true) return
        if (!publicKeyConfigured) {
            if (force) {
                _state.value = _state.value.copy(
                    checkStatus = CoreCheckStatus.Error("当前安装包未配置内核验签公钥，无法验证在线内核"),
                )
            }
            return
        }
        if (force) {
            _state.value = _state.value.copy(checkStatus = CoreCheckStatus.Checking)
        }

        checkJob = scope.launch {
            runCatching { fetchAndSelectCoreUpdate() }
                .onSuccess { selection ->
                    prefs.edit().putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()).apply()
                    val candidate = selection.bestCompatible
                    val incompat = selection.incompatibleNotice
                    if (candidate != null) {
                        _state.value = _state.value.copy(
                            checkStatus = CoreCheckStatus.UpdateAvailable(candidate, incompat),
                            availableUpdate = candidate,
                            incompatibleNotice = incompat,
                        )
                    } else {
                        val effectiveVer = effectiveCurrentVersion()
                        _state.value = _state.value.copy(
                            checkStatus = CoreCheckStatus.UpToDate(effectiveVer, incompat),
                            availableUpdate = null,
                            incompatibleNotice = incompat,
                        )
                    }
                }
                .onFailure { err ->
                    if (force) {
                        val msg = when (err) {
                            is CoreHttpStatusException -> when (err.code) {
                                403, 429 -> "GitHub 接口请求受限，请稍后再试或配置镜像前缀"
                                else -> "检查内核更新失败（HTTP ${err.code}）"
                            }
                            is SecurityException -> err.message ?: "内核清单签名校验失败"
                            else -> "网络连接失败，请检查网络或镜像设置"
                        }
                        _state.value = _state.value.copy(checkStatus = CoreCheckStatus.Error(msg))
                    }
                }
        }
    }

    fun startDownload(candidate: CoreReleaseCandidate) {
        if (!publicKeyConfigured) {
            _state.value = _state.value.copy(
                downloadState = CoreDownloadState.Error("未配置验签公钥，已拒绝安装动态内核"),
            )
            return
        }
        downloadJob?.cancel()
        downloadJob = scope.launch {
            val store = NativeLibraryLoader.runtimeStore()
                ?: run {
                    NativeLibraryLoader.bindApplication(context)
                    NativeLibraryLoader.runtimeStore()
                }
            if (store == null) {
                _state.value = _state.value.copy(
                    downloadState = CoreDownloadState.Error("内核存储目录初始化失败"),
                )
                return@launch
            }

            val stagingDir = store.stagingDir()
            stagingDir.listFiles()?.forEach { runCatching { it.delete() } }
            val safeVer = candidate.manifest.coreVersion.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val partFile = File(stagingDir, "core-$safeVer.part")

            _state.value = _state.value.copy(
                downloadState = CoreDownloadState.Downloading(
                    downloadedBytes = 0L,
                    totalBytes = candidate.artifact.sizeBytes,
                    speedBytesPerSec = 0L,
                ),
            )

            var conn: HttpURLConnection? = null
            try {
                val downloadUrl = applyMirrorPrefix(candidate.soDownloadUrl, _state.value.mirrorPrefix)
                conn = openFollowingRedirects(downloadUrl, accept = "application/octet-stream")
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw CoreHttpStatusException(code, "下载失败（HTTP $code）")
                }
                val contentLength = conn.contentLengthLong.let { len ->
                    if (len > 0L) len else candidate.artifact.sizeBytes
                }

                val buffer = ByteArray(16 * 1024)
                var downloaded = 0L
                var lastReportTime = System.currentTimeMillis()
                var lastReportBytes = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(partFile).use { output ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            val dt = now - lastReportTime
                            if (dt >= 150L) {
                                val speedBps = ((downloaded - lastReportBytes) * 1000L / dt).coerceAtLeast(0L)
                                lastReportTime = now
                                lastReportBytes = downloaded
                                _state.value = _state.value.copy(
                                    downloadState = CoreDownloadState.Downloading(
                                        downloadedBytes = downloaded,
                                        totalBytes = contentLength,
                                        speedBytesPerSec = speedBps,
                                    ),
                                )
                            }
                        }
                        output.flush()
                    }
                }

                _state.value = _state.value.copy(downloadState = CoreDownloadState.Verifying)
                val ok = CoreManifestVerifier.verifyFileIntegrity(
                    file = partFile,
                    expectedSha256 = candidate.artifact.sha256,
                    expectedSizeBytes = candidate.artifact.sizeBytes,
                )
                if (!ok) {
                    throw SecurityException("内核文件 SHA-256 或大小校验不通过，已丢弃")
                }

                NativeLibraryLoader.installVerifiedCore(
                    manifest = candidate.manifest,
                    artifact = candidate.artifact,
                    stagedFile = partFile,
                )
                _state.value = _state.value.copy(
                    downloadState = CoreDownloadState.ReadyToRestart(
                        targetVersion = candidate.manifest.coreVersion,
                        isRevert = false,
                    ),
                    availableUpdate = null,
                    checkStatus = CoreCheckStatus.UpToDate(
                        currentVersion = candidate.manifest.coreVersion,
                        incompatibleNotice = _state.value.incompatibleNotice,
                    ),
                )
            } catch (e: CancellationException) {
                runCatching { partFile.delete() }
                _state.value = _state.value.copy(downloadState = CoreDownloadState.Idle)
                throw e
            } catch (e: Exception) {
                runCatching { partFile.delete() }
                val msg = when (e) {
                    is CoreHttpStatusException -> "下载内核失败（HTTP ${e.code}）"
                    else -> e.message?.takeIf { it.isNotBlank() } ?: "下载或校验内核失败"
                }
                _state.value = _state.value.copy(downloadState = CoreDownloadState.Error(msg))
            } finally {
                conn?.disconnect()
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = _state.value.copy(downloadState = CoreDownloadState.Idle)
    }

    fun resetDownloadState() {
        _state.value = _state.value.copy(downloadState = CoreDownloadState.Idle)
    }

    fun revertToBuiltIn() {
        downloadJob?.cancel()
        NativeLibraryLoader.revertToBuiltIn()
        _state.value = _state.value.copy(
            downloadState = CoreDownloadState.ReadyToRestart(
                targetVersion = BuildConfig.BUNDLED_CORE_VERSION,
                isRevert = true,
            ),
        )
    }

    fun restartApp() {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(launchIntent)
        }
        Process.killProcess(Process.myPid())
    }

    private fun effectiveCurrentVersion(): String {
        val loaded = NativeLibraryLoader.state.value
        val pending = loaded.pendingRestartVersion
        return if (!pending.isNullOrBlank() && compareSemVer(pending, loaded.version) > 0) {
            pending
        } else {
            loaded.version
        }
    }

    private fun fetchAndSelectCoreUpdate(): CoreReleaseSelection {
        val verifiedReleases = fetchVerifiedCoreReleases()
        val storeState = NativeLibraryLoader.runtimeStore()?.loadState()
        val blacklist = storeState?.blacklistedVersions ?: emptySet()
        return selectCoreUpdateFromManifests(
            releases = verifiedReleases,
            effectiveCurrentVersion = effectiveCurrentVersion(),
            supportedAbiVersion = BuildConfig.CORE_ABI_VERSION,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            blacklistedVersions = blacklist,
        )
    }

    private fun fetchVerifiedCoreReleases(): List<VerifiedCoreReleaseEntry> {
        val conn = openFollowingRedirects(GITHUB_API_RELEASES, accept = "application/vnd.github+json")
        try {
            val code = conn.responseCode
            if (code == 404) return emptyList()
            if (code == 403 || code == 429) {
                return fetchVerifiedCoreReleasesFromWebFallback()
            }
            if (code !in 200..299) {
                throw CoreHttpStatusException(code, "HTTP $code")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(body)
            val entries = mutableListOf<VerifiedCoreReleaseEntry>()
            var inspected = 0

            for (i in 0 until arr.length()) {
                if (inspected >= MAX_RELEASES_TO_INSPECT) break
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optBoolean("draft", false) || obj.optBoolean("prerelease", false)) continue
                val tag = obj.optString("tag_name", "").trim()
                if (!tag.startsWith("core-v")) continue

                inspected++
                val htmlUrl = obj.optString("html_url", "${AppUpdateManager.GITHUB_REPO_URL}/releases/tag/$tag")
                val assets = obj.optJSONArray("assets") ?: continue
                val assetUrls = LinkedHashMap<String, String>()
                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    val name = asset.optString("name", "").trim()
                    val url = asset.optString("browser_download_url", "").trim()
                    if (name.isNotBlank() && url.isNotBlank()) {
                        assetUrls[name] = url
                    }
                }
                val manifestUrl = assetUrls[MANIFEST_FILENAME] ?: continue
                val sigUrl = assetUrls[MANIFEST_SIG_FILENAME] ?: continue
                val verified = downloadAndVerifyManifest(manifestUrl, sigUrl, htmlUrl, assetUrls) ?: continue
                entries += verified
            }
            return entries
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchVerifiedCoreReleasesFromWebFallback(): List<VerifiedCoreReleaseEntry> {
        val releasesUrl = applyMirrorPrefix(
            "${AppUpdateManager.GITHUB_REPO_URL}/releases",
            _state.value.mirrorPrefix,
        )
        val conn = openFollowingRedirects(releasesUrl, accept = "text/html,application/xhtml+xml")
        val tags = try {
            val code = conn.responseCode
            if (code == 404) return emptyList()
            if (code !in 200..299) {
                throw CoreHttpStatusException(code, "HTTP $code")
            }
            val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            extractCoreReleaseTagsFromHtml(html)
        } finally {
            conn.disconnect()
        }

        val entries = mutableListOf<VerifiedCoreReleaseEntry>()
        for (tag in tags.take(MAX_RELEASES_TO_INSPECT)) {
            val baseDownload = "${AppUpdateManager.GITHUB_REPO_URL}/releases/download/$tag"
            val htmlUrl = "${AppUpdateManager.GITHUB_REPO_URL}/releases/tag/$tag"
            val verified = downloadAndVerifyManifest(
                manifestUrl = "$baseDownload/$MANIFEST_FILENAME",
                sigUrl = "$baseDownload/$MANIFEST_SIG_FILENAME",
                htmlUrl = htmlUrl,
                assetOverrides = emptyMap(),
            ) ?: continue
            entries += verified
        }
        return entries
    }

    private fun downloadAndVerifyManifest(
        manifestUrl: String,
        sigUrl: String,
        htmlUrl: String,
        assetOverrides: Map<String, String>,
    ): VerifiedCoreReleaseEntry? {
        val mirror = _state.value.mirrorPrefix
        val manifestBytes = runCatching {
            downloadBytes(applyMirrorPrefix(manifestUrl, mirror), maxBytes = 512 * 1024)
        }.getOrNull() ?: return null
        val sigText = runCatching {
            downloadBytes(applyMirrorPrefix(sigUrl, mirror), maxBytes = 16 * 1024).toString(Charsets.UTF_8)
        }.getOrNull() ?: return null

        if (!CoreManifestVerifier.verifyManifestSignature(
                manifestBytes = manifestBytes,
                signatureBase64 = sigText,
                publicKeyBase64 = BuildConfig.CORE_SIGNING_PUBLIC_KEY_B64,
            )
        ) {
            return null
        }

        val manifest = runCatching {
            CoreManifest.parse(manifestBytes.toString(Charsets.UTF_8))
        }.getOrNull() ?: return null

        return VerifiedCoreReleaseEntry(
            manifest = manifest,
            htmlUrl = htmlUrl,
            assetUrlOverrides = assetOverrides,
        )
    }

    private fun downloadBytes(url: String, maxBytes: Int): ByteArray {
        val conn = openFollowingRedirects(url, accept = "application/octet-stream")
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw CoreHttpStatusException(code, "HTTP $code")
            }
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            conn.inputStream.use { input ->
                var total = 0
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) {
                        throw IllegalStateException("响应体积超出上限")
                    }
                    out.write(buf, 0, read)
                }
            }
            return out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }

    private fun openFollowingRedirects(url: String, accept: String): HttpURLConnection {
        var currentUrl = url
        repeat(5) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", "RabbitControl-Android/${BuildConfig.VERSION_NAME}")
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw CoreHttpStatusException(code, "重定向地址为空")
                currentUrl = URL(URL(currentUrl), location).toString()
            } else {
                return conn
            }
        }
        throw IllegalStateException("重定向次数过多")
    }

    companion object {
        const val GITHUB_API_RELEASES =
            "https://api.github.com/repos/TokenBoom/rabbitcontrol-android/releases?per_page=20"
        const val MANIFEST_FILENAME = "core-manifest.json"
        const val MANIFEST_SIG_FILENAME = "core-manifest.json.sig"
        private const val AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
        private const val MAX_RELEASES_TO_INSPECT = 8
        private const val KEY_LAST_CHECK_MS = "last_check_ms"
        private const val KEY_MIRROR_PREFIX = "mirror_prefix"
    }
}
