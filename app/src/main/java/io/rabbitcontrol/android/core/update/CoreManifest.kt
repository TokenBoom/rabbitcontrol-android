package io.rabbitcontrol.android.core.update

import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.i18n.AppStrings
import org.json.JSONObject

data class CoreAbiArtifact(
    val abi: String,
    val fileName: String,
    val sha256: String,
    val sizeBytes: Long,
)

data class CoreManifest(
    val schemaVersion: Int,
    val coreVersion: String,
    val tagName: String,
    val coreGitSha: String,
    val abiVersion: Int,
    val minAppVersion: String,
    val publishedAt: String,
    val releaseNotes: String,
    val abis: Map<String, CoreAbiArtifact>,
) {
    companion object {
        private val SHA256_REGEX = Regex("^[0-9a-f]{64}$")

        fun parse(rawJson: String): CoreManifest {
            val obj = JSONObject(rawJson)
            val schemaVersion = obj.optInt("schemaVersion", 1)
            require(schemaVersion == 1) {
                AppStrings.get(R.string.core_err_dl_or_verify, "不支持的清单格式版本: $schemaVersion")
            }

            val coreVersion = obj.optString("coreVersion", "").trim()
                .removePrefix("v")
                .removePrefix("V")
            require(coreVersion.isNotBlank() && isSafeSegment(coreVersion)) {
                AppStrings.get(R.string.core_err_dl_or_verify, "无效的内核版本号")
            }

            val tagName = obj.optString("tagName", "core-v$coreVersion").trim()
                .ifBlank { "core-v$coreVersion" }
            val coreGitSha = obj.optString("coreGitSha", "unknown").trim().ifBlank { "unknown" }
            val abiVersion = obj.optInt("abiVersion", 0)
            require(abiVersion > 0) {
                AppStrings.get(R.string.core_err_dl_or_verify, "无效的 ABI 版本: $abiVersion")
            }

            val minAppVersion = obj.optString("minAppVersion", "0.2.0").trim().ifBlank { "0.2.0" }
            val publishedAt = obj.optString("publishedAt", "").trim()
            val releaseNotes = cleanReleaseNotes(obj.optString("releaseNotes", ""))

            val abisObj = obj.optJSONObject("abis")
                ?: throw IllegalArgumentException(
                    AppStrings.get(R.string.core_err_dl_or_verify, "清单缺少 abis 字段"),
                )
            val abis = LinkedHashMap<String, CoreAbiArtifact>()
            val keys = abisObj.keys()
            while (keys.hasNext()) {
                val abi = keys.next().trim()
                val item = abisObj.optJSONObject(abi) ?: continue
                val fileName = item.optString("fileName", "").trim()
                val sha256 = item.optString("sha256", "").trim().lowercase()
                val sizeBytes = item.optLong("sizeBytes", 0L)
                require(abi.isNotBlank()) { "ABI is blank" }
                require(isSafeSingleFileName(fileName)) { "Invalid fileName: $fileName" }
                require(SHA256_REGEX.matches(sha256)) { "Invalid SHA-256: $sha256" }
                require(sizeBytes > 0L) { "Invalid sizeBytes: $sizeBytes" }
                abis[abi] = CoreAbiArtifact(
                    abi = abi,
                    fileName = fileName,
                    sha256 = sha256,
                    sizeBytes = sizeBytes,
                )
            }
            require(abis.isNotEmpty()) {
                AppStrings.get(R.string.core_err_dl_or_verify, "清单未包含任何可用架构产物")
            }

            return CoreManifest(
                schemaVersion = schemaVersion,
                coreVersion = coreVersion,
                tagName = tagName,
                coreGitSha = coreGitSha,
                abiVersion = abiVersion,
                minAppVersion = minAppVersion,
                publishedAt = publishedAt,
                releaseNotes = releaseNotes,
                abis = abis,
            )
        }

        internal fun isSafeSingleFileName(name: String): Boolean {
            if (name.isBlank() || name == "." || name == "..") return false
            if (name.contains('/') || name.contains('\\') || name.contains("..")) return false
            return name.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
        }

        internal fun isSafeSegment(value: String): Boolean {
            if (value.isBlank() || value == "." || value == "..") return false
            if (value.contains('/') || value.contains('\\') || value.contains("..")) return false
            return value.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
        }
    }
}

data class CoreReleaseManifest(
    val schemaVersion: String,
    val coreVersion: String,
    val tag: String,
    val gitCommit: String,
    val abiVersion: Int,
    val specVersion: String,
    val minClientAndroidVersion: String,
    val protocolSession: Int,
    val protocolPunch: Int,
    val protocolBeacon: Int,
) {
    companion object {
        fun parse(rawJson: String): CoreReleaseManifest {
            val obj = JSONObject(rawJson)
            val schemaVersion = obj.optString("schemaVersion", "1.0.0").trim().ifBlank { "1.0.0" }
            val coreVersion = obj.optString("coreVersion", "").trim()
                .removePrefix("v")
                .removePrefix("V")
            require(coreVersion.isNotBlank()) { "coreVersion is missing" }
            val tag = obj.optString("tag", "v$coreVersion").trim().ifBlank { "v$coreVersion" }
            val gitCommit = obj.optString("gitCommit", "unknown").trim().ifBlank { "unknown" }
            val abiVersion = obj.optInt("abiVersion", 0)
            require(abiVersion > 0) { "Invalid abiVersion: $abiVersion" }
            val specVersion = obj.optString("specVersion", "1.0.0").trim().ifBlank { "1.0.0" }
            val minClients = obj.optJSONObject("minClientVersions")
            val minClientAndroid = minClients?.optString("android", "0.2.2")?.trim()
                ?.takeIf { it.isNotBlank() } ?: "0.2.2"
            val protos = obj.optJSONObject("protocolVersions")
            return CoreReleaseManifest(
                schemaVersion = schemaVersion,
                coreVersion = coreVersion,
                tag = tag,
                gitCommit = gitCommit,
                abiVersion = abiVersion,
                specVersion = specVersion,
                minClientAndroidVersion = minClientAndroid,
                protocolSession = protos?.optInt("session", 1) ?: 1,
                protocolPunch = protos?.optInt("punch", 1) ?: 1,
                protocolBeacon = protos?.optInt("beacon", 1) ?: 1,
            )
        }
    }
}
