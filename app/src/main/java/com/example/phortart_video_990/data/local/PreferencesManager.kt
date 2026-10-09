package com.example.phortart_video_990.data.local

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "photoart_video_990_prefs"
        private const val KEY_IS_FIRST_LANGUAGE_SELECTED = "is_first_language_selected"
        private const val KEY_HAS_COMPLETED_INTRO = "has_completed_intro"
        private const val KEY_LANGUAGE_CODE = "language_code"
        private const val KEY_LANGUAGE_NAME = "language_name"

        @Volatile
        private var INSTANCE: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                val safeContext = context.applicationContext ?: context
                INSTANCE ?: PreferencesManager(safeContext).also { INSTANCE = it }
            }
        }
    }

    var isFirstLanguageSelected: Boolean
        get() = prefs.getBoolean(KEY_IS_FIRST_LANGUAGE_SELECTED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_FIRST_LANGUAGE_SELECTED, value).apply()

    var hasCompletedIntro: Boolean
        get() = prefs.getBoolean(KEY_HAS_COMPLETED_INTRO, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_COMPLETED_INTRO, value).apply()

    var languageCode: String
        get() = prefs.getString(KEY_LANGUAGE_CODE, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_LANGUAGE_CODE, value).apply()

    var languageName: String
        get() = prefs.getString(KEY_LANGUAGE_NAME, "English") ?: "English"
        set(value) = prefs.edit().putString(KEY_LANGUAGE_NAME, value).apply()

    var historyJson: String?
        get() = prefs.getString("user_history_json", null)
        set(value) = prefs.edit().putString("user_history_json", value).apply()

    fun saveLanguage(code: String, name: String) {
        prefs.edit()
            .putString(KEY_LANGUAGE_CODE, code)
            .putString(KEY_LANGUAGE_NAME, name)
            .putBoolean(KEY_IS_FIRST_LANGUAGE_SELECTED, true)
            .apply()
    }
}
