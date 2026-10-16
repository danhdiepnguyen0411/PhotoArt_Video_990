package com.aiart.photo.beauti.editor.ui.screen.feature.music

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos

/**
 * Animated Audio Waveform Visualizer matching Photo AI design.
 * Renders 14 vertical gradient pill-shaped bars that oscillate smoothly
 * when audio is playing.
 */
class AudioWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barCount = 14
    // Base height fractions corresponding to Photo AI .fl-wave: 45%, 85%, 60%, 100%
    private val baseFractions = floatArrayOf(0.45f, 0.85f, 0.60f, 1.00f)

    // Gradient colors: #6F8CF2 (top) to #B57AF0 (bottom)
    private val startColor = Color.parseColor("#6F8CF2")
    private val endColor = Color.parseColor("#B57AF0")

    private var isPlaying: Boolean = false
    private var animFraction: Float = 0f
    private var animator: ValueAnimator? = null

    init {
        setupAnimator()
    }

    private fun setupAnimator() {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                animFraction = va.animatedValue as Float
                invalidate()
            }
        }
    }

    fun setPlaying(playing: Boolean) {
        if (isPlaying == playing) return
        isPlaying = playing
        if (playing) {
            if (isAttachedToWindow && visibility == VISIBLE) {
                if (animator?.isStarted != true) {
                    animator?.start()
                } else if (animator?.isPaused == true) {
                    animator?.resume()
                }
            }
        } else {
            animator?.pause()
            invalidate()
        }
    }

    fun isPlaying(): Boolean = isPlaying

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            paint.shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                startColor, endColor,
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isPlaying && visibility == VISIBLE && animator?.isStarted != true) {
            animator?.start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isPlaying) {
            if (animator?.isStarted != true) {
                animator?.start()
            } else if (animator?.isPaused == true) {
                animator?.resume()
            }
        } else {
            animator?.pause()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density = resources.displayMetrics.density
        val barWidth = 2.4f * density
        val barGap = 2.0f * density
        val totalWaveWidth = barCount * barWidth + (barCount - 1) * barGap
        val startX = (w - totalWaveWidth) / 2f
        val centerY = h / 2f
        val maxH = h * 0.95f
        val radius = barWidth / 2f

        for (i in 0 until barCount) {
            val baseH = baseFractions[i % 4]
            val scale = if (isPlaying) {
                // Delay each bar by 70ms out of 1100ms total per Photo AI specification
                val delayOffset = (i * 70f) / 1100f
                val phase = (animFraction + delayOffset) % 1f
                // Smooth sine wave between 0.40 and 1.00 (ease-in-out)
                val factor = (0.5 - 0.5 * cos(2.0 * Math.PI * phase)).toFloat()
                0.40f + 0.60f * factor
            } else {
                0.40f
            }

            val barHeight = (maxH * baseH * scale).coerceAtLeast(barWidth)
            val left = startX + i * (barWidth + barGap)
            val right = left + barWidth
            val top = centerY - barHeight / 2f
            val bottom = centerY + barHeight / 2f

            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        }
    }
}
