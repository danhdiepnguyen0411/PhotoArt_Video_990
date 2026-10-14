package com.example.phortart_video_990.core.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import com.example.phortart_video_990.databinding.DialogAppLoadingBinding

class AppLoadingDialog(
    context: Context,
    private var titleText: String? = null,
    private var subtitleText: String? = null,
    private var isCancelableByUser: Boolean = false,
    private var onCancelAction: (() -> Unit)? = null
) : Dialog(context) {

    private lateinit var binding: DialogAppLoadingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogAppLoadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.6f)
        }

        applyCancelableState()
        updateTexts()
    }

    private fun applyCancelableState() {
        // Tuyệt đối không cho phép bấm ra bên ngoài dialog để thoát
        setCanceledOnTouchOutside(false)
        setCancelable(false)

        if (::binding.isInitialized) {
            binding.btnCancelLoading.visibility = if (isCancelableByUser) View.VISIBLE else View.GONE
            binding.btnCancelLoading.setOnClickListener {
                dismiss()
                onCancelAction?.invoke()
            }
        }
    }

    fun setCancelAction(cancelable: Boolean, action: (() -> Unit)? = null) {
        isCancelableByUser = cancelable
        if (action != null) {
            onCancelAction = action
        }
        applyCancelableState()
    }

    fun updateMessage(title: String, subtitle: String? = null) {
        titleText = title
        if (subtitle != null) {
            subtitleText = subtitle
        }
        if (::binding.isInitialized) {
            updateTexts()
        }
    }

    private fun updateTexts() {
        val effectiveTitle = titleText?.ifBlank { null } ?: context.getString(com.example.phortart_video_990.R.string.loading_creating_video)
        val effectiveSubtitle = subtitleText?.ifBlank { null } ?: context.getString(com.example.phortart_video_990.R.string.loading_please_wait)

        binding.tvLoadingTitle.text = effectiveTitle
        binding.tvLoadingSubtitle.text = effectiveSubtitle
        binding.tvLoadingSubtitle.visibility = View.VISIBLE
    }

    companion object {
        fun show(
            context: Context,
            title: String? = null,
            subtitle: String? = null,
            cancelable: Boolean = false,
            onCancel: (() -> Unit)? = null
        ): AppLoadingDialog {
            val dialog = AppLoadingDialog(context, title, subtitle, cancelable, onCancel)
            try {
                dialog.show()
            } catch (_: Exception) {}
            return dialog
        }
    }
}
