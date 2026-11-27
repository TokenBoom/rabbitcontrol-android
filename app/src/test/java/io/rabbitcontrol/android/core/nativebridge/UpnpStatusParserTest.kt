package io.rabbitcontrol.android.core.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpnpStatusParserTest {

    @Test
    fun parseUpnpStatusPayload_parsesAllStatesAndProtocols() {
        val disabled = parseUpnpStatusPayload("""{"enabled":false,"status":{"state":"disabled"}}""")
        assertFalse(disabled.enabled)
        assertEquals(UpnpState.Disabled, disabled.state)

        val discovering = parseUpnpStatusPayload("""{"enabled":true,"status":{"state":"discovering"}}""")
        assertTrue(discovering.enabled)
        assertEquals(UpnpState.Discovering, discovering.state)

        val mapped = parseUpnpStatusPayload(
            """{"enabled":true,"status":{"state":"mapped","external_addr":"203.0.113.10:49152","protocol":"upnp_igd_v2"}}""",
        )
        assertTrue(mapped.enabled)
        assertEquals(UpnpState.Mapped, mapped.state)
        assertEquals("203.0.113.10:49152", mapped.externalAddr)
        assertEquals("upnp_igd_v2", mapped.protocol)
        assertEquals("UPnP IGD v2", formatUpnpProtocol(mapped.protocol))

        val unavailable = parseUpnpStatusPayload(
            """{"enabled":true,"status":{"state":"unavailable","reason":"UPnP IGD 失败；NAT-PMP 超时"}}""",
        )
        assertTrue(unavailable.enabled)
        assertEquals(UpnpState.Unavailable, unavailable.state)
        assertEquals("UPnP IGD 失败；NAT-PMP 超时", unavailable.reason)
    }

    @Test
    fun parseUpnpStatusPayload_handlesInvalidInputAndLegacyFormats() {
        assertEquals(UpnpStatusModel(), parseUpnpStatusPayload(null))
        assertEquals(UpnpStatusModel(), parseUpnpStatusPayload("not json"))
        assertEquals("UPnP IGD", formatUpnpProtocol("upnp_igd_v1"))
        assertEquals("NAT-PMP", formatUpnpProtocol("nat_pmp"))
    }
}
