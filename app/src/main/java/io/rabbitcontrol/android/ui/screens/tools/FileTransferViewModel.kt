package io.rabbitcontrol.android.ui.screens.tools

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.i18n.NativeErrorLocalizer
import io.rabbitcontrol.android.core.nativebridge.FileTransferItem
import io.rabbitcontrol.android.core.nativebridge.RemoteFileEntry
import io.rabbitcontrol.android.core.nativebridge.toFileTransferItem
import io.rabbitcontrol.android.core.session.ActiveSessionState
import io.rabbitcontrol.android.core.session.FileToolEvent
import io.rabbitcontrol.android.core.session.SessionManager
import io.rabbitcontrol.android.core.session.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class FileTransferViewModel(
    private val context: Context,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val appDownloadDir: File by lazy {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        File(base, "RabbitControl").apply { mkdirs() }
    }
    private val stagingDir: File by lazy {
        File(context.cacheDir, "upload_staging").apply { mkdirs() }
    }
    private val exportedDownloadIds = mutableSetOf<Long>()
    private var loadedInitialForSession = false

    val sessionState: StateFlow<ActiveSessionState> = sessionManager.state

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _entries = MutableStateFlow<List<RemoteFileEntry>>(emptyList())
    val entries: StateFlow<List<RemoteFileEntry>> = _entries.asStateFlow()

    private val _transfers = MutableStateFlow<List<FileTransferItem>>(emptyList())
    val transfers: StateFlow<List<FileTransferItem>> = _transfers.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    data class Notice(
        val message: String,
        val isError: Boolean = false,
        val isSuccess: Boolean = false,
    )

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.fileEvents.collect { ev -> handleEvent(ev) }
        }
        viewModelScope.launch {
            sessionManager.state.collect { st ->
                if (st.status is SessionStatus.Streaming) {
                    if (!loadedInitialForSession && _currentPath.value.isEmpty()) {
                        loadedInitialForSession = true
                        listRemoteDir(null)
                        refreshTransfers()
                    }
                } else if (st.status is SessionStatus.Idle || st.status is SessionStatus.Ended || st.status is SessionStatus.Error) {
                    loadedInitialForSession = false
                }
            }
        }
        if (sessionManager.state.value.status is SessionStatus.Streaming) {
            loadedInitialForSession = true
            listRemoteDir(null)
            refreshTransfers()
        }
    }

    fun listRemoteDir(path: String?) {
        _loading.value = true
        viewModelScope.launch {
            val req = JSONObject().put("action", "file_list_remote")
            if (!path.isNullOrBlank()) req.put("path", path)
            sessionManager.callTool(req).onFailure {
                _loading.value = false
                it.message?.let { m -> _notice.value = Notice(NativeErrorLocalizer.localize(m), isError = true) }
            }
        }
    }

    fun navigateUp() {
        val cur = _currentPath.value.trimEnd('/', '\\')
        if (cur.isEmpty() || cur == "/") return
        val slash = maxOf(cur.lastIndexOf('/'), cur.lastIndexOf('\\'))
        val parent = if (slash <= 0) "/" else cur.substring(0, slash)
        listRemoteDir(parent)
    }

    fun createRemoteDir(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        if (_currentPath.value.isBlank()) {
            _notice.value = Notice(AppStrings.get(R.string.ft_err_wait_dir, "请先等待远端目录加载完成"), isError = true)
            return
        }
        viewModelScope.launch {
            sessionManager.callTool(
                JSONObject()
                    .put("action", "file_mkdir_remote")
                    .put("parent", _currentPath.value)
                    .put("name", clean),
            ).onFailure {
                it.message?.let { m -> _notice.value = Notice(NativeErrorLocalizer.localize(m), isError = true) }
            }
        }
    }

    fun downloadFile(entry: RemoteFileEntry) {
        if (entry.isDir) return
        viewModelScope.launch {
            val localTargetDir = appDownloadDir.absolutePath
            sessionManager.callTool(
                JSONObject()
                    .put("action", "file_download")
                    .put("remotePath", entry.path)
                    .put("localDir", localTargetDir),
            ).onSuccess {
                _notice.value = Notice(
                    AppStrings.get(R.string.ft_notice_queued_dl, "已加入下载队列: ${entry.name}", entry.name),
                    isSuccess = true,
                )
                refreshTransfers()
            }.onFailure {
                it.message?.let { m -> _notice.value = Notice(NativeErrorLocalizer.localize(m), isError = true) }
            }
        }
    }

    fun uploadUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val remoteDir = _currentPath.value
        if (remoteDir.isBlank()) {
            _notice.value = Notice(AppStrings.get(R.string.ft_err_wait_dir, "请先等待远端目录加载完成"), isError = true)
            return
        }
        viewModelScope.launch {
            var count = 0
            for (uri in uris) {
                val staged = withContext(Dispatchers.IO) { copyUriToStaging(uri) }
                if (staged == null) {
                    _notice.value = Notice(AppStrings.get(R.string.ft_err_read_file, "无法读取所选文件"), isError = true)
                } else {
                    sessionManager.callTool(
                        JSONObject()
                            .put("action", "file_upload")
                            .put("localPath", staged.absolutePath)
                            .put("remoteDir", remoteDir),
                    ).onSuccess { count++ }
                        .onFailure {
                            it.message?.let { m -> _notice.value = Notice(NativeErrorLocalizer.localize(m), isError = true) }
                        }
                }
            }
            if (count > 0) {
                _notice.value = Notice(
                    AppStrings.get(R.string.ft_notice_queued_ul, "已加入 $count 个上传任务", count),
                    isSuccess = true,
                )
                refreshTransfers()
            }
        }
    }

    fun cancelTransfer(id: Long) {
        viewModelScope.launch {
            sessionManager.callTool(JSONObject().put("action", "file_cancel").put("id", id))
            refreshTransfers()
        }
    }

    fun clearFinished() {
        viewModelScope.launch {
            sessionManager.callTool(JSONObject().put("action", "file_clear_finished"))
            refreshTransfers()
        }
    }

    fun refreshTransfers() {
        viewModelScope.launch {
            sessionManager.callTool(JSONObject().put("action", "file_list_transfers"))
                .onSuccess { obj ->
                    val arr = obj.optJSONArray("transfers")
                    if (arr != null) {
                        _transfers.value = List(arr.length()) { arr.getJSONObject(it).toFileTransferItem() }
                    }
                }
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    private fun handleEvent(ev: FileToolEvent) {
        when (ev) {
            is FileToolEvent.RemoteDir -> {
                _loading.value = false
                if (!ev.error.isNullOrBlank()) {
                    _notice.value = Notice(NativeErrorLocalizer.localize(ev.error), isError = true)
                } else {
                    _currentPath.value = ev.path
                    _entries.value = ev.entries.sortedWith(
                        compareByDescending<RemoteFileEntry> { it.isDir }.thenBy { it.name.lowercase() },
                    )
                }
            }
            is FileToolEvent.MkdirResult -> {
                if (ev.ok) listRemoteDir(_currentPath.value)
                else {
                    _notice.value = Notice(
                        ev.error?.let(NativeErrorLocalizer::localize)
                            ?: AppStrings.get(R.string.ft_err_mkdir_failed, "创建目录失败"),
                        isError = true,
                    )
                }
            }
            is FileToolEvent.TransferUpdated -> {
                val item = ev.item
                val current = _transfers.value.toMutableList()
                val idx = current.indexOfFirst { it.id == item.id }
                if (idx >= 0) current[idx] = item else current.add(0, item)
                _transfers.value = current

                if (item.direction == "upload" && item.status in setOf("done", "completed", "failed", "cancelled")) {
                    runCatching {
                        val f = File(item.localPath)
                        if (f.parentFile?.absolutePath == stagingDir.absolutePath) f.delete()
                    }
                    if (item.status == "done" || item.status == "completed") {
                        listRemoteDir(_currentPath.value.ifBlank { null })
                    }
                } else if (item.direction == "download" && item.status in setOf("done", "completed")) {
                    if (exportedDownloadIds.add(item.id)) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val src = File(item.localPath.ifBlank { File(appDownloadDir, item.fileName).absolutePath })
                            val exported = exportToPublicDownloads(src, item.fileName)
                            if (exported) {
                                runCatching { src.delete() }
                                _notice.value = Notice(
                                    AppStrings.get(
                                        R.string.ft_notice_saved_downloads,
                                        "已保存至 Downloads/RabbitControl/${item.fileName}",
                                        item.fileName,
                                    ),
                                    isSuccess = true,
                                )
                            } else {
                                _notice.value = Notice(
                                    AppStrings.get(
                                        R.string.ft_notice_saved_path,
                                        "已下载至 ${src.absolutePath}",
                                        src.absolutePath,
                                    ),
                                    isSuccess = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun exportToPublicDownloads(source: File, fileName: String): Boolean {
        if (!source.exists() || !source.isFile) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return runCatching {
                val pub = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "RabbitControl",
                ).apply { mkdirs() }
                source.copyTo(File(pub, fileName), overwrite = true)
                true
            }.getOrDefault(false)
        }
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/RabbitControl")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { input -> input.copyTo(out) }
                } ?: return false
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                true
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        }.onFailure { err ->
            Log.w("RC_DIAG", "exportToPublicDownloads failed for $fileName: ${err.message}")
        }.getOrDefault(false)
    }

    private fun copyUriToStaging(uri: Uri): File? = runCatching {
        val name = queryDisplayName(uri) ?: "upload_${System.currentTimeMillis()}"
        val dest = File(stagingDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest
    }.getOrNull()

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
