package com.rabbitcontrol.android.core.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceKeyProviderTest {
    @Test
    fun deriveStableDeviceKey_returnsDeterministic64CharLowercaseHex() {
        val k1 = DeviceKeyProvider.deriveStableDeviceKey("9b4c1d2e3f4a5b6c", "com.rabbitcontrol.android")
        val k2 = DeviceKeyProvider.deriveStableDeviceKey("  9B4C1D2E3F4A5B6C  ", "com.rabbitcontrol.android")

        requireNotNull(k1)
        assertEquals(64, k1.length)
        assertTrue(k1.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(k1, k2)
    }

    @Test
    fun deriveStableDeviceKey_differsAcrossPackageNamesAndAndroidIds() {
        val a = DeviceKeyProvider.deriveStableDeviceKey("9b4c1d2e3f4a5b6c", "com.rabbitcontrol.android")
        val b = DeviceKeyProvider.deriveStableDeviceKey("9b4c1d2e3f4a5b6c", "com.rabbitcontrol.android.debug")
        val c = DeviceKeyProvider.deriveStableDeviceKey("1111222233334444", "com.rabbitcontrol.android")

        requireNotNull(a)
        requireNotNull(b)
        requireNotNull(c)
        assertNotEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun deriveStableDeviceKey_rejectsBlankAndInvalidIds() {
        assertNull(DeviceKeyProvider.deriveStableDeviceKey(null, "com.rabbitcontrol.android"))
        assertNull(DeviceKeyProvider.deriveStableDeviceKey("   ", "com.rabbitcontrol.android"))
        assertNull(DeviceKeyProvider.deriveStableDeviceKey("1234567", "com.rabbitcontrol.android"))
        assertNull(DeviceKeyProvider.deriveStableDeviceKey("9774d56d682e549c", "com.rabbitcontrol.android"))
        assertNull(DeviceKeyProvider.deriveStableDeviceKey("9774D56D682E549C", "com.rabbitcontrol.android"))
        assertNull(DeviceKeyProvider.deriveStableDeviceKey("0000000000000000", "com.rabbitcontrol.android"))
    }
}
