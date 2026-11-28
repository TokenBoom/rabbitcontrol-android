package com.rabbitcontrol.android.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

private class InMemoryPrefBackend : StringPrefBackend {
    private val strings = mutableMapOf<String, String?>()
    private val booleans = mutableMapOf<String, Boolean>()
    private val ints = mutableMapOf<String, Int>()

    override fun getString(key: String, def: String?): String? = strings[key] ?: def
    override fun getBoolean(key: String, def: Boolean): Boolean = booleans[key] ?: def
    override fun getInt(key: String, def: Int): Int = ints[key] ?: def

    override fun putValues(
        strings: Map<String, String?>,
        booleans: Map<String, Boolean>,
        ints: Map<String, Int>,
    ) {
        strings.forEach { (k, v) -> if (v == null) this.strings.remove(k) else this.strings[k] = v }
        booleans.forEach { (k, v) -> this.booleans[k] = v }
        ints.forEach { (k, v) -> this.ints[k] = v }
    }
}

class AppLockStoreTest {
    @Test
    fun setAndVerifyPinAndDisableWork() {
        var now = 1_000_000L
        val backend = InMemoryPrefBackend()
        val store = AppLockStore(backend) { now }

        assertFalse(store.state.value.enabled)
        assertFalse(store.setPin("123"))
        assertTrue(store.setPin("2468"))
        assertTrue(store.state.value.enabled)
        assertEquals(4, store.state.value.pinLength)
        assertFalse(store.state.value.locked)

        store.lockNow()
        assertTrue(store.state.value.locked)
        assertFalse(store.verifyPin("0000"))
        assertEquals(1, store.state.value.failedAttempts)
        assertTrue(store.state.value.locked)

        assertTrue(store.verifyPin("2468"))
        assertEquals(0, store.state.value.failedAttempts)
        assertFalse(store.state.value.locked)

        // Fresh instance from persisted backend starts locked
        val reloaded = AppLockStore(backend) { now }
        assertTrue(reloaded.state.value.enabled)
        assertTrue(reloaded.state.value.locked)
        assertTrue(reloaded.verifyPin("2468"))

        assertFalse(reloaded.disableWithPin("9999"))
        assertTrue(reloaded.disableWithPin("2468"))
        assertFalse(reloaded.state.value.enabled)
    }

    @Test
    fun backgroundAutoLockRespectsTimeouts() {
        var now = 10_000L
        val store = AppLockStore(InMemoryPrefBackend()) { now }
        assertTrue(store.setPin("135790"))

        // 60s timeout
        store.setAutoLockTimeoutSec(60)
        store.onAppBackgrounded()
        now += 30_000L
        store.onAppForegrounded()
        assertFalse(store.state.value.locked)

        store.onAppBackgrounded()
        now += 61_000L
        store.onAppForegrounded()
        assertTrue(store.state.value.locked)
        assertTrue(store.verifyPin("135790"))

        // Immediate (0s)
        store.setAutoLockTimeoutSec(0)
        store.onAppBackgrounded()
        store.onAppForegrounded()
        assertTrue(store.state.value.locked)
        assertTrue(store.verifyPin("135790"))

        // Never (-1)
        store.setAutoLockTimeoutSec(-1)
        store.onAppBackgrounded()
        now += 3_600_000L
        store.onAppForegrounded()
        assertFalse(store.state.value.locked)
    }
}
