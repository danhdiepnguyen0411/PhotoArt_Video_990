package com.example.phortart_video_990.core.dialog

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.phortart_video_990.R

object AppDialogHelper {

    /**
     * Shows Save Success popup
     */
    fun showSaveSuccessDialog(activity: Activity, onDone: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_save_success, null)
        val containerView = dialogView.findViewById<LinearLayout>(R.id.llDialogContainer)
        containerView?.background = ContextCompat.getDrawable(activity, R.drawable.bg_dialog_task_removal)

        val btnDone = dialogView.findViewById<TextView>(R.id.btnDone)

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnDone.setOnClickListener {
            dialog.dismiss()
            onDone?.invoke()
        }

        dialog.show()
        dialog.window?.apply {
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.90).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    /**
     * Shows Restore Error popup
     */
    fun showRestoreErrorDialog(
        activity: Activity,
        onRetry: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ) {
        showErrorDialog(
            activity = activity,
            title = activity.getString(R.string.restore_error_title),
            subtitle = activity.getString(R.string.restore_error_desc),
            onRetry = onRetry,
            onCancel = onCancel
        )
    }

    /**
     * Shows AI Generation Error popup
     */
    fun showAiErrorDialog(
        activity: Activity,
        onRetry: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ) {
        showErrorDialog(
            activity = activity,
            title = activity.getString(R.string.ai_gen_error_title),
            subtitle = activity.getString(R.string.ai_gen_error_desc),
            onRetry = onRetry,
            onCancel = onCancel
        )
    }

    /**
     * Generic error retry dialog
     */
    fun showErrorDialog(
        activity: Activity,
        title: String,
        subtitle: String,
        onRetry: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_error_retry, null)
        val containerView = dialogView.findViewById<LinearLayout>(R.id.llDialogContainer)
        containerView?.background = ContextCompat.getDrawable(activity, R.drawable.bg_dialog_task_removal)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvSubtitle)
        val btnTryAgain = dialogView.findViewById<TextView>(R.id.btnTryAgain)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)

        tvTitle.text = title
        tvSubtitle.text = subtitle

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnTryAgain.setOnClickListener {
            dialog.dismiss()
            onRetry?.invoke()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
            onCancel?.invoke()
        }

        dialog.show()
        dialog.window?.apply {
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.90).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    /**
     * Shows No Internet popup
     */
    fun showNoInternetDialog(
        activity: Activity,
        onRetry: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_no_internet, null)
        val containerView = dialogView.findViewById<LinearLayout>(R.id.llDialogContainer)
        containerView?.background = ContextCompat.getDrawable(activity, R.drawable.bg_dialog_task_removal)

        val btnTryAgain = dialogView.findViewById<TextView>(R.id.btnTryAgain)
        val btnDismiss = dialogView.findViewById<TextView>(R.id.btnDismiss)

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnTryAgain.setOnClickListener {
            dialog.dismiss()
            onRetry?.invoke()
        }

        btnDismiss.setOnClickListener {
            dialog.dismiss()
            onDismiss?.invoke()
        }

        dialog.show()
        dialog.window?.apply {
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.90).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    /**
     * Shows Rate App popup
     */
    fun showRateDialog(activity: FragmentActivity, onRated: (() -> Unit)? = null) {
        RateAppDialogFragment.newInstance(onRated).show(activity.supportFragmentManager, "rate_dialog")
    }

    /**
     * Shows Exit App popup
     */
    fun showExitDialog(activity: FragmentActivity, onExit: (() -> Unit)? = null) {
        ExitAppDialogFragment.newInstance(onExit).show(activity.supportFragmentManager, "exit_dialog")
    }
}
