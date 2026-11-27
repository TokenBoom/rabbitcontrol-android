package io.rabbitcontrol.android.core.error

import com.rabbitcontrol.android.error.RcErrorAction
import com.rabbitcontrol.android.error.RcErrorCodes
import com.rabbitcontrol.android.error.RcErrorPresentation
import com.rabbitcontrol.android.error.RcErrorSeverity
import com.rabbitcontrol.android.error.RcUiError
import com.rabbitcontrol.android.error.RcUiErrorWireDto
import io.rabbitcontrol.android.core.nativebridge.NativeRuntimeBootstrap
import io.rabbitcontrol.android.session.Core
import org.json.JSONObject

class RcUiErrorException(
    val uiError: RcUiError,
    cause: Throwable? = null,
) : Exception(uiError.message.ifBlank { uiError.title }, cause)

object RcUiErrorCodec {
    fun parseJson(raw: String?): RcUiError? {
        val trimmed = raw?.trim().orEmpty()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        val code = obj.optString("code", "").trim()
        val title = obj.optString("title", "").trim()
        val message = obj.optString("message", "").trim()
        if (code.isEmpty() || (title.isEmpty() && message.isEmpty())) {
            return null
        }
        val dto = RcUiErrorWireDto(
            code = RcErrorCodes.resolveWireAlias(code) ?: code,
            title = title,
            message = message.ifEmpty { title },
            suggestion = obj.optString("suggestion", "").trim(),
            severity = obj.optString("severity", "").trim().takeIf { it.isNotEmpty() },
            presentation = obj.optString("presentation", "").trim().takeIf { it.isNotEmpty() },
            action = obj.optString("action", "").trim().takeIf { it.isNotEmpty() },
            retryable = obj.optBoolean("retryable", false),
            status = if (obj.has("status") && !obj.isNull("status")) obj.optInt("status") else null,
            detail = obj.optString("detail", "").trim().takeIf { it.isNotEmpty() && it != "null" },
        )
        return dto.toUiError()
    }

    fun fromJsonObject(obj: JSONObject?): RcUiError? {
        if (obj == null) return null
        return parseJson(obj.toString())
    }

    fun fromThrowable(
        err: Throwable?,
        fallbackCode: String = RcErrorCodes.COMMON_UNKNOWN,
        fallbackMessage: String? = null,
    ): RcUiError {
        if (err is RcUiErrorException) return err.uiError
        val rawMsg = err?.message?.trim()
        parseJson(rawMsg)?.let { return it }
        val detail = rawMsg?.takeIf { it.isNotBlank() } ?: err?.javaClass?.simpleName
        return resolvePlatformError(
            code = fallbackCode,
            detail = detail,
            fallbackMessage = fallbackMessage ?: rawMsg,
        )
    }

    fun resolvePlatformError(
        code: String,
        detail: String? = null,
        fallbackTitle: String? = null,
        fallbackMessage: String? = null,
        fallbackSuggestion: String = "",
        presentationOverride: RcErrorPresentation? = null,
        actionOverride: RcErrorAction? = null,
        severityOverride: RcErrorSeverity? = null,
    ): RcUiError {
        val resolved = if (NativeRuntimeBootstrap.isReady) {
            runCatching {
                parseJson(Core.resolveUiError(code, detail))
            }.getOrNull()
        } else {
            null
        }
        if (resolved != null) {
            return resolved.copy(
                presentation = presentationOverride ?: resolved.presentation,
                action = actionOverride ?: resolved.action,
                severity = severityOverride ?: resolved.severity,
            )
        }
        val canonical = RcErrorCodes.resolveWireAlias(code) ?: code
        val defaultSeverity = severityOverride ?: when (canonical) {
            RcErrorCodes.PLATFORM_ANDROID_ACCESSIBILITY_DENIED -> RcErrorSeverity.WARNING
            else -> RcErrorSeverity.ERROR
        }
        val defaultPresentation = presentationOverride ?: when (canonical) {
            RcErrorCodes.VALIDATION_INVALID_INPUT -> RcErrorPresentation.INLINE
            RcErrorCodes.PLATFORM_ANDROID_ACCESSIBILITY_DENIED -> RcErrorPresentation.BANNER
            RcErrorCodes.PLATFORM_ANDROID_MEDIA_PROJECTION_DENIED -> RcErrorPresentation.MODAL
            else -> RcErrorPresentation.TOAST
        }
        val defaultAction = actionOverride ?: when (canonical) {
            RcErrorCodes.AUTH_RELOGIN_REQUIRED,
            RcErrorCodes.AUTH_TOKEN_EXPIRED,
            RcErrorCodes.AUTH_TOKEN_REVOKED,
            RcErrorCodes.AUTH_TOKEN_REUSE_DETECTED,
            RcErrorCodes.AUTH_UNAUTHORIZED -> RcErrorAction.RELOGIN
            RcErrorCodes.AUTH_PASSWORD_CHANGE_REQUIRED -> RcErrorAction.CHANGE_PASSWORD
            RcErrorCodes.PLATFORM_ANDROID_ACCESSIBILITY_DENIED,
            RcErrorCodes.PLATFORM_ANDROID_MEDIA_PROJECTION_DENIED -> RcErrorAction.OPEN_PERMISSIONS
            RcErrorCodes.PROXY_CONFIG_CONFLICT,
            RcErrorCodes.VALIDATION_INVALID_CONFIG -> RcErrorAction.OPEN_SETTINGS
            else -> RcErrorAction.RETRY
        }
        val msg = fallbackMessage?.takeIf { it.isNotBlank() }
            ?: detail?.takeIf { it.isNotBlank() }
            ?: "操作失败，请稍后重试"
        val title = fallbackTitle?.takeIf { it.isNotBlank() } ?: msg
        return RcUiError(
            code = canonical,
            title = title,
            message = msg,
            suggestion = fallbackSuggestion,
            severity = defaultSeverity,
            presentation = defaultPresentation,
            action = defaultAction,
            retryable = defaultAction == RcErrorAction.RETRY,
            status = null,
            detail = detail?.takeIf { it.isNotBlank() },
        )
    }

    fun inlineValidation(
        message: String,
        suggestion: String = "",
        code: String = RcErrorCodes.VALIDATION_INVALID_INPUT,
    ): RcUiError = RcUiError(
        code = code,
        title = message,
        message = message,
        suggestion = suggestion,
        severity = RcErrorSeverity.WARNING,
        presentation = RcErrorPresentation.INLINE,
        action = RcErrorAction.NONE,
        retryable = false,
    )
}
