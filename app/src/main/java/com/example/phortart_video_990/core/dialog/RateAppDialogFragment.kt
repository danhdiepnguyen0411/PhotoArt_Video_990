package com.example.phortart_video_990.core.dialog

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.phortart_video_990.R
import com.example.phortart_video_990.data.local.PreferencesManager

class RateAppDialogFragment : DialogFragment() {

    private var currentRating: Int = 5
    private var onRatedListener: (() -> Unit)? = null

    companion object {
        fun newInstance(onRated: (() -> Unit)? = null): RateAppDialogFragment {
            return RateAppDialogFragment().apply {
                this.onRatedListener = onRated
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
        return inflater.inflate(R.layout.dialog_rate_app, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imgRatingFace = view.findViewById<ImageView>(R.id.imgRatingFace)
        val btnSubmit = view.findViewById<TextView>(R.id.btnSubmit)
        val btnNotNow = view.findViewById<TextView>(R.id.btnNotNow)

        val stars = arrayOf(
            view.findViewById<ImageView>(R.id.ivStar1),
            view.findViewById<ImageView>(R.id.ivStar2),
            view.findViewById<ImageView>(R.id.ivStar3),
            view.findViewById<ImageView>(R.id.ivStar4),
            view.findViewById<ImageView>(R.id.ivStar5)
        )

        fun updateRatingUI(rating: Int) {
            val faceRes = when (rating) {
                1 -> R.drawable.ic_1star
                2 -> R.drawable.ic_2star
                3 -> R.drawable.ic_3star
                4 -> R.drawable.ic_4star
                else -> R.drawable.ic_5star
            }
            imgRatingFace.setImageResource(faceRes)

            for (i in stars.indices) {
                stars[i].colorFilter = null
                if (i < rating) {
                    stars[i].setImageResource(R.drawable.ic_rate_star_filled)
                } else {
                    stars[i].setImageResource(R.drawable.ic_rate_star_outline)
                }
            }
        }

        updateRatingUI(currentRating)

        for (i in stars.indices) {
            stars[i].setOnClickListener {
                currentRating = i + 1
                updateRatingUI(currentRating)
            }
        }

        btnSubmit.setOnClickListener {
            val ctx = requireContext()
            // Mark as rated in PreferencesManager and legacy app_settings
            PreferencesManager.getInstance(ctx).hasRatedApp = true
            val legacyPrefs = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            legacyPrefs.edit().putBoolean("has_rated_app", true).apply()

            // Notify user rating was successful
            Toast.makeText(ctx, getString(R.string.rate_success), Toast.LENGTH_SHORT).show()

            // Invoke callback to update UI (hide rate option in settings)
            onRatedListener?.invoke()

            dismiss()
        }

        btnNotNow.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }
}
