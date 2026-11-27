package io.rabbitcontrol.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryRulePrefBackend : StringPrefBackend {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String, def: String?): String? = map[key] ?: def
    override fun getBoolean(key: String, def: Boolean): Boolean = def
    override fun getInt(key: String, def: Int): Int = def
    override fun putValues(
        strings: Map<String, String?>,
        booleans: Map<String, Boolean>,
        ints: Map<String, Int>,
    ) {
        strings.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
    }
}

class PortMapRuleStoreTest {
    @Test
    fun roundTripsPerDeviceRulesAndClearsCleanly() {
        val store = PortMapRuleStore(InMemoryRulePrefBackend())
        val r1 = SavedPortMapRule("SSH", 2222, "127.0.0.1", 22, true)
        val r2 = SavedPortMapRule("Web", 8080, "10.0.0.5", 80, false)

        store.saveRules("123456789", listOf(r1, r2))
        val loaded = store.loadRules("123456789")
        assertEquals(listOf(r1, r2), loaded)
        assertTrue(store.loadRules("999999999").isEmpty())

        store.clearRules("123456789")
        assertTrue(store.loadRules("123456789").isEmpty())
    }
}
