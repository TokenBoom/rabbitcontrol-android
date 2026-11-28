package com.rabbitcontrol.android.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLocaleManagerTest {

    @Test
    fun fromStorageKeyParsesKnownValuesAndDefaultsToSystem() {
        assertEquals(AppLanguageMode.System, AppLanguageMode.fromStorageKey("system"))
        assertEquals(AppLanguageMode.SimplifiedChinese, AppLanguageMode.fromStorageKey("zh-Hans"))
        assertEquals(AppLanguageMode.TraditionalChinese, AppLanguageMode.fromStorageKey("zh-Hant"))
        assertEquals(AppLanguageMode.English, AppLanguageMode.fromStorageKey("en"))
        assertEquals(AppLanguageMode.System, AppLanguageMode.fromStorageKey(null))
        assertEquals(AppLanguageMode.System, AppLanguageMode.fromStorageKey(""))
        assertEquals(AppLanguageMode.System, AppLanguageMode.fromStorageKey("fr-FR"))
    }

    @Test
    fun fromLanguageTagMapsChineseScriptsRegionsAndEnglishFallbacks() {
        assertEquals(AppLanguageMode.SimplifiedChinese, AppLanguageMode.fromLanguageTag("zh"))
        assertEquals(AppLanguageMode.SimplifiedChinese, AppLanguageMode.fromLanguageTag("zh-CN"))
        assertEquals(AppLanguageMode.SimplifiedChinese, AppLanguageMode.fromLanguageTag("zh_CN"))
        assertEquals(AppLanguageMode.SimplifiedChinese, AppLanguageMode.fromLanguageTag("zh-Hans-CN"))

        assertEquals(AppLanguageMode.TraditionalChinese, AppLanguageMode.fromLanguageTag("zh-TW"))
        assertEquals(AppLanguageMode.TraditionalChinese, AppLanguageMode.fromLanguageTag("zh-HK"))
        assertEquals(AppLanguageMode.TraditionalChinese, AppLanguageMode.fromLanguageTag("zh-MO"))
        assertEquals(AppLanguageMode.TraditionalChinese, AppLanguageMode.fromLanguageTag("zh-Hant"))
        assertEquals(AppLanguageMode.TraditionalChinese, AppLanguageMode.fromLanguageTag("zh-Hant-TW"))

        assertEquals(AppLanguageMode.English, AppLanguageMode.fromLanguageTag("en"))
        assertEquals(AppLanguageMode.English, AppLanguageMode.fromLanguageTag("en-US"))
        assertEquals(AppLanguageMode.English, AppLanguageMode.fromLanguageTag("ja-JP"))
        assertEquals(AppLanguageMode.English, AppLanguageMode.fromLanguageTag(""))
        assertEquals(AppLanguageMode.English, AppLanguageMode.fromLanguageTag(null))
    }
}
