package com.example.phortart_video_990.data.repository

import com.example.phortart_video_990.R
import com.example.phortart_video_990.data.api.ApiClient
import com.example.phortart_video_990.data.api.TemplateApiService
import com.example.phortart_video_990.data.model.TemplateModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TemplateRepository(
    private val api: TemplateApiService = ApiClient.templateApi
) {

    private val localFallbackTemplates = listOf(
        TemplateModel(
            id = "tpl_1",
            title = "Selfie Tỏa Sáng",
            duration = "0:15",
            views = "4.2k",
            source = "Photo AI",
            category = "Hot",
            localImageRes = R.drawable.template_selfie
        ),
        TemplateModel(
            id = "tpl_2",
            title = "Xứ Sở Thần Tiên",
            duration = "0:20",
            views = "3.8k",
            source = "CapCut",
            category = "Ảo ảnh",
            localImageRes = R.drawable.template_fantasy
        ),
        TemplateModel(
            id = "tpl_3",
            title = "Xu Hướng Giới Trẻ",
            duration = "0:12",
            views = "8.9k",
            source = "CapCut",
            category = "Hot",
            localImageRes = R.drawable.template_trendy
        ),
        TemplateModel(
            id = "tpl_4",
            title = "Thước Phim Điện Ảnh",
            duration = "0:30",
            views = "2.1k",
            source = "Photo AI",
            category = "Hiệu ứng",
            localImageRes = R.drawable.template_cinematic
        ),
        TemplateModel(
            id = "tpl_5",
            title = "Hoài Niệm Vintage",
            duration = "0:18",
            views = "5.6k",
            source = "CapCut",
            category = "Chân dung",
            localImageRes = R.drawable.template_vintage
        ),
        TemplateModel(
            id = "tpl_6",
            title = "Vũ Điệu Năng Động",
            duration = "0:15",
            views = "9.1k",
            source = "Photo AI",
            category = "Nhảy múa",
            localImageRes = R.drawable.suggestion_ai_dance
        ),
        TemplateModel(
            id = "tpl_7",
            title = "Ảo Mộng Anime",
            duration = "0:25",
            views = "7.4k",
            source = "CapCut",
            category = "Anime",
            localImageRes = R.drawable.suggestion_ai_style
        ),
        TemplateModel(
            id = "tpl_8",
            title = "Hiệu Ứng Ma Thuật",
            duration = "0:14",
            views = "6.3k",
            source = "Photo AI",
            category = "Hiệu ứng",
            localImageRes = R.drawable.suggestion_ai_effect
        )
    )

    suspend fun getTemplates(category: String): List<TemplateModel> = withContext(Dispatchers.IO) {
        try {
            val remote = api.getTemplates(if (category == "Tất cả") null else category)
            if (remote.isNotEmpty()) {
                remote
            } else {
                filterFallback(category)
            }
        } catch (e: Exception) {
            // Graceful fallback to bundled templates if network fails
            filterFallback(category)
        }
    }

    private fun filterFallback(category: String): List<TemplateModel> {
        return if (category == "Tất cả") {
            localFallbackTemplates
        } else {
            val filtered = localFallbackTemplates.filter { it.category.equals(category, ignoreCase = true) }
            if (filtered.isNotEmpty()) filtered else localFallbackTemplates
        }
    }
}
