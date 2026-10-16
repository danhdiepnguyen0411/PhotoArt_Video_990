package com.aiart.photo.beauti.editor.core.sdk

import android.content.Context
import android.util.Log
import com.code12.art.ArtMagicClient
import com.aiart.photo.beauti.editor.BuildConfig

object ArtSdkManager {

    private const val TAG = "ArtSdkManager"
    private var client: ArtMagicClient? = null

    /**
     * Initializes the ArtMagicClient SDK with credentials loaded from local.properties via BuildConfig.
     */
    fun init(
        context: Context,
        appId: String = BuildConfig.ART_APP_ID,
        secretKey: String = BuildConfig.ART_SECRET_KEY
    ) {
        try {
            if (appId.isBlank() || secretKey.isBlank()) {
                Log.w(TAG, "ArtMagicClient credentials are empty! Please check local.properties.")
            }
            client = ArtMagicClient(context.applicationContext, appId, secretKey)
            Log.d(TAG, "ArtMagicClient SDK initialized successfully with appId: $appId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ArtMagicClient SDK: ${e.message}", e)
        }
    }

    /**
     * Get the active ArtMagicClient instance.
     */
    fun getClient(): ArtMagicClient? = client

    /**
     * Check if the SDK client is initialized.
     */
    fun isInitialized(): Boolean = client != null
}
