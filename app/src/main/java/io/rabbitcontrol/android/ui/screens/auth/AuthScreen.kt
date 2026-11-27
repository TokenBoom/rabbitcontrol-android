package io.rabbitcontrol.android.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rabbitcontrol.android.error.RcErrorPresentation
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.nativebridge.ServerConfigModel
import io.rabbitcontrol.android.ui.components.ProvideRabbitTextToolbar
import io.rabbitcontrol.android.ui.components.RabbitCard
import io.rabbitcontrol.android.ui.components.RabbitDialog
import io.rabbitcontrol.android.ui.components.RabbitPrimaryButton
import io.rabbitcontrol.android.ui.components.RabbitSecondaryButton
import io.rabbitcontrol.android.ui.components.RabbitStatusPill
import io.rabbitcontrol.android.ui.components.RabbitTextField
import io.rabbitcontrol.android.ui.components.RabbitToast
import io.rabbitcontrol.android.ui.components.RabbitToastModalOverlay
import io.rabbitcontrol.android.ui.components.RabbitTopBar
import io.rabbitcontrol.android.ui.components.RcErrorHub
import io.rabbitcontrol.android.ui.components.RcInlineError
import io.rabbitcontrol.android.ui.components.StatusTone
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.RabbitSpacing
import org.koin.androidx.compose.koinViewModel

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
    var showServerSheet by remember { mutableStateOf(false) }
    var expandAdvanced by remember { mutableStateOf(false) }

    LaunchedEffect(initialStep) {
        viewModel.init(initialStep)
    }

    LaunchedEffect(state.step) {
        if (state.step != AuthStep.Server) {
            showServerSheet = false
        }
    }

    LaunchedEffect(state.uiError) {
        val err = state.uiError
        if (err != null && err.presentation != RcErrorPresentation.INLINE) {
            RcErrorHub.present(err)
            viewModel.clearError()
        }
    }

    val regDisabledToast = stringResource(R.string.auth_reg_disabled_toast)
    LaunchedEffect(state.step, state.allowRegistration) {
        if (state.step == AuthStep.Register && !state.allowRegistration) {
            RabbitToast.warning(regDisabledToast)
        }
    }

    val isCredentialStep = state.step == AuthStep.Login || state.step == AuthStep.Register
    val isEmptyServerOnboarding = state.step == AuthStep.Server && servers.isEmpty()

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
                    if (servers.isNotEmpty()) showServerSheet = true
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

        if (isCredentialStep || isEmptyServerOnboarding) {
            val srvLabel = if (isCredentialStep) {
                activeServer?.displayName ?: state.savedServerAddress
            } else {
                null
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(horizontal = RabbitSpacing.Sm, vertical = RabbitSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (topBarBack != null) {
                    IconButton(
                        onClick = topBarBack,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = palette.text,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (!srvLabel.isNullOrBlank()) {
                    Surface(
                        onClick = viewModel::goToServerEdit,
                        shape = RoundedCornerShape(RabbitRadius.Pill),
                        color = palette.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = RabbitElevation.Card,
                        border = BorderStroke(1.dp, palette.line),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Dns,
                                contentDescription = null,
                                tint = palette.primaryText,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = srvLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp),
                            )
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.auth_switch_server),
                                tint = palette.muted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(RabbitSpacing.Sm))
                }
            }
        } else {
            val (topTitle, topSubtitle) = when (state.step) {
                AuthStep.Server -> stringResource(R.string.auth_server_mgmt_title) to if (servers.isNotEmpty()) {
                    stringResource(R.string.auth_server_mgmt_sub_count, servers.size)
                } else {
                    stringResource(R.string.auth_server_mgmt_sub_empty)
                }
                AuthStep.Trust -> stringResource(R.string.auth_trust_title) to (state.candidateServer?.let { srv ->
                    if (srv.apiPort != ServerConfigModel.DEFAULT_API_PORT) "${srv.address} · API ${srv.apiPort}" else srv.address
                } ?: "")
                AuthStep.Password -> stringResource(R.string.auth_password_title) to stringResource(R.string.auth_password_sub)
                else -> "" to ""
            }

            RabbitTopBar(
                title = topTitle,
                subtitle = topSubtitle.takeIf { it.isNotBlank() },
                onBack = topBarBack,
                actions = {
                    if (state.step == AuthStep.Server && servers.isNotEmpty()) {
                        val anyTesting = state.serverTests.values.any { it is ServerTestState.Testing }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val probeAllFailedMsg = stringResource(R.string.auth_probe_all_failed)
                            Row(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(RabbitRadius.Pill))
                                    .background(palette.surface)
                                    .border(1.dp, palette.line, RoundedCornerShape(RabbitRadius.Pill))
                                    .clickable(enabled = !anyTesting) {
                                        viewModel.testAllServers { ok, total ->
                                            if (ok == total) {
                                                RabbitToast.success(
                                                    io.rabbitcontrol.android.core.i18n.AppStrings.get(
                                                        R.string.auth_probe_summary,
                                                        "测速完成 · $ok/$total 台可用",
                                                        ok,
                                                        total,
                                                    ),
                                                )
                                            } else if (ok > 0) {
                                                RabbitToast.warning(
                                                    io.rabbitcontrol.android.core.i18n.AppStrings.get(
                                                        R.string.auth_probe_summary,
                                                        "测速完成 · $ok/$total 台可用",
                                                        ok,
                                                        total,
                                                    ),
                                                )
                                            } else {
                                                RabbitToast.error(probeAllFailedMsg)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (anyTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        color = palette.primaryText,
                                        strokeWidth = 1.6.dp,
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.NetworkCheck,
                                        null,
                                        tint = palette.text,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                                Text(
                                    text = if (anyTesting) stringResource(R.string.auth_probing) else stringResource(R.string.auth_probe),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = palette.text,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(RabbitRadius.Pill))
                                    .background(palette.primarySoft)
                                    .clickable {
                                        viewModel.startAddServer()
                                        expandAdvanced = false
                                        showServerSheet = true
                                    }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Rounded.Add, null, tint = palette.primaryText, modifier = Modifier.size(16.dp))
                                Text(stringResource(R.string.common_add), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.primaryText)
                            }
                        }
                    }
                },
            )
        }

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
                                    val testState = state.serverTests[srv.id]
                                    val isTesting = testState is ServerTestState.Testing
                                    RabbitCard(
                                        onClick = if (!state.busy) {
                                            { viewModel.selectSavedServer(srv, onAuthenticated) }
                                        } else {
                                            null
                                        },
                                        borderColor = if (isActive) palette.primary else palette.line,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
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
                                                        RabbitStatusPill(label = stringResource(R.string.auth_current_badge), tone = StatusTone.Primary)
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
                                                srv.customPortsSummary()?.let { portSummary ->
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = portSummary,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = palette.muted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = email ?: stringResource(R.string.auth_tap_to_login),
                                                    fontSize = 12.sp,
                                                    color = if (email != null) palette.muted else palette.primaryText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = palette.line, thickness = 1.dp)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                when (testState) {
                                                    is ServerTestState.Testing -> {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        ) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(13.dp),
                                                                color = palette.primaryText,
                                                                strokeWidth = 1.6.dp,
                                                            )
                                                            Text(
                                                                text = stringResource(R.string.auth_testing_conn),
                                                                fontSize = 12.sp,
                                                                color = palette.muted,
                                                            )
                                                        }
                                                    }
                                                    is ServerTestState.Reachable -> {
                                                        val (label, tone) = when {
                                                            !testState.healthy -> stringResource(R.string.auth_status_abnormal, testState.latencyMs) to StatusTone.Warning
                                                            testState.latencyMs < 150 -> stringResource(R.string.auth_status_latency, testState.latencyMs) to StatusTone.Success
                                                            testState.latencyMs <= 400 -> stringResource(R.string.auth_status_latency, testState.latencyMs) to StatusTone.Warning
                                                            else -> stringResource(R.string.auth_status_latency, testState.latencyMs) to StatusTone.Danger
                                                        }
                                                        RabbitStatusPill(label = label, tone = tone)
                                                    }
                                                    is ServerTestState.Failed -> {
                                                        RabbitStatusPill(label = stringResource(R.string.auth_status_failed), tone = StatusTone.Danger)
                                                    }
                                                    null -> {
                                                        Text(
                                                            text = stringResource(R.string.auth_status_untested),
                                                            fontSize = 11.sp,
                                                            color = palette.muted,
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .height(30.dp)
                                                        .clip(RoundedCornerShape(RabbitRadius.Pill))
                                                        .background(palette.primarySoft)
                                                        .clickable(enabled = !isTesting) {
                                                            viewModel.testServer(srv) { res ->
                                                                when (res) {
                                                                    is ServerTestState.Reachable -> {
                                                                        if (res.healthy) {
                                                                            RabbitToast.success(
                                                                                io.rabbitcontrol.android.core.i18n.AppStrings.get(
                                                                                    R.string.auth_toast_healthy,
                                                                                    "${srv.displayName} 连通正常 · ${res.latencyMs} ms",
                                                                                    srv.displayName,
                                                                                    res.latencyMs,
                                                                                ),
                                                                            )
                                                                        } else {
                                                                            RabbitToast.warning(
                                                                                io.rabbitcontrol.android.core.i18n.AppStrings.get(
                                                                                    R.string.auth_toast_unhealthy,
                                                                                    "${srv.displayName} 服务状态异常 · ${res.latencyMs} ms",
                                                                                    srv.displayName,
                                                                                    res.latencyMs,
                                                                                ),
                                                                            )
                                                                        }
                                                                    }
                                                                    is ServerTestState.Failed -> {
                                                                        RcErrorHub.present(res.error)
                                                                    }
                                                                    ServerTestState.Testing -> Unit
                                                                }
                                                            }
                                                        }
                                                        .padding(horizontal = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.NetworkCheck,
                                                        contentDescription = stringResource(R.string.auth_cd_test_conn),
                                                        tint = palette.primaryText,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                    Text(
                                                        text = if (isTesting) stringResource(R.string.auth_testing) else stringResource(R.string.auth_test),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = palette.primaryText,
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        viewModel.startEditServer(srv)
                                                        expandAdvanced = srv.hasCustomPorts
                                                        showServerSheet = true
                                                    },
                                                    modifier = Modifier.size(32.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Edit,
                                                        contentDescription = stringResource(R.string.auth_cd_edit_server),
                                                        tint = palette.muted,
                                                        modifier = Modifier.size(17.dp),
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { pendingDeleteServer = srv },
                                                    modifier = Modifier.size(32.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.DeleteOutline,
                                                        contentDescription = stringResource(R.string.auth_cd_delete_server),
                                                        tint = palette.muted,
                                                        modifier = Modifier.size(17.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            RabbitSecondaryButton(
                                label = stringResource(R.string.auth_add_new_server),
                                onClick = {
                                    viewModel.startAddServer()
                                    expandAdvanced = false
                                    showServerSheet = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                            ) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Image(
                                    painter = painterResource(R.drawable.rabbitcontrol),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(13.dp)),
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.auth_connect_server),
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.text,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.auth_connect_server_sub),
                                    fontSize = 13.sp,
                                    color = palette.muted,
                                )
                                Spacer(modifier = Modifier.height(28.dp))
                                val inlineErr = state.uiError?.takeIf { it.presentation == RcErrorPresentation.INLINE }
                                if (inlineErr != null) {
                                    RcInlineError(
                                        error = inlineErr,
                                        onDismiss = viewModel::clearError,
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                }
                                ServerFormContent(
                                    serverAddress = state.serverAddress,
                                    onAddressChange = viewModel::updateServerAddress,
                                    serverName = state.serverName,
                                    onNameChange = viewModel::updateServerName,
                                    apiPort = state.apiPort,
                                    onApiPortChange = viewModel::updateApiPort,
                                    signalPort = state.signalPort,
                                    onSignalPortChange = viewModel::updateSignalPort,
                                    relayPort = state.relayPort,
                                    onRelayPortChange = viewModel::updateRelayPort,
                                    caPin = state.caPin,
                                    onCaPinChange = viewModel::updateCaPin,
                                    expandAdvanced = expandAdvanced,
                                    onToggleExpandAdvanced = { expandAdvanced = !expandAdvanced },
                                    busy = state.busy,
                                    submitLabel = if (state.busy) {
                                        stringResource(R.string.auth_verifying_server)
                                    } else {
                                        stringResource(R.string.auth_verify_and_connect)
                                    },
                                    submitHeight = 50.dp,
                                    onSubmit = { viewModel.probeServer() },
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
                                stringResource(R.string.auth_trust_hint),
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
                                label = if (state.busy) stringResource(R.string.auth_saving) else stringResource(R.string.auth_trust_and_continue),
                                busy = state.busy,
                                onClick = { viewModel.trustAndContinue(onlyServer, onAuthenticated) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            RabbitSecondaryButton(
                                label = stringResource(R.string.auth_reenter),
                                onClick = {
                                    viewModel.backFromTrust()
                                    if (servers.isNotEmpty()) {
                                        showServerSheet = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    AuthStep.Login, AuthStep.Register -> {
                        val isLogin = state.step == AuthStep.Login
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Image(
                                painter = painterResource(R.drawable.rabbitcontrol),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(13.dp)),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (isLogin) stringResource(R.string.auth_welcome_title) else stringResource(R.string.auth_create_account_title),
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.text,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isLogin) {
                                    stringResource(R.string.auth_welcome_sub)
                                } else {
                                    stringResource(R.string.auth_create_account_sub)
                                },
                                fontSize = 13.sp,
                                color = palette.muted,
                            )
                            Spacer(modifier = Modifier.height(28.dp))

                            val inlineErr = state.uiError?.takeIf { it.presentation == RcErrorPresentation.INLINE }
                            if (inlineErr != null) {
                                RcInlineError(
                                    error = inlineErr,
                                    onDismiss = viewModel::clearError,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            if (isLogin) {
                                RabbitTextField(
                                    value = state.email,
                                    onValueChange = viewModel::updateEmail,
                                    label = stringResource(R.string.auth_email_label),
                                    placeholder = stringResource(R.string.auth_email_hint),
                                    leadingIcon = { Icon(Icons.Rounded.Person, null, tint = palette.muted) },
                                    keyboardType = KeyboardType.Email,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                RabbitTextField(
                                    value = state.password,
                                    onValueChange = viewModel::updatePassword,
                                    label = stringResource(R.string.auth_password_label),
                                    placeholder = stringResource(R.string.auth_password_hint),
                                    leadingIcon = { Icon(Icons.Rounded.Key, null, tint = palette.muted) },
                                    isPassword = true,
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                RabbitPrimaryButton(
                                    label = if (state.busy) stringResource(R.string.auth_signing_in) else stringResource(R.string.auth_sign_in),
                                    busy = state.busy,
                                    height = 50.dp,
                                    onClick = { viewModel.login(onAuthenticated) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                if (!state.allowRegistration) {
                                    Text(
                                        text = stringResource(R.string.auth_reg_disabled_banner),
                                        color = palette.muted,
                                        fontSize = 12.sp,
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                RabbitTextField(
                                    value = state.email,
                                    onValueChange = viewModel::updateEmail,
                                    label = stringResource(R.string.auth_email_label),
                                    placeholder = "name@company.com",
                                    leadingIcon = { Icon(Icons.Rounded.Person, null, tint = palette.muted) },
                                    keyboardType = KeyboardType.Email,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                RabbitTextField(
                                    value = state.displayName,
                                    onValueChange = viewModel::updateDisplayName,
                                    label = stringResource(R.string.auth_display_name_label),
                                    placeholder = stringResource(R.string.auth_display_name_hint),
                                    leadingIcon = { Icon(Icons.Rounded.Badge, null, tint = palette.muted) },
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                RabbitTextField(
                                    value = state.password,
                                    onValueChange = viewModel::updatePassword,
                                    label = stringResource(R.string.auth_password_label),
                                    placeholder = stringResource(R.string.auth_password_min_hint),
                                    leadingIcon = { Icon(Icons.Rounded.Key, null, tint = palette.muted) },
                                    isPassword = true,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                RabbitTextField(
                                    value = state.registerConfirmPassword,
                                    onValueChange = viewModel::updateRegisterConfirmPassword,
                                    label = stringResource(R.string.auth_confirm_password_label),
                                    placeholder = stringResource(R.string.auth_confirm_password_hint),
                                    leadingIcon = { Icon(Icons.Rounded.Key, null, tint = palette.muted) },
                                    isPassword = true,
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                RabbitPrimaryButton(
                                    label = if (state.busy) stringResource(R.string.auth_registering) else stringResource(R.string.auth_register_and_login),
                                    busy = state.busy,
                                    enabled = state.allowRegistration,
                                    height = 50.dp,
                                    onClick = { viewModel.register(onAuthenticated) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (isLogin) stringResource(R.string.auth_no_account_prompt) else stringResource(R.string.auth_has_account_prompt),
                                    fontSize = 13.sp,
                                    color = palette.muted,
                                )
                                Text(
                                    text = if (isLogin) stringResource(R.string.auth_go_register) else stringResource(R.string.auth_go_login),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.primaryText,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(RabbitRadius.Sm))
                                        .clickable { viewModel.switchAuthMode(register = isLogin) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    AuthStep.Password -> {
                        RabbitCard(modifier = Modifier.fillMaxWidth()) {
                            val inlineErr = state.uiError?.takeIf { it.presentation == RcErrorPresentation.INLINE }
                            if (inlineErr != null) {
                                RcInlineError(
                                    error = inlineErr,
                                    onDismiss = viewModel::clearError,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            RabbitTextField(
                                value = state.currentPassword,
                                onValueChange = viewModel::updateCurrentPassword,
                                label = stringResource(R.string.auth_current_pw_label),
                                placeholder = stringResource(R.string.auth_current_pw_label),
                                isPassword = true,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            RabbitTextField(
                                value = state.newPassword,
                                onValueChange = viewModel::updateNewPassword,
                                label = stringResource(R.string.auth_new_pw_label),
                                placeholder = stringResource(R.string.auth_new_pw_label),
                                isPassword = true,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            RabbitTextField(
                                value = state.confirmPassword,
                                onValueChange = viewModel::updateConfirmPassword,
                                label = stringResource(R.string.auth_confirm_new_pw_label),
                                placeholder = stringResource(R.string.auth_confirm_new_pw_hint),
                                isPassword = true,
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            RabbitPrimaryButton(
                                label = if (state.busy) stringResource(R.string.auth_saving) else stringResource(R.string.auth_save_new_pw),
                                busy = state.busy,
                                onClick = { viewModel.changePassword(onAuthenticated) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showServerSheet && state.step == AuthStep.Server) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val editing = state.editingServer
        val submitText = when {
            state.busy -> if (editing != null && !viewModel.needsReprobeForEdit(editing)) {
                stringResource(R.string.auth_saving)
            } else {
                stringResource(R.string.auth_verifying_server)
            }
            editing != null && !viewModel.needsReprobeForEdit(editing) -> stringResource(R.string.auth_save_changes)
            editing != null -> stringResource(R.string.auth_reverify_and_save)
            else -> stringResource(R.string.auth_verify_and_add)
        }
        ModalBottomSheet(
            onDismissRequest = { showServerSheet = false },
            sheetState = sheetState,
            containerColor = palette.surface,
            tonalElevation = 0.dp,
        ) {
            ProvideRabbitTextToolbar {
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
                            text = if (editing != null) stringResource(R.string.auth_edit_server) else stringResource(R.string.auth_add_new_server),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.text,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { showServerSheet = false },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = palette.muted, modifier = Modifier.size(18.dp))
                        }
                    }

                    val updatedToast = stringResource(R.string.auth_server_updated_toast)
                    val sheetInlineErr = state.uiError?.takeIf { it.presentation == RcErrorPresentation.INLINE }
                    if (sheetInlineErr != null) {
                        RcInlineError(
                            error = sheetInlineErr,
                            onDismiss = viewModel::clearError,
                        )
                    }
                    ServerFormContent(
                        serverAddress = state.serverAddress,
                        onAddressChange = viewModel::updateServerAddress,
                        serverName = state.serverName,
                        onNameChange = viewModel::updateServerName,
                        apiPort = state.apiPort,
                        onApiPortChange = viewModel::updateApiPort,
                        signalPort = state.signalPort,
                        onSignalPortChange = viewModel::updateSignalPort,
                        relayPort = state.relayPort,
                        onRelayPortChange = viewModel::updateRelayPort,
                        caPin = state.caPin,
                        onCaPinChange = viewModel::updateCaPin,
                        expandAdvanced = expandAdvanced,
                        onToggleExpandAdvanced = { expandAdvanced = !expandAdvanced },
                        busy = state.busy,
                        submitLabel = submitText,
                        onSubmit = {
                            viewModel.submitServerForm(
                                onMetadataSaved = {
                                    showServerSheet = false
                                    RabbitToast.success(updatedToast)
                                },
                            )
                        },
                    )
                }
            }
            RabbitToastModalOverlay()
        }
    }

    pendingDeleteServer?.let { srv ->
        RabbitDialog(
            title = stringResource(R.string.auth_remove_server_title),
            message = stringResource(R.string.auth_remove_server_msg, srv.displayName),
            onDismissRequest = { pendingDeleteServer = null },
            dismissLabel = stringResource(R.string.common_cancel),
            confirmLabel = stringResource(R.string.common_remove),
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
    apiPort: String,
    onApiPortChange: (String) -> Unit,
    signalPort: String,
    onSignalPortChange: (String) -> Unit,
    relayPort: String,
    onRelayPortChange: (String) -> Unit,
    caPin: String,
    onCaPinChange: (String) -> Unit,
    expandAdvanced: Boolean,
    onToggleExpandAdvanced: () -> Unit,
    busy: Boolean,
    submitLabel: String,
    submitHeight: Dp = 48.dp,
    onSubmit: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val showAdvanced = expandAdvanced ||
        caPin.isNotBlank() ||
        apiPort.isNotBlank() ||
        signalPort.isNotBlank() ||
        relayPort.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RabbitTextField(
            value = serverAddress,
            onValueChange = onAddressChange,
            label = stringResource(R.string.address_label),
            placeholder = stringResource(R.string.api_hint),
            leadingIcon = { Icon(Icons.Rounded.Dns, null, tint = palette.muted) },
            keyboardType = KeyboardType.Uri,
        )
        RabbitTextField(
            value = serverName,
            onValueChange = onNameChange,
            label = stringResource(R.string.auth_server_name_label),
            placeholder = stringResource(R.string.auth_server_name_hint),
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, null, tint = palette.muted) },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RabbitRadius.Sm))
                .clickable(onClick = onToggleExpandAdvanced)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.auth_advanced_settings),
                fontSize = 13.sp,
                color = palette.muted,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = palette.muted,
                modifier = Modifier.size(18.dp),
            )
        }

        if (showAdvanced) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RabbitTextField(
                    value = apiPort,
                    onValueChange = onApiPortChange,
                    label = stringResource(R.string.auth_api_port_label),
                    placeholder = ServerConfigModel.DEFAULT_API_PORT.toString(),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                RabbitTextField(
                    value = signalPort,
                    onValueChange = onSignalPortChange,
                    label = stringResource(R.string.auth_signal_port_label),
                    placeholder = ServerConfigModel.DEFAULT_SIGNAL_PORT.toString(),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                RabbitTextField(
                    value = relayPort,
                    onValueChange = onRelayPortChange,
                    label = stringResource(R.string.auth_relay_port_label),
                    placeholder = ServerConfigModel.DEFAULT_RELAY_PORT.toString(),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
            RabbitTextField(
                value = caPin,
                onValueChange = onCaPinChange,
                label = stringResource(R.string.auth_manual_pin_label),
                placeholder = stringResource(R.string.pin_hint),
                leadingIcon = { Icon(Icons.Rounded.Key, null, tint = palette.muted) },
                singleLine = false,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        RabbitPrimaryButton(
            label = submitLabel,
            busy = busy,
            height = submitHeight,
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
