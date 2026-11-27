package io.rabbitcontrol.android.ui.screens.tools

import android.util.Base64
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.i18n.NativeErrorLocalizer
import io.rabbitcontrol.android.core.nativebridge.TerminalSessionSummary
import io.rabbitcontrol.android.core.session.ActiveSessionState
import io.rabbitcontrol.android.core.session.SessionManager
import io.rabbitcontrol.android.core.session.SessionStatus
import io.rabbitcontrol.android.core.session.TerminalToolEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class TerminalTabState(
    val termId: Int,
    val name: String,
    val title: String,
    val cwd: String,
    val exitedCode: Int? = null,
    val renderRevision: Long = 0L,
)

class TerminalViewModel(
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val buffers = mutableMapOf<Int, AnsiTerminalBuffer>()
    private var cols = 80
    private var rows = 24
    private var refreshedForStreaming = false

    val sessionState: StateFlow<ActiveSessionState> = sessionManager.state

    private val _sessions = MutableStateFlow<List<TerminalSessionSummary>>(emptyList())
    val sessions: StateFlow<List<TerminalSessionSummary>> = _sessions.asStateFlow()

    private val _openTabs = MutableStateFlow<List<TerminalTabState>>(emptyList())
    val openTabs: StateFlow<List<TerminalTabState>> = _openTabs.asStateFlow()

    private val _activeTermId = MutableStateFlow<Int?>(null)
    val activeTermId: StateFlow<Int?> = _activeTermId.asStateFlow()

    private val _renderedText = MutableStateFlow(AnnotatedString(""))
    val renderedText: StateFlow<AnnotatedString> = _renderedText.asStateFlow()

    private val _ctrlSticky = MutableStateFlow(false)
    val ctrlSticky: StateFlow<Boolean> = _ctrlSticky.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.terminalEvents.collect { ev -> handleEvent(ev) }
        }
        viewModelScope.launch {
            sessionManager.state.collect { st ->
                if (st.status is SessionStatus.Streaming) {
                    if (!refreshedForStreaming) {
                        refreshedForStreaming = true
                        refreshSessions()
                    }
                } else if (st.status is SessionStatus.Idle || st.status is SessionStatus.Ended || st.status is SessionStatus.Error) {
                    refreshedForStreaming = false
                }
            }
        }
        if (sessionManager.state.value.status is SessionStatus.Streaming) {
            refreshedForStreaming = true
            refreshSessions()
        }
    }

    fun refreshSessions() {
        viewModelScope.launch {
            sessionManager.callTool(JSONObject().put("action", "terminal_list"))
                .onFailure { _error.value = it.message }
        }
    }

    fun createTerminal() {
        viewModelScope.launch {
            sessionManager.callTool(
                JSONObject()
                    .put("action", "terminal_open")
                    .put("cols", cols)
                    .put("rows", rows)
                    .put("attach", true),
            ).onFailure { _error.value = it.message }
        }
    }

    fun attachTerminal(summary: TerminalSessionSummary) {
        _activeTermId.value = summary.termId
        if (_openTabs.value.none { it.termId == summary.termId }) {
            _openTabs.value = _openTabs.value + TerminalTabState(summary.termId, summary.name, summary.title, summary.cwd)
        }
        buffers.getOrPut(summary.termId) { AnsiTerminalBuffer(cols, rows) }
        viewModelScope.launch {
            sessionManager.callTool(
                JSONObject()
                    .put("action", "terminal_attach")
                    .put("termId", summary.termId)
                    .put("cols", cols)
                    .put("rows", rows),
            ).onFailure { _error.value = it.message }
        }
        refreshRenderedOutput()
    }

    fun selectTab(termId: Int) {
        _activeTermId.value = termId
        refreshRenderedOutput()
    }

    fun detachBackToList() {
        val active = _activeTermId.value
        _activeTermId.value = null
        if (active != null) {
            viewModelScope.launch {
                sessionManager.callTool(JSONObject().put("action", "terminal_detach").put("termId", active))
            }
        }
    }

    fun closeTerminal(termId: Int) {
        viewModelScope.launch {
            sessionManager.callTool(JSONObject().put("action", "terminal_close").put("termId", termId))
                .onSuccess {
                    buffers.remove(termId)
                    _openTabs.value = _openTabs.value.filterNot { it.termId == termId }
                    if (_activeTermId.value == termId) {
                        _activeTermId.value = _openTabs.value.lastOrNull()?.termId
                        refreshRenderedOutput()
                    }
                    refreshSessions()
                }
                .onFailure { _error.value = it.message }
        }
    }

    fun renameTerminal(termId: Int, newName: String) {
        viewModelScope.launch {
            sessionManager.callTool(
                JSONObject().put("action", "terminal_rename").put("termId", termId).put("name", newName),
            ).onFailure { _error.value = it.message }
        }
    }

    fun updateGridSize(newCols: Int, newRows: Int) {
        val c = newCols.coerceIn(24, 240)
        val r = newRows.coerceIn(10, 120)
        if (c == cols && r == rows) return
        cols = c
        rows = r
        buffers.values.forEach { it.resize(c, r) }
        val active = _activeTermId.value ?: return
        viewModelScope.launch {
            sessionManager.callTool(
                JSONObject()
                    .put("action", "terminal_resize")
                    .put("termId", active)
                    .put("cols", c)
                    .put("rows", r),
            )
        }
        refreshRenderedOutput()
    }

    fun toggleCtrlSticky() {
        _ctrlSticky.value = !_ctrlSticky.value
    }

    fun sendTextOrCommand(raw: String) {
        val active = _activeTermId.value ?: return
        val payload = if (_ctrlSticky.value && raw.length == 1) {
            _ctrlSticky.value = false
            val upper = raw[0].uppercaseChar()
            if (upper in '@'..'Z') {
                byteArrayOf((upper.code - 64).toByte())
            } else {
                raw.toByteArray(Charsets.UTF_8)
            }
        } else {
            raw.toByteArray(Charsets.UTF_8)
        }
        sendBytes(active, payload)
    }

    fun sendBytes(termId: Int, bytes: ByteArray) {
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        viewModelScope.launch {
            sessionManager.callTool(
                JSONObject()
                    .put("action", "terminal_input")
                    .put("termId", termId)
                    .put("dataBase64", b64),
            ).onFailure { _error.value = it.message }
        }
    }

    fun clearActiveBuffer() {
        val active = _activeTermId.value ?: return
        buffers[active]?.clear()
        refreshRenderedOutput()
    }

    fun clearError() {
        _error.value = null
    }

    private fun handleEvent(ev: TerminalToolEvent) {
        when (ev) {
            is TerminalToolEvent.SessionList -> _sessions.value = ev.sessions
            is TerminalToolEvent.Opened -> {
                if (!ev.ok) {
                    _error.value = ev.error?.let(NativeErrorLocalizer::localize)
                        ?: AppStrings.get(R.string.term_err_create, "创建终端失败")
                    return
                }
                buffers.getOrPut(ev.termId) { AnsiTerminalBuffer(cols, rows) }
                val tab = TerminalTabState(
                    ev.termId,
                    ev.name.ifBlank { AppStrings.get(R.string.term_default_tab_name, "终端 ${ev.termId}", ev.termId) },
                    ev.title,
                    ev.cwd,
                )
                _openTabs.value = (_openTabs.value.filterNot { it.termId == ev.termId } + tab)
                if (ev.attached) {
                    _activeTermId.value = ev.termId
                }
                refreshSessions()
                refreshRenderedOutput()
            }
            is TerminalToolEvent.Attached -> {
                if (!ev.ok) {
                    _error.value = ev.error?.let(NativeErrorLocalizer::localize)
                        ?: AppStrings.get(R.string.term_err_attach, "附着终端失败")
                    return
                }
                val buf = buffers.getOrPut(ev.termId) { AnsiTerminalBuffer(cols, rows) }
                buf.clear()
                ev.session?.let { s ->
                    val tab = TerminalTabState(
                        s.termId,
                        s.name.ifBlank { AppStrings.get(R.string.term_default_tab_name, "终端 ${s.termId}", s.termId) },
                        s.title,
                        s.cwd,
                    )
                    _openTabs.value = (_openTabs.value.filterNot { it.termId == s.termId } + tab)
                }
                _activeTermId.value = ev.termId
                refreshRenderedOutput()
            }
            is TerminalToolEvent.Output -> {
                val buf = buffers.getOrPut(ev.termId) { AnsiTerminalBuffer(cols, rows) }
                if (ev.bytes.isNotEmpty()) {
                    buf.feed(ev.bytes)
                }
                if (_activeTermId.value == ev.termId) {
                    refreshRenderedOutput()
                }
            }
            is TerminalToolEvent.Exited -> {
                _openTabs.value = _openTabs.value.map {
                    if (it.termId == ev.termId) it.copy(exitedCode = ev.exitCode) else it
                }
                refreshSessions()
            }
            is TerminalToolEvent.Renamed -> {
                if (ev.ok) {
                    _openTabs.value = _openTabs.value.map {
                        if (it.termId == ev.termId) it.copy(name = ev.name) else it
                    }
                    refreshSessions()
                } else {
                    _error.value = ev.error?.let(NativeErrorLocalizer::localize)
                        ?: AppStrings.get(R.string.term_err_rename, "重命名失败")
                }
            }
            is TerminalToolEvent.Error -> _error.value = NativeErrorLocalizer.localize(ev.message)
        }
    }

    private fun refreshRenderedOutput() {
        val active = _activeTermId.value ?: return
        _renderedText.value = buffers[active]?.renderSnapshot() ?: AnnotatedString("")
    }
}
