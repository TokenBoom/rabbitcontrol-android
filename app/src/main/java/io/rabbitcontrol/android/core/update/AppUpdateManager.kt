package io.rabbitcontrol.android.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val publishedAt: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSizeBytes: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("tagName", tagName)
        .put("versionName", versionName)
        .put("title", title)
        .put("releaseNotes", releaseNotes)
        .put("htmlUrl", htmlUrl)
        .put("publishedAt", publishedAt)
        .put("apkUrl", apkUrl ?: "")
        .put("apkName", apkName ?: "")
        .put("apkSizeBytes", apkSizeBytes)

    companion object {
        fun fromJson(obj: JSONObject): ReleaseInfo = ReleaseInfo(
            tagName = obj.optString("tagName", ""),
            versionName = obj.optString("versionName", ""),
            title = obj.optString("title", ""),
            releaseNotes = obj.optString("releaseNotes", ""),
            htmlUrl = obj.optString("htmlUrl", AppUpdateManager.GITHUB_RELEASES_URL),
            publishedAt = obj.optString("publishedAt", ""),
            apkUrl = obj.optString("apkUrl", "").takeIf { it.isNotBlank() },
            apkName = obj.optString("apkName", "").takeIf { it.isNotBlank() },
            apkSizeBytes = obj.optLong("apkSizeBytes", 0L),
        )
    }
}

sealed interface UpdateCheckStatus {
    data object Idle : UpdateCheckStatus
    data object Checking : UpdateCheckStatus
    data class UpToDate(val currentVersion: String) : UpdateCheckStatus
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckStatus
    data class Error(val message: String) : UpdateCheckStatus
}

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
    ) : UpdateDownloadState
    data class ReadyToInstall(
        val apkPath: String,
        val needsInstallPermission: Boolean = false,
    ) : UpdateDownloadState
    data class Error(val message: String) : UpdateDownloadState
}

data class AppUpdateUiState(
    val currentVersion: String,
    val checkStatus: UpdateCheckStatus = UpdateCheckStatus.Idle,
    val downloadState: UpdateDownloadState = UpdateDownloadState.Idle,
    val availableRelease: ReleaseInfo? = null,
) {
    val hasUpdate: Boolean
        get() = availableRelease != null
}

fun compareSemVer(remoteRaw: String, localRaw: String): Int {
    val r = parseSemVerParts(remoteRaw)
    val l = parseSemVerParts(localRaw)
    for (i in 0 until 3) {
        if (r[i] != l[i]) return r[i].compareTo(l[i])
    }
    return 0
}

private fun parseSemVerParts(raw: String): IntArray {
    val cleaned = raw.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
    val tokens = cleaned.split('.')
    return IntArray(3) { idx ->
        tokens.getOrNull(idx)
            ?.takeWhile { it.isDigit() }
            ?.toIntOrNull()
            ?: 0
    }
}

fun pickBestApkAsset(assets: JSONArray?): Triple<String, String, Long>? {
    if (assets == null || assets.length() == 0) return null
    val candidates = mutableListOf<Triple<String, String, Long>>()
    for (i in 0 until assets.length()) {
        val obj = assets.optJSONObject(i) ?: continue
        val name = obj.optString("name", "")
        val url = obj.optString("browser_download_url", "")
        val size = obj.optLong("size", 0L)
        if (name.lowercase().endsWith(".apk") && url.isNotBlank()) {
            candidates += Triple(url, name, size)
        }
    }
    if (candidates.isEmpty()) return null
    return candidates.firstOrNull { it.second.lowercase().contains("arm64") }
        ?: candidates.first()
}

fun cleanReleaseNotes(raw: String): String {
    if (raw.isBlank()) return "暂无更新说明"
    val lines = raw
        .replace("\r\n", "\n")
        .split('\n')
        .map { line ->
            var s = line.trimEnd()
            val trimmed = s.trimStart()
            s = when {
                trimmed.startsWith("### ") -> trimmed.removePrefix("### ").trim()
                trimmed.startsWith("## ") -> trimmed.removePrefix("## ").trim()
                trimmed.startsWith("# ") -> trimmed.removePrefix("# ").trim()
                trimmed.startsWith("- ") -> "• ${trimmed.removePrefix("- ").trim()}"
                trimmed.startsWith("* ") -> "• ${trimmed.removePrefix("* ").trim()}"
                else -> s
            }
            s.replace("**", "").replace("`", "")
        }
    return lines.joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
        .ifBlank { "暂无更新说明" }
}

internal fun extractApkFromExpandedAssetsHtml(html: String): Pair<String, String>? {
    val regex = Regex("""href="([^"]*/releases/download/[^"]+\.apk)"""")
    val matches = regex.findAll(html).map { match ->
        val path = match.groupValues[1]
        val fullUrl = if (path.startsWith("http")) path else "https://github.com$path"
        val fileName = fullUrl.substringAfterLast('/').substringBefore('?')
        fullUrl to fileName
    }.toList()
    if (matches.isEmpty()) return null
    return matches.firstOrNull { it.second.lowercase().contains("arm64") } ?: matches.first()
}

internal fun extractReleaseNotesFromTagHtml(html: String): String {
    val bodyMatch = Regex("""<div[^>]*class="[^"]*markdown-body[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?: return "暂无更新说明"
    val plain = bodyMatch
        .replace(Regex("""<li[^>]*>"""), "\n• ")
        .replace(Regex("""<(p|br|h[1-6]|div)[^>]*>"""), "\n")
        .replace(Regex("""<[^>]+>"""), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
    return cleanReleaseNotes(plain)
}

private class HttpStatusException(val code: Int, override val message: String) : Exception(message)

class AppUpdateManager(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences("rabbitcontrol_app_update", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val currentVersion: String = resolveVersionName(context)
    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    private fun loadInitialState(): AppUpdateUiState {
        val cachedRaw = prefs.getString(KEY_CACHED_RELEASE, null)
        val cached = cachedRaw?.let { raw ->
            runCatching { ReleaseInfo.fromJson(JSONObject(raw)) }.getOrNull()
        }
        return if (cached != null && compareSemVer(cached.versionName, currentVersion) > 0) {
            AppUpdateUiState(
                currentVersion = currentVersion,
                checkStatus = UpdateCheckStatus.UpdateAvailable(cached),
                availableRelease = cached,
            )
        } else {
            if (cachedRaw != null) {
                prefs.edit().remove(KEY_CACHED_RELEASE).apply()
            }
            AppUpdateUiState(currentVersion = currentVersion)
        }
    }

    fun checkForUpdate(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force) {
            val lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
            if (now - lastCheck < AUTO_CHECK_INTERVAL_MS) {
                return
            }
        }
        if (checkJob?.isActive == true) return
        if (force) {
            _state.value = _state.value.copy(checkStatus = UpdateCheckStatus.Checking)
        }

        checkJob = scope.launch {
            runCatching { fetchLatestRelease() }
                .onSuccess { release ->
                    prefs.edit().putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()).apply()
                    if (release != null && compareSemVer(release.versionName, currentVersion) > 0) {
                        prefs.edit().putString(KEY_CACHED_RELEASE, release.toJson().toString()).apply()
                        _state.value = _state.value.copy(
                            checkStatus = UpdateCheckStatus.UpdateAvailable(release),
                            availableRelease = release,
                        )
                    } else {
                        prefs.edit().remove(KEY_CACHED_RELEASE).apply()
                        _state.value = _state.value.copy(
                            checkStatus = UpdateCheckStatus.UpToDate(currentVersion),
                            availableRelease = null,
                        )
                    }
                }
                .onFailure { err ->
                    if (force) {
                        val msg = when (err) {
                            is HttpStatusException -> when (err.code) {
                                403, 429 -> "GitHub 接口请求受限，请稍后再试"
                                else -> "检查更新失败（HTTP ${err.code}）"
                            }
                            else -> "网络连接失败，请检查网络或代理设置"
                        }
                        _state.value = _state.value.copy(checkStatus = UpdateCheckStatus.Error(msg))
                    }
                }
        }
    }

    fun startDownload(release: ReleaseInfo) {
        val apkUrl = release.apkUrl
        if (apkUrl.isNullOrBlank()) {
            _state.value = _state.value.copy(
                downloadState = UpdateDownloadState.Error("该版本未提供 APK 安装包，请前往浏览器查看"),
            )
            return
        }
        downloadJob?.cancel()
        downloadJob = scope.launch {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            updatesDir.listFiles()?.forEach { runCatching { it.delete() } }

            val safeName = (release.apkName ?: "rabbitcontrol-${release.versionName}.apk")
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
            val partFile = File(updatesDir, "$safeName.part")
            val targetFile = File(updatesDir, safeName)

            _state.value = _state.value.copy(
                downloadState = UpdateDownloadState.Downloading(
                    downloadedBytes = 0L,
                    totalBytes = release.apkSizeBytes,
                    speedBytesPerSec = 0L,
                ),
            )

            var conn: HttpURLConnection? = null
            try {
                conn = openFollowingRedirects(apkUrl, accept = "application/octet-stream")
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw HttpStatusException(code, "下载失败（HTTP $code）")
                }
                val contentLength = conn.contentLengthLong.let { len ->
                    if (len > 0L) len else release.apkSizeBytes
                }
                val buffer = ByteArray(16 * 1024)
                var downloaded = 0L
                var lastReportTime = System.currentTimeMillis()
                var lastReportBytes = 0L
                var speedBps = 0L

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
                                speedBps = ((downloaded - lastReportBytes) * 1000L / dt).coerceAtLeast(0L)
                                lastReportTime = now
                                lastReportBytes = downloaded
                                _state.value = _state.value.copy(
                                    downloadState = UpdateDownloadState.Downloading(
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

                if (targetFile.exists()) targetFile.delete()
                if (!partFile.renameTo(targetFile)) {
                    throw IllegalStateException("安装包保存失败")
                }

                val canInstall = context.packageManager.canRequestPackageInstalls()
                _state.value = _state.value.copy(
                    downloadState = UpdateDownloadState.ReadyToInstall(
                        apkPath = targetFile.absolutePath,
                        needsInstallPermission = !canInstall,
                    ),
                )
                triggerInstall(targetFile.absolutePath)
            } catch (e: kotlinx.coroutines.CancellationException) {
                runCatching { partFile.delete() }
                _state.value = _state.value.copy(downloadState = UpdateDownloadState.Idle)
                throw e
            } catch (e: Exception) {
                runCatching { partFile.delete() }
                val msg = when (e) {
                    is HttpStatusException -> "下载失败（HTTP ${e.code}）"
                    else -> e.message?.takeIf { it.isNotBlank() } ?: "下载安装包失败，可尝试浏览器下载"
                }
                _state.value = _state.value.copy(downloadState = UpdateDownloadState.Error(msg))
            } finally {
                conn?.disconnect()
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = _state.value.copy(downloadState = UpdateDownloadState.Idle)
    }

    fun resetDownloadState() {
        _state.value = _state.value.copy(downloadState = UpdateDownloadState.Idle)
    }

    fun triggerInstall(apkPath: String): Boolean {
        val file = File(apkPath)
        if (!file.exists()) {
            _state.value = _state.value.copy(
                downloadState = UpdateDownloadState.Error("安装包文件已清理，请重新下载"),
            )
            return false
        }
        val canInstall = context.packageManager.canRequestPackageInstalls()
        if (!canInstall) {
            _state.value = _state.value.copy(
                downloadState = UpdateDownloadState.ReadyToInstall(
                    apkPath = apkPath,
                    needsInstallPermission = true,
                ),
            )
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(permIntent) }
            return false
        }

        _state.value = _state.value.copy(
            downloadState = UpdateDownloadState.ReadyToInstall(
                apkPath = apkPath,
                needsInstallPermission = false,
            ),
        )
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return true
    }

    private fun fetchLatestRelease(): ReleaseInfo? {
        val conn = openFollowingRedirects(GITHUB_API_LATEST, accept = "application/vnd.github+json")
        try {
            val code = conn.responseCode
            if (code == 404) return null
            if (code == 403 || code == 429) {
                // Shared VPN exit IPs frequently exhaust unauthenticated api.github.com quotas; fall back to web Releases.
                return fetchLatestReleaseFromWebFallback()
            }
            if (code !in 200..299) {
                throw HttpStatusException(code, "HTTP $code")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val obj = JSONObject(body)
            val tag = obj.optString("tag_name", "").trim()
            if (tag.isBlank()) return null
            val ver = tag.removePrefix("v").removePrefix("V")
            val asset = pickBestApkAsset(obj.optJSONArray("assets"))
            return ReleaseInfo(
                tagName = tag,
                versionName = ver,
                title = obj.optString("name", tag).ifBlank { tag },
                releaseNotes = cleanReleaseNotes(obj.optString("body", "")),
                htmlUrl = obj.optString("html_url", GITHUB_RELEASES_URL).ifBlank { GITHUB_RELEASES_URL },
                publishedAt = obj.optString("published_at", "").take(10),
                apkUrl = asset?.first,
                apkName = asset?.second,
                apkSizeBytes = asset?.third ?: 0L,
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchLatestReleaseFromWebFallback(): ReleaseInfo? {
        val conn = openFollowingRedirects("$GITHUB_REPO_URL/releases/latest", accept = "text/html,application/xhtml+xml")
        try {
            val code = conn.responseCode
            if (code == 404) return null
            if (code !in 200..299) {
                throw HttpStatusException(code, "HTTP $code")
            }
            val finalUrl = conn.url.toString()
            val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val tagMarker = "/releases/tag/"
            val idx = finalUrl.indexOf(tagMarker)
            if (idx < 0) return null
            val encodedTag = finalUrl.substring(idx + tagMarker.length).substringBefore('?').substringBefore('#').trim()
            if (encodedTag.isBlank()) return null
            val decodedTag = java.net.URLDecoder.decode(encodedTag, "UTF-8")
            val ver = decodedTag.removePrefix("v").removePrefix("V")
            val asset = fetchApkFromExpandedAssets(encodedTag)
            return ReleaseInfo(
                tagName = decodedTag,
                versionName = ver,
                title = decodedTag,
                releaseNotes = extractReleaseNotesFromTagHtml(html),
                htmlUrl = finalUrl,
                publishedAt = "",
                apkUrl = asset?.first,
                apkName = asset?.second,
                apkSizeBytes = 0L,
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchApkFromExpandedAssets(encodedTag: String): Pair<String, String>? {
        val conn = openFollowingRedirects(
            "$GITHUB_REPO_URL/releases/expanded_assets/$encodedTag",
            accept = "text/html",
        )
        return try {
            if (conn.responseCode !in 200..299) return null
            val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            extractApkFromExpandedAssetsHtml(html)
        } catch (_: Exception) {
            null
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
                setRequestProperty("User-Agent", "RabbitControl-Android/$currentVersion")
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw HttpStatusException(code, "重定向地址为空")
                currentUrl = URL(URL(currentUrl), location).toString()
            } else {
                return conn
            }
        }
        throw IllegalStateException("重定向次数过多")
    }

    companion object {
        const val GITHUB_REPO_URL = "https://github.com/TokenBoom/rabbitcontrol-android"
        const val GITHUB_ISSUES_URL = "https://github.com/TokenBoom/rabbitcontrol-android/issues"
        const val GITHUB_RELEASES_URL = "https://github.com/TokenBoom/rabbitcontrol-android/releases"
        const val GITHUB_API_LATEST = "https://api.github.com/repos/TokenBoom/rabbitcontrol-android/releases/latest"
        private const val AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
        private const val KEY_LAST_CHECK_MS = "last_check_ms"
        private const val KEY_CACHED_RELEASE = "cached_release"

        private fun resolveVersionName(context: Context): String = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "0.2.0"
    }
}
