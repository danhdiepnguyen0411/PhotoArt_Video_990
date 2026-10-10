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
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_DENIED_COUNT, 0)
    }

    fun incrementDeniedCount(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_DENIED_COUNT, 0)
        prefs.edit().putInt(KEY_DENIED_COUNT, current + 1).apply()
    }

    fun resetDeniedCount(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DENIED_COUNT, 0).apply()
    }

    fun shouldShowNotificationDialog(context: Context): Boolean {
        if (suppressNotificationDialog) return false
        if (isNotificationPermissionGranted(context)) return false
        if (hasShownNotificationDialogThisSession) return false
        if (isUserRevoked(context)) return true
        if (systemPermissionRequestedThisSession) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
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
        monitoringJob?.cancel()
        val appContext = context.applicationContext

        val initialPostNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        }
        val initialNotifEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()

        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            delay(500)
            var count = 0
            while (count < 150) { // poll for ~60s
                delay(400)
                count++

                val currentPostNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        appContext, android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    NotificationManagerCompat.from(appContext).areNotificationsEnabled()
                }
                val currentNotifEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()

                val newlyGrantedPostNotif = currentPostNotif && !initialPostNotif
                val newlyEnabledNotif = currentNotifEnabled && !initialNotifEnabled
                val allGrantedNow = currentPostNotif && currentNotifEnabled

                if (newlyGrantedPostNotif || newlyEnabledNotif || (allGrantedNow && (!initialPostNotif || !initialNotifEnabled))) {
                    withContext(Dispatchers.Main) {
                        bringToFront(appContext, targetActivityClass)
                        onGranted?.invoke()
                    }
                    break
                }
            }
        }
    }

    fun bringToFront(context: Context, targetActivityClass: Class<*> = MainActivity::class.java) {
        try {
            val intent = Intent(context, targetActivityClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = android.app.ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
                val pendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                pendingIntent.send(options.toBundle())
            } else {
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            try {
                val intent = Intent(context, targetActivityClass).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun stopMonitoring() {
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
            val intent = createSettingsIntent(context)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            startMonitoringPermissionAndBringToFront(context, targetActivityClass, onGranted)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openAppSettings(context: Context, onGranted: (() -> Unit)? = null) {
        val targetClass = if (context is Activity) context.javaClass else MainActivity::class.java
        openAppSettings(context, targetClass, onGranted)
    }

    fun openNotificationSettings(
        context: Context,
        targetActivityClass: Class<*>,
        onGranted: (() -> Unit)? = null
    ) {
        try {
            isAwaitingSettingsResult = true
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                createSettingsIntent(context)
            }
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            startMonitoringPermissionAndBringToFront(context, targetActivityClass, onGranted)
        } catch (e: Exception) {
            openAppSettings(context, targetActivityClass, onGranted)
        }
    }

    fun openNotificationSettings(context: Context, onGranted: (() -> Unit)? = null) {
        val targetClass = if (context is Activity) context.javaClass else MainActivity::class.java
        openNotificationSettings(context, targetClass, onGranted)
    }
}
