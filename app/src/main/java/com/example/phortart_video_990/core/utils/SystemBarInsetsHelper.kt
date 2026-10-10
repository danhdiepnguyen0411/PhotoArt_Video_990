package com.example.phortart_video_990.core.utils

import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object SystemBarInsetsHelper {

    fun hideSystemBars(window: Window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        @Suppress("DEPRECATION")
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            window.decorView.systemUiVisibility = (
                window.decorView.systemUiVisibility
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    fun hideSystemNavigationBar(window: Window) {
        hideSystemBars(window)
    }

    fun applyBottomInset(
        rootView: View,
        marginViews: List<View> = emptyList(),
        paddingViews: List<View> = emptyList()
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            for (view in marginViews) {
                val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
                lp.bottomMargin = bottomInset
                view.layoutParams = lp
            }

            for (view in paddingViews) {
                view.setPadding(
                    view.paddingLeft,
                    view.paddingTop,
                    view.paddingRight,
                    bottomInset
                )
            }
            insets
        }
    }
}
