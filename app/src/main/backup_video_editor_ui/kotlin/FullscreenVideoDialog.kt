package com.example.phortart_video_990.ui.screen.feature.memories

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.animation.doOnEnd
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.databinding.DialogFullscreenVideoBinding
import java.util.Locale

class FullscreenVideoDialog(
    context: Context,
    private val photos: List<String>,
    private val videoDurationSec: Float,
    private var initialTimeSec: Float,
    private val onDismissCallback: (newTimeSec: Float, isPlaying: Boolean) -> Unit
) : Dialog(context, R.style.DialogFullScreen) {

    private lateinit var binding: DialogFullscreenVideoBinding
    private var isPlaying = false
    private var currentTimeSec = initialTimeSec
    private var currentPhotoIndex = -1
    private var animator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogFullscreenVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        setupUI()
        startPlayback()
    }

    private fun setupUI() {
        val secondsPerPhoto = videoDurationSec / photos.size.coerceAtLeast(1)
        val initialIndex = (currentTimeSec / secondsPerPhoto).toInt().coerceIn(0, photos.lastIndex.coerceAtLeast(0))
        displayPhoto(initialIndex)
        updateTimeUi(currentTimeSec)

        binding.btnFullscreenClose.setOnClickListener {
            dismiss()
        }

        binding.ivFullscreenPhoto.setOnClickListener {
            togglePlayback()
        }

        binding.btnFullscreenPlayPause.setOnClickListener {
            togglePlayback()
        }

        setOnDismissListener {
            animator?.cancel()
            animator = null
            onDismissCallback(currentTimeSec, isPlaying)
        }
    }

    private fun displayPhoto(index: Int) {
        if (index !in photos.indices || index == currentPhotoIndex) return
        currentPhotoIndex = index

        binding.ivFullscreenPhoto.load(Uri.parse(photos[index])) {
            crossfade(true)
            crossfade(300)
        }
    }

    private fun startPlayback() {
        if (photos.isEmpty()) return
        isPlaying = true
        updatePlayStateUi(true)

        val remainingTime = videoDurationSec - currentTimeSec
        val remainingMs = (remainingTime * 1000L).toLong()

        animator?.cancel()
        animator = ValueAnimator.ofFloat(currentTimeSec, videoDurationSec).apply {
            duration = remainingMs
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val time = va.animatedValue as Float
                currentTimeSec = time
                updateTimeUi(time)

                val secondsPerPhoto = videoDurationSec / photos.size.coerceAtLeast(1)
                val targetIndex = (time / secondsPerPhoto).toInt().coerceIn(0, photos.lastIndex)
                displayPhoto(targetIndex)
            }
            doOnEnd {
                if (currentTimeSec >= videoDurationSec) {
                    currentTimeSec = 0f
                    displayPhoto(0)
                    startPlayback()
                }
            }
            start()
        }
    }

    private fun pausePlayback() {
        isPlaying = false
        updatePlayStateUi(false)
        animator?.cancel()
        animator = null
    }

    private fun togglePlayback() {
        if (isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    private fun updatePlayStateUi(playing: Boolean) {
        if (playing) {
            binding.btnFullscreenPlayPause.alpha = 0f
            binding.btnFullscreenPlayPause.isClickable = false
            binding.ivFullscreenPlayPauseIcon.setImageResource(R.drawable.ic_pause_small)
        } else {
            binding.btnFullscreenPlayPause.alpha = 1f
            binding.btnFullscreenPlayPause.isClickable = true
            binding.ivFullscreenPlayPauseIcon.setImageResource(R.drawable.ic_play_small)
        }
    }

    private fun updateTimeUi(timeSec: Float) {
        binding.tvFullscreenTime.text = "${formatTime(timeSec)} / ${formatTime(videoDurationSec)}"
        val progress = ((timeSec / videoDurationSec) * 1000).toInt()
        binding.fullscreenProgressBar.progress = progress
    }

    private fun formatTime(seconds: Float): String {
        val totalSecs = seconds.toInt()
        val m = totalSecs / 60
        val s = totalSecs % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }
}
