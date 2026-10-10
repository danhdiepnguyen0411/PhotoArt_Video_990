package com.example.phortart_video_990.ui.screen.feature.restore.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View

class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress: Float = 0f
    private val strokeWidthPx = 11f * resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#E7EEFB")
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
    }

    private val oval = RectF()
    private val gradientColors = intArrayOf(
        Color.parseColor("#9D53F7"), // Tím
        Color.parseColor("#4B7BF6"), // Xanh dương
        Color.parseColor("#9D53F7")  // Tím
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = strokeWidthPx / 2f + 4f
        oval.set(inset, inset, w - inset, h - inset)

        val cx = w / 2f
        val cy = h / 2f
        val sweepGradient = SweepGradient(cx, cy, gradientColors, floatArrayOf(0f, 0.5f, 1f))
        val matrix = Matrix()
        matrix.postRotate(-90f, cx, cy)
        sweepGradient.setLocalMatrix(matrix)
        progressPaint.shader = sweepGradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw track
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)

        // Draw progress arc from top (-90 degrees)
        if (progress > 0f) {
            val sweepAngle = (progress / 100f) * 360f
            canvas.drawArc(oval, -90f, sweepAngle, false, progressPaint)
        }
    }

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 100f)
        invalidate()
    }

    fun getProgress(): Float = progress
}
