package com.aiart.photo.beauti.editor

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.aiart.photo.beauti.editor.core.sdk.ArtSdkManager
import com.aiart.photo.beauti.editor.core.utils.ShortcutHelper

class PhotoArtApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        // Reset nếu user chưa hoàn thành intro (hoặc kill app tại Intro) -> bắt đầu lại từ Splash/Language
        val prefs = com.aiart.photo.beauti.editor.data.local.PreferencesManager.getInstance(this)
        if (!prefs.hasCompletedIntro) {
            prefs.isFirstLanguageSelected = false
            prefs.languageCode = com.aiart.photo.beauti.editor.core.utils.LocaleHelper.getDeviceLanguage()
        }

        // Initialize ArtMagicClient SDK
        ArtSdkManager.init(this)

        // Initialize Uninstall shortcut for app launcher menu
        ShortcutHelper.initUninstallShortcut(this)
    }
}
