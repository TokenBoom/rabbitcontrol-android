package com.rabbitcontrol.android.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class CoreUpdateManagerTest {

    private fun entry(
        version: String,
        abiVersion: Int,
        abi: String = "arm64-v8a",
    ): VerifiedCoreReleaseEntry {
        val artifact = CoreAbiArtifact(
            abi = abi,
            fileName = "librabbitcontrol_android-$abi.so",
            sha256 = "b".repeat(64),
            sizeBytes = 4_000_000L,
        )
        val manifest = CoreManifest(
            schemaVersion = 1,
            coreVersion = version,
            tagName = "core-v$version",
            coreGitSha = "sha$version",
            abiVersion = abiVersion,
            minAppVersion = if (abiVersion > 1) "0.3.0" else "0.2.0",
            publishedAt = "2026-11-26T12:00:00Z",
            releaseNotes = "Update $version",
            abis = mapOf(abi to artifact),
        )
        return VerifiedCoreReleaseEntry(
            manifest = manifest,
            htmlUrl = "https://github.com/TokenBoom/rabbitcontrol-android/releases/tag/core-v$version",
        )
    }

    @Test
    fun selectCoreUpdateFromManifests_picksBestCompatibleAndSurfacesIncompatibleNotice() {
        val releases = listOf(
            entry("0.2.0", abiVersion = 2),
            entry("0.1.20", abiVersion = 1),
            entry("0.1.19", abiVersion = 1),
        )

        val selection = selectCoreUpdateFromManifests(
            releases = releases,
            effectiveCurrentVersion = "0.1.18",
            supportedAbiVersion = 1,
            supportedAbis = listOf("arm64-v8a"),
            blacklistedVersions = emptySet(),
        )

        assertEquals("0.1.20", selection.bestCompatible?.manifest?.coreVersion)
        assertNotNull(selection.incompatibleNotice)
        assertEquals("0.2.0", selection.incompatibleNotice?.coreVersion)
        assertEquals(2, selection.incompatibleNotice?.requiredAbiVersion)
        assertEquals("0.3.0", selection.incompatibleNotice?.minAppVersion)
    }

    @Test
    fun selectCoreUpdateFromManifests_skipsBlacklistedAndAlreadyUpToDate() {
        val releases = listOf(
            entry("0.1.20", abiVersion = 1),
            entry("0.1.19", abiVersion = 1),
        )

        val skippedBlacklist = selectCoreUpdateFromManifests(
            releases = releases,
            effectiveCurrentVersion = "0.1.18",
            supportedAbiVersion = 1,
            supportedAbis = listOf("arm64-v8a"),
            blacklistedVersions = setOf("0.1.20"),
        )
        assertEquals("0.1.19", skippedBlacklist.bestCompatible?.manifest?.coreVersion)

        val upToDate = selectCoreUpdateFromManifests(
            releases = releases,
            effectiveCurrentVersion = "0.1.20",
            supportedAbiVersion = 1,
            supportedAbis = listOf("arm64-v8a"),
            blacklistedVersions = emptySet(),
        )
        assertNull(upToDate.bestCompatible)
    }

    @Test
    fun extractCoreReleaseTagsFromHtml_filtersOnlyCoreTagsInOrder() {
        val html = """
            <a href="/TokenBoom/rabbitcontrol-android/releases/tag/v0.2.1">v0.2.1</a>
            <a href="/TokenBoom/rabbitcontrol-android/releases/tag/core-v0.1.20">core-v0.1.20</a>
            <a href="/TokenBoom/rabbitcontrol-android/releases/tag/core-v0.1.19">core-v0.1.19</a>
            <a href="/TokenBoom/rabbitcontrol-android/releases/tag/core-v0.1.20">duplicate</a>
        """.trimIndent()
        assertEquals(listOf("core-v0.1.20", "core-v0.1.19"), extractCoreReleaseTagsFromHtml(html))
    }

    @Test
    fun mirrorPrefix_normalizesAndAppliesSafely() {
        assertEquals("", normalizeMirrorPrefix("javascript:alert(1)"))
        assertEquals("https://ghfast.top/", normalizeMirrorPrefix("  https://ghfast.top "))
        assertEquals(
            "https://ghfast.top/https://github.com/TokenBoom/rabbitcontrol-android/releases/download/core-v0.1.19/a.so",
            applyMirrorPrefix(
                "https://github.com/TokenBoom/rabbitcontrol-android/releases/download/core-v0.1.19/a.so",
                "https://ghfast.top",
            ),
        )
    }

    @Test
    fun selectCoreUpdateFromManifests_picksAbiV2CandidateOverCurrent() {
        val releases = listOf(
            entry("0.3.0", abiVersion = 3),
            entry("0.2.3", abiVersion = 2),
            entry("0.2.2", abiVersion = 2),
        )

        val selection = selectCoreUpdateFromManifests(
            releases = releases,
            effectiveCurrentVersion = "0.2.2",
            supportedAbiVersion = 2,
            supportedAbis = listOf("arm64-v8a"),
            blacklistedVersions = emptySet(),
        )

        assertEquals("0.2.3", selection.bestCompatible?.manifest?.coreVersion)
        assertEquals(3, selection.incompatibleNotice?.requiredAbiVersion)
    }

    @Test
    fun coreReleaseManifest_parsesCoreV022ReleaseManifestSchema() {
        val json = """
            {
              "schemaVersion": "1.0.0",
              "coreVersion": "0.2.2",
              "tag": "v0.2.2",
              "gitCommit": "bb41388abc1234",
              "abiVersion": 2,
              "specVersion": "1.0.0",
              "minClientVersions": {
                "android": "0.2.2",
                "apple": "0.2.2"
              },
              "protocolVersions": {
                "session": 1,
                "punch": 1,
                "beacon": 1
              }
            }
        """.trimIndent()

        val parsed = CoreReleaseManifest.parse(json)
        assertEquals("0.2.2", parsed.coreVersion)
        assertEquals("v0.2.2", parsed.tag)
        assertEquals("bb41388abc1234", parsed.gitCommit)
        assertEquals(2, parsed.abiVersion)
        assertEquals("1.0.0", parsed.specVersion)
        assertEquals("0.2.2", parsed.minClientAndroidVersion)
        assertEquals(1, parsed.protocolSession)
        assertEquals(1, parsed.protocolPunch)
        assertEquals(1, parsed.protocolBeacon)
    }

    @Test
    fun coreReleaseManifest_parsesAndroidArtifactsAndVerifiesUnifiedEntry() {
        val kpGen = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }
        val kp = kpGen.generateKeyPair()
        val pubB64 = Base64.getEncoder().encodeToString(kp.public.encoded)

        val soSha = "a".repeat(64)
        val soSize = 5574000L
        val coreManifestBytes = """
            {
              "schemaVersion": 1,
              "coreVersion": "0.2.3",
              "tagName": "core-v0.2.3",
              "coreGitSha": "a4be694",
              "abiVersion": 2,
              "minAppVersion": "0.2.2",
              "publishedAt": "2026-11-27T04:42:07Z",
              "releaseNotes": "",
              "abis": {
                "arm64-v8a": {
                  "fileName": "librabbitcontrol_android-arm64-v8a.so",
                  "sha256": "$soSha",
                  "sizeBytes": $soSize
                }
              }
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val sigDer = Signature.getInstance("SHA256withECDSA").run {
            initSign(kp.private)
            update(coreManifestBytes)
            sign()
        }
        val sigBytes = (Base64.getEncoder().encodeToString(sigDer) + "\n").toByteArray(Charsets.UTF_8)

        val coreManifestSha = CoreManifestVerifier.sha256Hex(coreManifestBytes)
        val sigSha = CoreManifestVerifier.sha256Hex(sigBytes)

        val unifiedJson = """
            {
              "schemaVersion": "1.0.0",
              "coreVersion": "0.2.3",
              "tag": "v0.2.3",
              "gitCommit": "a4be6947954cb3f32110fe7d95db05ef42a3fed7",
              "abiVersion": 2,
              "specVersion": "1.0.0",
              "minClientVersions": { "android": "0.2.3" },
              "protocolVersions": { "session": 1, "punch": 1, "beacon": 1 },
              "platforms": {
                "android": {
                  "artifacts": [
                    { "name": "core-manifest.json", "format": "json", "sha256": "$coreManifestSha", "size": ${coreManifestBytes.size} },
                    { "name": "core-manifest.json.sig", "format": "ecdsa-sha256-b64", "sha256": "$sigSha", "size": ${sigBytes.size} },
                    { "name": "librabbitcontrol_android-arm64-v8a.so", "arch": "arm64-v8a", "format": "so", "sha256": "$soSha", "size": $soSize }
                  ]
                }
              }
            }
        """.trimIndent()

        val unified = CoreReleaseManifest.parse(unifiedJson)
        assertNotNull(unified.androidManifestArtifact)
        assertNotNull(unified.androidSigArtifact)
        assertNotNull(unified.androidSoArtifacts["arm64-v8a"])

        val verified = buildVerifiedEntryFromUnifiedManifest(
            unified = unified,
            manifestBytes = coreManifestBytes,
            sigBytes = sigBytes,
            publicKeyBase64 = pubB64,
        )
        assertNotNull(verified)

        val selection = selectCoreUpdateFromManifests(
            releases = listOf(verified!!),
            effectiveCurrentVersion = "0.2.2",
            supportedAbiVersion = 2,
            supportedAbis = listOf("arm64-v8a"),
            blacklistedVersions = emptySet(),
        )
        val candidate = selection.bestCompatible
        assertNotNull(candidate)
        assertEquals("0.2.3", candidate!!.manifest.coreVersion)
        assertEquals(
            "https://github.com/TokenBoom/rabbitcontrol-core/releases/download/v0.2.3/librabbitcontrol_android-arm64-v8a.so",
            candidate.soDownloadUrl,
        )
        assertEquals(
            "https://github.com/TokenBoom/rabbitcontrol-core/releases/tag/v0.2.3",
            candidate.htmlUrl,
        )

        // Tampered manifest bytes or wrong signature must fail closed
        assertNull(
            buildVerifiedEntryFromUnifiedManifest(
                unified = unified,
                manifestBytes = coreManifestBytes + byteArrayOf(0x20),
                sigBytes = sigBytes,
                publicKeyBase64 = pubB64,
            ),
        )

        // Mismatched .so sha256 between unified release-manifest.json and core-manifest.json must fail closed
        val mismatchedUnified = unified.copy(
            androidArtifacts = unified.androidArtifacts.map { art ->
                if (art.format == "so") art.copy(sha256 = "f".repeat(64)) else art
            },
        )
        assertNull(
            buildVerifiedEntryFromUnifiedManifest(
                unified = mismatchedUnified,
                manifestBytes = coreManifestBytes,
                sigBytes = sigBytes,
                publicKeyBase64 = pubB64,
            ),
        )

        // Mismatched version must fail closed
        assertNull(
            buildVerifiedEntryFromUnifiedManifest(
                unified = unified.copy(coreVersion = "0.2.4"),
                manifestBytes = coreManifestBytes,
                sigBytes = sigBytes,
                publicKeyBase64 = pubB64,
            ),
        )
    }
}
