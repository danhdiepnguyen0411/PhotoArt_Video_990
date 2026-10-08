package com.example.phortart_video_990.core.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.example.phortart_video_990.data.local.PreferencesManager
import java.util.Locale

object LocaleHelper {

    val SUPPORTED_LANGUAGES = listOf("en", "vi", "es", "fr", "hi", "ja", "ko")

    fun onAttach(context: Context): Context {
        val lang = getSavedLanguage(context)
        return setLocale(context, lang)
    }

    fun getSavedLanguage(context: Context): String {
        return PreferencesManager.getInstance(context).languageCode
    }

    fun setLocale(context: Context, language: String): Context {
        updateResources(context, language)
        return context
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val resources = context.resources
        val configuration = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
            return context.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
            @Suppress("DEPRECATION")
            resources.updateConfiguration(configuration, resources.displayMetrics)
            return context
        }
    }
}
