package io.rabbitcontrol.android.ui.screens.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.core.session.SessionStatus
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import org.koin.androidx.compose.koinViewModel

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

    var selectedTab by remember { mutableIntStateOf(0) }
    var showMkdirDialog by remember { mutableStateOf(false) }
    var newDirName by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    val pickFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadUris(uris)
            selectedTab = 1
        }
    }

    LaunchedEffect(notice) {
        val msg = notice
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearNotice()
        }
    }

    Scaffold(
        containerColor = palette.canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { pickFilesLauncher.launch(arrayOf("*/*")) },
                    containerColor = palette.primary,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Rounded.UploadFile, "上传文件")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .systemBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = palette.text)
                }
                Text("文件传输 · $deviceName", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = palette.text, modifier = Modifier.weight(1f))
                if (selectedTab == 0) {
                    IconButton(onClick = { showMkdirDialog = true }) {
                        Icon(Icons.Rounded.CreateNewFolder, "新建文件夹", tint = palette.text)
                    }
                    IconButton(onClick = { viewModel.listRemoteDir(currentPath.ifBlank { null }) }) {
                        Icon(Icons.Rounded.Refresh, "刷新", tint = palette.text)
                    }
                } else {
                    IconButton(onClick = viewModel::clearFinished) {
                        Icon(Icons.Rounded.DeleteSweep, "清除已完成", tint = palette.text)
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab, containerColor = palette.surface) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("远端文件") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("传输队列 (${transfers.size})") })
            }

            when (val st = sessionState.status) {
                is SessionStatus.Connecting -> {
                    Text(
                        text = st.label,
                        fontSize = 12.sp,
                        color = palette.primary,
                        modifier = Modifier.fillMaxWidth().background(palette.primarySoft).padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                is SessionStatus.Error -> {
                    Text(
                        text = "会话异常: ${st.message}",
                        fontSize = 12.sp,
                        color = palette.danger,
                        modifier = Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                is SessionStatus.Ended -> {
                    Text(
                        text = "会话已断开: ${st.reason}",
                        fontSize = 12.sp,
                        color = palette.danger,
                        modifier = Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                else -> Unit
            }

            if (selectedTab == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.surface)
                        .clickable { viewModel.navigateUp() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null, tint = palette.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentPath.ifBlank { "加载远端主目录…" },
                        fontSize = 13.sp,
                        color = palette.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text("上一级", fontSize = 12.sp, color = palette.primary)
                }
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries, key = { "${it.path}#${it.name}#${it.isDir}" }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.surface)
                                .border(1.dp, palette.line, RoundedCornerShape(14.dp))
                                .clickable {
                                    if (entry.isDir) viewModel.listRemoteDir(entry.path)
                                    else viewModel.downloadFile(entry)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (entry.isDir) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                                contentDescription = null,
                                tint = if (entry.isDir) palette.primary else palette.muted,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text)
                                if (!entry.isDir) {
                                    Text(formatBytes(entry.size), fontSize = 12.sp, color = palette.muted)
                                }
                            }
                            if (!entry.isDir) {
                                Icon(Icons.Rounded.Download, "下载", tint = palette.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (transfers.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("暂无传输任务", color = palette.muted)
                            }
                        }
                    }
                    items(transfers, key = { it.id }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.surface)
                                .border(1.dp, palette.line, RoundedCornerShape(14.dp))
                                .padding(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${if (item.direction == "upload") "上传" else "下载"} · ${item.fileName}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = palette.text,
                                    modifier = Modifier.weight(1f),
                                )
                                if (item.status in setOf("active", "running", "pending")) {
                                    IconButton(onClick = { viewModel.cancelTransfer(item.id) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Rounded.Cancel, "取消", tint = palette.danger, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(4.dp))
                            val statusStr = when (item.status) {
                                "done", "completed" -> "已完成"
                                "failed" -> "失败: ${item.error ?: ""}"
                                "cancelled" -> "已取消"
                                else -> "进行中 · ${formatBytes(item.transferredBytes)} / ${formatBytes(item.totalBytes)}"
                            }
                            Text(statusStr, fontSize = 12.sp, color = if (item.status == "failed") palette.danger else palette.muted)
                        }
                    }
                }
            }
        }
    }

    if (showMkdirDialog) {
        AlertDialog(
            onDismissRequest = { showMkdirDialog = false },
            title = { Text("新建远端文件夹") },
            text = {
                OutlinedTextField(
                    value = newDirName,
                    onValueChange = { newDirName = it },
                    placeholder = { Text("输入文件夹名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createRemoteDir(newDirName)
                    newDirName = ""
                    showMkdirDialog = false
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showMkdirDialog = false }) { Text("取消") }
            },
        )
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
