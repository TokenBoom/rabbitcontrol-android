package io.rabbitcontrol.android.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.nativebridge.ServerConfigModel
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthScreen(
    initialStep: AuthStep,
    onlyServer: Boolean,
    canNavigateBack: Boolean,
    onBack: () -> Unit,
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val palette = LocalRabbitPalette.current
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val activeServer by viewModel.activeServer.collectAsStateWithLifecycle()
    val savedEmails by viewModel.savedAccountEmails.collectAsStateWithLifecycle()

    var pendingDeleteServer by remember { mutableStateOf<ServerConfigModel?>(null) }

    LaunchedEffect(initialStep) {
        viewModel.init(initialStep)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .systemBarsPadding()
            .imePadding(),
    ) {
        if (canNavigateBack || state.step == AuthStep.Trust) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (state.step == AuthStep.Trust) viewModel.backFromTrust() else onBack()
                }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = palette.text)
                }
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.rabbitcontrol),
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("RabbitControl", color = palette.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))

            val (title, subtitle) = when (state.step) {
                AuthStep.Server -> "服务器管理" to "可添加多台服务器并随时切换，各服务器账号独立保存。"
                AuthStep.Trust -> "确认服务器指纹" to (state.candidateServer?.address ?: "")
                AuthStep.Login -> "账号登录" to "登录 RabbitControl 账号以查看和控制设备。"
                AuthStep.Register -> "注册新账号" to "注册成功后将自动登录并绑定本机。"
                AuthStep.Password -> "设置新密码" to "首次登录需要修改初始密码，完成后才能查看设备。"
            }
            Text(title, color = palette.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(subtitle, color = palette.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (state.step == AuthStep.Server && servers.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.line, RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "已保存服务器 (${servers.size})",
                        color = palette.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    servers.forEach { srv ->
                        val isActive = srv.id == activeServer?.id
                        val email = savedEmails[srv.id]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isActive) palette.primarySoft.copy(alpha = 0.45f) else palette.field)
                                .border(
                                    1.dp,
                                    if (isActive) palette.primary else palette.line,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable(enabled = !state.busy) {
                                    viewModel.selectSavedServer(srv, onAuthenticated)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Dns,
                                null,
                                tint = if (isActive) palette.primary else palette.muted,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        srv.displayName,
                                        color = palette.text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (isActive) {
                                        Text(
                                            "当前",
                                            color = palette.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(palette.primarySoft)
                                                .padding(horizontal = 6.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                                val meta = buildString {
                                    if (srv.name.isNotBlank()) {
                                        append(srv.address)
                                        append(" · ")
                                    }
                                    append(email ?: "点击登录此服务器")
                                }
                                Text(meta, color = palette.muted, fontSize = 12.sp)
                            }
                            IconButton(
                                onClick = { pendingDeleteServer = srv },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "删除服务器",
                                    tint = palette.muted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.line, RoundedCornerShape(18.dp))
                    .padding(20.dp),
            ) {
                when (state.step) {
                    AuthStep.Server -> {
                        if (servers.isNotEmpty()) {
                            Text(
                                "添加新服务器",
                                color = palette.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        AuthFieldLabel("服务器名称（可选）")
                        Spacer(modifier = Modifier.height(6.dp))
                        AuthTextField(
                            value = state.serverName,
                            onValueChange = viewModel::updateServerName,
                            placeholder = "例如 公司内网 / 家用电脑",
                            leading = { Icon(Icons.Rounded.Label, null, tint = palette.muted) },
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        AuthFieldLabel("服务器地址")
                        Spacer(modifier = Modifier.height(6.dp))
                        AuthTextField(
                            value = state.serverAddress,
                            onValueChange = viewModel::updateServerAddress,
                            placeholder = "主机名或 IP，默认端口 21118",
                            leading = { Icon(Icons.Rounded.Dns, null, tint = palette.muted) },
                            keyboardType = KeyboardType.Uri,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        AuthFieldLabel("CA 指纹（可选）")
                        Spacer(modifier = Modifier.height(6.dp))
                        AuthTextField(
                            value = state.caPin,
                            onValueChange = viewModel::updateCaPin,
                            placeholder = "64 位 SHA-256 指纹",
                            leading = { Icon(Icons.Rounded.Key, null, tint = palette.muted) },
                            singleLine = false,
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        PrimaryActionButton(
                            label = if (state.busy) "正在校验服务器…" else if (servers.isEmpty()) "校验服务器" else "校验并添加服务器",
                            busy = state.busy,
                            onClick = viewModel::probeServer,
                        )
                    }

                    AuthStep.Trust -> {
                        val cand = state.candidateServer
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(palette.successSoft)
                                .align(Alignment.CenterHorizontally),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Check, null, tint = palette.success, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            state.probeReport,
                            color = palette.success,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "请核对下面的 CA SHA-256 指纹。确认后，本机只会信任该服务器证书体系。",
                            color = palette.muted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.field)
                                .border(1.dp, palette.line, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                        ) {
                            Text(
                                text = cand?.fingerprint.orEmpty().chunked(4).joinToString(" "),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = palette.text,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        PrimaryActionButton(
                            label = if (state.busy) "正在保存…" else "信任并继续",
                            busy = state.busy,
                            onClick = { viewModel.trustAndContinue(onlyServer, onAuthenticated) },
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = viewModel::backFromTrust,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("重新输入")
                        }
                    }

                    AuthStep.Login, AuthStep.Register -> {
                        val srvLabel = activeServer?.displayName ?: state.savedServerAddress
                        if (!srvLabel.isNullOrBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.field)
                                    .border(1.dp, palette.line, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Dns, null, tint = palette.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("当前服务器", fontSize = 11.sp, color = palette.muted)
                                    Text(srvLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text)
                                }
                                Text(
                                    "切换服务器",
                                    color = palette.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(palette.primarySoft)
                                        .clickable { viewModel.goToServerEdit() }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Segmented Login / Register switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.field)
                                .border(1.dp, palette.line, RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val isRegister = state.step == AuthStep.Register
                            AuthModeTab(
                                label = "账号登录",
                                selected = !isRegister,
                                modifier = Modifier.weight(1f),
                            ) { viewModel.switchAuthMode(register = false) }
                            AuthModeTab(
                                label = "注册新账号",
                                selected = isRegister,
                                modifier = Modifier.weight(1f),
                            ) { viewModel.switchAuthMode(register = true) }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        if (state.step == AuthStep.Login) {
                            AuthFieldLabel("邮箱")
                            Spacer(modifier = Modifier.height(6.dp))
                            AuthTextField(
                                value = state.email,
                                onValueChange = viewModel::updateEmail,
                                placeholder = "请输入邮箱",
                                leading = { Icon(Icons.Rounded.Person, null, tint = palette.muted) },
                                keyboardType = KeyboardType.Email,
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            AuthFieldLabel("密码")
                            Spacer(modifier = Modifier.height(6.dp))
                            AuthTextField(
                                value = state.password,
                                onValueChange = viewModel::updatePassword,
                                placeholder = "请输入密码",
                                leading = { Icon(Icons.Rounded.Key, null, tint = palette.muted) },
                                isPassword = true,
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            PrimaryActionButton(
                                label = if (state.busy) "正在登录…" else "登录",
                                busy = state.busy,
                                onClick = { viewModel.login(onAuthenticated) },
                            )
                        } else {
                            if (!state.allowRegistration) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(palette.dangerSoft)
                                        .padding(12.dp),
                                ) {
                                    Text(
                                        "管理员已关闭自助注册，请联系管理员开通",
                                        color = palette.danger,
                                        fontSize = 13.sp,
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                            AuthFieldLabel("邮箱")
                            Spacer(modifier = Modifier.height(6.dp))
                            AuthTextField(
                                value = state.email,
                                onValueChange = viewModel::updateEmail,
                                placeholder = "name@company.com",
                                leading = { Icon(Icons.Rounded.Person, null, tint = palette.muted) },
                                keyboardType = KeyboardType.Email,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            AuthFieldLabel("显示名称（可选）")
                            Spacer(modifier = Modifier.height(6.dp))
                            AuthTextField(
                                value = state.displayName,
                                onValueChange = viewModel::updateDisplayName,
                                placeholder = "例如 张三的手机",
                                leading = { Icon(Icons.Rounded.Badge, null, tint = palette.muted) },
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            AuthFieldLabel("密码")
                            Spacer(modifier = Modifier.height(6.dp))
                            AuthTextField(
                                value = state.password,
                                onValueChange = viewModel::updatePassword,
                                placeholder = "至少 8 位密码",
                                leading = { Icon(Icons.Rounded.Key, null, tint = palette.muted) },
                                isPassword = true,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            AuthFieldLabel("确认密码")
                            Spacer(modifier = Modifier.height(6.dp))
                            AuthTextField(
                                value = state.registerConfirmPassword,
                                onValueChange = viewModel::updateRegisterConfirmPassword,
                                placeholder = "再次输入密码",
                                leading = { Icon(Icons.Rounded.Key, null, tint = palette.muted) },
                                isPassword = true,
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            PrimaryActionButton(
                                label = if (state.busy) "正在注册…" else "注册并登录",
                                busy = state.busy,
                                enabled = state.allowRegistration,
                                onClick = { viewModel.register(onAuthenticated) },
                            )
                        }
                    }

                    AuthStep.Password -> {
                        AuthFieldLabel("当前密码")
                        Spacer(modifier = Modifier.height(6.dp))
                        AuthTextField(
                            value = state.currentPassword,
                            onValueChange = viewModel::updateCurrentPassword,
                            placeholder = "当前密码",
                            isPassword = true,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        AuthFieldLabel("新密码")
                        Spacer(modifier = Modifier.height(6.dp))
                        AuthTextField(
                            value = state.newPassword,
                            onValueChange = viewModel::updateNewPassword,
                            placeholder = "新密码",
                            isPassword = true,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        AuthFieldLabel("确认新密码")
                        Spacer(modifier = Modifier.height(6.dp))
                        AuthTextField(
                            value = state.confirmPassword,
                            onValueChange = viewModel::updateConfirmPassword,
                            placeholder = "再次输入新密码",
                            isPassword = true,
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        PrimaryActionButton(
                            label = if (state.busy) "正在保存…" else "保存新密码",
                            busy = state.busy,
                            onClick = { viewModel.changePassword(onAuthenticated) },
                        )
                    }
                }

                if (!state.error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.dangerSoft)
                            .padding(12.dp),
                    ) {
                        Text(state.error.orEmpty(), color = palette.danger, fontSize = 13.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    pendingDeleteServer?.let { srv ->
        AlertDialog(
            onDismissRequest = { pendingDeleteServer = null },
            title = { Text("移除服务器") },
            text = { Text("确认移除服务器「${srv.displayName}」？该服务器在本机保存的登录凭证也将一并清除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeSavedServer(srv)
                    pendingDeleteServer = null
                }) { Text("移除", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteServer = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun AuthModeTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) palette.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) palette.primary else palette.muted,
        )
    }
}

@Composable
private fun AuthFieldLabel(label: String) {
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = LocalRabbitPalette.current.text)
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leading: (@Composable () -> Unit)? = null,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val palette = LocalRabbitPalette.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = palette.muted, fontSize = 14.sp) },
        leadingIcon = leading,
        singleLine = singleLine,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
        ),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = palette.field,
            unfocusedContainerColor = palette.field,
            focusedBorderColor = palette.primary,
            unfocusedBorderColor = palette.line,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PrimaryActionButton(
    label: String,
    busy: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = LocalRabbitPalette.current.primary,
            contentColor = Color.White,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
