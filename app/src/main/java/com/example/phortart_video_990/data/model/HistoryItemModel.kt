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
    val photoCount: String? = null
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
}
