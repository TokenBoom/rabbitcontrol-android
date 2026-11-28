package com.rabbitcontrol.android.core.nativebridge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlignedModelsParsingTest {
    @Test
    fun parsesTerminalLockStateAndUnlockResult() {
        val lockJson = JSONObject()
            .put("required", true)
            .put("unlocked", false)
            .put("username", "alice")
            .put("retry_after_secs", 12)
        val lock = parseTerminalLockState(lockJson)
        assertTrue(lock.required)
        assertFalse(lock.unlocked)
        assertEquals("alice", lock.username)
        assertEquals(12, lock.retryAfterSecs)

        val unlockJson = JSONObject()
            .put("ok", false)
            .put("error", "Authentication failed")
            .put("retry_after_secs", 5)
        val res = parseTerminalUnlockResult(unlockJson)
        assertFalse(res.ok)
        assertEquals("Authentication failed", res.error)
        assertEquals(5, res.retryAfterSecs)
    }

    @Test
    fun parsesHostVersionRemoteUpdateProgressAndCertStatus() {
        val hv = parseRemoteHostVersion(
            JSONObject()
                .put("version", "0.3.0")
                .put("arch", "arm64")
                .put("os", "macos")
                .put("updateCapable", true)
                .put("isOwnerSession", true)
                .put("serviceInstalled", false),
        )
        assertNotNull(hv)
        assertEquals("0.3.0", hv!!.version)
        assertTrue(hv.isOwnerSession)
        assertTrue(hv.updateCapable)

        val prog = parseRemoteUpdateProgress(
            JSONObject()
                .put("stage", "downloading")
                .put("progressPct", 42)
                .put("downloadedBytes", 1024L)
                .put("totalBytes", 2048L)
                .put("targetVersion", "0.3.1")
                .put("message", "Downloading"),
        )
        assertNotNull(prog)
        assertEquals("downloading", prog!!.stage)
        assertEquals(42, prog.progressPct)

        val cert = parseDeviceCertStatus(
            JSONObject()
                .put("valid", true)
                .put("serialHex", "abcd")
                .put("fingerprintSha256", "deadbeef")
                .put("expiresAtUnixSec", 1800000000L)
                .put("needsRenewal", false),
        )
        assertTrue(cert.valid)
        assertEquals("abcd", cert.serialHex)
        assertEquals("deadbeef", cert.fingerprintSha256)
        assertFalse(cert.needsRenewal)
    }
}
