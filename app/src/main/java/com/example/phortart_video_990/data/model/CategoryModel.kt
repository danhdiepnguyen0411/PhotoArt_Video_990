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

    val displayName: String
        get() {
            val raw = name.ifBlank { code }
            return if (isMusicCategory && raw.startsWith("MUSIC_", ignoreCase = true)) {
                raw.substring(6)
            } else {
                raw
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
