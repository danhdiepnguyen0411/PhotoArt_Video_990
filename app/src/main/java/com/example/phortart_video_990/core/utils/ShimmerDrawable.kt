package com.example.phortart_video_990.core.utils

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

/**
 * Animated Shimmer Light Beam Drawable for loading template cards & images.
 * Provides a clean solid background with a white light beam sweeping across diagonally.
 */
class ShimmerDrawable(
    private val cornerRadius: Float = 0f,
    private val baseColor: Int = Color.parseColor("#EFF2F7")
) : Drawable(), Animatable {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null
    private var progress = -1f
    private val rectF = RectF()

    init {
        start()
    }

    override fun start() {
        if (animator?.isRunning == true) return
        animator?.cancel()
        animator = ValueAnimator.ofFloat(-1.2f, 1.8f).apply {
            duration = 1300L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    override fun stop() {
        animator?.cancel()
        animator = null
        invalidateSelf()
    }

    override fun isRunning(): Boolean = animator?.isRunning == true

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return

        rectF.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())

        // 1. Draw base light grey/white background
        paint.shader = null
        paint.color = baseColor
        if (cornerRadius > 0f) {
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
        } else {
            canvas.drawRect(rectF, paint)
        }

        // 2. Draw sweeping diagonal white light beam
        val beamWidth = w * 0.9f
        val startX = b.left + (w * progress)
        val endX = startX + beamWidth

        val shader = LinearGradient(
            startX, b.top.toFloat(),
            endX, b.top + (h * 0.35f),
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(80, 255, 255, 255),
                Color.argb(220, 255, 255, 255),
                Color.argb(80, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.35f, 0.5f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )

        paint.shader = shader
        if (cornerRadius > 0f) {
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
        } else {
            canvas.drawRect(rectF, paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
