package com.rabbitcontrol.android.core.i18n

import androidx.annotation.StringRes
import com.rabbitcontrol.android.R

object AppStrings {
    fun get(@StringRes resId: Int, fallback: String): String {
        val ctx = AppLocaleManager.localizedContext() ?: return fallback
        return runCatching { ctx.getString(resId) }.getOrDefault(fallback)
    }

    fun get(@StringRes resId: Int, fallback: String, vararg args: Any): String {
        val ctx = AppLocaleManager.localizedContext() ?: return fallback
        return runCatching { ctx.getString(resId, *args) }.getOrDefault(fallback)
    }

    fun getOrNull(@StringRes resId: Int, vararg args: Any): String? {
        val ctx = AppLocaleManager.localizedContext() ?: return null
        return runCatching {
            if (args.isEmpty()) ctx.getString(resId) else ctx.getString(resId, *args)
        }.getOrNull()
    }
}

object NativeErrorLocalizer {
    fun localize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        if (AppLocaleManager.localizedContext() == null) return trimmed
        if (AppLocaleManager.activeLanguageMode() == AppLanguageMode.SimplifiedChinese) {
            return trimmed
        }

        val mapped = when {
            trimmed.contains("运行时尚未初始化") -> R.string.err_native_runtime_not_init
            trimmed.contains("无效的会话请求") -> R.string.err_native_invalid_session_req
            trimmed.contains("解析会话请求失败") -> R.string.err_native_parse_session_req
            trimmed.contains("无效的工具请求") -> R.string.err_native_invalid_tool_req
            trimmed.contains("解析工具请求失败") -> R.string.err_native_parse_tool_req
            trimmed.contains("无效被控端指令") -> R.string.err_native_invalid_host_cmd
            trimmed.contains("解析被控端指令失败") -> R.string.err_native_parse_host_cmd
            trimmed.contains("未连接服务器") -> R.string.err_native_not_connected
            trimmed.contains("会话尚未建立") || trimmed.contains("无活跃会话") -> R.string.err_native_no_active_session
            trimmed.contains("CA 指纹") && trimmed.contains("不匹配") -> R.string.err_auth_tls_pin_mismatch
            trimmed.contains("连接超时") || trimmed.contains("timed out") -> R.string.err_auth_timeout
            trimmed.contains("用户名或密码") || trimmed.contains("邮箱或密码") -> R.string.err_auth_invalid_credentials
            trimmed.contains("缺少字段") -> R.string.err_native_missing_field
            else -> null
        }

        return if (mapped != null) {
            AppStrings.getOrNull(mapped) ?: trimmed
        } else {
            trimmed
        }
    }
}
