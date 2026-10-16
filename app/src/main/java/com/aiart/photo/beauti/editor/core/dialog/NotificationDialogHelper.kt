package com.aiart.photo.beauti.editor.core.dialog

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.core.notification.NotificationScheduler
import com.aiart.photo.beauti.editor.core.utils.NotificationPermissionManager
import com.aiart.photo.beauti.editor.core.utils.PermissionReturnHelper
import com.aiart.photo.beauti.editor.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Helper object for checking notification permission, showing
 * the custom notification permission dialog, and automatically returning
 * to the app as soon as permission is granted in Settings.
 */
object NotificationDialogHelper {

    private var currentDialog: Dialog? = null
    private var pollingJob: Job? = null

    fun isShowing(): Boolean = currentDialog?.isShowing == true

    /**
     * Checks if notification permission is currently granted and enabled.
     */
    fun isNotificationGranted(context: Context): Boolean {
        return NotificationPermissionManager.isNotificationPermissionGranted(context)
    }

    /**
     * Shows the custom notification permission dialog matching Figma/design specs.
     */
    fun showNotificationDialog(
        activity: Activity,
        onEnable: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        // Prevent multiple duplicate dialogs from appearing simultaneously
        if (currentDialog?.isShowing == true) return

        NotificationPermissionManager.hasShownNotificationDialogThisSession = true

        val dialog = Dialog(activity)
        currentDialog = dialog
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_notification_permission, null)
        dialog.setContentView(view)

        setupDialogWindow(dialog, activity)

        var isGoingToSettings = false

        // "Go to settings" action button
        view.findViewById<View>(R.id.btnGoToSettings)?.setOnClickListener {
            isGoingToSettings = true
            dialog.dismiss()
            PreferencesManager.getInstance(activity).shouldShowNotiDialogOnHome = false
            PermissionReturnHelper.openNotificationSettings(activity)
            startPollingForNotificationPermission(activity, onEnable)
        }

        // "Don't allow / Cancel" action button
        view.findViewById<View>(R.id.btnDontAllow)?.setOnClickListener {
            PreferencesManager.getInstance(activity).shouldShowNotiDialogOnHome = false
            dialog.dismiss()
            onDismiss?.invoke()
        }

        dialog.setOnDismissListener {
            if (currentDialog == dialog) {
                currentDialog = null
            }
            if (!isGoingToSettings) {
                onDismiss?.invoke()
            }
        }

        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        setupDialogWindow(dialog, activity)
    }

    fun showCustomDialog(
        activity: Activity,
        onPermissionGranted: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        showNotificationDialog(activity, onPermissionGranted, onDismiss)
    }

    /**
     * Polls in background while in Settings. As soon as user grants notification permission,
     * automatically brings the app back to the foreground.
     */
    fun startPollingForNotificationPermission(
        context: Context,
        onGranted: (() -> Unit)? = null
    ) {
        pollingJob?.cancel()
        pollingJob = CoroutineScope(Dispatchers.IO + Job()).launch {
            val startTime = System.currentTimeMillis()
            while (isActive && System.currentTimeMillis() - startTime < 120_000L) {
                delay(200L)
                if (isNotificationGranted(context)) {
                    withContext(Dispatchers.Main) {
                        PreferencesManager.getInstance(context).isNotificationEnabled = true
                        PreferencesManager.getInstance(context).notificationDeniedCount = 0
                        NotificationPermissionManager.setPreviouslyGranted(context, true)
                        NotificationPermissionManager.setUserRevoked(context, false)
                        NotificationPermissionManager.resetDeniedCount(context)
                        NotificationScheduler.scheduleInactivityNotifications(context)

                        PermissionReturnHelper.bringAppToFront(context)
                        onGranted?.invoke()
                    }
                    break
                }
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun dismissNotificationDialog(forActivity: Activity? = null) {
        if (currentDialog?.isShowing == true) {
            try {
                currentDialog?.dismiss()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        currentDialog = null
    }

    private fun setupDialogWindow(dialog: Dialog, context: Context) {
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = window.attributes.also { it.dimAmount = 0.5f }

            val metrics = context.resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val horizontalMargin = (32 * metrics.density).toInt()
            val maxWidth = (340 * metrics.density).toInt()
            val widthPx = (screenWidth - horizontalMargin * 2).coerceAtMost(maxWidth)

            window.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}
