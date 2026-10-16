package com.aiart.photo.beauti.editor.core.utils

import android.os.SystemClock
import android.view.View

/**
 * Utility and extension functions to prevent rapid multi-clicks,
 * avoiding duplicate screens, multiple photo picker launches, or stacked dialogs.
 */
object ClickDebouncer {
    private var lastClickTime: Long = 0L

    @Synchronized
    fun canClick(debounceMs: Long = 800L): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickTime < debounceMs) {
            return false
        }
        lastClickTime = now
        return true
    }
}

fun View.setOnSingleClickListener(debounceMs: Long = 600L, action: (View) -> Unit) {
    setOnClickListener(object : View.OnClickListener {
        private var lastClickTime: Long = 0L

        override fun onClick(v: View) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastClickTime >= debounceMs) {
                lastClickTime = now
                action(v)
            }
        }
    })
}
