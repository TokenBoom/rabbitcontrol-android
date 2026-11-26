package io.rabbitcontrol.android.core.nativebridge

import io.rabbitcontrol.android.core.update.CoreAbiArtifact
import io.rabbitcontrol.android.core.update.CoreManifest
import io.rabbitcontrol.android.core.update.CoreManifestVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CoreRuntimeStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createStagedBinary(store: CoreRuntimeStore, name: String, content: ByteArray): Pair<CoreAbiArtifact, File> {
        val staged = File(store.stagingDir(), name)
        staged.writeBytes(content)
        val artifact = CoreAbiArtifact(
            abi = "arm64-v8a",
            fileName = "librabbitcontrol_android-arm64-v8a.so",
            sha256 = CoreManifestVerifier.sha256Hex(content),
            sizeBytes = content.size.toLong(),
        )
        return artifact to staged
    }

    private fun manifest(version: String, abiVersion: Int = 1, artifact: CoreAbiArtifact) = CoreManifest(
        schemaVersion = 1,
        coreVersion = version,
        tagName = "core-v$version",
        coreGitSha = "sha$version",
        abiVersion = abiVersion,
        minAppVersion = "0.2.0",
        publishedAt = "2026-11-26T12:00:00Z",
        releaseNotes = "notes",
        abis = mapOf("arm64-v8a" to artifact),
    )

    @Test
    fun freshBootSelectsBuiltIn_andInstallSelectsReadOnlyDynamic() {
        val store = CoreRuntimeStore(tempFolder.newFolder("core_runtime"))
        assertEquals(BootCandidate.BuiltIn, store.selectCandidateForBoot("0.1.18", 1))

        val (artifact, staged) = createStagedBinary(store, "v19.part", "so-bytes-0.1.19".toByteArray())
        val entry = store.installVerifiedBinary(manifest("0.1.19", 1, artifact), artifact, staged)
        assertEquals("0.1.19", entry.version)

        val candidate = store.selectCandidateForBoot("0.1.18", 1)
        assertTrue(candidate is BootCandidate.Dynamic)
        val dyn = candidate as BootCandidate.Dynamic
        assertEquals("0.1.19", dyn.entry.version)
        assertFalse(dyn.soFile.canWrite())
        assertTrue(dyn.soFile.canRead())
    }

    @Test
    fun apkUpgradeWithNewerBuiltIn_automaticallySwitchesToBuiltIn() {
        val store = CoreRuntimeStore(tempFolder.newFolder("core_runtime"))
        val (artifact, staged) = createStagedBinary(store, "v19.part", "so-bytes-0.1.19".toByteArray())
        store.installVerifiedBinary(manifest("0.1.19", 1, artifact), artifact, staged)

        // Built-in core upgraded to 0.1.19 (or 0.1.20) via APK update
        assertEquals(BootCandidate.BuiltIn, store.selectCandidateForBoot("0.1.19", 1))
        assertNull(store.loadState().activeVersion)
    }

    @Test
    fun crashLoop_blacklistsBadVersionAndRollsBackToPreviousGoodOrBuiltIn() {
        val store = CoreRuntimeStore(tempFolder.newFolder("core_runtime"))

        // First install 0.1.19 and mark boot success so it becomes previousGoodVersion
        val (art19, staged19) = createStagedBinary(store, "v19.part", "so-bytes-0.1.19".toByteArray())
        store.installVerifiedBinary(manifest("0.1.19", 1, art19), art19, staged19)
        store.recordBootAttempt("0.1.19")
        store.markBootSuccess("0.1.19", isDynamic = true)

        // Now install 0.1.20 and simulate 2 crashes before markBootSuccess
        val (art20, staged20) = createStagedBinary(store, "v20.part", "so-bytes-0.1.20".toByteArray())
        store.installVerifiedBinary(manifest("0.1.20", 1, art20), art20, staged20)
        store.recordBootAttempt("0.1.20")
        store.recordBootAttempt("0.1.20")

        val fallback = store.selectCandidateForBoot("0.1.18", 1)
        assertTrue(fallback is BootCandidate.Dynamic)
        assertEquals("0.1.19", (fallback as BootCandidate.Dynamic).entry.version)
        assertTrue("0.1.20" in store.loadState().blacklistedVersions)
        assertTrue(store.loadState().lastFallbackReason?.contains("0.1.20") == true)

        // Revert to built-in clears dynamic state
        store.revertToBuiltIn()
        assertEquals(BootCandidate.BuiltIn, store.selectCandidateForBoot("0.1.18", 1))
        assertNull(store.loadState().activeVersion)
    }
}
