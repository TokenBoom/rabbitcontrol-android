package io.rabbitcontrol.android.core.data

import android.content.Context
import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class SavedPortMapRule(
    val name: String,
    val localPort: Int,
    val targetAddr: String,
    val targetPort: Int,
    val enabled: Boolean = true,
)

class PortMapRuleStore internal constructor(
    private val backend: StringPrefBackend,
) {
    constructor(context: Context) : this(
        SharedPrefsBackend(
            context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
        ),
    )

    fun loadRules(deviceId: String): List<SavedPortMapRule> {
        val id = deviceId.trim()
        if (id.isEmpty()) return emptyList()
        val raw = backend.getString(key(id), null) ?: return emptyList()
        return parseRulesJson(raw)
    }

    fun saveRules(deviceId: String, rules: List<SavedPortMapRule>) {
        val id = deviceId.trim()
        if (id.isEmpty()) return
        if (rules.isEmpty()) {
            clearRules(id)
            return
        }
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(
                JSONObject()
                    .put("name", r.name)
                    .put("localPort", r.localPort)
                    .put("targetAddr", r.targetAddr)
                    .put("targetPort", r.targetPort)
                    .put("enabled", r.enabled),
            )
        }
        backend.putValues(strings = mapOf(key(id) to arr.toString()))
    }

    fun clearRules(deviceId: String) {
        val id = deviceId.trim()
        if (id.isEmpty()) return
        backend.putValues(strings = mapOf(key(id) to null))
    }

    internal fun parseRulesJson(raw: String): List<SavedPortMapRule> =
        runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val localPort = obj.optInt("localPort", 0)
                    val targetPort = obj.optInt("targetPort", 0)
                    if (localPort !in 1..65535 || targetPort !in 1..65535) continue
                    add(
                        SavedPortMapRule(
                            name = obj.optString("name", ""),
                            localPort = localPort,
                            targetAddr = obj.optString("targetAddr", "127.0.0.1").ifBlank { "127.0.0.1" },
                            targetPort = targetPort,
                            enabled = obj.optBoolean("enabled", true),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())

    private fun key(deviceId: String) = "rules_${deviceId.trim()}"

    private companion object {
        const val PREF_NAME = "rc_portmap_rules"
    }
}
