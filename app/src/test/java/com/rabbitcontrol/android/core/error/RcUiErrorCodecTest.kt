package com.rabbitcontrol.android.core.error

import com.rabbitcontrol.android.error.RcErrorAction
import com.rabbitcontrol.android.error.RcErrorCodes
import com.rabbitcontrol.android.error.RcErrorPresentation
import com.rabbitcontrol.android.error.RcErrorSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RcUiErrorCodecTest {

    @Test
    fun parseJson_parsesFullSpecPayloadAndResolvesAlias() {
        val raw = """
            {
              "code": "token_expired",
              "title": "Session Expired",
              "message": "Your login token has expired.",
              "suggestion": "Sign in again to continue.",
              "severity": "warning",
              "presentation": "banner",
              "action": "relogin",
              "retryable": true,
              "status": 401,
              "detail": "jwt exp=1700000000"
            }
        """.trimIndent()

        val ui = RcUiErrorCodec.parseJson(raw)
        assertNotNull(ui)
        assertEquals(RcErrorCodes.AUTH_TOKEN_EXPIRED, ui!!.code)
        assertEquals("Session Expired", ui.title)
        assertEquals("Your login token has expired.", ui.message)
        assertEquals("Sign in again to continue.", ui.suggestion)
        assertEquals(RcErrorSeverity.WARNING, ui.severity)
        assertEquals(RcErrorPresentation.BANNER, ui.presentation)
        assertEquals(RcErrorAction.RELOGIN, ui.action)
        assertTrue(ui.retryable)
        assertEquals(401, ui.status)
        assertEquals("jwt exp=1700000000", ui.detail)
    }

    @Test
    fun parseJson_unknownEnumsFallbackSafelyAndRejectsNonJson() {
        assertNull(RcUiErrorCodec.parseJson("plain error message"))
        assertNull(RcUiErrorCodec.parseJson("""{"code":""}"""))

        val ui = RcUiErrorCodec.parseJson(
            """
            {
              "code": "RC_SESSION_HANDSHAKE_TIMEOUT",
              "title": "Timeout",
              "message": "",
              "severity": "weird_level",
              "presentation": "unknown_surface",
              "action": "mystery_action"
            }
            """.trimIndent(),
        )
        assertNotNull(ui)
        assertEquals("Timeout", ui!!.message)
        assertEquals(RcErrorSeverity.ERROR, ui.severity)
        assertEquals(RcErrorPresentation.TOAST, ui.presentation)
        assertEquals(RcErrorAction.NONE, ui.action)
        assertFalse(ui.retryable)
    }

    @Test
    fun fromThrowable_extractsStructuredJsonAndFallback() {
        val rawJson = """
            {
              "code": "tool.forbidden",
              "title": "Permission Denied",
              "message": "File transfer disabled by remote host.",
              "suggestion": "Ask remote owner to enable file transfer.",
              "severity": "warning",
              "presentation": "toast",
              "action": "none"
            }
        """.trimIndent()

        val fromJsonErr = RcUiErrorCodec.fromThrowable(RuntimeException(rawJson))
        assertEquals(RcErrorCodes.TOOL_FORBIDDEN, fromJsonErr.code)
        assertEquals("File transfer disabled by remote host.", fromJsonErr.message)

        val wrapped = RcUiErrorException(fromJsonErr)
        assertEquals(fromJsonErr, RcUiErrorCodec.fromThrowable(wrapped))

        val plainFallback = RcUiErrorCodec.fromThrowable(
            IllegalStateException("socket closed"),
            fallbackCode = RcErrorCodes.SIGNAL_SERVER_SHUTDOWN,
            fallbackMessage = "Disconnected",
        )
        assertEquals(RcErrorCodes.SIGNAL_SERVER_SHUTDOWN, plainFallback.code)
        assertEquals("Disconnected", plainFallback.message)
        assertEquals("socket closed", plainFallback.detail)
    }

    @Test
    fun inlineValidation_buildsInlineValidationError() {
        val ui = RcUiErrorCodec.inlineValidation("Port out of range")
        assertEquals(RcErrorCodes.VALIDATION_INVALID_INPUT, ui.code)
        assertEquals(RcErrorSeverity.WARNING, ui.severity)
        assertEquals(RcErrorPresentation.INLINE, ui.presentation)
        assertEquals("Port out of range", ui.message)
    }
}
