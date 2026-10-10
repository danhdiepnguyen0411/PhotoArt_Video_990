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

    fun getDefaultCategories(): List<CategoryModel> = listOf(CategoryModel.ALL)

    fun getDefaultTemplates(): List<TemplateModel> = emptyList()

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
                        val activeCategories = categories.filter { it.active && it.name.isNotBlank() && !it.isAiTool }
                            .sortedBy { it.orderPosition }

                        val resultList = mutableListOf(CategoryModel.ALL)
                        resultList.addAll(activeCategories)
                        Log.d(TAG, "Loaded ${resultList.size} categories from API")
                        continuation.resume(resultList)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing categories: ${e.message}", e)
                        continuation.resume(getDefaultCategories())
                    }
                }
            }
        }
    }

    /**
     * Fetch ONLY template categories (excludes MUSIC and AI TOOL categories).
     */
    suspend fun getTemplateCategories(): List<CategoryModel> {
        val all = getCategories()
        val templateCats = mutableListOf<CategoryModel>()
        val fromApi = all.filter { it.code != "ALL" && !it.isMusicCategory && !it.isAiTool }
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
        musicCats.addAll(all.filter { it.code != "ALL" && it.isMusicCategory && !it.isAiTool })
        return musicCats
    }

    /**
     * Fetch visual templates from API (excludes music tracks and AI TOOL).
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
            return@withContext emptyList()
        }

        suspendCancellableCoroutine { continuation ->
            val filterParam = if (isAll) "" else categoryCode ?: ""

            client.getTemplateApps(page = 0, size = 100, category = filterParam) { jsonElement, throwable ->
                if (!continuation.isActive) return@getTemplateApps

                if (throwable != null || jsonElement == null) {
                    Log.e(TAG, "Failed to fetch templates: ${throwable?.message}")
                    continuation.resume(emptyList())
                } else {
                    try {
                        val allTemplates = parseTemplates(jsonElement)
                        val visualTemplates = allTemplates.filter { !it.isMusic }

                        if (visualTemplates.isEmpty()) {
                            continuation.resume(emptyList())
                            return@getTemplateApps
                        }

                        if (!isAll && filterParam.isNotBlank()) {
                            val filtered = visualTemplates.filter {
                                it.category.equals(filterParam, ignoreCase = true) ||
                                        (!categoryName.isNullOrBlank() && it.category.equals(categoryName, ignoreCase = true))
                            }
                            continuation.resume(filtered)
                            return@getTemplateApps
                        }

                        continuation.resume(visualTemplates)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing templates: ${e.message}", e)
                        continuation.resume(emptyList())
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

    /**
     * Process image with template code using ArtMagicClient SDK backend.
     */
    suspend fun processImageEditing(
        file: java.io.File,
        code: String,
        options: String = "{}",
        attachmentFiles: List<java.io.File> = emptyList(),
        prompt: String? = null
    ): Result<JsonElement> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val client = getClient()
            if (client == null) {
                if (continuation.isActive) continuation.resume(Result.failure(Exception("ArtMagicClient is not initialized")))
                return@suspendCancellableCoroutine
            }

            client.processImageEditing(file, code, options, attachmentFiles, prompt) { result, error ->
                if (!continuation.isActive) return@processImageEditing
                if (error != null) {
                    continuation.resume(Result.failure(error))
                } else if (result != null) {
                    continuation.resume(Result.success(result))
                } else {
                    continuation.resume(Result.failure(Exception("Unknown error processing image")))
                }
            }
        }
    }

    /**
     * Poll result for image editing by requestId.
     */
    suspend fun getImageEditingResult(requestId: String): Result<JsonElement> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val client = getClient()
            if (client == null) {
                if (continuation.isActive) continuation.resume(Result.failure(Exception("ArtMagicClient is not initialized")))
                return@suspendCancellableCoroutine
            }

            client.getImageEditingResult(requestId) { result, error ->
                if (!continuation.isActive) return@getImageEditingResult
                if (error != null) {
                    continuation.resume(Result.failure(error))
                } else if (result != null) {
                    continuation.resume(Result.success(result))
                } else {
                    continuation.resume(Result.failure(Exception("Unknown error querying result")))
                }
            }
        }
    }

    /**
     * Poll image editing result with adaptive interval and timeout.
     */
    suspend fun pollImageEditingResult(
        requestId: String,
        intervalMillis: Long = 5_000L,
        timeoutMillis: Long = 180_000L,
        onProgress: ((Int) -> Unit)? = null
    ): Result<JsonElement> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var currentInterval = intervalMillis.coerceAtLeast(4_000L)
        val maxInterval = 8_000L
        var attempt = 0

        // Initial delay before 1st poll
        kotlinx.coroutines.delay(4_000L)

        val pollResult = kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
            while (true) {
                attempt++
                val res = getImageEditingResult(requestId)
                if (res.isSuccess) {
                    val data = res.getOrNull()
                    if (data != null && isImageEditingResultReady(data)) {
                        Log.d(TAG, "Polling requestId=$requestId succeeded on attempt $attempt")
                        return@withTimeoutOrNull res
                    }
                    currentInterval = (currentInterval * 1.2f).toLong().coerceAtMost(maxInterval)
                } else {
                    val err = res.exceptionOrNull()
                    val msg = err?.message.orEmpty()
                    val isRetriable = msg.contains("408") ||
                            msg.contains("429") ||
                            msg.contains("202") ||
                            msg.contains("404") && attempt <= 4 ||
                            msg.contains("processing", ignoreCase = true) ||
                            msg.contains("queue", ignoreCase = true) ||
                            msg.contains("pending", ignoreCase = true) ||
                            msg.contains("not ready", ignoreCase = true) ||
                            msg.contains("timeout", ignoreCase = true)

                    if (!isRetriable) {
                        Log.e(TAG, "Fatal error polling requestId=$requestId: $msg")
                        return@withTimeoutOrNull res
                    }
                }

                // Update simulated progress between 25% and 90%
                val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                val progress = (25 + 65 * (1.0 - Math.exp(-elapsedSec / 25.0))).toInt().coerceIn(25, 92)
                onProgress?.invoke(progress)

                kotlinx.coroutines.delay(currentInterval)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }

        pollResult ?: Result.failure(Exception("Polling timeout after ${timeoutMillis}ms for requestId=$requestId"))
    }

    fun extractRequestId(element: JsonElement?): String? {
        if (element == null || !element.isJsonObject) return null
        val obj = element.asJsonObject
        val dataObj = obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        return dataObj?.get("requestId")?.asString
            ?: dataObj?.get("request_id")?.asString
            ?: dataObj?.get("taskId")?.asString
            ?: dataObj?.get("task_id")?.asString
            ?: obj.get("requestId")?.asString
            ?: obj.get("request_id")?.asString
            ?: obj.get("taskId")?.asString
            ?: obj.get("task_id")?.asString
    }

    fun extractVideoUrl(element: JsonElement?): String? {
        if (element == null || !element.isJsonObject) return null
        val root = element.asJsonObject

        fun findUrlInObject(obj: com.google.gson.JsonObject?): String? {
            if (obj == null) return null
            val keys = listOf(
                "output_url", "video_url", "output_video_url", "url",
                "result_url", "output", "videoUrl", "outputUrl"
            )
            for (key in keys) {
                val elementValue = obj.get(key)
                if (elementValue != null && !elementValue.isJsonNull) {
                    val str = elementValue.asString
                    if (!str.isNullOrBlank() && str.startsWith("http", ignoreCase = true)) {
                        return str
                    }
                }
            }
            return null
        }

        // 1. Kiểm tra data object (nơi chứa output_url từ processImageEditing)
        val dataObj = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        findUrlInObject(dataObj)?.let { return it }

        // 2. Kiểm tra data con bên trong data.data nếu có
        val nestedDataObj = dataObj?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        findUrlInObject(nestedDataObj)?.let { return it }

        // 3. Kiểm tra root object
        findUrlInObject(root)?.let { return it }

        return null
    }

    private fun isImageEditingResultReady(data: JsonElement): Boolean {
        return extractVideoUrl(data) != null
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
