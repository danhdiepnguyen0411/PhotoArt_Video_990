package com.example.phortart_video_990.ui.screen.feature.restore.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import coil.load
import com.example.phortart_video_990.R
import com.google.android.material.card.MaterialCardView

class BeforeAfterSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val ivAfter = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private val ivBefore = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private val dividerLine = View(context).apply {
        setBackgroundColor(Color.WHITE)
        val density = resources.displayMetrics.density
        layoutParams = LayoutParams((2.5f * density).toInt(), LayoutParams.MATCH_PARENT)
    }

    private val thumbCard = MaterialCardView(context).apply {
        val density = resources.displayMetrics.density
        val size = (34 * density).toInt()
        layoutParams = LayoutParams(size, size)
        cardElevation = 4 * density
        radius = size / 2f
        setCardBackgroundColor(Color.WHITE)
        strokeWidth = 0

        val icon = ImageView(context).apply {
            val iconSize = (18 * density).toInt()
            layoutParams = LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER
            }
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_compare_arrows_purple))
        }
        addView(icon)
    }

    private val badgeBefore = TextView(context).apply {
        text = "Trước"
        setTextColor(Color.WHITE)
        textSize = 11.5f
        setTypeface(null, android.graphics.Typeface.BOLD)
        val density = resources.displayMetrics.density
        val padH = (12 * density).toInt()
        val padV = (4.5f * density).toInt()
        setPadding(padH, padV, padH, padV)
        background = ContextCompat.getDrawable(context, R.drawable.bg_badge_before)
        val margin = (12 * density).toInt()
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            setMargins(margin, margin, margin, margin)
        }
    }

    private val badgeAfter = TextView(context).apply {
        text = "Sau"
        setTextColor(Color.WHITE)
        textSize = 11.5f
        setTypeface(null, android.graphics.Typeface.BOLD)
        val density = resources.displayMetrics.density
        val padH = (12 * density).toInt()
        val padV = (4.5f * density).toInt()
        setPadding(padH, padV, padH, padV)
        background = ContextCompat.getDrawable(context, R.drawable.bg_badge_after)
        val margin = (12 * density).toInt()
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(margin, margin, margin, margin)
        }
    }

    private var splitRatio: Float = 0.5f

    init {
        addView(ivAfter)
        addView(ivBefore)
        addView(dividerLine)
        addView(thumbCard)
        addView(badgeBefore)
        addView(badgeAfter)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (child == ivBefore) {
            val save = canvas.save()
            val splitX = width * splitRatio
            canvas.clipRect(0f, 0f, splitX, height.toFloat())
            val result = super.drawChild(canvas, child, drawingTime)
            canvas.restoreToCount(save)
            return result
        }
        return super.drawChild(canvas, child, drawingTime)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateSliderPosition()
    }

    private fun updateSliderPosition() {
        val w = width
        val h = height
        if (w == 0 || h == 0) return

        val splitX = w * splitRatio

        // Center divider line on splitX
        val lineWidth = dividerLine.width
        dividerLine.x = splitX - lineWidth / 2f

        // Center thumb on splitX and vertically
        val thumbW = thumbCard.width
        val thumbH = thumbCard.height
        thumbCard.x = splitX - thumbW / 2f
        thumbCard.y = (h - thumbH) / 2f
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val touchX = event.x
                val w = width.toFloat()
                if (w > 0f) {
                    splitRatio = (touchX / w).coerceIn(0.04f, 0.96f)
                    updateSliderPosition()
                    invalidate()
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun setBeforeImage(source: Any?) {
        if (source != null) {
            ivBefore.load(source) {
                crossfade(true)
            }
        }
    }

    fun setAfterImage(source: Any?) {
        if (source != null) {
            ivAfter.load(source) {
                crossfade(true)
            }
        }
    }
}
