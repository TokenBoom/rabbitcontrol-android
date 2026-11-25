package io.rabbitcontrol.android.ui.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rabbitcontrol.android.core.nativebridge.PortMapRule
import io.rabbitcontrol.android.core.nativebridge.toPortMapRule
import io.rabbitcontrol.android.core.session.SessionManager
import io.rabbitcontrol.android.core.session.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class PortMapViewModel(
    private val sessionManager: SessionManager,
) : ViewModel() {

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

    fun refresh() {
        viewModelScope.launch {
            sessionManager.callTool(JSONObject().put("action", "portmap_list"))
                .onSuccess { applyRulesFromResponse(it) }
                .onFailure { _error.value = it.message }
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
                .onFailure { _error.value = it.message }
        }
    }

    fun toggleRule(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            sessionManager.callTool(
                JSONObject().put("action", "portmap_toggle").put("id", id).put("enabled", enabled),
            ).onSuccess { applyRulesFromResponse(it) }
                .onFailure { _error.value = it.message }
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            sessionManager.callTool(JSONObject().put("action", "portmap_delete").put("id", id))
                .onSuccess { applyRulesFromResponse(it) }
                .onFailure { _error.value = it.message }
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
