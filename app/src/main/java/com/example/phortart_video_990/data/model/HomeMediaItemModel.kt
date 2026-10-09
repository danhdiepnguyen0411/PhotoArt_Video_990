package com.example.phortart_video_990.data.model

import androidx.annotation.DrawableRes

data class HomeMediaItemModel(
    @param:DrawableRes val imageRes: Int,
    val label: String,
    val category: String = ""
)
