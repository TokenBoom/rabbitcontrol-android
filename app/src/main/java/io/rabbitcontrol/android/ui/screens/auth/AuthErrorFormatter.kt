package io.rabbitcontrol.android.ui.screens.auth

import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.i18n.NativeErrorLocalizer

enum class AuthAction {
    Login,
    Register,
    ChangePassword,
    ProbeServer,
    SwitchServer,
    RemoveServer,
    General,
}

/**
 * Formats low-level Rust client / server auth and network errors into localized user-facing messages.
 */
object AuthErrorFormatter {
    private val API_ERROR_REGEX = Regex(
        pattern = """^服务端返回\s+(\d+)\s+\(([^)]+)\)[：:]\s*(.*)$""",
        options = setOf(RegexOption.DOT_MATCHES_ALL),
    )

    private val CJK_REGEX = Regex("""[\u4e00-\u9fff]""")

    fun format(raw: String?, action: AuthAction = AuthAction.General): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return defaultMessage(action)

        API_ERROR_REGEX.matchEntire(text)?.let { match ->
            val status = match.groupValues[1].toIntOrNull() ?: 0
            val code = match.groupValues[2].trim().lowercase()
            val detail = match.groupValues[3].trim()
            return mapApiError(status = status, code = code, detail = detail, action = action)
        }

        val prefixSep = listOf("：", ":").firstNotNullOfOrNull { sep ->
            val idx = text.indexOf(sep)
            if (idx > 0) text.substring(0, idx).trim() to text.substring(idx + 1).trim() else null
        }

        if (prefixSep != null) {
            val (prefix, detail) = prefixSep
            when (prefix) {
                "网络错误" -> return mapNetworkError(detail)
                "TLS 校验失败" -> return mapTlsError(detail)
                "配置错误" -> return mapConfigError(detail)
                "协议错误" -> return AppStrings.get(
                    R.string.err_auth_protocol,
                    "服务器响应异常，请检查服务器版本或稍后重试",
                )
                "需要重新登录" -> return AppStrings.get(
                    R.string.err_auth_session_expired,
                    "登录状态已失效，请重新登录",
                )
            }
        }

        mapTransportKeywords(text)?.let { return it }

        if (CJK_REGEX.containsMatchIn(text)) {
            return NativeErrorLocalizer.localize(text)
        }
        return defaultMessage(action)
    }

    private fun mapApiError(
        status: Int,
        code: String,
        detail: String,
        action: AuthAction,
    ): String {
        val lowerDetail = detail.lowercase()

        when (code) {
            "invalid_credentials", "wrong_password", "invalid_password" -> {
                return if (action == AuthAction.ChangePassword) {
                    AppStrings.get(R.string.err_auth_current_password_wrong, "当前密码不正确，请重新输入")
                } else {
                    AppStrings.get(R.string.err_auth_invalid_credentials, "邮箱或密码错误，请重新输入")
                }
            }
            "user_not_found", "account_not_found" -> return AppStrings.get(
                R.string.err_auth_user_not_found,
                "该邮箱尚未注册，请先注册账号",
            )
            "email_already_exists", "email_exists", "user_already_exists", "duplicate_email", "conflict" -> {
                return AppStrings.get(R.string.err_auth_email_exists, "该邮箱已被注册，请直接登录")
            }
            "registration_disabled" -> return AppStrings.get(
                R.string.err_auth_reg_disabled,
                "当前服务器已关闭自助注册，请联系管理员开通",
            )
            "account_disabled", "user_disabled", "forbidden_disabled" -> {
                return AppStrings.get(R.string.err_auth_account_disabled, "该账号已被停用，请联系管理员处理")
            }
            "account_locked", "too_many_attempts", "rate_limited", "rate_limit_exceeded", "too_many_requests" -> {
                return AppStrings.get(R.string.err_auth_rate_limited, "操作过于频繁，请稍后再试")
            }
            "password_change_required" -> return AppStrings.get(
                R.string.err_auth_password_change_required,
                "首次登录需先修改初始密码",
            )
            "weak_password", "password_too_short", "invalid_password_policy" -> {
                return AppStrings.get(R.string.err_auth_weak_password, "密码强度不足，请输入至少 8 位密码")
            }
            "same_password" -> return AppStrings.get(R.string.err_auth_same_password, "新密码不能与当前密码相同")
            "invalid_email" -> return AppStrings.get(R.string.err_auth_invalid_email, "邮箱格式不正确，请检查后重试")
            "invalid_input", "bad_request", "validation_error" -> {
                return mapInvalidInputDetail(lowerDetail, action)
                    ?: if (action == AuthAction.Login) {
                        AppStrings.get(R.string.err_auth_invalid_input_login, "输入信息有误，请检查邮箱和密码")
                    } else {
                        AppStrings.get(R.string.err_auth_invalid_input_general, "输入信息格式有误，请检查后重试")
                    }
            }
            "unauthorized", "token_expired", "invalid_token", "token_reuse_detected" -> {
                return if (action == AuthAction.Login) {
                    AppStrings.get(R.string.err_auth_invalid_credentials, "邮箱或密码错误，请重新输入")
                } else {
                    AppStrings.get(R.string.err_auth_session_expired, "登录状态已失效，请重新登录")
                }
            }
            "service_unavailable", "offline_or_booting", "internal_error", "database_error" -> {
                return AppStrings.get(R.string.err_auth_server_unavailable, "服务器暂时不可用，请稍后重试")
            }
        }

        return when (status) {
            400 -> mapInvalidInputDetail(lowerDetail, action)
                ?: AppStrings.get(R.string.err_auth_bad_request, "请求参数有误，请检查输入内容")
            401 -> when (action) {
                AuthAction.ChangePassword -> AppStrings.get(
                    R.string.err_auth_current_password_wrong,
                    "当前密码不正确，请重新输入",
                )
                AuthAction.Login -> AppStrings.get(
                    R.string.err_auth_invalid_credentials,
                    "邮箱或密码错误，请重新输入",
                )
                else -> AppStrings.get(R.string.err_auth_session_expired, "登录状态已失效，请重新登录")
            }
            403 -> when {
                "disabled" in lowerDetail || "停用" in detail -> AppStrings.get(
                    R.string.err_auth_account_disabled,
                    "该账号已被停用，请联系管理员处理",
                )
                "registr" in lowerDetail -> AppStrings.get(
                    R.string.err_auth_reg_disabled,
                    "当前服务器已关闭自助注册，请联系管理员开通",
                )
                else -> AppStrings.get(R.string.err_auth_forbidden, "没有操作权限，请联系管理员")
            }
            404 -> if (action == AuthAction.ProbeServer) {
                AppStrings.get(R.string.err_auth_not_found, "未找到控制服务接口，请检查服务器地址和端口")
            } else {
                AppStrings.get(R.string.err_auth_not_implemented, "服务端暂不支持该功能")
            }
            409 -> if (action == AuthAction.Register) {
                AppStrings.get(R.string.err_auth_email_exists, "该邮箱已被注册，请直接登录")
            } else {
                AppStrings.get(R.string.err_auth_conflict, "数据状态冲突，请刷新后重试")
            }
            422 -> mapInvalidInputDetail(lowerDetail, action)
                ?: AppStrings.get(R.string.err_auth_validation, "提交的信息不符合要求，请检查后重试")
            429 -> AppStrings.get(R.string.err_auth_too_many_attempts, "尝试次数过多，请稍后再试")
            in 500..599 -> AppStrings.get(R.string.err_auth_internal, "服务器内部异常，请稍后重试")
            else -> defaultMessage(action)
        }
    }

    private fun mapInvalidInputDetail(lowerDetail: String, action: AuthAction): String? {
        return when {
            "email" in lowerDetail -> AppStrings.get(R.string.err_auth_invalid_email, "邮箱格式不正确，请检查后重试")
            "password" in lowerDetail && (
                "short" in lowerDetail ||
                    "length" in lowerDetail ||
                    "min" in lowerDetail ||
                    "8" in lowerDetail
                ) -> AppStrings.get(R.string.err_auth_password_min_len, "密码长度至少 8 位")
            "password" in lowerDetail && ("same" in lowerDetail || "match" in lowerDetail) -> {
                AppStrings.get(R.string.err_auth_same_password, "新密码不能与当前密码相同")
            }
            "password" in lowerDetail -> {
                if (action == AuthAction.Login) {
                    AppStrings.get(R.string.err_auth_invalid_credentials, "邮箱或密码错误，请重新输入")
                } else {
                    AppStrings.get(R.string.err_auth_password_format, "密码格式不符合要求，请重新输入")
                }
            }
            "display_name" in lowerDetail || "name" in lowerDetail -> AppStrings.get(
                R.string.err_auth_invalid_display_name,
                "显示名称格式不正确",
            )
            else -> null
        }
    }

    private fun mapNetworkError(detail: String): String {
        mapTransportKeywords(detail)?.let { return it }
        return AppStrings.get(R.string.err_auth_network, "网络连接失败，请检查网络或服务器地址")
    }

    private fun mapTlsError(detail: String): String {
        val lower = detail.lowercase()
        return when {
            "指纹" in detail || "fingerprint" in lower || "pin" in lower -> {
                AppStrings.get(R.string.err_auth_tls_pin_mismatch, "服务器证书指纹不匹配，请核对 CA 指纹")
            }
            "eof" in lower || "end of file" in lower || "reset" in lower -> {
                AppStrings.get(R.string.err_auth_tls_interrupted, "安全连接意外中断，请检查服务器端口或网络")
            }
            else -> AppStrings.get(R.string.err_auth_tls_failed, "安全连接校验失败，请检查服务器证书配置")
        }
    }

    private fun mapConfigError(detail: String): String {
        val lower = detail.lowercase()
        return when {
            "host" in lower || "port" in lower || "address" in lower -> AppStrings.get(
                R.string.err_auth_config_addr,
                "服务器地址格式不正确",
            )
            CJK_REGEX.containsMatchIn(detail) -> NativeErrorLocalizer.localize(detail)
            else -> AppStrings.get(R.string.err_auth_config_general, "配置参数有误，请检查后重试")
        }
    }

    private fun mapTransportKeywords(raw: String): String? {
        val lower = raw.lowercase()
        return when {
            "timed out" in lower || "timeout" in lower -> AppStrings.get(
                R.string.err_auth_timeout,
                "连接服务器超时，请检查网络或服务器地址",
            )
            "connection refused" in lower || "refused" in lower -> AppStrings.get(
                R.string.err_auth_refused,
                "无法连接到服务器，请确认地址和端口",
            )
            "failed to lookup" in lower || "dns" in lower || "name or service" in lower -> {
                AppStrings.get(R.string.err_auth_dns, "无法解析服务器域名，请检查网络或地址拼写")
            }
            "unreachable" in lower || "network is down" in lower -> AppStrings.get(
                R.string.err_auth_unreachable,
                "网络不可达，请检查网络连接",
            )
            "unexpected eof" in lower || "end of file" in lower || "connection reset" in lower -> {
                AppStrings.get(R.string.err_auth_disconnected, "与服务器的连接意外断开，请稍后重试")
            }
            "handshake" in lower -> AppStrings.get(
                R.string.err_auth_handshake,
                "安全握手失败，请检查服务器 HTTPS 配置",
            )
            else -> null
        }
    }

    private fun defaultMessage(action: AuthAction): String = when (action) {
        AuthAction.Login -> AppStrings.get(R.string.err_auth_login_failed, "登录失败，请检查邮箱和密码")
        AuthAction.Register -> AppStrings.get(R.string.err_auth_register_failed, "注册失败，请检查填写信息后重试")
        AuthAction.ChangePassword -> AppStrings.get(R.string.err_auth_change_pw_failed, "修改密码失败，请重试")
        AuthAction.ProbeServer -> AppStrings.get(R.string.err_auth_probe_failed, "无法连接到服务器，请检查地址和端口")
        AuthAction.SwitchServer -> AppStrings.get(R.string.err_auth_switch_server_failed, "切换服务器失败，请重试")
        AuthAction.RemoveServer -> AppStrings.get(R.string.err_auth_remove_server_failed, "删除服务器失败，请重试")
        AuthAction.General -> AppStrings.get(R.string.err_auth_general_failed, "操作失败，请稍后重试")
    }
}
