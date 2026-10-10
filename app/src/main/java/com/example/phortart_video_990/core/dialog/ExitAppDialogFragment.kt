package com.example.phortart_video_990.core.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.phortart_video_990.R

class ExitAppDialogFragment : DialogFragment() {

    var onExitClick: (() -> Unit)? = null

    companion object {
        fun newInstance(onExit: (() -> Unit)? = null): ExitAppDialogFragment {
            return ExitAppDialogFragment().apply {
                this.onExitClick = onExit
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_exit_app, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.background = androidx.core.content.ContextCompat.getDrawable(
            requireContext(),
            R.drawable.bg_dialog_task_removal
        )

        val btnExit = view.findViewById<TextView>(R.id.btnExit)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)

        btnExit?.setOnClickListener {
            dismiss()
            onExitClick?.invoke()
        }

        btnCancel?.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.90).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }
}
