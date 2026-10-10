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
    private var titleText: String = "Đang xử lý...",
    private var subtitleText: String? = "Vui lòng chờ trong giây lát",
    private val isCancelableByUser: Boolean = false
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

        setCancelable(isCancelableByUser)
        setCanceledOnTouchOutside(isCancelableByUser)

        updateTexts()
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
        binding.tvLoadingTitle.text = titleText
        if (!subtitleText.isNullOrBlank()) {
            binding.tvLoadingSubtitle.text = subtitleText
            binding.tvLoadingSubtitle.visibility = View.VISIBLE
        } else {
            binding.tvLoadingSubtitle.visibility = View.GONE
        }
    }

    companion object {
        fun show(
            context: Context,
            title: String = "Đang xử lý...",
            subtitle: String? = "Vui lòng chờ trong giây lát",
            cancelable: Boolean = false
        ): AppLoadingDialog {
            val dialog = AppLoadingDialog(context, title, subtitle, cancelable)
            try {
                dialog.show()
            } catch (_: Exception) {}
            return dialog
        }
    }
}
