package com.example.phortart_video_990

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.example.phortart_video_990.core.sdk.ArtSdkManager
import com.example.phortart_video_990.core.utils.ShortcutHelper

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
        // Initialize ArtMagicClient SDK
        ArtSdkManager.init(this)

        // Initialize Uninstall shortcut for app launcher menu
        ShortcutHelper.initUninstallShortcut(this)
    }
}
