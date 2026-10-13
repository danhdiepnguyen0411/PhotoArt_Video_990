package com.example.phortart_video_990.core.utils

import android.content.Context
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.data.model.TemplateModel

object TemplateRecommendationHelper {

    /**
     * Default list for "Dành riêng cho bạn" - strictly returns empty list (no hardcoded templates).
     */
    fun getDefaultForYouTemplates(): List<TemplateModel> = emptyList()

    /**
     * Default list for "Hot" - strictly returns empty list (no hardcoded templates).
     */
    fun getDefaultHotTemplates(): List<TemplateModel> = emptyList()

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
        if (candidatePool.isEmpty()) return emptyList()

        val prefs = PreferencesManager.getInstance(context)
        val rankedCategories = prefs.getUsedCategoriesRanked()
        val recentlyShownIds = prefs.getRecentlyShownTemplateIds().toSet()
        val lastSelected = prefs.getLastSelectedCategory()?.trim()?.uppercase()
        val cleanLast = lastSelected?.removePrefix("MUSIC_")?.removePrefix("AI_")

        // 1. Ensure candidate pool has all candidates from real data only
        val fullPool = candidatePool.distinctBy {
            it.id.ifBlank { it.title }
        }

        // 2. Score candidates by user's selected/ranked category:
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

        // 4. Record the selected template IDs for session anti-collision (keeps last 10-15)
        val selectedIds = result.map { it.id.ifBlank { it.title } }
        prefs.recordShownTemplateIds(selectedIds)

        return result
    }
}
