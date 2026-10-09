package com.example.phortart_video_990.data.model

import androidx.annotation.DrawableRes

data class IntroPageModel(
    val stepNumber: String,
    val title: String,
    val description: String,
    @param:DrawableRes val iconRes: Int,
    @param:DrawableRes val illustrationRes: Int,
    val buttonText: String
)
