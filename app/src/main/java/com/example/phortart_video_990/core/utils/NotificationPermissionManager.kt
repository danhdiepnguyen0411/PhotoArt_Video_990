package com.example.phortart_video_990.core.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.phortart_video_990.MainActivity
import com.example.phortart_video_990.core.dialog.NotificationDialogHelper
import kotlinx.coroutines.*

object NotificationPermissionManager {

    private const val PREF_NAME = "notification_permission_prefs"
    private const val KEY_DENIED_COUNT = "denied_count"
    private const val KEY_PREVIOUSLY_GRANTED = "previously_granted"
    private const val KEY_USER_REVOKED = "user_revoked"

    var hasShownNotificationDialogThisSession = false
    var systemPermissionRequestedThisSession = false
    var suppressNotificationDialog = false
    var isAppInBackground = false
        private set
    var isAwaitingSettingsResult = false

    private var monitoringJob: Job? = null

    fun resetSessionState() {
        hasShownNotificationDialogThisSession = false
        systemPermissionRequestedThisSession = false
        suppressNotificationDialog = false
        isAwaitingSettingsResult = false
    }

    fun onAppBackgrounded() {
        isAppInBackground = true
    }

    fun onAppForegrounded(context: Context) {
        isAppInBackground = false

        val isGranted = isNotificationPermissionGranted(context)
        if (isGranted) {
            resetDeniedCount(context)
            stopMonitoring()
            isAwaitingSettingsResult = false
        } else {
            if (isAwaitingSettingsResult) {
                isAwaitingSettingsResult = false
            }
        }
    }

    fun isPreviouslyGranted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PREVIOUSLY_GRANTED, false)
    }

    fun setPreviouslyGranted(context: Context, granted: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PREVIOUSLY_GRANTED, granted).apply()
    }

    fun isUserRevoked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USER_REVOKED, false)
    }

    fun setUserRevoked(context: Context, revoked: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_USER_REVOKED, revoked).apply()
    }

    fun isNotificationPermissionGranted(context: Context): Boolean {
        val areNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val isPostNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val granted = areNotificationsEnabled && isPostNotificationGranted
        if (granted) {
            setPreviouslyGranted(context, true)
            setUserRevoked(context, false)
        } else {
            if (isPreviouslyGranted(context)) {
                setUserRevoked(context, true)
            }
        }
        return granted
    }

    fun getDeniedCount(context: Context): Int {
        return com.example.phortart_video_990.data.local.PreferencesManager.getInstance(context).notificationDeniedCount
    }

    fun incrementDeniedCount(context: Context) {
        val appPrefs = com.example.phortart_video_990.data.local.PreferencesManager.getInstance(context)
        appPrefs.notificationDeniedCount++
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DENIED_COUNT, appPrefs.notificationDeniedCount).apply()
    }

    fun resetDeniedCount(context: Context) {
        com.example.phortart_video_990.data.local.PreferencesManager.getInstance(context).notificationDeniedCount = 0
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DENIED_COUNT, 0).apply()
    }

    fun shouldShowNotificationDialog(context: Context): Boolean {
        if (suppressNotificationDialog) return false
        if (isNotificationPermissionGranted(context)) return false
        if (hasShownNotificationDialogThisSession) return false
        if (systemPermissionRequestedThisSession) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        if (isUserRevoked(context)) return true
        return getDeniedCount(context) >= 2
    }

    fun createSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }

    fun startMonitoringPermissionAndBringToFront(
        context: Context,
        targetActivityClass: Class<*> = MainActivity::class.java,
        onGranted: (() -> Unit)? = null
    ) {
        NotificationDialogHelper.startPollingForNotificationPermission(context, onGranted)
    }

    fun bringToFront(context: Context, targetActivityClass: Class<*> = MainActivity::class.java) {
        PermissionReturnHelper.bringAppToFront(context)
    }

    fun stopMonitoring() {
        NotificationDialogHelper.stopPolling()
        monitoringJob?.cancel()
        monitoringJob = null
        isAwaitingSettingsResult = false
    }

    fun openAppSettings(
        context: Context,
        targetActivityClass: Class<*>,
        onGranted: (() -> Unit)? = null
    ) {
        try {
            isAwaitingSettingsResult = true
            PermissionReturnHelper.openAppSettings(context)
            NotificationDialogHelper.startPollingForNotificationPermission(context, onGranted)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openAppSettings(context: Context, onGranted: (() -> Unit)? = null) {
        openAppSettings(context, MainActivity::class.java, onGranted)
    }

    fun openNotificationSettings(
        context: Context,
        targetActivityClass: Class<*>,
        onGranted: (() -> Unit)? = null
    ) {
        try {
            isAwaitingSettingsResult = true
            PermissionReturnHelper.openNotificationSettings(context)
            NotificationDialogHelper.startPollingForNotificationPermission(context, onGranted)
        } catch (e: Exception) {
            openAppSettings(context, targetActivityClass, onGranted)
        }
    }

    fun openNotificationSettings(context: Context, onGranted: (() -> Unit)? = null) {
        openNotificationSettings(context, MainActivity::class.java, onGranted)
    }
}
