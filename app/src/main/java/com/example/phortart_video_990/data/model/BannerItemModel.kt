package com.example.phortart_video_990.data.model

import androidx.annotation.DrawableRes

data class BannerItemModel(
    @param:DrawableRes val imageRes: Int,
    val titleLine1: String,
    val titleLine2Prefix: String,
    val highlight: String,
    val description: String,
    val buttonText: String,
    val targetFeature: String,
    val chip: String = ""
)
