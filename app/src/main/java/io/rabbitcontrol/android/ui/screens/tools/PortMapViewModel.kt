package io.rabbitcontrol.android.ui.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbitcontrol.android.error.RcErrorCodes
import io.rabbitcontrol.android.core.error.RcUiErrorCodec
import io.rabbitcontrol.android.core.nativebridge.PortMapRule
import io.rabbitcontrol.android.core.nativebridge.toPortMapRule
import io.rabbitcontrol.android.core.session.ActiveSessionState
import io.rabbitcontrol.android.core.session.SessionManager
import io.rabbitcontrol.android.core.session.SessionStatus
import io.rabbitcontrol.android.ui.components.RcErrorHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class PortMapViewModel(
    private val sessionManager: SessionManager,
) : ViewModel() {

    val sessionState: StateFlow<ActiveSessionState> = sessionManager.state
    val rules: StateFlow<List<PortMapRule>> = sessionManager.portMapRules

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var refreshedForStreaming = false

    init {
        viewModelScope.launch {
            sessionManager.state.collect { st ->
                if (st.status is SessionStatus.Streaming) {
                    if (!refreshedForStreaming) {
                        refreshedForStreaming = true
                        refresh()
                    }
                } else if (st.status is SessionStatus.Idle || st.status is SessionStatus.Ended || st.status is SessionStatus.Error) {
                    refreshedForStreaming = false
                }
            }
        }
        if (sessionManager.state.value.status is SessionStatus.Streaming) {
            refreshedForStreaming = true
            refresh()
        }
    }

    private fun handleToolFailure(err: Throwable) {
        val uiErr = RcUiErrorCodec.fromThrowable(err, fallbackCode = RcErrorCodes.TOOL_PORTMAP_BIND_FAILED)
        if (uiErr.code == RcErrorCodes.TOOL_FORBIDDEN) {
            RcErrorHub.present(uiErr)
        } else {
            _error.value = uiErr.message.ifBlank { uiErr.title }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            sessionManager.callTool(JSONObject().put("action", "portmap_list"))
                .onSuccess { applyRulesFromResponse(it) }
                .onFailure(::handleToolFailure)
        }
    }

    fun addRule(name: String, localPort: Int, targetAddr: String, targetPort: Int) {
        viewModelScope.launch {
            sessionManager.callTool(
                JSONObject()
                    .put("action", "portmap_add")
                    .put("name", name.ifBlank { "Port $localPort" })
                    .put("localPort", localPort)
                    .put("targetAddr", targetAddr.ifBlank { "127.0.0.1" })
                    .put("targetPort", targetPort),
            ).onSuccess { applyRulesFromResponse(it) }
                .onFailure(::handleToolFailure)
        }
    }

    fun toggleRule(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            sessionManager.callTool(
                JSONObject().put("action", "portmap_toggle").put("id", id).put("enabled", enabled),
            ).onSuccess { applyRulesFromResponse(it) }
                .onFailure(::handleToolFailure)
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            sessionManager.callTool(JSONObject().put("action", "portmap_delete").put("id", id))
                .onSuccess { applyRulesFromResponse(it) }
                .onFailure(::handleToolFailure)
        }
    }

    private fun applyRulesFromResponse(obj: JSONObject) {
        val arr = obj.optJSONArray("rules") ?: return
        sessionManager.updatePortMapRules(List(arr.length()) { arr.getJSONObject(it).toPortMapRule() })
    }

    fun clearError() {
        _error.value = null
    }
}
