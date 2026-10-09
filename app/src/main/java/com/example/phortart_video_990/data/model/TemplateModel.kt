package com.example.phortart_video_990.data.model

import androidx.annotation.DrawableRes
import com.google.gson.annotations.SerializedName

data class TemplateModel(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("duration") val duration: String = "0:15",
    @SerializedName("views") val views: String = "1.2k",
    @SerializedName("source") val source: String = "CapCut",
    @SerializedName("category") val category: String = "Tất cả",
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("video_url") val videoUrl: String? = null,
    @DrawableRes val localImageRes: Int? = null
)
