package io.rabbitcontrol.android.ui.screens.detail

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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.data.DeviceToolKind
import io.rabbitcontrol.android.core.data.rememberWallpaperImage
import io.rabbitcontrol.android.core.nativebridge.RemoteDevice
import io.rabbitcontrol.android.core.session.SessionStatus
import io.rabbitcontrol.android.ui.components.RabbitAdaptiveSheet
import io.rabbitcontrol.android.ui.components.RabbitCard
import io.rabbitcontrol.android.ui.components.RabbitDialog
import io.rabbitcontrol.android.ui.components.RabbitPrimaryButton
import io.rabbitcontrol.android.ui.components.RabbitSecondaryButton
import io.rabbitcontrol.android.ui.components.RabbitSectionHeader
import io.rabbitcontrol.android.ui.components.RabbitStatusPill
import io.rabbitcontrol.android.ui.components.RabbitTextField
import io.rabbitcontrol.android.ui.components.RabbitToast
import io.rabbitcontrol.android.ui.components.RabbitTopBar
import io.rabbitcontrol.android.ui.components.RcErrorHub
import io.rabbitcontrol.android.ui.components.StatusTone
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.RabbitSpacing
import org.koin.androidx.compose.koinViewModel

private enum class DetailSheetMode {
    ReorderTools,
    MoreTools,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    device: RemoteDevice,
    onBack: () -> Unit,
    onEnterDesktop: (watchOnly: Boolean) -> Unit,
    onOpenTool: (DeviceToolKind) -> Unit,
    embedded: Boolean = false,
    viewModel: DeviceDetailViewModel = koinViewModel(),
) {
    val palette = LocalRabbitPalette.current
    val clipboard = LocalClipboardManager.current

    val toolOrder by viewModel.toolOrder.collectAsStateWithLifecycle()
    val wallpaperVersion by viewModel.wallpaperVersion.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val connecting by viewModel.connecting.collectAsStateWithLifecycle()
    val removing by viewModel.removing.collectAsStateWithLifecycle()
    val wolSending by viewModel.wolSending.collectAsStateWithLifecycle()
    val connectError by viewModel.connectError.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteTargets.collectAsStateWithLifecycle()
    val liveDevices by viewModel.devices.collectAsStateWithLifecycle()
    val savedTerminalPasswordIds by viewModel.savedTerminalPasswordIds.collectAsStateWithLifecycle()
    val liveDevice = remember(device, liveDevices) {
        liveDevices.firstOrNull { it.id == device.id } ?: device
    }

    val wallpaperBitmap = rememberWallpaperImage(viewModel.wallpaperCache, liveDevice.id, wallpaperVersion, 960)
    val isCurrentStreaming = activeSession.targetId == liveDevice.id && activeSession.status is SessionStatus.Streaming
    val isCurrentToolsOnly = isCurrentStreaming && (activeSession.toolsOnly || activeSession.mediaPaused)
    val isCurrentConnected = activeSession.targetId == liveDevice.id && activeSession.negotiated != null && !isCurrentToolsOnly
    val isFav = liveDevice.id in favorites

    var sheetMode by remember(liveDevice.id) { mutableStateOf<DetailSheetMode?>(null) }
    var powerConfirmKind by remember(liveDevice.id) { mutableStateOf<String?>(null) }
    var showRemoveConfirm by remember(liveDevice.id) { mutableStateOf(false) }
    var showRenameDialog by remember(liveDevice.id) { mutableStateOf(false) }
    val osLabel = remember(liveDevice.os) { formatDetailOs(liveDevice.os) }

    fun triggerTool(kind: DeviceToolKind) {
        when (kind) {
            DeviceToolKind.WatchMode -> {
                viewModel.startSessionAndNavigate(liveDevice.id, liveDevice.name, liveDevice.os, watchOnly = true, toolsOnly = false) {
                    onEnterDesktop(true)
                }
            }
            DeviceToolKind.FileTransfer, DeviceToolKind.Terminal, DeviceToolKind.PortMap -> {
                viewModel.startSessionAndNavigate(liveDevice.id, liveDevice.name, liveDevice.os, watchOnly = false, toolsOnly = true) {
                    onOpenTool(kind)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .then(if (embedded) Modifier else Modifier.systemBarsPadding()),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RabbitTopBar(
                title = liveDevice.name,
                subtitle = osLabel,
                onBack = if (embedded) null else onBack,
                actions = {
                    if (liveDevice.id.isNotBlank() && !liveDevice.id.contains(':')) {
                        IconButton(
                            onClick = { showRenameDialog = true },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.device_rename_title),
                                tint = palette.muted,
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.toggleFavorite(liveDevice.id) },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = stringResource(R.string.home_cd_favorite),
                            tint = if (isFav) palette.primaryText else palette.muted,
                        )
                    }
                },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(RabbitSpacing.Lg),
            ) {
                // Compact Hero Card (0.dp shadow, hairline border)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RabbitRadius.Xl),
                    color = palette.heroDarkBg,
                    tonalElevation = 0.dp,
                    shadowElevation = RabbitElevation.Card,
                    border = BorderStroke(1.dp, palette.line),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(196.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(palette.heroCardBg, palette.heroDarkBg),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (wallpaperBitmap != null) {
                            Image(
                                bitmap = wallpaperBitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xA60A0A0A)),
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x2EFFFFFF))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.LaptopMac, null, tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (isCurrentConnected) {
                                    stringResource(R.string.detail_session_connected)
                                } else {
                                    viewModel.lastConnectedLabel(liveDevice.id)
                                },
                                fontSize = 13.sp,
                                color = Color(0xFFD4D4D4),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            val canWol = !liveDevice.online && !liveDevice.disabled && liveDevice.wolCapable
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.startSessionAndNavigate(liveDevice.id, liveDevice.name, liveDevice.os, watchOnly = false) {
                                            onEnterDesktop(false)
                                        }
                                    },
                                    enabled = !connecting,
                                    shape = RoundedCornerShape(RabbitRadius.Pill),
                                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.primary,
                                        contentColor = Color.White,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 22.dp),
                                    modifier = Modifier.height(48.dp),
                                ) {
                                    if (connecting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.detail_connecting), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    } else {
                                        Text(
                                            stringResource(
                                                if (isCurrentConnected) R.string.home_btn_resume_control else R.string.detail_enter_desktop,
                                            ),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(18.dp))
                                    }
                                }
                                if (canWol) {
                                    Button(
                                        onClick = {
                                            viewModel.sendWakeOnLan(liveDevice.id) { msg ->
                                                RabbitToast.success(msg)
                                            }
                                        },
                                        enabled = !wolSending,
                                        shape = RoundedCornerShape(RabbitRadius.Pill),
                                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF1F2937),
                                            contentColor = Color.White,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        modifier = Modifier.height(48.dp),
                                    ) {
                                        if (wolSending) {
                                            CircularProgressIndicator(modifier = Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Rounded.Power, null, modifier = Modifier.size(17.dp))
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            stringResource(if (wolSending) R.string.wol_sending else R.string.wol_button),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isCurrentToolsOnly) {
                    RabbitCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Terminal, null, tint = palette.primaryText, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.detail_tools_session_active),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.text,
                                )
                                Text(
                                    stringResource(R.string.detail_tools_session_sub),
                                    fontSize = 11.sp,
                                    color = palette.muted,
                                )
                            }
                            TextButton(
                                onClick = {
                                    viewModel.upgradeActiveSessionToDesktop {
                                        onEnterDesktop(false)
                                    }
                                },
                            ) {
                                Text(
                                    stringResource(R.string.detail_enter_desktop),
                                    color = palette.primaryText,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }

                // 2x2 Quick Tools Grid
                RabbitSectionHeader(
                    title = stringResource(R.string.detail_quick_tools),
                    trailing = {
                        Text(
                            text = stringResource(R.string.detail_custom_order),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.primaryText,
                            modifier = Modifier
                                .clip(RoundedCornerShape(RabbitRadius.Sm))
                                .clickable { sheetMode = DetailSheetMode.ReorderTools }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    },
                )

                val rows = toolOrder.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { pair ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            pair.forEach { tool ->
                                RabbitCard(
                                    onClick = if (!connecting) {
                                        { triggerTool(tool) }
                                    } else {
                                        null
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(palette.primarySoft),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(toolIcon(tool), null, tint = palette.primaryText, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = tool.label,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = palette.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Device Metadata & Power Entry Card
                RabbitCard(modifier = Modifier.fillMaxWidth()) {
                    val (statusLabel, statusTone) = when {
                        liveDevice.disabled -> stringResource(R.string.home_status_disabled) to StatusTone.Muted
                        liveDevice.busy -> stringResource(R.string.home_status_busy) to StatusTone.Primary
                        liveDevice.online -> stringResource(R.string.home_status_online) to StatusTone.Success
                        else -> stringResource(R.string.home_status_offline) to StatusTone.Muted
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Computer, null, tint = palette.muted, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.detail_device_status), fontSize = 14.sp, color = palette.muted, modifier = Modifier.weight(1f))
                        RabbitStatusPill(label = statusLabel, tone = statusTone)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = palette.line, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.LaptopMac, null, tint = palette.muted, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.detail_os_info), fontSize = 14.sp, color = palette.muted, modifier = Modifier.weight(1f))
                        Text(osLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text)
                    }

                    if (liveDevice.id.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = palette.line, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        val formatted = if (liveDevice.id.contains(':')) liveDevice.id else liveDevice.id.chunked(3).joinToString(" ")
                        val copiedIdMsg = stringResource(R.string.detail_copied_id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp)
                                .clip(RoundedCornerShape(RabbitRadius.Sm))
                                .clickable {
                                    clipboard.setText(AnnotatedString(liveDevice.id))
                                    RabbitToast.success(copiedIdMsg)
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Icon(Icons.Rounded.Key, null, tint = palette.muted, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.detail_device_id), fontSize = 14.sp, color = palette.muted, modifier = Modifier.weight(1f))
                            Text(formatted, fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = palette.text)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Rounded.ContentCopy, null, tint = palette.primaryText, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = palette.line, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(RabbitRadius.Sm))
                            .clickable { sheetMode = DetailSheetMode.MoreTools }
                            .padding(vertical = 4.dp),
                    ) {
                        Icon(Icons.Rounded.Tune, null, tint = palette.primaryText, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.detail_more_and_power), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.muted)
                    }

                    if (liveDevice.id in savedTerminalPasswordIds) {
                        val clearedMsg = stringResource(R.string.detail_cleared_term_pw)
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = palette.line, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp)
                                .clip(RoundedCornerShape(RabbitRadius.Sm))
                                .clickable {
                                    viewModel.clearSavedTerminalPassword(liveDevice.id)
                                    RabbitToast.info(clearedMsg)
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, null, tint = palette.muted, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.detail_clear_term_pw),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.text,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.muted)
                        }
                    }

                    if (liveDevice.id.isNotBlank() && !liveDevice.id.contains(':')) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = palette.line, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp)
                                .clip(RoundedCornerShape(RabbitRadius.Sm))
                                .alpha(if (removing) 0.5f else 1f)
                                .clickable(enabled = !removing) { showRemoveConfirm = true }
                                .padding(vertical = 4.dp),
                        ) {
                            Icon(Icons.Rounded.LinkOff, null, tint = palette.danger, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(if (removing) R.string.detail_unbinding else R.string.detail_unbind),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.danger,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.muted)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    LaunchedEffect(connectError) {
        val err = connectError
        if (err != null) {
            RcErrorHub.present(err)
            viewModel.clearError()
        }
    }

    val activeSheet = sheetMode
    if (activeSheet != null) {
        var draftOrder by remember(activeSheet, toolOrder) { mutableStateOf(toolOrder) }

        RabbitAdaptiveSheet(
            onDismissRequest = { sheetMode = null },
            maxDialogWidth = 480.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = RabbitSpacing.Xl)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (activeSheet == DetailSheetMode.ReorderTools) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.detail_order_title),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.text,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.detail_order_sub),
                                fontSize = 12.sp,
                                color = palette.muted,
                            )
                        }
                        IconButton(
                            onClick = { sheetMode = null },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = palette.muted, modifier = Modifier.size(18.dp))
                        }
                    }

                    draftOrder.forEachIndexed { idx, tool ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .clip(RoundedCornerShape(RabbitRadius.Md))
                                .background(palette.field)
                                .border(1.dp, palette.line, RoundedCornerShape(RabbitRadius.Md))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(palette.surface)
                                    .border(1.dp, palette.line, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.muted,
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.primarySoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(toolIcon(tool), null, tint = palette.primaryText, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                tool.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.text,
                                modifier = Modifier.weight(1f),
                            )
                            ReorderArrowButton(
                                icon = Icons.Rounded.ArrowUpward,
                                contentDescription = stringResource(R.string.detail_move_up),
                                enabled = idx > 0,
                                onClick = {
                                    if (idx > 0) {
                                        val m = draftOrder.toMutableList()
                                        m[idx] = m[idx - 1].also { m[idx - 1] = m[idx] }
                                        draftOrder = m
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            ReorderArrowButton(
                                icon = Icons.Rounded.ArrowDownward,
                                contentDescription = stringResource(R.string.detail_move_down),
                                enabled = idx < draftOrder.lastIndex,
                                onClick = {
                                    if (idx < draftOrder.lastIndex) {
                                        val m = draftOrder.toMutableList()
                                        m[idx] = m[idx + 1].also { m[idx + 1] = m[idx] }
                                        draftOrder = m
                                    }
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RabbitSecondaryButton(
                            label = stringResource(R.string.common_cancel),
                            onClick = { sheetMode = null },
                            modifier = Modifier.weight(1f),
                        )
                        RabbitPrimaryButton(
                            label = stringResource(R.string.detail_save_order),
                            onClick = {
                                viewModel.saveToolOrder(draftOrder)
                                sheetMode = null
                            },
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.detail_more_and_power),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.text,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { sheetMode = DetailSheetMode.ReorderTools }) {
                            Text(stringResource(R.string.detail_adjust_order), color = palette.primaryText)
                        }
                        IconButton(
                            onClick = { sheetMode = null },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = palette.muted, modifier = Modifier.size(18.dp))
                        }
                    }

                    toolOrder.forEach { tool ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .clip(RoundedCornerShape(RabbitRadius.Md))
                                .background(palette.field)
                                .border(1.dp, palette.line, RoundedCornerShape(RabbitRadius.Md))
                                .clickable {
                                    sheetMode = null
                                    triggerTool(tool)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.primarySoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(toolIcon(tool), null, tint = palette.primaryText, modifier = Modifier.size(17.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(tool.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.muted, modifier = Modifier.size(18.dp))
                        }
                    }

                    HorizontalDivider(color = palette.line, thickness = 1.dp)
                    Text(stringResource(R.string.detail_remote_power), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        PowerActionChip(
                            icon = Icons.Rounded.RestartAlt,
                            label = stringResource(R.string.detail_reboot),
                            modifier = Modifier.weight(1f),
                        ) {
                            sheetMode = null
                            powerConfirmKind = "reboot"
                        }
                        PowerActionChip(
                            icon = Icons.Rounded.PowerSettingsNew,
                            label = stringResource(R.string.detail_shutdown),
                            modifier = Modifier.weight(1f),
                        ) {
                            sheetMode = null
                            powerConfirmKind = "shutdown"
                        }
                    }
                }
            }
        }
    }

    if (powerConfirmKind != null) {
        val isReboot = powerConfirmKind == "reboot"
        val verb = stringResource(if (isReboot) R.string.detail_power_reboot_verb else R.string.detail_power_shutdown_verb)
        val unsupportedMsg = stringResource(R.string.detail_power_unsupported, verb)
        RabbitDialog(
            title = stringResource(R.string.detail_power_confirm_title, verb),
            message = stringResource(R.string.detail_power_confirm_msg, verb, liveDevice.name),
            onDismissRequest = { powerConfirmKind = null },
            dismissLabel = stringResource(R.string.common_cancel),
            confirmLabel = stringResource(R.string.detail_power_confirm_btn, verb),
            destructive = true,
            onConfirm = {
                powerConfirmKind = null
                RabbitToast.info(unsupportedMsg)
            },
        )
    }

    if (showRemoveConfirm) {
        val removedMsg = stringResource(R.string.detail_unbind_success)
        RabbitDialog(
            title = stringResource(R.string.detail_unbind_title),
            message = stringResource(R.string.detail_unbind_msg, liveDevice.name),
            onDismissRequest = { showRemoveConfirm = false },
            dismissLabel = stringResource(R.string.common_cancel),
            confirmLabel = stringResource(R.string.detail_unbind_confirm),
            destructive = true,
            onConfirm = {
                showRemoveConfirm = false
                viewModel.removeDevice(liveDevice.id) {
                    RabbitToast.success(removedMsg)
                    onBack()
                }
            },
        )
    }

    if (showRenameDialog) {
        var aliasDraft by remember(liveDevice.name) { mutableStateOf(liveDevice.name) }
        val renamedMsg = stringResource(R.string.device_rename_success)
        RabbitDialog(
            title = stringResource(R.string.device_rename_title),
            onDismissRequest = { showRenameDialog = false },
            dismissLabel = stringResource(R.string.common_cancel),
            confirmLabel = stringResource(R.string.common_save),
            confirmEnabled = aliasDraft.trim().isNotEmpty(),
            onConfirm = {
                showRenameDialog = false
                viewModel.renameDevice(liveDevice.id, aliasDraft.trim()) { ok, err ->
                    if (ok) {
                        RabbitToast.success(renamedMsg)
                    } else if (!err.isNullOrBlank()) {
                        RabbitToast.error(err)
                    }
                }
            },
        ) {
            RabbitTextField(
                value = aliasDraft,
                onValueChange = { aliasDraft = it },
                placeholder = stringResource(R.string.device_rename_hint),
            )
        }
    }
}

@Composable
private fun PowerActionChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(RabbitRadius.Md))
            .background(palette.dangerSoft)
            .border(1.dp, palette.danger.copy(alpha = 0.25f), RoundedCornerShape(RabbitRadius.Md))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = palette.danger, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.danger)
    }
}

@Composable
private fun ReorderArrowButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surface)
            .border(1.dp, palette.line, RoundedCornerShape(8.dp))
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = palette.text,
            modifier = Modifier.size(17.dp),
        )
    }
}

private fun formatDetailOs(raw: String): String {
    val normalized = raw.trim().lowercase()
    return when {
        normalized.isEmpty() -> io.rabbitcontrol.android.core.i18n.AppStrings.get(R.string.home_device_default_name, "桌面设备")
        normalized.contains("mac") || normalized.contains("darwin") -> "macOS"
        normalized.contains("win") -> "Windows"
        normalized.contains("linux") -> "Linux"
        normalized.contains("android") -> "Android"
        normalized.contains("ios") -> "iOS"
        else -> raw.trim()
    }
}

private fun toolIcon(kind: DeviceToolKind): ImageVector = when (kind) {
    DeviceToolKind.FileTransfer -> Icons.Rounded.FolderOpen
    DeviceToolKind.WatchMode -> Icons.Rounded.Visibility
    DeviceToolKind.Terminal -> Icons.Rounded.Terminal
    DeviceToolKind.PortMap -> Icons.Rounded.Cable
}
