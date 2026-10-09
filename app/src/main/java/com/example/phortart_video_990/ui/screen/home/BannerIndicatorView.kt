package com.example.phortart_video_990.ui.screen.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class BannerIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dotCount = 3
    private var currentPosition = 0
    private var currentOffset = 0f

    private val density = resources.displayMetrics.density
    private val dotHeight = 8f * density
    private val cornerRadius = dotHeight / 2f
    private val inactiveWidth = 8f * density
    private val activeWidth = 20f * density
    private val gap = 7f * density

    private val paintInactive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        style = Paint.Style.FILL
    }

    private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A000000")
        style = Paint.Style.STROKE
        strokeWidth = 0.5f * density
    }

    private val paintActive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotRect = RectF()
    private val dotBoundsList = mutableListOf<RectF>()

    var onDotClickListener: ((Int) -> Unit)? = null

    fun setDotCount(count: Int) {
        dotCount = count.coerceAtLeast(1)
        requestLayout()
        invalidate()
    }

    fun setProgress(position: Int, offset: Float) {
        currentPosition = position.coerceIn(0, dotCount - 1)
        currentOffset = offset.coerceIn(0f, 1f)
        invalidate()
    }

    fun setSelectedPage(position: Int) {
        currentPosition = position.coerceIn(0, dotCount - 1)
        currentOffset = 0f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalWidth = activeWidth + (dotCount - 1) * inactiveWidth + (dotCount - 1) * gap + paddingLeft + paddingRight
        val totalHeight = dotHeight + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(totalWidth.toInt(), widthMeasureSpec),
            resolveSize(totalHeight.toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dotCount <= 0) return

        val totalContentWidth = activeWidth + (dotCount - 1) * inactiveWidth + (dotCount - 1) * gap
        val startX = (width - totalContentWidth) / 2f
        val startY = (height - dotHeight) / 2f

        dotBoundsList.clear()
        var currentX = startX

        val pos = currentPosition
        val frac = currentOffset

        for (i in 0 until dotCount) {
            val w = when (i) {
                pos -> activeWidth - (activeWidth - inactiveWidth) * frac
                pos + 1 -> inactiveWidth + (activeWidth - inactiveWidth) * frac
                else -> inactiveWidth
            }

            dotRect.set(currentX, startY, currentX + w, startY + dotHeight)
            dotBoundsList.add(RectF(dotRect))

            // Draw base inactive circle/capsule
            canvas.drawRoundRect(dotRect, cornerRadius, cornerRadius, paintInactive)
            canvas.drawRoundRect(dotRect, cornerRadius, cornerRadius, paintStroke)

            // Draw active gradient overlay
            val activeWeight = ((w - inactiveWidth) / (activeWidth - inactiveWidth)).coerceIn(0f, 1f)
            if (activeWeight > 0.01f) {
                val a = (activeWeight * 255).toInt()
                paintActive.alpha = a
                val c1 = Color.argb(a, 0x30, 0xC8, 0xF4)
                val c2 = Color.argb(a, 0x66, 0x76, 0xEE)
                val c3 = Color.argb(a, 0xD8, 0x5D, 0xDD)
                paintActive.shader = LinearGradient(
                    dotRect.left, dotRect.top, dotRect.right, dotRect.bottom,
                    intArrayOf(c1, c2, c3),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(dotRect, cornerRadius, cornerRadius, paintActive)
            }

            currentX += w + gap
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && dotBoundsList.isNotEmpty()) {
            val x = event.x
            val y = event.y
            for (i in dotBoundsList.indices) {
                val rect = dotBoundsList[i]
                val touchRect = RectF(rect.left - gap / 2, 0f, rect.right + gap / 2, height.toFloat())
                if (touchRect.contains(x, y)) {
                    onDotClickListener?.invoke(i)
                    performClick()
                    return true
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
