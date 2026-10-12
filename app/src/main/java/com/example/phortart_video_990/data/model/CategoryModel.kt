package com.example.phortart_video_990.data.model

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

    val displayName: String
        get() {
            val raw = name.ifBlank { code }.trim()
            if (raw.equals("ALL", ignoreCase = true) || raw.equals("Tất cả", ignoreCase = true)) {
                return "Tất cả"
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
