package com.aiart.photo.beauti.editor.data.model

data class LanguageModel(
    val code: String,
    val name: String,
    var subName: String,
    val flagAsset: String,
    var isSelected: Boolean = false,
    val subResId: Int = 0
)
