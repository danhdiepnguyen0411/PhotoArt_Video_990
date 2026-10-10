package com.example.phortart_video_990.core.utils

import android.app.Activity
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object KeyboardUtils {

    fun hideSoftKeyboard(view: View?) {
        if (view == null) return
        val context = view.context
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
        (context as? Activity)?.let { activity ->
            imm?.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
    }

    fun handleTouchToDismissKeyboard(activity: Activity, ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val focusedView = activity.currentFocus
            if (focusedView is EditText) {
                val x = ev.rawX
                val y = ev.rawY

                if (isTouchInsideView(focusedView, x, y)) {
                    return false
                }

                if (isTouchOnAnyEditText(activity.window.decorView, x, y)) {
                    return false
                }

                if (isTouchOnInteractiveView(activity.window.decorView, x, y)) {
                    return false
                }

                val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(focusedView.windowToken, 0)
                imm?.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
                WindowInsetsControllerCompat(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.ime())

                focusedView.clearFocus()
                activity.window.decorView.isFocusableInTouchMode = true
                activity.window.decorView.requestFocus()
            }
        }
        return false
    }

    private fun isTouchInsideView(view: View, x: Float, y: Float): Boolean {
        if (!view.isShown) return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val left = location[0]
        val top = location[1]
        val right = left + view.width
        val bottom = top + view.height
        return x >= left && x <= right && y >= top && y <= bottom
    }

    private fun isTouchOnAnyEditText(view: View, x: Float, y: Float): Boolean {
        if (!view.isShown) return false
        if (view is EditText) {
            if (isTouchInsideView(view, x, y)) {
                return true
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (isTouchOnAnyEditText(child, x, y)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isTouchOnInteractiveView(view: View, x: Float, y: Float): Boolean {
        if (!view.isShown) return false
        if (!isTouchInsideView(view, x, y)) return false

        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                if (isTouchOnInteractiveView(child, x, y)) {
                    return true
                }
            }
        }

        val id = view.id
        val className = view.javaClass.name
        if (id == android.R.id.content ||
            id == com.google.android.material.R.id.touch_outside ||
            id == com.google.android.material.R.id.design_bottom_sheet ||
            id == com.google.android.material.R.id.coordinator ||
            id == com.google.android.material.R.id.container ||
            className.contains("DecorView") ||
            className.contains("CoordinatorLayout")) {
            return false
        }

        return view.isClickable || view.isLongClickable || view.hasOnClickListeners()
    }
}
