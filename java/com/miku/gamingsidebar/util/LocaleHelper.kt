package com.miku.gamingsidebar.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "miku_locale_prefs"
    private const val KEY_LANG = "app_language"

    const val LANG_SYSTEM = "system"
    const val LANG_ES = "es"
    const val LANG_EN = "en"

    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANG, LANG_SYSTEM) ?: LANG_SYSTEM
    }

    fun setLanguage(context: Context, langCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, langCode).apply()
        applyLocale(context, langCode)
    }

    fun applyLocale(context: Context, langCode: String? = null) {
        val targetLang = langCode ?: getSavedLanguage(context)
        val locale = if (targetLang == LANG_SYSTEM) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                LocaleList.getDefault()[0] ?: Locale.getDefault()
            } else {
                @Suppress("DEPRECATION")
                Locale.getDefault()
            }
        } else {
            Locale.forLanguageTag(targetLang)
        }

        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        @Suppress("DEPRECATION")
        context.applicationContext.resources.updateConfiguration(config, context.applicationContext.resources.displayMetrics)
    }

    fun wrap(context: Context): Context {
        val targetLang = getSavedLanguage(context)
        if (targetLang == LANG_SYSTEM) return context

        val locale = Locale.forLanguageTag(targetLang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
            return context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            return context
        }
    }
}
