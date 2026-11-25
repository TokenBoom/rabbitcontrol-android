package io.rabbitcontrol.android.ui.screens.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorFormatterTest {
    @Test
    fun mapsLoginInvalidCredentialsToFriendlyChinese() {
        val msg = AuthErrorFormatter.format(
            "服务端返回 401 (invalid_credentials)：invalid email or password",
            AuthAction.Login,
        )
        assertEquals("邮箱或密码错误，请重新输入", msg)
    }

    @Test
    fun mapsInvalidInputEmailDetail() {
        val msg = AuthErrorFormatter.format(
            "服务端返回 400 (invalid_input)：email must contain a single '@'",
            AuthAction.Register,
        )
        assertEquals("邮箱格式不正确，请检查后重试", msg)
    }

    @Test
    fun mapsEmailAlreadyExists() {
        val msg = AuthErrorFormatter.format(
            "服务端返回 409 (email_already_exists)：email already registered",
            AuthAction.Register,
        )
        assertEquals("该邮箱已被注册，请直接登录", msg)
    }

    @Test
    fun mapsRegistrationDisabled() {
        val msg = AuthErrorFormatter.format(
            "服务端返回 403 (registration_disabled)：registration is disabled",
            AuthAction.Register,
        )
        assertEquals("当前服务器已关闭自助注册，请联系管理员开通", msg)
    }

    @Test
    fun mapsAccountDisabled() {
        val msg = AuthErrorFormatter.format(
            "服务端返回 403 (account_disabled)：account is disabled",
            AuthAction.Login,
        )
        assertEquals("该账号已被停用，请联系管理员处理", msg)
    }

    @Test
    fun mapsRateLimited() {
        val msg = AuthErrorFormatter.format(
            "服务端返回 429 (rate_limited)：too many requests",
            AuthAction.Login,
        )
        assertEquals("操作过于频繁，请稍后再试", msg)
    }

    @Test
    fun mapsServer500Error() {
        val msg = AuthErrorFormatter.format(
            "服务端返回 500 (internal_error)：db panic",
            AuthAction.Login,
        )
        assertEquals("服务器暂时不可用，请稍后重试", msg)
    }

    @Test
    fun mapsChangePasswordInvalidCredentials() {
        val msg = AuthErrorFormatter.format(
            "服务端返回 401 (invalid_credentials)：invalid password",
            AuthAction.ChangePassword,
        )
        assertEquals("当前密码不正确，请重新输入", msg)
    }

    @Test
    fun mapsNetworkTimeout() {
        val msg = AuthErrorFormatter.format(
            "网络错误：connection timed out",
            AuthAction.ProbeServer,
        )
        assertEquals("连接服务器超时，请检查网络或服务器地址", msg)
    }

    @Test
    fun mapsTlsPinMismatch() {
        val msg = AuthErrorFormatter.format(
            "TLS 校验失败：CA 指纹不匹配",
            AuthAction.ProbeServer,
        )
        assertEquals("服务器证书指纹不匹配，请核对 CA 指纹", msg)
    }

    @Test
    fun stripsConfigPrefixWhenChineseDetailPresent() {
        val msg = AuthErrorFormatter.format(
            "配置错误：CA 指纹应为 64 位十六进制 SHA-256",
            AuthAction.ProbeServer,
        )
        assertEquals("CA 指纹应为 64 位十六进制 SHA-256", msg)
    }
}
