package com.aiart.photo.beauti.editor.core.utils

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout

/**
 * Layout container that projects an animated sweeping white shimmer light beam
 * over its child views (cards, skeleton bars, placeholders) while data is loading.
 */
class SkeletonShimmerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }

    private var animator: ValueAnimator? = null
    private var shimmerProgress = -1.2f
    private var isShimmering = false

    init {
        setWillNotDraw(false)
    }

    fun startShimmer() {
        if (isShimmering) return
        isShimmering = true
        animator?.cancel()
        animator = ValueAnimator.ofFloat(-1.2f, 1.8f).apply {
            duration = 1300L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                shimmerProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopShimmer() {
        isShimmering = false
        animator?.cancel()
        animator = null
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!isShimmering || width <= 0 || height <= 0) {
            super.dispatchDraw(canvas)
            return
        }

        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val beamWidth = (w * 0.85f).coerceAtLeast(300f)
        val startX = w * shimmerProgress
        val endX = startX + beamWidth

        shimmerPaint.shader = LinearGradient(
            startX, 0f,
            endX, h * 0.4f,
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

        canvas.drawRect(0f, 0f, w, h, shimmerPaint)
        canvas.restoreToCount(saveCount)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == View.VISIBLE) {
            startShimmer()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopShimmer()
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        if (visibility == View.VISIBLE) {
            startShimmer()
        } else {
            stopShimmer()
        }
    }
}
