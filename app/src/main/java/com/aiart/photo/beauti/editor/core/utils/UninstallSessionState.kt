package com.aiart.photo.beauti.editor.core.utils

import android.content.Context
import com.aiart.photo.beauti.editor.data.local.PreferencesManager

object UninstallSessionState {
    @Volatile
    var isUninstallActive: Boolean = false

    @Volatile
    var shouldSkipSplash: Boolean = false

    fun markUninstallActive(context: Context) {
        isUninstallActive = true
        shouldSkipSplash = true
        try {
            val prefs = PreferencesManager.getInstance(context)
            prefs.isUninstallActive = true
            prefs.shouldSkipSplashOnce = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun markUninstallClosed(context: Context) {
        isUninstallActive = false
        shouldSkipSplash = true
        try {
            val prefs = PreferencesManager.getInstance(context)
            prefs.isUninstallActive = false
            prefs.shouldSkipSplashOnce = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isUninstallActive(context: Context): Boolean {
        if (isUninstallActive) return true
        return try {
            PreferencesManager.getInstance(context).isUninstallActive
        } catch (e: Exception) {
            false
        }
    }
}
