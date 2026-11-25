package io.rabbitcontrol.android.core.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigModelTest {
    @Test
    fun normalizesServerIdAndDisplayNameFallback() {
        val a = ServerConfigModel(
            address = " 192.168.8.9:21118 ",
            fingerprint = "AABBCC",
            caPem = "pem",
            name = "",
        )
        val b = ServerConfigModel(
            address = "192.168.8.9:21118",
            fingerprint = "aabbcc",
            caPem = "pem",
            name = "公司主机",
        )
        assertEquals(a.id, b.id)
        assertEquals(" 192.168.8.9:21118 ", a.displayName)
        assertEquals("公司主机", b.displayName)
    }

    @Test
    fun formatsCustomPortsSummaryOnlyWhenNonDefault() {
        val defaultServer = ServerConfigModel(
            address = "example.com",
            fingerprint = "fp",
            caPem = "pem",
        )
        assertFalse(defaultServer.hasCustomPorts)
        assertNull(defaultServer.customPortsSummary())

        val customServer = ServerConfigModel(
            address = "example.com",
            fingerprint = "fp",
            caPem = "pem",
            apiPort = 31118,
            signalPort = 31116,
            relayPort = ServerConfigModel.DEFAULT_RELAY_PORT,
        )
        assertTrue(customServer.hasCustomPorts)
        assertEquals("API 31118 · 信令 31116", customServer.customPortsSummary())
    }
}
