package com.example.phortart_video_990.core.utils

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import kotlin.random.Random

object TemplateViewsHelper {

    private const val PREF_NAME = "template_dynamic_views_prefs"
    private const val KEY_PREFIX_COUNT = "view_count_"
    private const val KEY_PREFIX_TIME = "view_time_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Get or incrementally update views for a given template.
     * Guaranteed to increase monotonically ("tăng dần") over time and visits.
     */
    fun getOrIncrementViews(context: Context, templateKey: String): String {
        val safeKey = if (templateKey.isBlank()) "default_template" else templateKey.trim()
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()

        val countKey = KEY_PREFIX_COUNT + safeKey
        val timeKey = KEY_PREFIX_TIME + safeKey

        val lastCount = prefs.getLong(countKey, 0L)
        val lastTime = prefs.getLong(timeKey, 0L)

        val currentCount: Long
        if (lastCount <= 0L) {
            // First time initialization: generate unique base count for this template (between 180,000 and 880,000)
            val seed = Math.abs(safeKey.hashCode().toLong())
            val base = 180_000L + (seed % 700_000L)
            currentCount = base
            prefs.edit()
                .putLong(countKey, currentCount)
                .putLong(timeKey, now)
                .apply()
        } else {
            // Incremental update:
            // Calculate time elapsed (in seconds)
            val elapsedSec = if (lastTime > 0L) (now - lastTime) / 1000L else 0L

            // Increment if at least 2 seconds have passed or on new visit
            if (elapsedSec >= 2L) {
                // Random incremental increase guaranteed to visibly advance 0.2K - 0.6K:
                val timeDrift = (elapsedSec / 15L).coerceAtMost(2000L) * Random.nextLong(10L, 30L)
                val delta = Random.nextLong(180L, 520L) + timeDrift

                currentCount = lastCount + delta
                prefs.edit()
                    .putLong(countKey, currentCount)
                    .putLong(timeKey, now)
                    .apply()
            } else {
                currentCount = lastCount
            }
        }

        return formatViews(currentCount)
    }

    fun formatViews(count: Long): String {
        return if (count >= 1_000_000L) {
            val m = count / 1_000_000.0
            String.format(Locale.GERMANY, "%.1fM", m)
        } else if (count >= 1_000L) {
            val k = count / 1_000.0
            String.format(Locale.GERMANY, "%.1fK", k)
        } else {
            count.toString()
        }
    }
}
