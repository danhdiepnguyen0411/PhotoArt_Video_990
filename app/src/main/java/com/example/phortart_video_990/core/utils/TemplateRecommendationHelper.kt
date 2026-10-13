package com.example.phortart_video_990.core.utils

import android.content.Context
import com.example.phortart_video_990.R
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.model.TemplateModel

object TemplateRecommendationHelper {

    /**
     * Default list for "Lịch sử gần đây" (Matching the exact mockup on the right)
     */
    fun getDefaultRecentHistory(): List<HistoryItemModel> {
        return listOf(
            HistoryItemModel(
                id = "hist_figma_01",
                title = "Cùng nhau đi khắp nơi",
                date = "26/06/2024",
                imageRes = R.drawable.history_1,
                duration = "00:16",
                photoCount = "6",
                type = "Gom ảnh"
            ),
            HistoryItemModel(
                id = "hist_figma_02",
                title = "Khoảnh khắc thanh xuân",
                date = "26/06/2024",
                imageRes = R.drawable.history_2,
                duration = null,
                photoCount = null,
                type = "Khôi phục"
            ),
            HistoryItemModel(
                id = "hist_figma_03",
                title = "Tình yêu ngọt ngào",
                date = "26/06/2024",
                imageRes = R.drawable.history_3,
                duration = "00:15",
                photoCount = "2",
                type = "Gom ảnh"
            )
        )
    }

    /**
     * Default list for "Dành riêng cho bạn" (Music tracks)
     */
    fun getDefaultForYouTemplates(): List<TemplateModel> {
        return listOf(
            TemplateModel(
                id = "fy_mus_01",
                title = "HipHop Groove",
                category = "MUSIC_HIPHOP",
                duration = "00:20",
                views = "820,5K",
                photoCount = "6",
                localImageRes = R.drawable.suggestion_ai_style,
                audioUrl = "https://cdn.pixabay.com/download/audio/2022/01/26/audio_d0c6ff1e01.mp3?filename=street-dance-beat-118837.mp3"
            ),
            TemplateModel(
                id = "fy_mus_02",
                title = "Electronic Sunset",
                category = "MUSIC_EDM",
                duration = "00:25",
                views = "934,1K",
                photoCount = "8",
                localImageRes = R.drawable.suggestion_ai_dance,
                audioUrl = "https://cdn.pixabay.com/download/audio/2022/01/18/audio_d0a13f69d2.mp3?filename=electronic-future-beats-117997.mp3"
            ),
            TemplateModel(
                id = "fy_mus_03",
                title = "Chill Lofi Beat",
                category = "MUSIC_LOFI",
                duration = "00:28",
                views = "450,2K",
                photoCount = "5",
                localImageRes = R.drawable.suggestion_ai_portrait,
                audioUrl = "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3?filename=lofi-study-112191.mp3"
            ),
            TemplateModel(
                id = "fy_mus_04",
                title = "Upbeat Summer Vibes",
                category = "MUSIC_POP",
                duration = "00:22",
                views = "765,4K",
                photoCount = "7",
                localImageRes = R.drawable.suggestion_ai_effect,
                audioUrl = "https://cdn.pixabay.com/download/audio/2022/03/15/audio_c8c8a73467.mp3?filename=summer-walk-152722.mp3"
            ),
            TemplateModel(
                id = "fy_mus_05",
                title = "Acoustic Morning Breeze",
                category = "MUSIC_CHILL",
                duration = "00:30",
                views = "612,9K",
                photoCount = "6",
                localImageRes = R.drawable.suggestion_ai_fantasy,
                audioUrl = "https://cdn.pixabay.com/download/audio/2022/10/14/audio_9939f792cb.mp3?filename=acoustic-guitars-ambient-124483.mp3"
            )
        )
    }

    /**
     * Default list for "Hot" (Hot Music tracks)
     */
    fun getDefaultHotTemplates(): List<TemplateModel> {
        return listOf(
            TemplateModel(
                id = "hot_mus_01",
                title = "Lumen Phonk Bass",
                category = "MUSIC_BASS",
                duration = "00:17",
                views = "940,8K",
                photoCount = "5",
                localImageRes = R.drawable.template_fantasy,
                imageUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
                audioUrl = "https://cdn.pixabay.com/download/audio/2022/01/18/audio_d0a13f69d2.mp3?filename=electronic-future-beats-117997.mp3"
            ),
            TemplateModel(
                id = "hot_mus_02",
                title = "Hard EDM Festival",
                category = "MUSIC_EDM",
                duration = "00:25",
                views = "880,1K",
                photoCount = "4",
                localImageRes = R.drawable.template_trendy,
                imageUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
                audioUrl = "https://cdn.pixabay.com/download/audio/2022/03/15/audio_c8c8a73467.mp3?filename=summer-walk-152722.mp3"
            ),
            TemplateModel(
                id = "hot_mus_03",
                title = "Urban Street Rhythm",
                category = "MUSIC_HIPHOP",
                duration = "00:20",
                views = "765,4K",
                photoCount = "6",
                localImageRes = R.drawable.template_selfie,
                imageUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&auto=format&fit=crop&q=80",
                audioUrl = "https://cdn.pixabay.com/download/audio/2022/01/26/audio_d0c6ff1e01.mp3?filename=street-dance-beat-118837.mp3"
            ),
            TemplateModel(
                id = "hot_mus_04",
                title = "Emotional Memory Trail",
                category = "MUSIC_CINEMATIC",
                duration = "00:35",
                views = "990,3K",
                photoCount = "5",
                localImageRes = R.drawable.template_cinematic,
                imageUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
                audioUrl = "https://cdn.pixabay.com/download/audio/2022/03/24/audio_3d1ef9b2c5.mp3?filename=cinematic-atmosphere-score-115321.mp3"
            ),
            TemplateModel(
                id = "hot_mus_05",
                title = "Midnight Lofi Chill",
                category = "MUSIC_LOFI",
                duration = "00:28",
                views = "620,0K",
                photoCount = "5",
                localImageRes = R.drawable.template_vintage,
                imageUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&auto=format&fit=crop&q=80",
                audioUrl = "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3?filename=lofi-study-112191.mp3"
            )
        )
    }

    /**
     * Recommendation & Anti-collision Engine for "Dành riêng cho bạn":
     * • Ưu tiên category người dùng vừa chọn hoặc hay sử dụng
     * • Ranking category theo template đã sử dụng -> user thấy các template tương tự
     * • Chống trùng: chống trùng 10-15 templates giữa session, từ 10-15 trở đi random tiếp -> tránh cảm giác trùng.
     */
    fun recommendForYouTemplates(
        context: Context,
        candidatePool: List<TemplateModel>,
        count: Int = 8
    ): List<TemplateModel> {
        val prefs = PreferencesManager.getInstance(context)
        val rankedCategories = prefs.getUsedCategoriesRanked()
        val recentlyShownIds = prefs.getRecentlyShownTemplateIds().toSet()
        val lastSelected = prefs.getLastSelectedCategory()?.trim()?.uppercase()
        val cleanLast = lastSelected?.removePrefix("MUSIC_")?.removePrefix("AI_")

        // 1. Ensure candidate pool has all candidates with default Figma templates prioritized
        val fullPool = (getDefaultForYouTemplates() + candidatePool).distinctBy {
            it.id.ifBlank { it.title }
        }

        // 2. Score candidates by user's selected/ranked category:
        // Category vừa chọn được boost điểm cao nhất (10000), sau đó đến rank lịch sử sử dụng
        fun getCategoryWeight(template: TemplateModel): Int {
            val cat = template.category.trim().uppercase()
            val cleanCat = cat.removePrefix("MUSIC_").removePrefix("AI_")

            if (cleanLast != null && (cleanCat == cleanLast || cat == lastSelected)) {
                return 10000
            }

            val index = rankedCategories.indexOfFirst {
                val cleanRanked = it.trim().uppercase().removePrefix("MUSIC_").removePrefix("AI_")
                cleanRanked == cleanCat || it.trim().uppercase() == cat
            }
            return if (index != -1) {
                1000 - index * 10
            } else {
                0
            }
        }

        // 3. Partition pool into Fresh (not in 10-15 recent) vs Already Seen
        val freshCandidates = fullPool.filter { item ->
            val key = item.id.ifBlank { item.title }
            !recentlyShownIds.contains(key)
        }.toMutableList()

        val seenCandidates = fullPool.filter { item ->
            val key = item.id.ifBlank { item.title }
            recentlyShownIds.contains(key)
        }.toMutableList()

        // Sort fresh candidates by category relevance first, preserving default order for ties
        val sortedFresh = freshCandidates.sortedWith(
            compareByDescending<TemplateModel> { getCategoryWeight(it) }
        ).toMutableList()

        val result = mutableListOf<TemplateModel>()

        // Pick as many fresh candidates as available up to count
        val takeFromFresh = sortedFresh.take(count)
        result.addAll(takeFromFresh)

        // If fresh candidates are fewer than count (or after 10-15 items), random tiếp from remaining pool
        if (result.size < count) {
            val needed = count - result.size
            val remainingCandidates = seenCandidates.shuffled()
            result.addAll(remainingCandidates.take(needed))
        }

        // Fallback if result is still empty
        if (result.isEmpty()) {
            result.addAll(getDefaultForYouTemplates())
        }

        // 4. Record the selected template IDs for session anti-collision (keeps last 10-15)
        val selectedIds = result.map { it.id.ifBlank { it.title } }
        prefs.recordShownTemplateIds(selectedIds)

        return result
    }
}
