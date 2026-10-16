package com.example.phortart_video_990.core.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.phortart_video_990.MainActivity
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.dialog.NotificationDialogHelper
import com.example.phortart_video_990.core.utils.LocaleHelper
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.data.model.TemplateModel

object NotificationScheduler {

    /**
     * Schedules inactivity reminder notifications for 3 days, 7 days, and 14 days.
     * Called when:
     * - Notification permission is granted
     * - User opens or resumes the app (resets the inactivity timer)
     * - Device finishes rebooting
     */
    fun scheduleInactivityNotifications(context: Context) {
        val prefs = PreferencesManager.getInstance(context)
        prefs.lastActiveTime = System.currentTimeMillis()

        if (!NotificationDialogHelper.isNotificationGranted(context)) {
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val daysList = listOf(3, 7, 14)
        for (days in daysList) {
            val triggerAtMillis = System.currentTimeMillis() + (days * 24L * 60L * 60L * 1000L)
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.ACTION_TRIGGER_INACTIVITY
                putExtra(NotificationReceiver.EXTRA_DAYS, days)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1000 + days,
                intent,
                flags
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } catch (e: Exception) {
                try {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } catch (_: Exception) {}
            }
        }
    }

    fun cancelInactivityNotifications(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val daysList = listOf(3, 7, 14)
        for (days in daysList) {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.ACTION_TRIGGER_INACTIVITY
                putExtra(NotificationReceiver.EXTRA_DAYS, days)
            }
            val flags = PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1000 + days,
                intent,
                flags
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    /**
     * Checks if new templates have arrived from the API.
     * If new templates exist and permission is granted, posts the new template notification.
     */
    fun checkAndNotifyNewTemplates(context: Context, templates: List<TemplateModel>) {
        if (templates.isEmpty()) return
        val prefs = PreferencesManager.getInstance(context)
        val knownIds = prefs.getKnownTemplateIds()
        val currentIds = templates.map { it.id.toString() }.toSet()

        if (knownIds.isEmpty()) {
            // Initial caching - don't spam on fresh install
            prefs.saveKnownTemplateIds(currentIds)
            return
        }

        val newIds = currentIds.filter { !knownIds.contains(it) }
        if (newIds.isNotEmpty()) {
            // New templates found!
            prefs.saveKnownTemplateIds(knownIds + currentIds)
            showNewTemplateNotification(context)
        }
    }

    fun showNewTemplateNotification(context: Context) {
        if (!NotificationDialogHelper.isNotificationGranted(context)) return

        val localizedContext = LocaleHelper.getDeviceLocalizedContext(context)
        val title = localizedContext.getString(R.string.noti_new_template_title)
        val body = localizedContext.getString(R.string.noti_new_template_body)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NotificationReceiver.CHANNEL_ID,
                NotificationReceiver.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders and updates for PhotoArt Video"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(NotificationReceiver.EXTRA_TARGET_SCREEN, "discover")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NotificationReceiver.NOTI_ID_NEW_TEMPLATE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, NotificationReceiver.CHANNEL_ID)
            .setSmallIcon(R.drawable.photo_ai_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(NotificationReceiver.NOTI_ID_NEW_TEMPLATE, builder.build())
        } catch (_: SecurityException) {}
    }
}
