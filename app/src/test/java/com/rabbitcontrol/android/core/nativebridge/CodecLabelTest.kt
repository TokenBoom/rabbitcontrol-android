package com.rabbitcontrol.android.core.nativebridge

import com.rabbitcontrol.android.core.data.ConnectionModePref
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecLabelTest {

    @Test
    fun normalizeVideoCodecId_handlesProtocolAndComponentNames() {
        assertEquals("h264", normalizeVideoCodecId("h264"))
        assertEquals("h264", normalizeVideoCodecId("c2.qti.avc.decoder.low_latency"))
        assertEquals("hevc", normalizeVideoCodecId("HEVC"))
        assertEquals("hevc", normalizeVideoCodecId("c2.qti.hevc.decoder"))
        assertEquals("av1", normalizeVideoCodecId("av1"))
        assertEquals("av1", normalizeVideoCodecId("c2.qti.av1.decoder.low_latency"))
        assertEquals("av1", normalizeVideoCodecId("video/av01"))
        assertNull(normalizeVideoCodecId(null))
        assertNull(normalizeVideoCodecId(""))
    }

    @Test
    fun formatVideoCodecLabel_formatsAndFallsBackToComponentName() {
        assertEquals("H.264", formatVideoCodecLabel("h264"))
        assertEquals("HEVC", formatVideoCodecLabel("hevc"))
        assertEquals("AV1", formatVideoCodecLabel("av1"))
        assertEquals("HEVC", formatVideoCodecLabel(null, "c2.android.hevc.decoder"))
        assertEquals("AV1", formatVideoCodecLabel("", "c2.android.av1-dav1d.decoder"))
        assertNull(formatVideoCodecLabel("", ""))
    }

    @Test
    fun connectionModePref_fromWireParsesVariantsAndDefaultsToAuto() {
        assertEquals(ConnectionModePref.Auto, ConnectionModePref.fromWire("auto"))
        assertEquals(ConnectionModePref.P2pOnly, ConnectionModePref.fromWire("p2p_only"))
        assertEquals(ConnectionModePref.P2pOnly, ConnectionModePref.fromWire(" P2P "))
        assertEquals(ConnectionModePref.TcpRelay, ConnectionModePref.fromWire("tcp_relay"))
        assertEquals(ConnectionModePref.TcpRelay, ConnectionModePref.fromWire("relay"))
        assertEquals(ConnectionModePref.Auto, ConnectionModePref.fromWire(null))
        assertEquals(ConnectionModePref.Auto, ConnectionModePref.fromWire("unknown"))
    }

    @Test
    fun parseHighRefreshAdvisory_handlesOptimalSuggestAndRecommendedResolution() {
        assertNull(parseHighRefreshAdvisory(null))

        val optimal = parseHighRefreshAdvisory(
            JSONObject("""{"level":"optimal","reason":"optimal","can_enable_hevc":false}"""),
        )!!
        assertEquals("optimal", optimal.level)
        assertFalse(optimal.canEnableHevc)
        assertNull(optimal.recommendedWidth)
        assertNull(optimal.recommendedHeight)

        val noHevc = parseHighRefreshAdvisory(
            JSONObject(
                """
                {
                  "level": "above_sweet_spot",
                  "reason": "h264_no_hevc",
                  "can_enable_hevc": false,
                  "recommended_width": 720,
                  "recommended_height": 1602
                }
                """.trimIndent(),
            ),
        )!!
        assertEquals("above_sweet_spot", noHevc.level)
        assertEquals("h264_no_hevc", noHevc.reason)
        assertFalse(noHevc.canEnableHevc)
        assertEquals(720, noHevc.recommendedWidth)
        assertEquals(1602, noHevc.recommendedHeight)

        val suggestHevc = parseHighRefreshAdvisory(
            JSONObject("""{"level":"suggest_hevc","reason":"h264_suggest_hevc","can_enable_hevc":true,"recommended_width":null,"recommended_height":null}"""),
        )!!
        assertTrue(suggestHevc.canEnableHevc)
        assertNull(suggestHevc.recommendedWidth)
    }
}
