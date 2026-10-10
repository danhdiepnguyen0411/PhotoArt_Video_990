package com.example.phortart_video_990.data.repository

import android.util.Log
import com.code12.art.ArtMagicClient
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.sdk.ArtSdkManager
import com.example.phortart_video_990.data.model.CategoryModel
import com.example.phortart_video_990.data.model.TemplateModel
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class TemplateRepository(
    private val getClient: () -> ArtMagicClient? = { ArtSdkManager.getClient() }
) {

    private val gson = Gson()
    private val TAG = "TemplateRepository"

    fun getDefaultCategories(): List<CategoryModel> = listOf(
        CategoryModel(code = "ALL", name = "Tất cả", active = true),
        CategoryModel(code = "AI_EFFECT", name = "AI Effect", active = true),
        CategoryModel(code = "AI_STYLE", name = "AI Style", active = true),
        CategoryModel(code = "AI_DANCE", name = "AI Dance", active = true)
    )

    fun getDefaultTemplates(): List<TemplateModel> = listOf(
        TemplateModel(
            id = "tpl_1",
            title = "Cùng nhau đi khắp th...",
            duration = "00:16",
            views = "892,4K",
            photoCount = "6",
            category = "AI_EFFECT",
            localImageRes = R.drawable.template_trendy
        ),
        TemplateModel(
            id = "tpl_2",
            title = "Khoảnh khắc đáng nhớ",
            duration = "00:20",
            views = "756,3K",
            photoCount = "4",
            category = "AI_STYLE",
            localImageRes = R.drawable.template_selfie
        ),
        TemplateModel(
            id = "tpl_3",
            title = "Tình yêu ngọt ngào",
            duration = "00:15",
            views = "412,8K",
            photoCount = "2",
            category = "AI_DANCE",
            localImageRes = R.drawable.suggestion_ai_portrait
        ),
        TemplateModel(
            id = "tpl_4",
            title = "Một ngày ở Paris",
            duration = "00:18",
            views = "934,5K",
            photoCount = "5",
            category = "AI_EFFECT",
            localImageRes = R.drawable.suggestion_ai_style
        ),
        TemplateModel(
            id = "tpl_5",
            title = "Ánh nắng thanh xuân",
            duration = "00:21",
            views = "620,1K",
            photoCount = "3",
            category = "AI_STYLE",
            localImageRes = R.drawable.template_vintage
        ),
        TemplateModel(
            id = "tpl_6",
            title = "Vũ trụ huyền bí",
            duration = "00:17",
            views = "815,6K",
            photoCount = "7",
            category = "AI_DANCE",
            localImageRes = R.drawable.template_fantasy
        )
    )

    /**
     * Fetch all categories from API.
     */
    suspend fun getCategories(): List<CategoryModel> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val client = getClient()
            if (client == null) {
                Log.w(TAG, "ArtMagicClient is null when fetching categories")
                if (continuation.isActive) continuation.resume(getDefaultCategories())
                return@suspendCancellableCoroutine
            }

            client.getImageEditingCategories(page = 0, size = 50, groupCode = null) { jsonElement, throwable ->
                if (!continuation.isActive) return@getImageEditingCategories

                if (throwable != null || jsonElement == null) {
                    Log.e(TAG, "Failed to fetch categories: ${throwable?.message}")
                    continuation.resume(getDefaultCategories())
                } else {
                    try {
                        val categories = parseCategories(jsonElement)
                        val activeCategories = categories.filter { it.active && it.name.isNotBlank() }
                            .sortedBy { it.orderPosition }

                        if (activeCategories.isEmpty()) {
                            continuation.resume(getDefaultCategories())
                        } else {
                            val resultList = mutableListOf(CategoryModel.ALL)
                            resultList.addAll(activeCategories)
                            Log.d(TAG, "Loaded ${resultList.size} categories from API")
                            continuation.resume(resultList)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing categories: ${e.message}", e)
                        continuation.resume(getDefaultCategories())
                    }
                }
            }
        }
    }

    /**
     * Fetch ONLY template categories (excludes MUSIC categories).
     */
    suspend fun getTemplateCategories(): List<CategoryModel> {
        val all = getCategories()
        val templateCats = mutableListOf<CategoryModel>()
        val fromApi = all.filter { it.code != "ALL" && !it.isMusicCategory }
        if (fromApi.isEmpty()) {
            return getDefaultCategories()
        }
        templateCats.add(CategoryModel.ALL)
        templateCats.addAll(fromApi)
        return templateCats
    }

    /**
     * Fetch ONLY music categories for the music selector.
     */
    suspend fun getMusicCategories(): List<CategoryModel> {
        val all = getCategories()
        val musicCats = mutableListOf(CategoryModel.ALL)
        musicCats.addAll(all.filter { it.code != "ALL" && it.isMusicCategory })
        return musicCats
    }

    /**
     * Fetch visual templates from API (excludes music tracks).
     */
    suspend fun getTemplates(
        categoryCode: String? = null,
        categoryName: String? = null
    ): List<TemplateModel> = withContext(Dispatchers.IO) {
        val isAll = categoryCode.isNullOrBlank() ||
                categoryCode.equals("ALL", ignoreCase = true) ||
                categoryCode.equals("Tất cả", ignoreCase = true)

        val client = getClient()
        if (client == null) {
            val defaults = getDefaultTemplates()
            val filtered = if (isAll) defaults else defaults.filter { it.category.equals(categoryCode, ignoreCase = true) }.ifEmpty { defaults }
            return@withContext filtered
        }

        suspendCancellableCoroutine { continuation ->
            val filterParam = if (isAll) "" else categoryCode ?: ""

            client.getTemplateApps(page = 0, size = 100, category = filterParam) { jsonElement, throwable ->
                if (!continuation.isActive) return@getTemplateApps

                if (throwable != null || jsonElement == null) {
                    Log.e(TAG, "Failed to fetch templates: ${throwable?.message}")
                    val defaults = getDefaultTemplates()
                    val filtered = if (isAll) defaults else defaults.filter { it.category.equals(categoryCode, ignoreCase = true) }.ifEmpty { defaults }
                    continuation.resume(filtered)
                } else {
                    try {
                        val allTemplates = parseTemplates(jsonElement)
                        val visualTemplates = allTemplates.filter { !it.isMusic }

                        if (visualTemplates.isEmpty()) {
                            val defaults = getDefaultTemplates()
                            val filtered = if (isAll) defaults else defaults.filter { it.category.equals(categoryCode, ignoreCase = true) }.ifEmpty { defaults }
                            continuation.resume(filtered)
                            return@getTemplateApps
                        }

                        if (!isAll && filterParam.isNotBlank()) {
                            val filtered = visualTemplates.filter {
                                it.category.equals(filterParam, ignoreCase = true) ||
                                        (!categoryName.isNullOrBlank() && it.category.equals(categoryName, ignoreCase = true))
                            }
                            if (filtered.isNotEmpty()) {
                                continuation.resume(filtered)
                                return@getTemplateApps
                            }
                        }

                        continuation.resume(visualTemplates)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing templates: ${e.message}", e)
                        val defaults = getDefaultTemplates()
                        continuation.resume(defaults)
                    }
                }
            }
        }
    }

    /**
     * Fetch music tracks for the choose music section.
     */
    suspend fun getMusicTracks(
        categoryCode: String? = null
    ): List<TemplateModel> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val client = getClient()
            if (client == null) {
                if (continuation.isActive) continuation.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            val isAll = categoryCode.isNullOrBlank() ||
                    categoryCode.equals("ALL", ignoreCase = true) ||
                    categoryCode.equals("Tất cả", ignoreCase = true)

            client.getTemplateApps(page = 0, size = 100, category = "") { jsonElement, throwable ->
                if (!continuation.isActive) return@getTemplateApps

                if (throwable != null || jsonElement == null) {
                    continuation.resume(emptyList())
                } else {
                    try {
                        val allItems = parseTemplates(jsonElement)
                        val musicItems = allItems.filter { it.isMusic }

                        if (!isAll && !categoryCode.isNullOrBlank()) {
                            val target = categoryCode.trim()
                            val cleanTarget = target.removePrefix("MUSIC_").lowercase()
                            val filtered = musicItems.filter {
                                it.category.equals(target, ignoreCase = true) ||
                                        it.category.removePrefix("MUSIC_").lowercase() == cleanTarget
                            }
                            continuation.resume(if (filtered.isNotEmpty()) filtered else musicItems)
                        } else {
                            continuation.resume(musicItems)
                        }
                    } catch (e: Exception) {
                        continuation.resume(emptyList())
                    }
                }
            }
        }
    }

    private fun parseCategories(jsonElement: JsonElement): List<CategoryModel> {
        return try {
            if (jsonElement.isJsonObject) {
                val obj = jsonElement.asJsonObject
                val data = obj.get("data")
                if (data != null && data.isJsonArray) {
                    val listType = object : TypeToken<List<CategoryModel>>() {}.type
                    return gson.fromJson(data, listType) ?: emptyList()
                }
            }
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing categories json: ${e.message}")
            emptyList()
        }
    }

    private fun parseTemplates(jsonElement: JsonElement): List<TemplateModel> {
        return try {
            if (jsonElement.isJsonObject) {
                val obj = jsonElement.asJsonObject
                val data = obj.get("data")
                if (data != null && data.isJsonArray) {
                    val listType = object : TypeToken<List<TemplateModel>>() {}.type
                    return gson.fromJson(data, listType) ?: emptyList()
                }
            }
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing templates json: ${e.message}")
            emptyList()
        }
    }
}
