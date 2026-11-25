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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UploadFile
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

private enum class FileTransferTab(val label: String) {
    Remote("远端文件"),
    Queue("传输队列"),
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

    val pickFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadUris(uris)
            selectedTab = FileTransferTab.Queue
        }
    }

    LaunchedEffect(notice) {
        val msg = notice
        if (!msg.isNullOrBlank()) {
            when {
                msg.contains("失败") || msg.contains("错误") || msg.contains("异常") || msg.contains("无法") ->
                    RabbitToast.error(msg)
                msg.contains("已") || msg.contains("成功") || msg.contains("完成") ->
                    RabbitToast.success(msg)
                else -> RabbitToast.info(msg)
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
                    Icon(Icons.Rounded.UploadFile, "上传文件")
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
                title = "文件传输",
                subtitle = deviceName,
                onBack = onBack,
                actions = {
                    if (selectedTab == FileTransferTab.Remote) {
                        IconButton(onClick = { showMkdirDialog = true }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.CreateNewFolder, "新建文件夹", tint = palette.text)
                        }
                        IconButton(
                            onClick = { viewModel.listRemoteDir(currentPath.ifBlank { null }) },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, "刷新", tint = palette.text)
                        }
                    } else {
                        IconButton(onClick = viewModel::clearFinished, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.DeleteSweep, "清除已完成", tint = palette.text)
                        }
                    }
                },
            )

            Box(modifier = Modifier.padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Xs)) {
                RabbitSegmentedControl(
                    options = FileTransferTab.entries,
                    selected = selectedTab,
                    labelProvider = {
                        if (it == FileTransferTab.Queue) "${it.label} (${transfers.size})" else it.label
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
                is SessionStatus.Error -> {
                    Text(
                        text = "会话异常: ${st.message}",
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
                    Text(
                        text = "会话已断开: ${st.reason}",
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
                        text = currentPath.ifBlank { "加载远端主目录…" },
                        fontSize = 13.sp,
                        color = palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("上一级", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.primaryText)
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
                                title = "当前目录为空",
                                description = "点击右下角上传按钮将手机文件发送到此目录。",
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
                                    Icon(Icons.Rounded.Download, "下载", tint = palette.primaryText, modifier = Modifier.size(20.dp))
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
                                title = "暂无传输任务",
                                description = "上传或下载文件时，实时进度会显示在这里。",
                            )
                        }
                    }
                    items(transfers, key = { it.id }) { item ->
                        RabbitCard(
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${if (item.direction == "upload") "上传" else "下载"} · ${item.fileName}",
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
                                        Icon(Icons.Rounded.Cancel, "取消", tint = palette.danger, modifier = Modifier.size(18.dp))
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
                                "done", "completed" -> "已完成"
                                "failed" -> "失败: ${item.error ?: ""}"
                                "cancelled" -> "已取消"
                                else -> "进行中 · ${formatBytes(item.transferredBytes)} / ${formatBytes(item.totalBytes)}"
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
            title = "新建远端文件夹",
            onDismissRequest = { showMkdirDialog = false },
            dismissLabel = "取消",
            confirmLabel = "创建",
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
                placeholder = "输入文件夹名称",
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
