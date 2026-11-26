package io.rabbitcontrol.android.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

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
}
