package com.example.phortart_video_990.core.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class PillSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val trackWidth = 44f * density
    private val trackHeight = 25f * density
    private val thumbRadius = 10f * density
    private val thumbPadding = 2.5f * density

    private val trackRect = RectF()
    private val baseTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DBE4F3")
    }
    private val checkedTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(2.5f * density, 0f, 1.2f * density, Color.parseColor("#26000000"))
    }

    private var progress: Float = 1f // 1f = checked, 0f = unchecked
    private var animator: ValueAnimator? = null

    var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    var isChecked: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                animateProgress(if (value) 1f else 0f)
            }
        }

    fun setCheckedSilently(value: Boolean) {
        isChecked = value
        progress = if (value) 1f else 0f
        invalidate()
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        isFocusable = true
        progress = if (isChecked) 1f else 0f

        setOnClickListener {
            toggle()
        }
    }

    fun toggle() {
        isChecked = !isChecked
        onCheckedChangeListener?.invoke(isChecked)
    }

    private fun animateProgress(target: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (trackWidth + paddingLeft + paddingRight).toInt()
        val h = (trackHeight + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(
            resolveSize(w, widthMeasureSpec),
            resolveSize(h, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = paddingLeft.toFloat()
        val top = paddingTop + (height - paddingBottom - paddingTop - trackHeight) / 2f
        trackRect.set(left, top, left + trackWidth, top + trackHeight)
        val corner = trackHeight / 2f

        // Draw base unchecked track
        canvas.drawRoundRect(trackRect, corner, corner, baseTrackPaint)

        // Draw checked gradient track with alpha interpolation
        if (progress > 0f) {
            checkedTrackPaint.shader = LinearGradient(
                left, top, left + trackWidth, top,
                intArrayOf(
                    Color.parseColor("#3285F5"),
                    Color.parseColor("#5A59F0"),
                    Color.parseColor("#7B4DF7")
                ),
                null,
                Shader.TileMode.CLAMP
            )
            checkedTrackPaint.alpha = (progress * 255).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(trackRect, corner, corner, checkedTrackPaint)
        }

        // Draw thumb circle
        val minThumbX = left + thumbPadding + thumbRadius
        val maxThumbX = left + trackWidth - thumbPadding - thumbRadius
        val thumbX = minThumbX + progress * (maxThumbX - minThumbX)
        val thumbY = top + trackHeight / 2f

        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)
    }
}
