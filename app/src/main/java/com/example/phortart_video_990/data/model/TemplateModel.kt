package com.example.phortart_video_990.data.model

import androidx.annotation.DrawableRes
import com.google.gson.annotations.SerializedName

data class TemplateModel(
    @SerializedName(value = "id", alternate = ["code"]) val id: String = "",
    @SerializedName(value = "title", alternate = ["name"]) val title: String = "",
    @SerializedName("description") val description: String? = null,
    @SerializedName("duration") val duration: String = "0:15",
    @SerializedName("views") val views: String = "1.2k",
    @SerializedName("source") val source: String = "Photo AI",
    @SerializedName(value = "category", alternate = ["categoryCode"]) val category: String = "",
    @SerializedName(value = "imageUrl", alternate = ["image_url"]) val imageUrl: String? = null,
    @SerializedName(value = "videoUrl", alternate = ["video_url"]) val videoUrl: String? = null,
    @SerializedName("videoThumbnailUrl") val videoThumbnailUrl: String? = null,
    @SerializedName("templateType") val templateType: String? = null,
    @SerializedName("premium") val premium: Boolean = false,
    @param:DrawableRes val localImageRes: Int? = null
) {
    val safeImageUrl: String?
        get() = imageUrl?.trim()?.ifBlank { null }

    val safeAudioUrl: String?
        get() = when {
            videoUrl?.trim()?.endsWith(".mp3", ignoreCase = true) == true -> videoUrl?.trim()
            imageUrl?.trim()?.endsWith(".mp3", ignoreCase = true) == true -> imageUrl?.trim()
            else -> videoUrl?.trim() ?: imageUrl?.trim()
        }

    val isMusic: Boolean
        get() = category.startsWith("MUSIC_", ignoreCase = true) ||
                templateType.equals("MUSIC", ignoreCase = true) ||
                templateType.equals("AUDIO", ignoreCase = true) ||
                videoUrl?.trim()?.endsWith(".mp3", ignoreCase = true) == true ||
                imageUrl?.trim()?.endsWith(".mp3", ignoreCase = true) == true
}
