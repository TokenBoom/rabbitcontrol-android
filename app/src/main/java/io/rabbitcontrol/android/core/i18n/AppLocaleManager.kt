package io.rabbitcontrol.android.core.i18n

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppLanguageMode(
    val storageKey: String,
    val bcp47Tag: String?,
) {
    System("system", null),
    SimplifiedChinese("zh-Hans", "zh-CN"),
    TraditionalChinese("zh-Hant", "zh-Hant"),
    English("en", "en");

    fun toJavaLocale(): Locale? = when (this) {
        System -> null
        SimplifiedChinese -> Locale.SIMPLIFIED_CHINESE
        TraditionalChinese -> Locale.TRADITIONAL_CHINESE
        English -> Locale.ENGLISH
    }

    companion object {
        fun fromStorageKey(raw: String?): AppLanguageMode =
            entries.firstOrNull { it.storageKey == raw } ?: System

        fun fromLanguageTag(tag: String?): AppLanguageMode {
            val clean = tag?.trim()?.lowercase()?.replace('_', '-').orEmpty()
            if (clean.isEmpty()) return English
            if (clean.contains("hant") ||
                clean.startsWith("zh-tw") ||
                clean.startsWith("zh-hk") ||
                clean.startsWith("zh-mo")
            ) {
                return TraditionalChinese
            }
            if (clean == "zh" || clean.startsWith("zh-")) {
                return SimplifiedChinese
            }
            return English
        }
    }
}

object AppLocaleManager {
    private const val PREFS_NAME = "rabbitcontrol_locale"
    private const val KEY_LANGUAGE_MODE = "language_mode"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cachedLocalizedContext: Context? = null

    private val _mode = MutableStateFlow(AppLanguageMode.System)
    val mode: StateFlow<AppLanguageMode> = _mode.asStateFlow()

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        var resolved = readSavedMode(app)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sysLocales = runCatching {
                app.getSystemService(LocaleManager::class.java)?.applicationLocales
            }.getOrNull()
            if (sysLocales != null) {
                resolved = if (sysLocales.isEmpty) {
                    AppLanguageMode.System
                } else {
                    AppLanguageMode.fromLanguageTag(sysLocales.toLanguageTags().substringBefore(','))
                }
                persistMode(app, resolved)
            }
        }
        _mode.value = resolved
        cachedLocalizedContext = buildLocalizedContext(app, resolved)
    }

    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }
        val saved = readSavedMode(base)
        return buildLocalizedContext(base, saved)
    }

    fun setLanguageMode(context: Context, nextMode: AppLanguageMode) {
        val app = appContext ?: context.applicationContext
        persistMode(app, nextMode)
        _mode.value = nextMode
        cachedLocalizedContext = buildLocalizedContext(app, nextMode)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = app.getSystemService(LocaleManager::class.java)
            if (lm != null) {
                lm.applicationLocales = if (nextMode == AppLanguageMode.System) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(nextMode.bcp47Tag ?: "en")
                }
                return
            }
        }
        (context as? Activity)?.recreate()
    }

    fun refreshLocalizedContext(base: Context? = null, newConfig: Configuration? = null) {
        val ctx = base?.applicationContext ?: appContext ?: return
        cachedLocalizedContext = if (_mode.value == AppLanguageMode.System && newConfig != null) {
            val localeCfg = Configuration().apply {
                setLocales(newConfig.locales)
            }
            ctx.createConfigurationContext(localeCfg)
        } else {
            buildLocalizedContext(ctx, _mode.value)
        }
    }

    fun localizedContext(): Context? = cachedLocalizedContext ?: appContext

    fun activeLanguageMode(): AppLanguageMode {
        val explicit = _mode.value
        if (explicit != AppLanguageMode.System) return explicit
        val sysTag = LocaleList.getDefault().get(0)?.toLanguageTag()
        return AppLanguageMode.fromLanguageTag(sysTag)
    }

    private fun readSavedMode(context: Context): AppLanguageMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppLanguageMode.fromStorageKey(prefs.getString(KEY_LANGUAGE_MODE, null))
    }

    private fun persistMode(context: Context, mode: AppLanguageMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_MODE, mode.storageKey)
            .commit()
    }

    private fun buildLocalizedContext(base: Context, mode: AppLanguageMode): Context {
        val locale = mode.toJavaLocale() ?: return base
        val config = Configuration()
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
