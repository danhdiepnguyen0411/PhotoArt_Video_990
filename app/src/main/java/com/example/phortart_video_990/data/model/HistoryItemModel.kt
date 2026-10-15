package com.example.phortart_video_990.data.model

import androidx.annotation.DrawableRes
import java.util.UUID

data class HistoryItemModel(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "Tất cả", // "Gom ảnh", "Prompt AI", "Khôi phục", "Template"
    val title: String = "",
    val date: String = "",
    val imageUri: String? = null,
    val imageUrl: String? = null,
    @param:DrawableRes val imageRes: Int = 0,
    val beforeUri: String? = null,
    val duration: String? = null,
    val photoCount: String? = null,
    val styleName: String? = null
) {
    val isVideo: Boolean
        get() {
            val t = type.lowercase()
            val tit = title.lowercase()
            val uri = (imageUri ?: imageUrl ?: "").lowercase()
            return t.contains("video") ||
                    t.contains("gom") ||
                    t.contains("template") ||
                    tit.contains("video") ||
                    uri.endsWith(".mp4") ||
                    uri.contains("photoartvideos") ||
                    !duration.isNullOrBlank()
        }

    val displayDuration: String
        get() {
            if (!duration.isNullOrBlank()) return duration
            if (isVideo) {
                val path = imageUri ?: imageUrl
                if (!path.isNullOrBlank()) {
                    try {
                        val file = java.io.File(path)
                        if (file.exists()) {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(file.absolutePath)
                            val timeMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            retriever.release()
                            if (timeMs > 0) {
                                val totalSec = (timeMs / 1000).toInt()
                                val m = totalSec / 60
                                val s = totalSec % 60
                                return String.format(java.util.Locale.US, "%02d:%02d", m, s)
                            }
                        }
                    } catch (_: Exception) {}
                }
                return "00:16"
            }
            return ""
        }

    val displayPhotoCount: String
        get() {
            if (!photoCount.isNullOrBlank()) return photoCount
            if (isVideo) {
                val match = Regex("""(\d+)""").find(title)
                if (match != null) {
                    return match.groupValues[1]
                }
                return "6"
            }
            return ""
        }

    val displayDate: String
        get() {
            if (date.isBlank()) return "26/06/2024"
            return try {
                if (date.contains("•") || date.contains(",")) {
                    val part = date.split("•")[0].trim()
                    val parsed = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).parse(part)
                    if (parsed != null) {
                        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(parsed)
                    } else {
                        part
                    }
                } else {
                    date
                }
            } catch (_: Exception) {
                if (date.contains("•")) date.split("•")[0].trim() else date
            }
        }

    val gridDisplayDate: String
        get() {
            if (date.isBlank()) {
                return try {
                    val fmt = java.text.SimpleDateFormat("d 'thg' M, yyyy", java.util.Locale("vi"))
                    fmt.format(java.util.Date())
                } catch (_: Exception) {
                    "25 thg 8, 2024"
                }
            }
            return if (date.contains("•")) date.split("•")[0].trim() else date.trim()
        }

    fun getLocalizedTitle(context: android.content.Context): String {
        val t = type.lowercase()
        val tit = title.trim()

        // 1. Restore item
        if (t.contains("khôi phục") || t.contains("restore") || t.contains("phục") ||
            tit in listOf(
                "Restore Old Photos", "老照片高清修复", "Hồi sinh ảnh cũ",
                "Реставрация старых фото", "Pulihkan Foto Lama", "Restaurar fotos antigas",
                "오래된 사진 복원", "古い写真の復元", "Ripristina vecchie foto",
                "Restaurer anciennes photos", "पुरानी फ़ोटो बहाली", "Restaurar fotos viejas",
                "Alte Fotos restaurieren", "إحياء الصور القديمة"
            )
        ) {
            return context.getString(com.example.phortart_video_990.R.string.func_02_restore)
        }

        // 2. Video from photos
        if (tit.contains("AI photo", ignoreCase = true) ||
            tit.contains("ảnh AI", ignoreCase = true) ||
            tit.contains("фото AI", ignoreCase = true) ||
            tit.contains("foto AI", ignoreCase = true) ||
            tit.startsWith("Video from", ignoreCase = true) ||
            tit.startsWith("Video từ", ignoreCase = true) ||
            tit.startsWith("Видео из", ignoreCase = true) ||
            tit.startsWith("Video dari", ignoreCase = true)
        ) {
            val countMatch = Regex("""(\d+)""").find(tit)
            val count = countMatch?.groupValues?.get(1)?.toIntOrNull() ?: displayPhotoCount.toIntOrNull() ?: 2
            val musicPart = if (tit.contains("•")) {
                val afterBullet = tit.substringAfter("•").trim()
                if (afterBullet.contains(":")) afterBullet.substringAfter(":").trim() else afterBullet
            } else null

            return if (!musicPart.isNullOrBlank()) {
                context.getString(com.example.phortart_video_990.R.string.video_title_with_music, count, musicPart)
            } else {
                context.getString(com.example.phortart_video_990.R.string.video_title_no_music, count)
            }
        }

        // 3. Known prompt templates
        val lowerTit = tit.lowercase()
        return when {
            lowerTit.contains("anime girl") || lowerTit.contains("manga") || lowerTit.contains("truyện tranh nhật") || lowerTit.contains("аниме-девушка") || lowerTit.contains("动漫美少女") ->
                context.getString(com.example.phortart_video_990.R.string.prompt_anime_full)
            lowerTit.contains("lâu đài") || lowerTit.contains("floating castle") || lowerTit.contains("парящий замок") || lowerTit.contains("kastil fantasi") || lowerTit.contains("天空之城") ->
                context.getString(com.example.phortart_video_990.R.string.prompt_fantasy_full)
            lowerTit.contains("mèo con") || lowerTit.contains("tách trà") || lowerTit.contains("котёнок") || lowerTit.contains("cute pet") || lowerTit.contains("anak kucing") || lowerTit.contains("tea cup") ->
                context.getString(com.example.phortart_video_990.R.string.prompt_pet_full)
            lowerTit.contains("cyberpunk") || lowerTit.contains("киберпанк") || lowerTit.contains("neon rực sáng") ->
                context.getString(com.example.phortart_video_990.R.string.prompt_cyberpunk_full)
            lowerTit.contains("rừng cây") || lowerTit.contains("waterfall") || lowerTit.contains("phát sáng bên hồ") || lowerTit.contains("водопад") || lowerTit.contains("hồ nước") ->
                context.getString(com.example.phortart_video_990.R.string.prompt_nature_full)
            lowerTit.contains("chân dung thiếu nữ") || lowerTit.contains("vintage artistic portrait") || lowerTit.contains("портрет женщины") || lowerTit.contains("potret artistik") ->
                context.getString(com.example.phortart_video_990.R.string.prompt_portrait_full)
            else -> tit
        }
    }
}
