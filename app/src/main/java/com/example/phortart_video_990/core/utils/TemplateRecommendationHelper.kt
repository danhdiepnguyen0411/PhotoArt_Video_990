package com.example.phortart_video_990.core.utils

import android.content.Context
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.data.model.TemplateModel

object TemplateRecommendationHelper {

    /**
     * Cache danh sách gợi ý "Dành riêng cho bạn" và "Hot" cho toàn bộ phiên mở app hiện tại.
     * Chỉ thay đổi 1 lần mỗi khi người dùng vào app (hoặc khi chủ động swipe-to-refresh).
     */
    private var sessionForYouTemplates: List<TemplateModel>? = null
    private var sessionHotTemplates: List<TemplateModel>? = null

    /**
     * Kiểm tra xem trong phiên mở app này đã có danh sách "Dành riêng cho bạn" hay chưa.
     */
    fun hasSessionRecommendations(): Boolean {
        return !sessionForYouTemplates.isNullOrEmpty()
    }

    /**
     * Lấy danh sách "Dành riêng cho bạn" đã cache của phiên hiện tại.
     */
    fun getSessionForYouTemplates(): List<TemplateModel>? = sessionForYouTemplates

    /**
     * Lấy danh sách "Hot" đã cache của phiên hiện tại.
     */
    fun getSessionHotTemplates(): List<TemplateModel>? = sessionHotTemplates

    /**
     * Xóa cache phiên hiện tại (chỉ gọi khi người dùng chủ động vuốt làm mới - swipe refresh).
     */
    fun clearSessionCache() {
        sessionForYouTemplates = null
        sessionHotTemplates = null
    }

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
     * • Nếu trong phiên hiện tại đã có danh sách gợi ý và không forceRefresh -> trả về đúng danh sách đó
     *   để giao diện ổn định, không bị nhảy/đổi khi click vào mục hoặc quay lại màn hình.
     * • Chỉ tính toán danh sách mới khi vào app lần đầu trong phiên (hoặc khi forceRefresh).
     * • Ưu tiên category người dùng hay sử dụng.
     * • Chống trùng 10-15 templates giữa các lần vào app để tạo cảm giác mới mẻ mỗi lần mở app.
     */
    fun recommendForYouTemplates(
        context: Context,
        candidatePool: List<TemplateModel>,
        count: Int = 8,
        forceRefresh: Boolean = false
    ): List<TemplateModel> {
        if (!forceRefresh && !sessionForYouTemplates.isNullOrEmpty()) {
            return sessionForYouTemplates!!
        }

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

        // Cache danh sách cho phiên mở app hiện tại
        sessionForYouTemplates = result

        return result
    }

    /**
     * Lấy hoặc tính toán danh sách "Hot Templates" không trùng với "Dành riêng cho bạn",
     * được ghim cố định cho toàn bộ phiên mở app hiện tại.
     */
    fun computeAndCacheHotTemplates(
        candidatePool: List<TemplateModel>,
        recommended: List<TemplateModel>,
        count: Int = 8,
        forceRefresh: Boolean = false
    ): List<TemplateModel> {
        if (!forceRefresh && !sessionHotTemplates.isNullOrEmpty()) {
            return sessionHotTemplates!!
        }

        val recKeys = recommended.map { it.id.ifBlank { it.title }.trim().lowercase() }.toSet()
        val availableHot = candidatePool
            .distinctBy { it.id.ifBlank { it.title }.trim().lowercase() }
            .filterNot { recKeys.contains(it.id.ifBlank { it.title }.trim().lowercase()) }
            .take(count)

        sessionHotTemplates = availableHot
        return availableHot
    }
}
