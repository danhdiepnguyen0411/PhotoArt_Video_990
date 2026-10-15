package com.example.phortart_video_990.core.view

import android.content.Context
import android.graphics.Rect
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView

/**
 * A TextView that continuously scrolls horizontally (marquee) when its text
 * length exceeds the available view width, preventing line breaks and layout breakage
 * across different localized languages (e.g. French, German, Russian, Spanish).
 *
 * When text fits within the view width, it is centered.
 */
class MarqueeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        ellipsize = TextUtils.TruncateAt.MARQUEE
        marqueeRepeatLimit = -1 // marquee forever
        isSingleLine = true
        isSelected = true
        includeFontPadding = false
    }

    override fun isFocused(): Boolean = true

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(true, direction, previouslyFocusedRect)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(true)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        adjustGravityForTextLength()
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        adjustGravityForTextLength()
    }

    private fun adjustGravityForTextLength() {
        val currentText = text?.toString() ?: return
        val availableWidth = width - compoundPaddingLeft - compoundPaddingRight
        if (availableWidth <= 0) return

        val textWidth = paint.measureText(currentText)
        val targetGravity = if (textWidth > availableWidth) {
            Gravity.START or Gravity.CENTER_VERTICAL
        } else {
            Gravity.CENTER
        }
        if (gravity != targetGravity) {
            gravity = targetGravity
        }
    }
}
