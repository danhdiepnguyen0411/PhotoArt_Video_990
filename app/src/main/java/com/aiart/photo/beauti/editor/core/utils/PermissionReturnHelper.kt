package com.aiart.photo.beauti.editor.core.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.aiart.photo.beauti.editor.MainActivity

/**
 * Robust helper to open App Settings and bring the app back to the foreground
 * immediately when the user grants permissions (Notification, Storage, etc.).
 *
 * Implements the proven triple-approach pattern:
 * 1. Open ACTION_APP_NOTIFICATION_SETTINGS or ACTION_APPLICATION_DETAILS_SETTINGS
 *    without FLAG_ACTIVITY_NEW_TASK so Settings is placed on top of the existing task stack.
 * 2. On permission granted, launch MainActivity with CLEAR_TOP or SINGLE_TOP or NEW_TASK
 *    to immediately clear Settings and restore the app.
 * 3. Fallback to moveTaskToFront (REORDER_TASKS) and PendingIntent with
 *    MODE_BACKGROUND_ACTIVITY_START_ALLOWED for Android 14+ / OEM-specific ROMs (MIUI/HyperOS/OneUI).
 */
object PermissionReturnHelper {

    private const val TAG = "PermissionReturnHelper"

    /**
     * Opens system app settings directly on top of the app without FLAG_ACTIVITY_NEW_TASK
     * so that bringing the app back via CLEAR_TOP can pop Settings off the stack immediately.
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened ACTION_APPLICATION_DETAILS_SETTINGS for package: ${context.packageName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app details settings", e)
        }
    }

    /**
     * Opens notification settings directly on top of the app.
     * Navigates directly to the notification permission toggle page.
     */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
                    putExtra("app_package", context.packageName)
                    putExtra("app_uid", context.applicationInfo.uid)
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened notification settings for package: ${context.packageName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification settings, falling back to app details", e)
            openAppSettings(context)
        }
    }

    /**
     * Brings the app back to foreground immediately when permission is granted.
     */
    fun bringAppToFront(context: Context) {
        val appContext = context.applicationContext ?: context
        Log.d(TAG, "Attempting to bring app to front from background...")

        // Approach 1 (Primary - proven in Magic Editor / TryOn):
        // Launch MainActivity with CLEAR_TOP + SINGLE_TOP + NEW_TASK to immediately pop Settings and resume app
        try {
            val reopenIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
            context.startActivity(reopenIntent)
            Log.d(TAG, "Approach 1 (CLEAR_TOP + SINGLE_TOP + NEW_TASK) dispatched successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Approach 1 failed", e)
        }

        // Approach 2: ActivityManager.moveTaskToFront (Requires android.permission.REORDER_TASKS)
        try {
            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val activity = context as? Activity
            val taskInfo = am?.appTasks?.firstOrNull()?.taskInfo
            val taskInfoId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                taskInfo?.taskId
            } else {
                @Suppress("DEPRECATION")
                taskInfo?.id
            }
            val targetTaskId = activity?.taskId ?: taskInfoId

            if (targetTaskId != null && targetTaskId != -1) {
                Log.d(TAG, "Moving taskId $targetTaskId to front via moveTaskToFront")
                am?.moveTaskToFront(targetTaskId, ActivityManager.MOVE_TASK_WITH_HOME)
            }

            am?.appTasks?.forEach { task ->
                try {
                    task.moveToFront()
                } catch (e: Exception) {
                    Log.w(TAG, "appTask.moveToFront failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Approach 2 (moveTaskToFront) failed", e)
        }

        // Approach 3: PendingIntent with background activity start mode allowed (Android 14+ / OEM ROMs)
        try {
            val bringIntent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                appContext,
                2001,
                bringIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
                pendingIntent.send(appContext, 0, null, null, null, null, options.toBundle())
            } else {
                pendingIntent.send()
            }
            Log.d(TAG, "Approach 3 (PendingIntent.send) dispatched successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Approach 3 (PendingIntent) failed", e)
        }
    }
}
