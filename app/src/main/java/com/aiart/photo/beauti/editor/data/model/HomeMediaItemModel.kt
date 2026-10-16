package com.aiart.photo.beauti.editor.data.model

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

data class HomeMediaItemModel(
    @param:DrawableRes val imageRes: Int = 0,
    val label: String,
    val category: String = "",
    @param:DrawableRes val iconRes: Int = 0,
    @param:ColorInt val iconTint: Int? = null,
    val imageUrl: String? = null,
    val imageUri: String? = null
)
