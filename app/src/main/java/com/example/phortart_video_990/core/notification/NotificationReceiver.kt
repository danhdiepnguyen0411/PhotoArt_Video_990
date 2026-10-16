package com.example.phortart_video_990.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
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

class NotificationReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "photoart_reminder_channel"
        const val CHANNEL_NAME = "PhotoArt Reminders"
        const val ACTION_TRIGGER_INACTIVITY = "com.example.phortart_video_990.ACTION_TRIGGER_INACTIVITY_NOTIFICATION"
        const val EXTRA_DAYS = "extra_days"
        const val EXTRA_TARGET_SCREEN = "target_screen"

        const val EXTRA_FORCE = "extra_force"

        const val NOTI_ID_NEW_TEMPLATE = 1001
        const val NOTI_ID_INACTIVITY_3D = 1003
        const val NOTI_ID_INACTIVITY_7D = 1007
        const val NOTI_ID_INACTIVITY_14D = 1014
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            if (NotificationDialogHelper.isNotificationGranted(context)) {
                NotificationScheduler.scheduleInactivityNotifications(context)
            }
            return
        }

        if (ACTION_TRIGGER_INACTIVITY == intent.action) {
            val days = intent.getIntExtra(EXTRA_DAYS, 0)
            if (days <= 0) return

            // Ensure notification permission is currently granted
            if (!NotificationDialogHelper.isNotificationGranted(context)) {
                return
            }

            val isForce = intent.getBooleanExtra(EXTRA_FORCE, false)
            val prefs = PreferencesManager.getInstance(context)
            val lastActive = prefs.lastActiveTime
            val now = System.currentTimeMillis()

            // If user has opened the app recently within this inactivity period, do not fire (unless forced for testing)
            val thresholdMillis = (days * 24L * 60L * 60L * 1000L) - (2L * 60L * 60L * 1000L)
            if (!isForce && lastActive > 0 && (now - lastActive) < thresholdMillis) {
                return
            }

            // Always resolve localized strings using device system language
            val localizedContext = LocaleHelper.getDeviceLocalizedContext(context)

            val (titleRes, bodyRes, targetScreen, notiId) = when (days) {
                3 -> NotificationData(
                    R.string.noti_remind_create_title,
                    R.string.noti_remind_create_body,
                    "home",
                    NOTI_ID_INACTIVITY_3D
                )
                7 -> NotificationData(
                    R.string.noti_suggest_template_title,
                    R.string.noti_suggest_template_body,
                    "discover",
                    NOTI_ID_INACTIVITY_7D
                )
                14 -> NotificationData(
                    R.string.noti_suggest_memories_title,
                    R.string.noti_suggest_memories_body,
                    "memories",
                    NOTI_ID_INACTIVITY_14D
                )
                else -> return
            }

            val title = localizedContext.getString(titleRes)
            val body = localizedContext.getString(bodyRes)

            showNotification(context, title, body, targetScreen, notiId)
        }
    }

    private data class NotificationData(
        val titleRes: Int,
        val bodyRes: Int,
        val targetScreen: String,
        val notificationId: Int
    )

    private fun showNotification(
        context: Context,
        title: String,
        body: String,
        targetScreen: String,
        notificationId: Int
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
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
            putExtra(EXTRA_TARGET_SCREEN, targetScreen)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.photo_ai_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {}
    }
}
