package com.rabbitcontrol.android.core.nativebridge

import com.rabbitcontrol.android.R
import com.rabbitcontrol.android.core.i18n.AppStrings
import com.rabbitcontrol.android.core.update.CoreAbiArtifact
import com.rabbitcontrol.android.core.update.CoreManifest
import com.rabbitcontrol.android.core.update.compareSemVer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class InstalledCoreEntry(
    val version: String,
    val abiVersion: Int,
    val sha256: String,
    val coreGitSha: String,
    val fileName: String,
    val sizeBytes: Long,
    val installedAtMs: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", version)
        .put("abiVersion", abiVersion)
        .put("sha256", sha256)
        .put("coreGitSha", coreGitSha)
        .put("fileName", fileName)
        .put("sizeBytes", sizeBytes)
        .put("installedAtMs", installedAtMs)

    companion object {
        fun fromJson(obj: JSONObject): InstalledCoreEntry? {
            val version = obj.optString("version", "").trim()
            val abiVersion = obj.optInt("abiVersion", 0)
            val sha256 = obj.optString("sha256", "").trim().lowercase()
            val coreGitSha = obj.optString("coreGitSha", "unknown").trim().ifBlank { "unknown" }
            val fileName = obj.optString("fileName", "").trim()
            val sizeBytes = obj.optLong("sizeBytes", 0L)
            val installedAtMs = obj.optLong("installedAtMs", 0L)
            if (version.isBlank() || abiVersion <= 0 || sha256.length != 64 || fileName.isBlank() || sizeBytes <= 0L) {
                return null
            }
            return InstalledCoreEntry(
                version = version,
                abiVersion = abiVersion,
                sha256 = sha256,
                coreGitSha = coreGitSha,
                fileName = fileName,
                sizeBytes = sizeBytes,
                installedAtMs = installedAtMs,
            )
        }
    }
}

data class CoreRuntimeState(
    val activeVersion: String? = null,
    val previousGoodVersion: String? = null,
    val bootingVersion: String? = null,
    val bootAttempts: Int = 0,
    val blacklistedVersions: Set<String> = emptySet(),
    val installed: Map<String, InstalledCoreEntry> = emptyMap(),
    val lastFallbackReason: String? = null,
) {
    fun toJson(): JSONObject {
        val blacklistArr = JSONArray()
        blacklistedVersions.sorted().forEach { blacklistArr.put(it) }
        val installedObj = JSONObject()
        installed.forEach { (ver, entry) -> installedObj.put(ver, entry.toJson()) }
        return JSONObject()
            .put("activeVersion", activeVersion ?: JSONObject.NULL)
            .put("previousGoodVersion", previousGoodVersion ?: JSONObject.NULL)
            .put("bootingVersion", bootingVersion ?: JSONObject.NULL)
            .put("bootAttempts", bootAttempts)
            .put("blacklistedVersions", blacklistArr)
            .put("installed", installedObj)
            .put("lastFallbackReason", lastFallbackReason ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(obj: JSONObject): CoreRuntimeState {
            val blacklist = LinkedHashSet<String>()
            obj.optJSONArray("blacklistedVersions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i, "").trim()
                    if (v.isNotBlank()) blacklist += v
                }
            }
            val installedMap = LinkedHashMap<String, InstalledCoreEntry>()
            obj.optJSONObject("installed")?.let { instObj ->
                val keys = instObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val entryObj = instObj.optJSONObject(key) ?: continue
                    val entry = InstalledCoreEntry.fromJson(entryObj) ?: continue
                    installedMap[entry.version] = entry
                }
            }
            fun optNonBlank(key: String): String? =
                if (obj.isNull(key)) null else obj.optString(key, "").trim().takeIf { it.isNotBlank() }

            return CoreRuntimeState(
                activeVersion = optNonBlank("activeVersion"),
                previousGoodVersion = optNonBlank("previousGoodVersion"),
                bootingVersion = optNonBlank("bootingVersion"),
                bootAttempts = obj.optInt("bootAttempts", 0).coerceAtLeast(0),
                blacklistedVersions = blacklist,
                installed = installedMap,
                lastFallbackReason = optNonBlank("lastFallbackReason"),
            )
        }
    }
}

sealed interface BootCandidate {
    data object BuiltIn : BootCandidate
    data class Dynamic(
        val entry: InstalledCoreEntry,
        val soFile: File,
    ) : BootCandidate
}

class CoreRuntimeStore(
    val rootDir: File,
) {
    private val stateFile = File(rootDir, "state.json")
    private val lock = Any()

    fun stagingDir(): File = File(rootDir, "tmp").apply { mkdirs() }

    fun loadState(): CoreRuntimeState = synchronized(lock) {
        if (!stateFile.isFile) return@synchronized CoreRuntimeState()
        runCatching {
            CoreRuntimeState.fromJson(JSONObject(stateFile.readText(Charsets.UTF_8)))
        }.getOrDefault(CoreRuntimeState())
    }

    fun saveStateSync(state: CoreRuntimeState) = synchronized(lock) {
        rootDir.mkdirs()
        val tmpFile = File(rootDir, "state.json.tmp")
        val bytes = state.toJson().toString(2).toByteArray(Charsets.UTF_8)
        FileOutputStream(tmpFile).use { fos ->
            fos.write(bytes)
            fos.flush()
            runCatching { fos.fd.sync() }
        }
        if (stateFile.exists()) {
            stateFile.delete()
        }
        if (!tmpFile.renameTo(stateFile)) {
            stateFile.writeBytes(bytes)
            tmpFile.delete()
        }
    }

    fun selectCandidateForBoot(
        builtInVersion: String,
        supportedAbi: Int,
        maxBootAttempts: Int = DEFAULT_MAX_BOOT_ATTEMPTS,
    ): BootCandidate = synchronized(lock) {
        var state = loadState()
        var dirty = false

        // 1. Crash-loop guard: if a dynamic version started booting >= maxBootAttempts times without
        // completing initialization (including native SIGSEGV aborts), blacklist it and roll back.
        val stuckVer = state.bootingVersion
        if (stuckVer != null && state.bootAttempts >= maxBootAttempts) {
            state = applyBlacklistAndFallback(
                state = state,
                badVersion = stuckVer,
                reason = AppStrings.get(
                    R.string.core_fallback_crash_loop,
                    "内核 v$stuckVer 连续启动失败，已自动回滚",
                    stuckVer,
                ),
                builtInVersion = builtInVersion,
                supportedAbi = supportedAbi,
            )
            dirty = true
        }

        // 2. If the APK itself was upgraded with an equal or newer built-in core, prefer built-in.
        val activeVer = state.activeVersion
        if (activeVer != null && compareSemVer(activeVer, builtInVersion) <= 0) {
            val prevGood = state.previousGoodVersion?.takeIf { compareSemVer(it, builtInVersion) > 0 }
            state = state.copy(
                activeVersion = null,
                previousGoodVersion = prevGood,
                bootingVersion = null,
                bootAttempts = 0,
            )
            dirty = true
        }

        val candidateVer = state.activeVersion
        if (candidateVer == null) {
            if (dirty) saveStateSync(state)
            return@synchronized BootCandidate.BuiltIn
        }

        val resolved = resolveUsableEntry(state, candidateVer, builtInVersion, supportedAbi)
        if (resolved != null) {
            if (dirty) saveStateSync(state)
            return@synchronized resolved
        }

        // Active entry missing/invalid/incompatible: try falling back to previousGoodVersion.
        val fallbackState = applyBlacklistAndFallback(
            state = state,
            badVersion = candidateVer,
            reason = state.lastFallbackReason ?: AppStrings.get(
                R.string.core_fallback_corrupt,
                "内核 v$candidateVer 文件校验无效，已自动回退",
                candidateVer,
            ),
            builtInVersion = builtInVersion,
            supportedAbi = supportedAbi,
        )
        saveStateSync(fallbackState)
        val prevVer = fallbackState.activeVersion ?: return@synchronized BootCandidate.BuiltIn
        return@synchronized resolveUsableEntry(fallbackState, prevVer, builtInVersion, supportedAbi)
            ?: BootCandidate.BuiltIn
    }

    fun recordBootAttempt(version: String) = synchronized(lock) {
        val state = loadState()
        val nextAttempts = if (state.bootingVersion == version) state.bootAttempts + 1 else 1
        saveStateSync(
            state.copy(
                bootingVersion = version,
                bootAttempts = nextAttempts,
            ),
        )
    }

    fun markBootSuccess(
        loadedVersion: String,
        isDynamic: Boolean,
    ) = synchronized(lock) {
        val state = loadState()
        val prevGood = if (isDynamic) loadedVersion else state.previousGoodVersion
        val nextState = state.copy(
            previousGoodVersion = prevGood,
            bootingVersion = null,
            bootAttempts = 0,
        )
        saveStateSync(nextState)
        pruneUnusedVersionDirs(nextState)
    }

    fun blacklistAndFallback(
        version: String,
        reason: String,
        builtInVersion: String,
        supportedAbi: Int,
    ) = synchronized(lock) {
        val next = applyBlacklistAndFallback(
            state = loadState(),
            badVersion = version,
            reason = reason,
            builtInVersion = builtInVersion,
            supportedAbi = supportedAbi,
        )
        saveStateSync(next)
        pruneUnusedVersionDirs(next)
    }

    fun installVerifiedBinary(
        manifest: CoreManifest,
        artifact: CoreAbiArtifact,
        stagedFile: File,
        nowMs: Long = System.currentTimeMillis(),
    ): InstalledCoreEntry = synchronized(lock) {
        require(CoreManifest.isSafeSegment(manifest.coreVersion)) { "非法内核版本号" }
        val versionDir = safeChildDir(manifest.coreVersion)
        versionDir.mkdirs()

        val targetFile = File(versionDir, TARGET_SO_FILENAME)
        if (targetFile.exists()) {
            targetFile.setWritable(true, true)
            targetFile.delete()
        }
        val tmpDest = File(versionDir, "$TARGET_SO_FILENAME.tmp")
        if (tmpDest.exists()) {
            tmpDest.setWritable(true, true)
            tmpDest.delete()
        }
        if (!stagedFile.renameTo(tmpDest)) {
            stagedFile.copyTo(tmpDest, overwrite = true)
            stagedFile.delete()
        }
        if (!tmpDest.renameTo(targetFile)) {
            throw IllegalStateException("无法写入内核文件到应用私有目录")
        }
        check(enforceReadOnlyExecutable(targetFile)) {
            "无法设置内核文件的只读执行权限"
        }

        val entry = InstalledCoreEntry(
            version = manifest.coreVersion,
            abiVersion = manifest.abiVersion,
            sha256 = artifact.sha256,
            coreGitSha = manifest.coreGitSha,
            fileName = TARGET_SO_FILENAME,
            sizeBytes = artifact.sizeBytes,
            installedAtMs = nowMs,
        )
        val cur = loadState()
        val nextInstalled = cur.installed.toMutableMap().apply {
            put(entry.version, entry)
        }
        val nextState = cur.copy(
            activeVersion = entry.version,
            bootingVersion = null,
            bootAttempts = 0,
            blacklistedVersions = cur.blacklistedVersions - entry.version,
            installed = nextInstalled,
            lastFallbackReason = null,
        )
        saveStateSync(nextState)
        pruneUnusedVersionDirs(nextState)
        entry
    }

    fun revertToBuiltIn() = synchronized(lock) {
        val nextState = loadState().copy(
            activeVersion = null,
            previousGoodVersion = null,
            bootingVersion = null,
            bootAttempts = 0,
            installed = emptyMap(),
            lastFallbackReason = null,
        )
        saveStateSync(nextState)
        pruneUnusedVersionDirs(nextState)
    }

    fun resolveSoFile(entry: InstalledCoreEntry): File? {
        if (!CoreManifest.isSafeSegment(entry.version) || !CoreManifest.isSafeSingleFileName(entry.fileName)) {
            return null
        }
        val dir = safeChildDir(entry.version)
        val file = File(dir, entry.fileName)
        val rootCanon = runCatching { rootDir.canonicalPath }.getOrNull() ?: return null
        val fileCanon = runCatching { file.canonicalPath }.getOrNull() ?: return null
        if (!fileCanon.startsWith(rootCanon + File.separator)) return null
        return file
    }

    private fun safeChildDir(versionSegment: String): File {
        require(CoreManifest.isSafeSegment(versionSegment)) { "非法路径段: $versionSegment" }
        val dir = File(rootDir, versionSegment)
        val rootCanon = rootDir.canonicalPath
        val dirCanon = dir.canonicalPath
        require(dirCanon.startsWith(rootCanon + File.separator)) { "路径越界: $dirCanon" }
        return dir
    }

    private fun resolveUsableEntry(
        state: CoreRuntimeState,
        version: String,
        builtInVersion: String,
        supportedAbi: Int,
    ): BootCandidate.Dynamic? {
        if (version in state.blacklistedVersions) return null
        if (compareSemVer(version, builtInVersion) <= 0) return null
        val entry = state.installed[version] ?: return null
        if (entry.abiVersion != supportedAbi) return null
        val soFile = resolveSoFile(entry) ?: return null
        if (!soFile.isFile || soFile.length() != entry.sizeBytes) return null
        return BootCandidate.Dynamic(entry, soFile)
    }

    private fun applyBlacklistAndFallback(
        state: CoreRuntimeState,
        badVersion: String,
        reason: String,
        builtInVersion: String,
        supportedAbi: Int,
    ): CoreRuntimeState {
        val nextBlacklist = state.blacklistedVersions + badVersion
        val nextInstalled = state.installed - badVersion
        val prevCandidate = state.previousGoodVersion?.takeIf {
            it != badVersion && it !in nextBlacklist
        }
        val usablePrev = prevCandidate?.let { ver ->
            val tmpState = state.copy(blacklistedVersions = nextBlacklist, installed = nextInstalled)
            resolveUsableEntry(tmpState, ver, builtInVersion, supportedAbi)?.entry?.version
        }
        return state.copy(
            activeVersion = usablePrev,
            previousGoodVersion = usablePrev,
            bootingVersion = null,
            bootAttempts = 0,
            blacklistedVersions = nextBlacklist,
            installed = nextInstalled,
            lastFallbackReason = reason,
        )
    }

    private fun pruneUnusedVersionDirs(state: CoreRuntimeState) {
        val keep = buildSet {
            state.activeVersion?.let(::add)
            state.previousGoodVersion?.let(::add)
        }
        val children = rootDir.listFiles() ?: return
        for (child in children) {
            if (!child.isDirectory) continue
            if (child.name == "tmp") continue
            if (child.name !in keep) {
                deleteRecursivelyWritable(child)
            }
        }
    }

    private fun deleteRecursivelyWritable(target: File) {
        if (target.isDirectory) {
            target.listFiles()?.forEach(::deleteRecursivelyWritable)
        }
        runCatching { target.setWritable(true, true) }
        runCatching { target.delete() }
    }

    companion object {
        const val TARGET_SO_FILENAME = "librabbitcontrol_android.so"
        const val DEFAULT_MAX_BOOT_ATTEMPTS = 2

        /**
         * Android 14-16 (targetSdk 36) requires dynamically loaded native libraries
         * to be non-writable prior to System.load().
         */
        fun enforceReadOnlyExecutable(file: File): Boolean {
            if (!file.isFile) return false
            val readOk = file.setReadable(true, false)
            val execOk = file.setExecutable(true, false)
            val writeCleared = file.setWritable(false, false)
            return readOk && execOk && writeCleared && file.canRead() && !file.canWrite()
        }
    }
}
