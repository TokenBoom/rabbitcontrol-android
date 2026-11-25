package io.rabbitcontrol.android.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.nativebridge.ServerConfigModel
import io.rabbitcontrol.android.ui.components.RabbitCard
import io.rabbitcontrol.android.ui.components.RabbitDialog
import io.rabbitcontrol.android.ui.components.RabbitPrimaryButton
import io.rabbitcontrol.android.ui.components.RabbitSecondaryButton
import io.rabbitcontrol.android.ui.components.RabbitSegmentedControl
import io.rabbitcontrol.android.ui.components.RabbitStatusPill
import io.rabbitcontrol.android.ui.components.RabbitTextField
import io.rabbitcontrol.android.ui.components.RabbitTopBar
import io.rabbitcontrol.android.ui.components.StatusTone
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.RabbitSpacing
import org.koin.androidx.compose.koinViewModel

private enum class AuthTabOption(val label: String) {
    Login("账号登录"),
    Register("注册新账号"),
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var showAddServerSheet by remember { mutableStateOf(false) }
    var expandCaPin by remember { mutableStateOf(false) }

    LaunchedEffect(initialStep) {
        viewModel.init(initialStep)
    }

    LaunchedEffect(state.step) {
        if (state.step != AuthStep.Server) {
            showAddServerSheet = false
        }
    }

    val (topTitle, topSubtitle) = when (state.step) {
        AuthStep.Server -> "服务器管理" to if (servers.isNotEmpty()) {
            "共 ${servers.size} 台 · 点击切换服务器"
        } else {
            "连接控制服务端以同步设备"
        }
        AuthStep.Trust -> "确认服务器指纹" to (state.candidateServer?.address ?: "")
        AuthStep.Login -> "账号登录" to "登录以同步设备与免密凭证"
        AuthStep.Register -> "注册新账号" to "注册后将自动登录并绑定本机"
        AuthStep.Password -> "设置新密码" to "首次登录需修改初始密码"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .systemBarsPadding()
            .imePadding(),
    ) {
        val topBarBack: (() -> Unit)? = when {
            state.step == AuthStep.Trust -> {
                {
                    viewModel.backFromTrust()
                    if (servers.isNotEmpty()) showAddServerSheet = true
                }
            }
            canNavigateBack -> onBack
            state.step == AuthStep.Server && activeServer != null -> {
                { viewModel.switchAuthMode(register = false) }
            }
            else -> null
        }

        BackHandler(
            enabled = !canNavigateBack &&
                (state.step == AuthStep.Trust || (state.step == AuthStep.Server && activeServer != null)),
        ) {
            topBarBack?.invoke()
        }

        RabbitTopBar(
            title = topTitle,
            subtitle = topSubtitle.takeIf { it.isNotBlank() },
            onBack = topBarBack,
            actions = {
                if (state.step == AuthStep.Server && servers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(RabbitRadius.Pill))
                            .background(palette.primarySoft)
                            .clickable {
                                viewModel.clearError()
                                showAddServerSheet = true
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = palette.primaryText, modifier = Modifier.size(16.dp))
                        Text("添加", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.primaryText)
                    }
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(RabbitSpacing.Md),
            ) {
                when (state.step) {
                    AuthStep.Server -> {
                        if (servers.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                servers.forEach { srv ->
                                    val isActive = srv.id == activeServer?.id
                                    val email = savedEmails[srv.id]
                                    RabbitCard(
                                        onClick = if (!state.busy) {
                                            { viewModel.selectSavedServer(srv, onAuthenticated) }
                                        } else {
                                            null
                                        },
                                        borderColor = if (isActive) palette.primary else palette.line,
                                        contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isActive) palette.primarySoft else palette.field),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Dns,
                                                    contentDescription = null,
                                                    tint = if (isActive) palette.primaryText else palette.muted,
                                                    modifier = Modifier.size(19.dp),
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Text(
                                                        text = srv.displayName,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = palette.text,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false),
                                                    )
                                                    if (isActive) {
                                                        RabbitStatusPill(label = "当前", tone = StatusTone.Primary)
                                                    }
                                                }
                                                if (srv.name.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = srv.address,
                                                        fontSize = 12.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = palette.muted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = email ?: "点击登录此服务器",
                                                    fontSize = 12.sp,
                                                    color = if (email != null) palette.muted else palette.primaryText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            IconButton(
                                                onClick = { pendingDeleteServer = srv },
                                                modifier = Modifier.size(40.dp),
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
                            }

                            if (!showAddServerSheet && !state.error.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(RabbitRadius.Md))
                                        .background(palette.dangerSoft)
                                        .padding(12.dp),
                                ) {
                                    Text(state.error.orEmpty(), color = palette.danger, fontSize = 13.sp)
                                }
                            }

                            RabbitSecondaryButton(
                                label = "添加新服务器",
                                onClick = {
                                    viewModel.clearError()
                                    showAddServerSheet = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            // First-time onboarding server form: compact horizontal header + form card
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.rabbitcontrol),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                )
                                Column {
                                    Text("连接 RabbitControl 服务器", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                    Text("输入私有或公网服务端地址，默认端口 21118", fontSize = 12.sp, color = palette.muted)
                                }
                            }
                            RabbitCard(modifier = Modifier.fillMaxWidth()) {
                                ServerFormContent(
                                    serverAddress = state.serverAddress,
                                    onAddressChange = viewModel::updateServerAddress,
                                    serverName = state.serverName,
                                    onNameChange = viewModel::updateServerName,
                                    caPin = state.caPin,
                                    onCaPinChange = viewModel::updateCaPin,
                                    expandCaPin = expandCaPin,
                                    onToggleExpandCaPin = { expandCaPin = !expandCaPin },
                                    busy = state.busy,
                                    error = state.error,
                                    submitLabel = if (state.busy) "正在校验服务器…" else "校验服务器",
                                    onSubmit = viewModel::probeServer,
                                )
                            }
                        }
                    }

                    AuthStep.Trust -> {
                        val cand = state.candidateServer
                        RabbitCard(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(palette.successSoft)
                                    .align(Alignment.CenterHorizontally),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Check, null, tint = palette.success, modifier = Modifier.size(26.dp))
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
                                    .clip(RoundedCornerShape(RabbitRadius.Md))
                                    .background(palette.field)
                                    .border(1.dp, palette.line, RoundedCornerShape(RabbitRadius.Md))
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
                            RabbitPrimaryButton(
                                label = if (state.busy) "正在保存…" else "信任并继续",
                                busy = state.busy,
                                onClick = { viewModel.trustAndContinue(onlyServer, onAuthenticated) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            RabbitSecondaryButton(
                                label = "重新输入",
                                onClick = {
                                    viewModel.backFromTrust()
                                    if (servers.isNotEmpty()) {
                                        showAddServerSheet = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (!state.error.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(RabbitRadius.Md))
                                        .background(palette.dangerSoft)
                                        .padding(12.dp),
                                ) {
                                    Text(state.error.orEmpty(), color = palette.danger, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    AuthStep.Login, AuthStep.Register -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.rabbitcontrol),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(9.dp)),
                            )
                            Column {
                                Text("RabbitControl", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                Text(
                                    if (state.step == AuthStep.Login) "登录账号以同步设备列表" else "注册后自动绑定本机",
                                    fontSize = 12.sp,
                                    color = palette.muted,
                                )
                            }
                        }

                        RabbitCard(modifier = Modifier.fillMaxWidth()) {
                            val srvLabel = activeServer?.displayName ?: state.savedServerAddress
                            if (!srvLabel.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(RabbitRadius.Md))
                                        .background(palette.field)
                                        .border(1.dp, palette.line, RoundedCornerShape(RabbitRadius.Md))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.Dns, null, tint = palette.primaryText, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("当前服务器", fontSize = 11.sp, color = palette.muted)
                                        Text(srvLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text)
                                    }
                                    Text(
                                        "切换服务器",
                                        color = palette.primaryText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(RabbitRadius.Sm))
                                            .background(palette.primarySoft)
                                            .clickable { viewModel.goToServerEdit() }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            val currentTab = if (state.step == AuthStep.Register) AuthTabOption.Register else AuthTabOption.Login
                            RabbitSegmentedControl(
                                options = AuthTabOption.entries,
                                selected = currentTab,
                                labelProvider = { it.label },
                                onSelect = { tab -> viewModel.switchAuthMode(register = tab == AuthTabOption.Register) },
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            if (state.step == AuthStep.Login) {
                                RabbitTextField(
                                    value = state.email,
                                    onValueChange = viewModel::updateEmail,
                                    label = "邮箱",
                                    placeholder = "请输入邮箱",
                                    leadingIcon = { Icon(Icons.Rounded.Person, null, tint = palette.muted) },
                                    keyboardType = KeyboardType.Email,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                RabbitTextField(
                                    value = state.password,
                                    onValueChange = viewModel::updatePassword,
                                    label = "密码",
                                    placeholder = "请输入密码",
                                    leadingIcon = { Icon(Icons.Rounded.Key, null, tint = palette.muted) },
                                    isPassword = true,
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                RabbitPrimaryButton(
                                    label = if (state.busy) "正在登录…" else "登录",
                                    busy = state.busy,
                                    onClick = { viewModel.login(onAuthenticated) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                if (!state.allowRegistration) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(RabbitRadius.Md))
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
                                RabbitTextField(
                                    value = state.email,
                                    onValueChange = viewModel::updateEmail,
                                    label = "邮箱",
                                    placeholder = "name@company.com",
                                    leadingIcon = { Icon(Icons.Rounded.Person, null, tint = palette.muted) },
                                    keyboardType = KeyboardType.Email,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                RabbitTextField(
                                    value = state.displayName,
                                    onValueChange = viewModel::updateDisplayName,
                                    label = "显示名称（可选）",
                                    placeholder = "例如 张三的手机",
                                    leadingIcon = { Icon(Icons.Rounded.Badge, null, tint = palette.muted) },
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                RabbitTextField(
                                    value = state.password,
                                    onValueChange = viewModel::updatePassword,
                                    label = "密码",
                                    placeholder = "至少 8 位密码",
                                    leadingIcon = { Icon(Icons.Rounded.Key, null, tint = palette.muted) },
                                    isPassword = true,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                RabbitTextField(
                                    value = state.registerConfirmPassword,
                                    onValueChange = viewModel::updateRegisterConfirmPassword,
                                    label = "确认密码",
                                    placeholder = "再次输入密码",
                                    leadingIcon = { Icon(Icons.Rounded.Key, null, tint = palette.muted) },
                                    isPassword = true,
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                RabbitPrimaryButton(
                                    label = if (state.busy) "正在注册…" else "注册并登录",
                                    busy = state.busy,
                                    enabled = state.allowRegistration,
                                    onClick = { viewModel.register(onAuthenticated) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            if (!state.error.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(RabbitRadius.Md))
                                        .background(palette.dangerSoft)
                                        .padding(12.dp),
                                ) {
                                    Text(state.error.orEmpty(), color = palette.danger, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    AuthStep.Password -> {
                        RabbitCard(modifier = Modifier.fillMaxWidth()) {
                            RabbitTextField(
                                value = state.currentPassword,
                                onValueChange = viewModel::updateCurrentPassword,
                                label = "当前密码",
                                placeholder = "当前密码",
                                isPassword = true,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            RabbitTextField(
                                value = state.newPassword,
                                onValueChange = viewModel::updateNewPassword,
                                label = "新密码",
                                placeholder = "新密码",
                                isPassword = true,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            RabbitTextField(
                                value = state.confirmPassword,
                                onValueChange = viewModel::updateConfirmPassword,
                                label = "确认新密码",
                                placeholder = "再次输入新密码",
                                isPassword = true,
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            RabbitPrimaryButton(
                                label = if (state.busy) "正在保存…" else "保存新密码",
                                busy = state.busy,
                                onClick = { viewModel.changePassword(onAuthenticated) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (!state.error.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(RabbitRadius.Md))
                                        .background(palette.dangerSoft)
                                        .padding(12.dp),
                                ) {
                                    Text(state.error.orEmpty(), color = palette.danger, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showAddServerSheet && state.step == AuthStep.Server) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddServerSheet = false },
            sheetState = sheetState,
            containerColor = palette.surface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = RabbitSpacing.Xl)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "添加新服务器",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { showAddServerSheet = false },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Rounded.Close, "关闭", tint = palette.muted, modifier = Modifier.size(18.dp))
                    }
                }

                ServerFormContent(
                    serverAddress = state.serverAddress,
                    onAddressChange = viewModel::updateServerAddress,
                    serverName = state.serverName,
                    onNameChange = viewModel::updateServerName,
                    caPin = state.caPin,
                    onCaPinChange = viewModel::updateCaPin,
                    expandCaPin = expandCaPin,
                    onToggleExpandCaPin = { expandCaPin = !expandCaPin },
                    busy = state.busy,
                    error = state.error,
                    submitLabel = if (state.busy) "正在校验服务器…" else "校验并添加",
                    onSubmit = viewModel::probeServer,
                )
            }
        }
    }

    pendingDeleteServer?.let { srv ->
        RabbitDialog(
            title = "移除服务器",
            message = "确认移除服务器「${srv.displayName}」？该服务器在本机保存的登录凭证也将一并清除。",
            onDismissRequest = { pendingDeleteServer = null },
            dismissLabel = "取消",
            confirmLabel = "移除",
            destructive = true,
            onConfirm = {
                viewModel.removeSavedServer(srv)
                pendingDeleteServer = null
            },
        )
    }
}

@Composable
private fun ServerFormContent(
    serverAddress: String,
    onAddressChange: (String) -> Unit,
    serverName: String,
    onNameChange: (String) -> Unit,
    caPin: String,
    onCaPinChange: (String) -> Unit,
    expandCaPin: Boolean,
    onToggleExpandCaPin: () -> Unit,
    busy: Boolean,
    error: String?,
    submitLabel: String,
    onSubmit: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val showCaField = expandCaPin || caPin.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RabbitTextField(
            value = serverAddress,
            onValueChange = onAddressChange,
            label = "服务器地址",
            placeholder = "主机名或 IP，默认端口 21118",
            leadingIcon = { Icon(Icons.Rounded.Dns, null, tint = palette.muted) },
            keyboardType = KeyboardType.Uri,
        )
        RabbitTextField(
            value = serverName,
            onValueChange = onNameChange,
            label = "服务器名称（可选）",
            placeholder = "例如 公司内网 / 家用电脑",
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, null, tint = palette.muted) },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RabbitRadius.Sm))
                .clickable(onClick = onToggleExpandCaPin)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "手动指定 CA 指纹（可选）",
                fontSize = 13.sp,
                color = palette.muted,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (showCaField) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = palette.muted,
                modifier = Modifier.size(18.dp),
            )
        }

        if (showCaField) {
            RabbitTextField(
                value = caPin,
                onValueChange = onCaPinChange,
                placeholder = "64 位 SHA-256 指纹",
                leadingIcon = { Icon(Icons.Rounded.Key, null, tint = palette.muted) },
                singleLine = false,
            )
        }

        if (!error.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RabbitRadius.Md))
                    .background(palette.dangerSoft)
                    .padding(12.dp),
            ) {
                Text(error, color = palette.danger, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        RabbitPrimaryButton(
            label = submitLabel,
            busy = busy,
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
