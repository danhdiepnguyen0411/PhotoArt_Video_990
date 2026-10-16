package com.example.phortart_video_990.data.model

import android.content.Context
import androidx.annotation.DrawableRes
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.utils.LocaleHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
    val styleName: String? = null,
    val timestamp: Long = 0L
) {
    val isVideo: Boolean
        get() {
            val t = type.lowercase()
            val tit = title.lowercase()
            val uri = (imageUri ?: imageUrl ?: "").lowercase()
            return t.contains("video") ||
                    t.contains("gom") ||
                    t.contains("template") ||
                    t.contains("dance") ||
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
                        val cleanPath = if (path.startsWith("file://")) path.removePrefix("file://") else path
                        val file = File(cleanPath)
                        if (file.exists()) {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(file.absolutePath)
                            val timeMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            retriever.release()
                            if (timeMs > 0) {
                                val totalSec = (timeMs / 1000).toInt()
                                val m = totalSec / 60
                                val s = totalSec % 60
                                return String.format(Locale.US, "%02d:%02d", m, s)
                            }
                        }
                    } catch (_: Exception) {}
                }
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

    fun parseToDate(): Date {
        if (timestamp > 0L) {
            return Date(timestamp)
        }
        if (date.isBlank()) {
            return Date()
        }
        return try {
            val s = date.trim()

            // Extract Year (e.g. 2024..2030)
            val yMatch = Regex("""\b(20\d\d)\b""").find(s)
            var year = yMatch?.groupValues?.get(1)?.toIntOrNull() ?: 2026

            // Extract Time HH:mm
            val tMatch = Regex("""(\d{1,2}):(\d{2})""").find(s)
            val hour = tMatch?.groupValues?.get(1)?.toIntOrNull() ?: 12
            val minute = tMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0

            val dPart = if (s.contains("•")) s.split("•")[0].trim() else s

            var month: Int? = null
            var day: Int? = null

            // 1. dd/MM/yyyy format (e.g. 26/06/2024 or 14/10/2026)
            val slashMatch = Regex("""(\d{1,2})/(\d{1,2})/(20\d\d)""").find(dPart)
            if (slashMatch != null) {
                day = slashMatch.groupValues[1].toIntOrNull()
                month = slashMatch.groupValues[2].toIntOrNull()
                year = slashMatch.groupValues[3].toIntOrNull() ?: year
            }

            // 2. Asian format: 10月 14, 2026 or 10월 14, 2026
            if (month == null || day == null) {
                val asianMatch = Regex("""(\d{1,2})\s*[月월]\s*(\d{1,2})""").find(dPart)
                if (asianMatch != null) {
                    month = asianMatch.groupValues[1].toIntOrNull()
                    day = asianMatch.groupValues[2].toIntOrNull()
                }
            }

            // 3. Vietnamese format: 14 thg 10, 2026 or Th10 14, 2026 or Tháng 10 14, 2026
            if (month == null || day == null) {
                val viThgMatch = Regex("""(\d{1,2})\s*thg\s*(\d{1,2})""", RegexOption.IGNORE_CASE).find(dPart)
                if (viThgMatch != null) {
                    day = viThgMatch.groupValues[1].toIntOrNull()
                    month = viThgMatch.groupValues[2].toIntOrNull()
                }
            }
            if (month == null || day == null) {
                val viThMatch = Regex("""Th(?:áng)?\s*(\d{1,2})[,\s]+(\d{1,2})""", RegexOption.IGNORE_CASE).find(dPart)
                if (viThMatch != null) {
                    month = viThMatch.groupValues[1].toIntOrNull()
                    day = viThMatch.groupValues[2].toIntOrNull()
                }
            }

            // 4. Named month abbreviations across multiple languages
            if (month == null || day == null) {
                val lower = dPart.lowercase()
                val monthMap = mapOf(
                    "jan" to 1, "янв" to 1, "ene" to 1, "gen" to 1, "janv" to 1,
                    "feb" to 2, "февр" to 2, "фев" to 2, "febr" to 2, "févr" to 2,
                    "mar" to 3, "мар" to 3, "mär" to 3,
                    "apr" to 4, "апр" to 4, "avr" to 4,
                    "may" to 5, "май" to 5, "mai" to 5, "mayo" to 5, "magg" to 5,
                    "jun" to 6, "июн" to 6, "giu" to 6,
                    "jul" to 7, "июл" to 7, "lug" to 7, "juil" to 7,
                    "aug" to 8, "авг" to 8, "août" to 8, "ago" to 8,
                    "sep" to 9, "sept" to 9, "сент" to 9, "сен" to 9, "set" to 9,
                    "oct" to 10, "окт" to 10, "okt" to 10, "ott" to 10,
                    "nov" to 11, "нояб" to 11, "ноя" to 11,
                    "dec" to 12, "дек" to 12, "dez" to 12, "déc" to 12, "dic" to 12
                )
                for ((prefix, mVal) in monthMap) {
                    if (lower.contains(prefix)) {
                        month = mVal
                        val dayMatch = Regex("""\b(\d{1,2})\b""").find(dPart)
                        if (dayMatch != null) {
                            day = dayMatch.groupValues[1].toIntOrNull()
                        }
                        break
                    }
                }
            }

            // Fallback numbers
            if (month == null || day == null) {
                val nums = Regex("""\b\d+\b""").findAll(dPart)
                    .mapNotNull { it.value.toIntOrNull() }
                    .filter { it != year }
                    .toList()
                if (nums.size >= 2) {
                    month = nums[0]
                    day = nums[1]
                } else if (nums.size == 1) {
                    day = nums[0]
                    month = 10
                } else {
                    day = 14
                    month = 10
                }
            }

            val safeMonth = (month ?: 10).coerceIn(1, 12)
            val safeDay = (day ?: 14).coerceIn(1, 31)
            val cal = Calendar.getInstance()
            cal.set(year, safeMonth - 1, safeDay, hour, minute, 0)
            cal.time
        } catch (_: Exception) {
            Date()
        }
    }

    fun getLocalizedGridDate(context: Context): String {
        val lang = LocaleHelper.getSavedLanguage(context)
        val locale = LocaleHelper.getLocale(lang)
        val parsedDate = parseToDate()
        return try {
            val sdf = SimpleDateFormat("MMM d, yyyy", locale)
            sdf.format(parsedDate)
        } catch (_: Exception) {
            gridDisplayDate
        }
    }

    fun getLocalizedFullDate(context: Context): String {
        val lang = LocaleHelper.getSavedLanguage(context)
        val locale = LocaleHelper.getLocale(lang)
        val parsedDate = parseToDate()
        return try {
            val sdf = SimpleDateFormat("MMM d, yyyy • HH:mm", locale)
            sdf.format(parsedDate)
        } catch (_: Exception) {
            date
        }
    }

    val displayDate: String
        get() {
            if (date.isBlank()) return "26/06/2024"
            return try {
                if (date.contains("•") || date.contains(",")) {
                    val part = date.split("•")[0].trim()
                    val parsed = SimpleDateFormat("MMM d, yyyy", Locale.US).parse(part)
                    if (parsed != null) {
                        SimpleDateFormat("dd/MM/yyyy", Locale.US).format(parsed)
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
                    val fmt = SimpleDateFormat("d 'thg' M, yyyy", Locale("vi"))
                    fmt.format(Date())
                } catch (_: Exception) {
                    "25 thg 8, 2024"
                }
            }
            return if (date.contains("•")) date.split("•")[0].trim() else date.trim()
        }

    fun getLocalizedTitle(context: Context): String {
        val locCtx = LocaleHelper.getLocalizedContext(context)
        val t = type.lowercase()
        val tit = title.trim()
        val lowerTit = tit.lowercase()

        // 1. Restore item
        if (t.contains("khôi phục") || t.contains("restore") || t.contains("phục") || t.contains("hồi sinh") ||
            tit in listOf(
                "Restore Old Photos", "老照片高清修复", "Hồi sinh ảnh cũ", "Khôi phục ảnh", "Khôi phục ảnh cũ",
                "Реставрация старых фото", "Pulihkan Foto Lama", "Restaurar fotos antigas",
                "오래된 사진 복원", "古い写真の復元", "Ripristina vecchie foto",
                "Restaurer anciennes photos", "पुरानी फ़ोटो बहाली", "Restaurar fotos viejas",
                "Alte Fotos restaurieren", "إحياء الصور القديمة"
            ) ||
            lowerTit.contains("hồi sinh") || lowerTit.contains("khôi phục") || lowerTit.contains("phục chế") ||
            lowerTit.contains("restore old photos") || lowerTit.contains("老照片") || lowerTit.contains("реставрация") ||
            lowerTit.contains("사진 복원") || lowerTit.contains("写真の復元")
        ) {
            return locCtx.getString(R.string.func_02_restore)
        }

        // 2. Enhance / Prompt AI default function name
        if (tit in listOf(
                "Tạo ảnh prompt AI", "AI Prompt Art", "AI 提示词绘图",
                "Фото по AI-промпту", "Gambar Prompt AI", "Imagem com prompt IA",
                "AI 프롬프트 이미지 생성", "AIプロンプト画像生成", "Immagine da prompt AI",
                "Image par prompt IA", "AI प्रॉम्प्ट चित्र", "KI-Bild per Prompt", "إنشاء صورة بأمر ذكاء اصطناعي",
                "Làm nét ảnh", "Enhance Photo", "照片高清修复"
            ) ||
            (t.contains("enhance") && !lowerTit.contains("anime") && !lowerTit.contains("mèo") && !lowerTit.contains("chân dung") && !lowerTit.contains("rừng") && !lowerTit.contains("lâu đài") && !lowerTit.contains("cyber"))
        ) {
            return locCtx.getString(R.string.func_03_enhance)
        }

        // 3. Upscale function name
        if (t.contains("upscale") || lowerTit.contains("nâng cấp ảnh") || lowerTit.contains("upscale")) {
            return locCtx.getString(R.string.func_05_upscale)
        }

        // 4. Video from photos
        if (tit.contains("AI photo", ignoreCase = true) ||
            tit.contains("ảnh AI", ignoreCase = true) ||
            tit.contains("ảnh", ignoreCase = true) ||
            tit.contains("photos", ignoreCase = true) ||
            tit.contains("photo", ignoreCase = true) ||
            tit.contains("фото", ignoreCase = true) ||
            tit.contains("foto", ignoreCase = true) ||
            tit.contains("照片", ignoreCase = true) ||
            tit.contains("写真", ignoreCase = true) ||
            tit.contains("사진", ignoreCase = true) ||
            tit.startsWith("Video", ignoreCase = true) ||
            tit.startsWith("Видео", ignoreCase = true) ||
            tit.startsWith("视频", ignoreCase = true) ||
            tit.startsWith("비디오", ignoreCase = true) ||
            tit.startsWith("動画", ignoreCase = true) ||
            (t.contains("gom") || t.contains("memories"))
        ) {
            val countMatch = Regex("""(\d+)""").find(tit)
            val count = countMatch?.groupValues?.get(1)?.toIntOrNull() ?: displayPhotoCount.toIntOrNull() ?: 2
            val musicPart = if (tit.contains("•")) {
                val afterBullet = tit.substringAfter("•").trim()
                if (afterBullet.contains(":")) afterBullet.substringAfter(":").trim() else afterBullet
            } else null

            return if (!musicPart.isNullOrBlank()) {
                locCtx.getString(R.string.video_title_with_music, count, musicPart)
            } else {
                locCtx.getString(R.string.video_title_no_music, count)
            }
        }

        // 5. Dance / Template video
        if (t.contains("dance") || lowerTit.contains("nhảy theo video") || lowerTit.contains("dance video") || lowerTit.contains("跟跳视频") || lowerTit.contains("танец по видео")) {
            val musicPart = if (tit.contains("•")) {
                val afterBullet = tit.substringAfter("•").trim()
                if (afterBullet.contains(":")) afterBullet.substringAfter(":").trim() else afterBullet
            } else null
            return if (!musicPart.isNullOrBlank()) {
                "${locCtx.getString(R.string.dance_video_title)} • $musicPart"
            } else {
                locCtx.getString(R.string.dance_video_title)
            }
        }

        // 6. Known prompt templates
        return when {
            lowerTit.contains("anime girl") || lowerTit.contains("manga") || lowerTit.contains("truyện tranh nhật") ||
                    lowerTit.contains("аниме") || lowerTit.contains("动漫美少女") || lowerTit.contains("アニメ") ||
                    lowerTit.contains("애니메이션") || lowerTit.contains("gadis anime") || lowerTit.contains("chica anime") ||
                    lowerTit.contains("fille anime") || lowerTit.contains("ragazza anime") || lowerTit.contains("garota de anime") ||
                    lowerTit.contains("एनीमे") || lowerTit.contains("فتاة أنمي") ->
                locCtx.getString(R.string.prompt_anime_full)

            lowerTit.contains("lâu đài") || lowerTit.contains("floating castle") || lowerTit.contains("парящий замок") ||
                    lowerTit.contains("kastil fantasi") || lowerTit.contains("天空之城") || lowerTit.contains("fantasy castle") ||
                    lowerTit.contains("château fantastique") || lowerTit.contains("castillo mágico") || lowerTit.contains("castelo mágico") ||
                    lowerTit.contains("castello magico") || lowerTit.contains("fantasieschloss") || lowerTit.contains("сказочный замок") ||
                    lowerTit.contains("魔法城堡") || lowerTit.contains("ファンタジー城") || lowerTit.contains("판타지 성") ||
                    lowerTit.contains("जादुई महल") || lowerTit.contains("قلعة ساحرة") ->
                locCtx.getString(R.string.prompt_fantasy_full)

            lowerTit.contains("mèo con") || lowerTit.contains("tách trà") || lowerTit.contains("котёнок") ||
                    lowerTit.contains("cute pet") || lowerTit.contains("anak kucing") || lowerTit.contains("tea cup") ||
                    lowerTit.contains("teacup") || lowerTit.contains("kitten") || lowerTit.contains("小萌猫") ||
                    lowerTit.contains("茶杯") || lowerTit.contains("子猫") || lowerTit.contains("ティーカップ") ||
                    lowerTit.contains("아기 고양이") || lowerTit.contains("찻잔") || lowerTit.contains("чайной чашке") ||
                    lowerTit.contains("kätzchen") || lowerTit.contains("gatito") || lowerTit.contains("chaton") ||
                    lowerTit.contains("gattino") || lowerTit.contains("gatinho") || lowerTit.contains("बिल्ली का बच्चा") ||
                    lowerTit.contains("قطة صغيرة") ->
                locCtx.getString(R.string.prompt_pet_full)

            lowerTit.contains("cyberpunk") || lowerTit.contains("киберпанк") || lowerTit.contains("neon rực sáng") ||
                    lowerTit.contains("neon warrior") || lowerTit.contains("chiến binh cyberpunk") || lowerTit.contains("赛博朋克") ||
                    lowerTit.contains("サイバーパンク") || lowerTit.contains("사이버펑크") || lowerTit.contains("prajurit cyberpunk") ||
                    lowerTit.contains("guerrier cyberpunk") || lowerTit.contains("guerrero cyberpunk") || lowerTit.contains("guerreiro cyberpunk") ||
                    lowerTit.contains("سايبربانك") || lowerTit.contains("сайберпанк") ->
                locCtx.getString(R.string.prompt_cyberpunk_full)

            lowerTit.contains("rừng cây") || lowerTit.contains("waterfall") || lowerTit.contains("phát sáng bên hồ") ||
                    lowerTit.contains("водопад") || lowerTit.contains("hồ nước") || lowerTit.contains("enchanted forest") ||
                    lowerTit.contains("dreamy lake") || lowerTit.contains("волшебный лес") || lowerTit.contains("чистого озера") ||
                    lowerTit.contains("梦幻森林") || lowerTit.contains("清澈湖畔") || lowerTit.contains("神秘的な森") ||
                    lowerTit.contains("澄みきった湖") || lowerTit.contains("신비로운 숲") || lowerTit.contains("호숫가") ||
                    lowerTit.contains("hutan ajaib") || lowerTit.contains("bosque encantado") || lowerTit.contains("forêt enchantée") ||
                    lowerTit.contains("zauberwald") || lowerTit.contains("غابة متوهجة") ->
                locCtx.getString(R.string.prompt_nature_full)

            lowerTit.contains("chân dung thiếu nữ") || lowerTit.contains("vintage artistic portrait") || lowerTit.contains("портрет девушки") ||
                    lowerTit.contains("portrait of a young lady") || lowerTit.contains("sunset light") || lowerTit.contains("лучах заката") ||
                    lowerTit.contains("少女肖像") || lowerTit.contains("夕阳余晖") || lowerTit.contains("女性のポートレート") ||
                    lowerTit.contains("夕日の光") || lowerTit.contains("여성의 초상화") || lowerTit.contains("일몰 빛") ||
                    lowerTit.contains("potret artistik") || lowerTit.contains("potret wanita") || lowerTit.contains("retrato de una joven") ||
                    lowerTit.contains("portrait délicat") || lowerTit.contains("porträt einer jungen frau") || lowerTit.contains("بورتريه فتاة") ->
                locCtx.getString(R.string.prompt_portrait_full)

            else -> tit
        }
    }
}
