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
        private const val KEY_HAS_RATED_APP = "has_rated_app"

        @Volatile
        private var INSTANCE: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                val safeContext = context.applicationContext ?: context
                INSTANCE ?: PreferencesManager(safeContext).also { INSTANCE = it }
            }
        }
    }

    var hasRatedApp: Boolean
        get() = prefs.getBoolean(KEY_HAS_RATED_APP, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_RATED_APP, value).apply()

    var isFirstLanguageSelected: Boolean
        get() = prefs.getBoolean(KEY_IS_FIRST_LANGUAGE_SELECTED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_FIRST_LANGUAGE_SELECTED, value).apply()

    var hasCompletedIntro: Boolean
        get() = prefs.getBoolean(KEY_HAS_COMPLETED_INTRO, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_COMPLETED_INTRO, value).apply()

    var shouldSkipSplashOnce: Boolean
        get() = prefs.getBoolean("skip_splash_once", false)
        set(value) = prefs.edit().putBoolean("skip_splash_once", value).apply()

    var isUninstallActive: Boolean
        get() = prefs.getBoolean("is_uninstall_active", false)
        set(value) = prefs.edit().putBoolean("is_uninstall_active", value).apply()

    var notificationDeniedCount: Int
        get() = prefs.getInt("notification_denied_count", 0)
        set(value) = prefs.edit().putInt("notification_denied_count", value).apply()

    var shouldShowNotiDialogOnHome: Boolean
        get() = prefs.getBoolean("should_show_noti_dialog_on_home", false)
        set(value) = prefs.edit().putBoolean("should_show_noti_dialog_on_home", value).apply()

    fun clearLegacyUninstallFlags() {
        if (prefs.contains("is_uninstall_active") || prefs.contains("skip_splash_once")) {
            prefs.edit()
                .remove("is_uninstall_active")
                .remove("skip_splash_once")
                .apply()
        }
    }

    var languageCode: String
        get() {
            val code = prefs.getString(KEY_LANGUAGE_CODE, null)
            return if (!code.isNullOrEmpty()) code else "en"
        }
        set(value) = prefs.edit().putString(KEY_LANGUAGE_CODE, value).apply()

    var languageName: String
        get() = prefs.getString(KEY_LANGUAGE_NAME, "English") ?: "English"
        set(value) = prefs.edit().putString(KEY_LANGUAGE_NAME, value).apply()

    var historyJson: String?
        get() = prefs.getString("user_history_json", null)
        set(value) = prefs.edit().putString("user_history_json", value).apply()

    var isNotificationEnabled: Boolean
        get() = prefs.getBoolean("is_notification_enabled", true)
        set(value) = prefs.edit().putBoolean("is_notification_enabled", value).apply()

    var lastSelectedMusicTrackId: String?
        get() = prefs.getString("last_selected_music_track_id", null)
        set(value) = prefs.edit().putString("last_selected_music_track_id", value).apply()

    var lastMusicScrollPosition: Int
        get() = prefs.getInt("last_music_scroll_position", -1)
        set(value) = prefs.edit().putInt("last_music_scroll_position", value).apply()

    var lastMusicScrollOffset: Int
        get() = prefs.getInt("last_music_scroll_offset", 0)
        set(value) = prefs.edit().putInt("last_music_scroll_offset", value).apply()

    fun recordSelectedCategory(category: String) {
        val cat = category.trim()
        if (cat.isBlank() || cat.equals("ALL", ignoreCase = true) || cat.equals("Tất cả", ignoreCase = true)) return
        prefs.edit().putString("last_selected_category", cat).apply()
        recordUsedTemplateCategory(cat)
    }

    fun getLastSelectedCategory(): String? {
        return prefs.getString("last_selected_category", null)?.takeIf { it.isNotBlank() }
    }

    fun recordUsedTemplateCategory(category: String) {
        val cat = category.trim()
        if (cat.isBlank()) return
        val currentJson = prefs.getString("used_template_categories", "{}") ?: "{}"
        try {
            val type = object : com.google.gson.reflect.TypeToken<MutableMap<String, Int>>() {}.type
            val map: MutableMap<String, Int> = com.google.gson.Gson().fromJson(currentJson, type) ?: mutableMapOf()
            val currentCount = map[cat] ?: 0
            map[cat] = currentCount + 1
            prefs.edit().putString("used_template_categories", com.google.gson.Gson().toJson(map)).apply()
        } catch (_: Exception) {}
    }

    fun getUsedCategoriesRanked(): List<String> {
        val currentJson = prefs.getString("used_template_categories", "{}") ?: "{}"
        return try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, Int>>() {}.type
            val map: Map<String, Int> = com.google.gson.Gson().fromJson(currentJson, type) ?: emptyMap()
            map.entries.sortedByDescending { it.value }.map { it.key }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getRecentlyShownTemplateIds(): List<String> {
        val raw = prefs.getString("recently_shown_template_ids", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun recordShownTemplateIds(ids: List<String>) {
        if (ids.isEmpty()) return
        val current = getRecentlyShownTemplateIds().toMutableList()
        for (id in ids) {
            current.remove(id) // avoid duplicates in tracking list
            current.add(id)
        }
        // Retain at most 15 items for session anti-collision
        val trimmed = if (current.size > 15) current.takeLast(15) else current
        prefs.edit().putString("recently_shown_template_ids", trimmed.joinToString(",")).apply()
    }

    fun saveLanguage(code: String, name: String) {
        prefs.edit()
            .putString(KEY_LANGUAGE_CODE, code)
            .putString(KEY_LANGUAGE_NAME, name)
            .putBoolean(KEY_IS_FIRST_LANGUAGE_SELECTED, true)
            .apply()
    }

    var lastActiveTime: Long
        get() = prefs.getLong("last_active_time", 0L)
        set(value) = prefs.edit().putLong("last_active_time", value).apply()

    fun getKnownTemplateIds(): Set<String> {
        return prefs.getStringSet("known_template_ids", emptySet())?.toSet() ?: emptySet()
    }

    fun saveKnownTemplateIds(ids: Set<String>) {
        prefs.edit().putStringSet("known_template_ids", HashSet(ids)).apply()
    }
}
