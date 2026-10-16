package com.aiart.photo.beauti.editor.data.model

import com.google.gson.annotations.SerializedName

data class CategoryModel(
    @SerializedName("code") val code: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("groupCode") val groupCode: String? = null,
    @SerializedName("groupName") val groupName: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("active") val active: Boolean = true,
    @SerializedName("orderPosition") val orderPosition: Int = 0
) {
    val isMusicCategory: Boolean
        get() = groupCode.equals("MUSIC", ignoreCase = true) ||
                groupName.equals("MUSIC", ignoreCase = true) ||
                code.startsWith("MUSIC_", ignoreCase = true) ||
                name.startsWith("MUSIC_", ignoreCase = true)

    val isAiTool: Boolean
        get() = code.replace("_", " ").equals("AI TOOL", ignoreCase = true) ||
                name.replace("_", " ").equals("AI TOOL", ignoreCase = true) ||
                code.equals("AI_TOOL", ignoreCase = true) ||
                name.equals("AI_TOOL", ignoreCase = true) ||
                groupCode.equals("AI_TOOL", ignoreCase = true) ||
                groupName.equals("AI_TOOL", ignoreCase = true)

    val isDanceCategory: Boolean
        get() = groupCode.equals("DANCING", ignoreCase = true) ||
                groupCode.equals("DANCE", ignoreCase = true) ||
                groupName.equals("DANCING", ignoreCase = true) ||
                groupName.equals("DANCE", ignoreCase = true) ||
                code.contains("DANCE", ignoreCase = true) ||
                name.contains("DANCE", ignoreCase = true) ||
                name.contains("NHẢY", ignoreCase = true) ||
                name.contains("VŨ ĐIỆU", ignoreCase = true)

    val isLegalLinkCategory: Boolean
        get() = code.contains("LEGAL_LINK", ignoreCase = true) ||
                name.contains("LEGAL_LINK", ignoreCase = true) ||
                code.contains("APP_LEGAL", ignoreCase = true) ||
                name.contains("APP_LEGAL", ignoreCase = true) ||
                groupCode?.contains("LEGAL_LINK", ignoreCase = true) == true ||
                groupName?.contains("LEGAL_LINK", ignoreCase = true) == true ||
                groupCode?.contains("APP_LEGAL", ignoreCase = true) == true ||
                groupName?.contains("APP_LEGAL", ignoreCase = true) == true

    val isAnimeCategory: Boolean
        get() = code.equals("ANIME", ignoreCase = true) ||
                name.equals("ANIME", ignoreCase = true) ||
                code.contains("ANIME", ignoreCase = true) ||
                name.contains("ANIME", ignoreCase = true) ||
                code.equals("MANGA", ignoreCase = true) ||
                name.equals("MANGA", ignoreCase = true) ||
                code.contains("MANGA", ignoreCase = true) ||
                name.contains("MANGA", ignoreCase = true) ||
                groupCode?.contains("ANIME", ignoreCase = true) == true ||
                groupName?.contains("ANIME", ignoreCase = true) == true ||
                groupCode?.contains("MANGA", ignoreCase = true) == true ||
                groupName?.contains("MANGA", ignoreCase = true) == true

    val displayName: String
        get() {
            val raw = name.ifBlank { code }.trim()
            if (raw.equals("ALL", ignoreCase = true) || raw.equals("Tất cả", ignoreCase = true)) {
                return if (isMusicCategory) "Dành cho bạn" else "Tất cả"
            }
            val cleaned = if (isMusicCategory && raw.startsWith("MUSIC_", ignoreCase = true)) {
                raw.substring(6)
            } else {
                raw
            }
            return if (cleaned.contains("_")) {
                cleaned.split("_").joinToString(" ") { word ->
                    if (word.equals("ai", ignoreCase = true)) "AI"
                    else word.lowercase().replaceFirstChar { it.uppercase() }
                }
            } else if (cleaned.length > 3 && cleaned.all { it.isUpperCase() || !it.isLetter() }) {
                cleaned.lowercase().replaceFirstChar { it.uppercase() }
            } else {
                cleaned
            }
        }

    companion object {
        val ALL = CategoryModel(
            code = "ALL",
            name = "Tất cả",
            active = true
        )
    }
}
