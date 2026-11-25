package io.rabbitcontrol.android.core.nativebridge

import org.junit.Assert.assertEquals
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
}
