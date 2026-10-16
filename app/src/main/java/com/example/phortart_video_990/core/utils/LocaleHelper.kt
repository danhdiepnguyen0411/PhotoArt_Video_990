package com.example.phortart_video_990.core.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.example.phortart_video_990.data.local.PreferencesManager
import java.util.Locale

object LocaleHelper {

    val SUPPORTED_LANGUAGES = listOf(
        "vi", "en", "ja", "ko", "zh", "fr", "es", "de", "it", "ar", "pt", "ru", "hi", "id"
    )

    fun getDeviceLanguage(): String {
        val sysLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.content.res.Resources.getSystem().configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            android.content.res.Resources.getSystem().configuration.locale
        }
        val lang = sysLocale.language.lowercase()
        val mappedLang = if (lang == "in") "id" else lang
        return if (SUPPORTED_LANGUAGES.contains(mappedLang)) mappedLang else "en"
    }

    fun onAttach(context: Context): Context {
        val prefs = PreferencesManager.getInstance(context)
        if (!prefs.hasCompletedIntro) {
            prefs.isFirstLanguageSelected = false
            val deviceLang = getDeviceLanguage()
            prefs.languageCode = deviceLang
            return setLocale(context, deviceLang)
        }
        val lang = getSavedLanguage(context)
        return setLocale(context, lang)
    }

    fun getSavedLanguage(context: Context): String {
        val prefs = PreferencesManager.getInstance(context)
        if (!prefs.hasCompletedIntro) {
            return getDeviceLanguage()
        }
        val code = prefs.languageCode
        return if (code.isNotEmpty() && SUPPORTED_LANGUAGES.contains(code.lowercase())) code else getDeviceLanguage()
    }

    fun setLocale(context: Context, language: String): Context {
        return updateResources(context, language)
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = getLocale(language)
        Locale.setDefault(locale)

        val resources = context.resources
        val configuration = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
            val localeList = android.os.LocaleList(locale)
            android.os.LocaleList.setDefault(localeList)
            configuration.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
        }

        configuration.setLayoutDirection(locale)

        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)

        try {
            val appCtx = context.applicationContext
            if (appCtx != null && appCtx !== context) {
                @Suppress("DEPRECATION")
                appCtx.resources.updateConfiguration(configuration, appCtx.resources.displayMetrics)
            }
        } catch (_: Exception) {}

        if (context is android.app.Activity) {
            try {
                @Suppress("DEPRECATION")
                context.baseContext?.resources?.updateConfiguration(configuration, resources.displayMetrics)
            } catch (_: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return context.createConfigurationContext(configuration)
        } else {
            return context
        }
    }

    fun getLocale(language: String): Locale {
        return when (language.lowercase()) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "id", "in" -> Locale("in")
            "ar" -> Locale("ar")
            "pt" -> Locale("pt")
            "ja" -> Locale.JAPANESE
            "ko" -> Locale.KOREAN
            "fr" -> Locale.FRENCH
            "de" -> Locale.GERMAN
            "it" -> Locale.ITALIAN
            "ru" -> Locale("ru")
            "hi" -> Locale("hi")
            "es" -> Locale("es")
            "vi" -> Locale("vi")
            else -> Locale(language)
        }
    }

    fun isRtlLanguage(language: String?): Boolean {
        return language?.lowercase() == "ar"
    }

    fun isRtl(context: Context): Boolean {
        val lang = getSavedLanguage(context)
        return isRtlLanguage(lang)
    }

    fun getDeviceLocalizedContext(context: Context): Context {
        val deviceLang = getDeviceLanguage()
        val locale = getLocale(deviceLang)
        val config = Configuration(context.resources.configuration)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }

    fun getLocalizedContext(context: Context): Context {
        val lang = getSavedLanguage(context)
        val locale = getLocale(lang)
        val config = Configuration(context.resources.configuration)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }
}
