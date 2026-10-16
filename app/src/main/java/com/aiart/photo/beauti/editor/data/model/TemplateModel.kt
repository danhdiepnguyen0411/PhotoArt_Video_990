package com.aiart.photo.beauti.editor.data.model

import androidx.annotation.DrawableRes
import com.google.gson.annotations.SerializedName

data class TemplateModel(
    @SerializedName(value = "id", alternate = ["code"]) val id: String = "",
    @SerializedName(value = "title", alternate = ["name", "musicTitle", "music_title", "musicName", "music_name"]) val title: String = "",
    @SerializedName("description") val description: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("views") val views: String? = null,
    @SerializedName(value = "photoCount", alternate = ["photo_count", "imageCount", "image_count", "numImages", "photos", "count"]) val photoCount: String? = null,
    @SerializedName("source") val source: String = "Photo AI",
    @SerializedName(value = "category", alternate = ["categoryCode"]) val category: String = "",
    @SerializedName(value = "imageUrl", alternate = ["image_url", "coverUrl", "cover_url"]) val imageUrl: String? = null,
    @SerializedName(value = "videoUrl", alternate = ["video_url"]) val videoUrl: String? = null,
    @SerializedName(value = "audioUrl", alternate = ["audio_url", "musicUrl", "music_url", "soundUrl", "sound_url"]) val audioUrl: String? = null,
    @SerializedName("videoThumbnailUrl") val videoThumbnailUrl: String? = null,
    @SerializedName(value = "templateType", alternate = ["template_type", "type"]) val templateType: String? = null,
    @SerializedName("premium") val premium: Boolean = false,
    @param:DrawableRes val localImageRes: Int? = null
) {
    val safeImageUrl: String?
        get() = imageUrl?.trim()?.ifBlank { null }

    val safeAudioUrl: String?
        get() = when {
            audioUrl?.isNotBlank() == true -> audioUrl.trim()
            videoUrl?.trim()?.endsWith(".mp3", ignoreCase = true) == true -> videoUrl.trim()
            imageUrl?.trim()?.endsWith(".mp3", ignoreCase = true) == true -> imageUrl.trim()
            else -> audioUrl?.trim() ?: videoUrl?.trim() ?: imageUrl?.trim()
        }

    val isMusic: Boolean
        get() = category.startsWith("MUSIC_", ignoreCase = true) ||
                templateType.equals("MUSIC", ignoreCase = true) ||
                templateType.equals("AUDIO", ignoreCase = true) ||
                (videoUrl?.trim()?.endsWith(".mp3", ignoreCase = true) == true && safeImageUrl == null) ||
                (videoUrl.isNullOrBlank() && safeImageUrl.isNullOrBlank() && !audioUrl.isNullOrBlank())

    val isStaticVideo: Boolean
        get() = templateType.equals("GENERATE_STATIC_VIDEO", ignoreCase = true) ||
                templateType.equals("GEN_VIDEO_STATIC", ignoreCase = true)

    val isDancingTemplate: Boolean
        get() = !isMusic && !isStaticVideo

    val categoryDisplayName: String
        get() {
            var raw = category.trim()
            if (raw.startsWith("MUSIC_", ignoreCase = true)) {
                raw = raw.substring(6)
            } else if (raw.startsWith("AI_", ignoreCase = true)) {
                raw = "AI " + raw.substring(3)
            }
            if (raw.isBlank()) return "Hot"
            return when (raw.uppercase().replace("-", "_").replace(" ", "_")) {
                "HIPHOP", "HIP_HOP" -> "Hip Hop"
                "EDM", "ELECTRONIC" -> "EDM"
                "LOFI", "LO_FI" -> "Lofi"
                "POP" -> "Pop"
                "CHILL" -> "Chill"
                "CINEMATIC" -> "Cinematic"
                "BASS" -> "Bass"
                "ROCK" -> "Rock"
                "ACOUSTIC" -> "Acoustic"
                "RNB", "R_B", "R&B" -> "R&B"
                "JAZZ" -> "Jazz"
                "DANCE" -> "Dance"
                "AI_DANCE" -> "AI Dance"
                "AI_EFFECT" -> "AI Effect"
                "AI_STYLE" -> "AI Style"
                "FANTASY" -> "Fantasy"
                "PORTRAIT" -> "Portrait"
                "TRENDY" -> "Trendy"
                "SELFIE" -> "Selfie"
                "VINTAGE" -> "Vintage"
                else -> {
                    if (raw.contains("_")) {
                        raw.split("_").joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
                    } else if (raw.length > 3 && raw.all { it.isUpperCase() }) {
                        raw.lowercase().replaceFirstChar { it.uppercase() }
                    } else {
                        raw
                    }
                }
            }
        }

    val durationSeconds: Int
        get() {
            val d = displayDuration
            val parts = d.split(":")
            if (parts.size == 2) {
                val min = parts[0].trim().toIntOrNull() ?: 0
                val sec = parts[1].trim().toIntOrNull() ?: 0
                val total = min * 60 + sec
                if (total > 0) return total
            }
            return 15
        }

    val targetPhotoCount: Int
        get() = (durationSeconds / 3).coerceAtLeast(1)

    val secondsPerPhoto: Float
        get() = durationSeconds.toFloat() / targetPhotoCount

    val displayDuration: String
        get() {
            if (!duration.isNullOrBlank() && duration != "00:16") {
                return duration
            }
            val key = id.ifBlank { title }
            val hash = kotlin.math.abs(key.hashCode())
            val seconds = 15 + (hash % 10)
            return String.format(java.util.Locale.US, "00:%02d", seconds)
        }

    val displayViews: String
        get() {
            if (!views.isNullOrBlank()) {
                return views
            }
            val key = id.ifBlank { title }
            val hash = kotlin.math.abs(key.hashCode())
            val viewCount = 300 + (hash % 680)
            val decimal = (hash / 7) % 10
            return "${viewCount},${decimal}K"
        }

    val displayPhotoCount: String
        get() {
            return targetPhotoCount.toString()
        }
}
