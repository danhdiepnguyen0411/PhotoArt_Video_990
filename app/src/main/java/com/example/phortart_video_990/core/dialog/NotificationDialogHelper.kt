package com.example.phortart_video_990.core.dialog

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.utils.NotificationPermissionManager

object NotificationDialogHelper {

    private var activeDialog: AlertDialog? = null
    private var activeActivityRef: java.lang.ref.WeakReference<Activity>? = null

    fun isShowing(): Boolean = activeDialog?.isShowing == true

    fun showNotificationDialog(
        activity: Activity,
        onEnable: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (activeDialog?.isShowing == true) return

        NotificationPermissionManager.hasShownNotificationDialogThisSession = true
        activeActivityRef = java.lang.ref.WeakReference(activity)

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_notification_permission, null)

        val containerView = dialogView.findViewById<LinearLayout>(R.id.llDialogContainer)
        containerView?.background = ContextCompat.getDrawable(
            activity,
            R.drawable.bg_dialog_task_removal
        )

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvSubtitle)
        val btnGoToSettings = dialogView.findViewById<TextView>(R.id.btnGoToSettings)
        val btnDontAllow = dialogView.findViewById<TextView>(R.id.btnDontAllow)

        tvTitle?.text = activity.getString(R.string.enable_notifications_title)
        tvSubtitle?.text = activity.getString(R.string.enable_notifications_desc)
        btnGoToSettings?.text = activity.getString(R.string.btn_go_to_settings)
        btnDontAllow?.text = activity.getString(R.string.btn_cancel)

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.setCanceledOnTouchOutside(false)

        btnGoToSettings?.setOnClickListener {
            dialog.dismiss()
            activeDialog = null
            activeActivityRef = null
            NotificationPermissionManager.openNotificationSettings(activity, activity.javaClass) {
                dismissNotificationDialog(activity)
                onEnable?.invoke()
            }
        }

        btnDontAllow?.setOnClickListener {
            dialog.dismiss()
            activeDialog = null
            activeActivityRef = null
            onDismiss?.invoke()
        }

        dialog.setOnDismissListener {
            if (activeDialog == dialog) {
                activeDialog = null
                activeActivityRef = null
            }
        }

        activeDialog = dialog
        dialog.show()
        dialog.window?.apply {
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.90).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    fun dismissNotificationDialog(forActivity: Activity? = null) {
        val currentOwner = activeActivityRef?.get()
        if (forActivity != null && currentOwner != null && currentOwner !== forActivity) {
            return
        }
        if (activeDialog?.isShowing == true) {
            try {
                activeDialog?.dismiss()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeDialog = null
        activeActivityRef = null
    }
}
