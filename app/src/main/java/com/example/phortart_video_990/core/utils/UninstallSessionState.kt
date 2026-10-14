package com.example.phortart_video_990.core.utils

object UninstallSessionState {
    /**
     * Set to true when user navigates back from UninstallActivity to Home,
     * so that MainActivity avoids showing SplashFragment.
     */
    @Volatile
    var shouldSkipSplash: Boolean = false
}
