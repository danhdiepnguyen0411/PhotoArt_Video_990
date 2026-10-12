package com.example.phortart_video_990.data.model

import androidx.annotation.DrawableRes
import java.util.UUID

data class HistoryItemModel(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "Tất cả", // "Gom ảnh", "Prompt AI", "Khôi phục"
    val title: String = "",
    val date: String = "",
    val imageUri: String? = null,
    val imageUrl: String? = null,
    @param:DrawableRes val imageRes: Int = 0,
    val beforeUri: String? = null
)
