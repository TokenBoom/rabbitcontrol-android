package io.rabbitcontrol.android.ui.screens.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.i18n.NativeErrorLocalizer
import io.rabbitcontrol.android.core.session.SessionStatus
import io.rabbitcontrol.android.ui.components.RabbitCard
import io.rabbitcontrol.android.ui.components.RabbitDialog
import io.rabbitcontrol.android.ui.components.RabbitEmptyState
import io.rabbitcontrol.android.ui.components.RabbitSegmentedControl
import io.rabbitcontrol.android.ui.components.RabbitTextField
import io.rabbitcontrol.android.ui.components.RabbitToast
import io.rabbitcontrol.android.ui.components.RabbitTopBar
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.RabbitSpacing
import org.koin.androidx.compose.koinViewModel

private enum class FileTransferTab(@androidx.annotation.StringRes val labelRes: Int) {
    Remote(R.string.ft_tab_remote),
    Queue(R.string.ft_tab_queue),
}

@Composable
fun FileTransferScreen(
    deviceName: String,
    onBack: () -> Unit,
    viewModel: FileTransferViewModel = koinViewModel(),
) {
    val palette = LocalRabbitPalette.current
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val currentPath by viewModel.currentPath.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val transfers by viewModel.transfers.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(FileTransferTab.Remote) }
    var showMkdirDialog by remember { mutableStateOf(false) }
    var newDirName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<io.rabbitcontrol.android.core.nativebridge.RemoteFileEntry?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<io.rabbitcontrol.android.core.nativebridge.RemoteFileEntry?>(null) }

    val pickFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadUris(uris)
            selectedTab = FileTransferTab.Queue
        }
    }

    LaunchedEffect(notice) {
        val current = notice
        if (current != null && current.message.isNotBlank()) {
            when {
                current.isError -> RabbitToast.error(current.message)
                current.isSuccess -> RabbitToast.success(current.message)
                else -> RabbitToast.info(current.message)
            }
            viewModel.clearNotice()
        }
    }

    Scaffold(
        containerColor = palette.canvas,
        floatingActionButton = {
            if (selectedTab == FileTransferTab.Remote) {
                FloatingActionButton(
                    onClick = { pickFilesLauncher.launch(arrayOf("*/*")) },
                    containerColor = palette.primary,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = RabbitElevation.Floating,
                        pressedElevation = 3.dp,
                        focusedElevation = RabbitElevation.Floating,
                        hoveredElevation = RabbitElevation.Floating,
                    ),
                ) {
                    Icon(Icons.Rounded.UploadFile, stringResource(R.string.ft_cd_upload))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            RabbitTopBar(
                title = stringResource(R.string.ft_title),
                subtitle = deviceName,
                onBack = onBack,
                actions = {
                    if (selectedTab == FileTransferTab.Remote) {
                        IconButton(onClick = { showMkdirDialog = true }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.CreateNewFolder, stringResource(R.string.ft_cd_mkdir), tint = palette.text)
                        }
                        IconButton(
                            onClick = { viewModel.listRemoteDir(currentPath.ifBlank { null }) },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, stringResource(R.string.common_refresh), tint = palette.text)
                        }
                    } else {
                        IconButton(onClick = viewModel::clearFinished, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.ft_cd_clear_done), tint = palette.text)
                        }
                    }
                },
            )

            Box(modifier = Modifier.padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Xs)) {
                val remoteTabLabel = stringResource(R.string.ft_tab_remote)
                val queueTabLabel = stringResource(R.string.ft_tab_queue)
                RabbitSegmentedControl(
                    options = FileTransferTab.entries,
                    selected = selectedTab,
                    labelProvider = {
                        when (it) {
                            FileTransferTab.Remote -> remoteTabLabel
                            FileTransferTab.Queue -> "$queueTabLabel (${transfers.size})"
                        }
                    },
                    onSelect = { selectedTab = it },
                )
            }

            when (val st = sessionState.status) {
                is SessionStatus.Connecting -> {
                    Text(
                        text = st.label,
                        fontSize = 12.sp,
                        color = palette.primaryText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = RabbitSpacing.Lg, vertical = 4.dp)
                            .clip(RoundedCornerShape(RabbitRadius.Sm))
                            .background(palette.primarySoft)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                is SessionStatus.Reconnecting -> {
                    Text(
                        text = st.reason.ifBlank { stringResource(R.string.session_reconnecting_generic) },
                        fontSize = 12.sp,
                        color = palette.warning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = RabbitSpacing.Lg, vertical = 4.dp)
                            .clip(RoundedCornerShape(RabbitRadius.Sm))
                            .background(palette.warningSoft)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                is SessionStatus.Error -> {
                    val errText = st.uiError?.let { it.message.ifBlank { it.title } }
                        ?: NativeErrorLocalizer.localize(st.message)
                    Text(
                        text = stringResource(R.string.ft_session_error, errText),
                        fontSize = 12.sp,
                        color = palette.danger,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = RabbitSpacing.Lg, vertical = 4.dp)
                            .clip(RoundedCornerShape(RabbitRadius.Sm))
                            .background(palette.dangerSoft)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                is SessionStatus.Ended -> {
                    val reasonText = st.uiError?.let { it.message.ifBlank { it.title } }
                        ?: NativeErrorLocalizer.localize(st.reason)
                    Text(
                        text = stringResource(R.string.ft_session_disconnected, reasonText),
                        fontSize = 12.sp,
                        color = palette.danger,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = RabbitSpacing.Lg, vertical = 4.dp)
                            .clip(RoundedCornerShape(RabbitRadius.Sm))
                            .background(palette.dangerSoft)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                else -> Unit
            }

            if (!sessionState.toolPermissions.allowFileTransfer) {
                Text(
                    text = stringResource(R.string.tool_permission_disabled_ft),
                    fontSize = 12.sp,
                    color = palette.warning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RabbitSpacing.Lg, vertical = 4.dp)
                        .clip(RoundedCornerShape(RabbitRadius.Sm))
                        .background(palette.warningSoft)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            if (selectedTab == FileTransferTab.Remote) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Sm)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(RabbitRadius.Md))
                        .background(palette.surface)
                        .border(1.dp, palette.line, RoundedCornerShape(RabbitRadius.Md))
                        .clickable { viewModel.navigateUp() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null, tint = palette.primaryText, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentPath.ifBlank { stringResource(R.string.ft_loading_home) },
                        fontSize = 13.sp,
                        color = palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ft_up_dir), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.primaryText)
                }
                if (loading) {
                    LinearProgressIndicator(
                        color = palette.primary,
                        trackColor = palette.line,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = RabbitSpacing.Lg,
                        end = RabbitSpacing.Lg,
                        top = RabbitSpacing.Xs,
                        bottom = 84.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (entries.isEmpty() && !loading) {
                        item {
                            RabbitEmptyState(
                                icon = Icons.Rounded.Folder,
                                title = stringResource(R.string.ft_empty_dir_title),
                                description = stringResource(R.string.ft_empty_dir_desc),
                            )
                        }
                    }
                    items(entries, key = { "${it.path}#${it.name}#${it.isDir}" }) { entry ->
                        RabbitCard(
                            onClick = {
                                if (entry.isDir) viewModel.listRemoteDir(entry.path)
                                else viewModel.downloadFile(entry)
                            },
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (entry.isDir) palette.primarySoft else palette.field),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = if (entry.isDir) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                                        contentDescription = null,
                                        tint = if (entry.isDir) palette.primaryText else palette.muted,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        entry.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!entry.isDir) {
                                        Text(formatBytes(entry.size), fontSize = 12.sp, color = palette.muted)
                                    }
                                }
                                if (!entry.isDir) {
                                    Icon(Icons.Rounded.Download, stringResource(R.string.ft_cd_download), tint = palette.primaryText, modifier = Modifier.size(20.dp))
                                }
                                var menuExpanded by remember(entry.path) { mutableStateOf(false) }
                                Box {
                                    IconButton(
                                        onClick = { menuExpanded = true },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.MoreVert,
                                            contentDescription = stringResource(R.string.ft_cd_more_actions),
                                            tint = palette.muted,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.ft_action_rename), color = palette.text) },
                                            onClick = {
                                                menuExpanded = false
                                                renameTarget = entry
                                                renameInput = entry.name
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.ft_action_delete), color = palette.danger) },
                                            onClick = {
                                                menuExpanded = false
                                                deleteTarget = entry
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(RabbitSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (transfers.isEmpty()) {
                        item {
                            RabbitEmptyState(
                                icon = Icons.Rounded.UploadFile,
                                title = stringResource(R.string.ft_empty_queue_title),
                                description = stringResource(R.string.ft_empty_queue_desc),
                            )
                        }
                    }
                    items(transfers, key = { it.id }) { item ->
                        RabbitCard(
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val dirLabel = if (item.direction == "upload") {
                                    stringResource(R.string.ft_dir_upload)
                                } else {
                                    stringResource(R.string.ft_dir_download)
                                }
                                Text(
                                    "$dirLabel · ${item.fileName}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = palette.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (item.status in setOf("active", "running", "pending")) {
                                    IconButton(
                                        onClick = { viewModel.cancelTransfer(item.id) },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(Icons.Rounded.Cancel, stringResource(R.string.common_cancel), tint = palette.danger, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { item.progress },
                                color = palette.primary,
                                trackColor = palette.line,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val statusStr = when (item.status) {
                                "done", "completed" -> stringResource(R.string.ft_status_done)
                                "failed" -> stringResource(
                                    R.string.ft_status_failed,
                                    item.error?.let(NativeErrorLocalizer::localize).orEmpty(),
                                )
                                "cancelled" -> stringResource(R.string.ft_status_cancelled)
                                else -> stringResource(
                                    R.string.ft_status_running,
                                    formatBytes(item.transferredBytes),
                                    formatBytes(item.totalBytes),
                                )
                            }
                            Text(
                                statusStr,
                                fontSize = 12.sp,
                                color = if (item.status == "failed") palette.danger else palette.muted,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMkdirDialog) {
        RabbitDialog(
            title = stringResource(R.string.ft_mkdir_title),
            onDismissRequest = { showMkdirDialog = false },
            dismissLabel = stringResource(R.string.common_cancel),
            confirmLabel = stringResource(R.string.ft_mkdir_create),
            confirmEnabled = newDirName.isNotBlank(),
            onConfirm = {
                viewModel.createRemoteDir(newDirName)
                newDirName = ""
                showMkdirDialog = false
            },
        ) {
            RabbitTextField(
                value = newDirName,
                onValueChange = { newDirName = it },
                placeholder = stringResource(R.string.ft_mkdir_hint),
            )
        }
    }

    renameTarget?.let { target ->
        RabbitDialog(
            title = stringResource(R.string.ft_rename_title),
            onDismissRequest = { renameTarget = null },
            dismissLabel = stringResource(R.string.common_cancel),
            confirmLabel = stringResource(R.string.ft_rename_confirm),
            confirmEnabled = renameInput.trim().isNotEmpty() &&
                !renameInput.contains('/') &&
                !renameInput.contains('\\'),
            onConfirm = {
                viewModel.renameRemote(target, renameInput)
                renameTarget = null
            },
        ) {
            RabbitTextField(
                value = renameInput,
                onValueChange = { renameInput = it },
                placeholder = stringResource(R.string.ft_rename_hint),
            )
        }
    }

    deleteTarget?.let { target ->
        RabbitDialog(
            title = stringResource(R.string.ft_delete_title),
            onDismissRequest = { deleteTarget = null },
            dismissLabel = stringResource(R.string.common_cancel),
            confirmLabel = stringResource(R.string.ft_action_delete),
            onConfirm = {
                viewModel.deleteRemote(target)
                deleteTarget = null
            },
        ) {
            Text(
                text = stringResource(R.string.ft_delete_confirm_msg, target.name),
                fontSize = 14.sp,
                color = palette.text,
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}
