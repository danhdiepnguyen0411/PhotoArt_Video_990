package com.example.phortart_video_990

import android.app.Application
import com.example.phortart_video_990.core.sdk.ArtSdkManager

class PhotoArtApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize ArtMagicClient SDK
        ArtSdkManager.init(this)
    }
}
