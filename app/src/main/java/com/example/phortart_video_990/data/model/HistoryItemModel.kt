package com.example.phortart_video_990.data.model

import androidx.annotation.DrawableRes

data class HistoryItemModel(
    val id: String,
    val type: String, // "video", "prompt", "restore"
    val title: String,
    val date: String,
    @param:DrawableRes val imageRes: Int
)
