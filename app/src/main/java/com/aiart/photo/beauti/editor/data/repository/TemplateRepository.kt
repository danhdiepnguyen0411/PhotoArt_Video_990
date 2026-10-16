package com.aiart.photo.beauti.editor.data.repository

import android.content.Context
import android.util.Log
import com.code12.art.ArtMagicClient
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.core.sdk.ArtSdkManager
import com.aiart.photo.beauti.editor.data.local.PreferencesManager
import com.aiart.photo.beauti.editor.data.model.CategoryModel
import com.aiart.photo.beauti.editor.data.model.TemplateModel
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class TemplateRepository(
    private val getClient: () -> ArtMagicClient? = { ArtSdkManager.getClient() }
) {

    private val gson = Gson()
    private val TAG = "TemplateRepository"
    private val TAG_VIDEOGEN = "PhotoArt_VideoGen"

    fun getDefaultCategories(): List<CategoryModel> = listOf(CategoryModel.ALL)

    fun getDefaultTemplates(): List<TemplateModel> = emptyList()

    fun getDefaultMusicCategories(): List<CategoryModel> = listOf(
        CategoryModel.ALL,
        CategoryModel(code = "MUSIC_EDM", name = "EDM", groupCode = "MUSIC"),
        CategoryModel(code = "MUSIC_CINEMATIC", name = "Cinematic", groupCode = "MUSIC"),
        CategoryModel(code = "MUSIC_BASS", name = "Bass", groupCode = "MUSIC"),
        CategoryModel(code = "MUSIC_HIPHOP", name = "Hiphop", groupCode = "MUSIC"),
        CategoryModel(code = "MUSIC_POP", name = "Pop", groupCode = "MUSIC")
    )

    fun getDefaultMusicTracks(): List<TemplateModel> = listOf(
        TemplateModel(
            id = "music_smith_solace",
            title = "SmithSolace",
            category = "MUSIC_EDM",
            duration = "00:25",
            audioUrl = "https://assets.mixkit.co/music/preview/mixkit-tech-house-vibes-130.mp3",
            templateType = "MUSIC"
        ),
        TemplateModel(
            id = "music_burning_ashes",
            title = "Burning Ashes",
            category = "MUSIC_CINEMATIC",
            duration = "00:22",
            audioUrl = "https://assets.mixkit.co/music/preview/mixkit-cinematic-mystery-suspense-hum-2852.mp3",
            templateType = "MUSIC"
        ),
        TemplateModel(
            id = "music_future_bass",
            title = "Future Bass",
            category = "MUSIC_BASS",
            duration = "00:20",
            audioUrl = "https://assets.mixkit.co/music/preview/mixkit-games-worldbeat-466.mp3",
            templateType = "MUSIC"
        ),
        TemplateModel(
            id = "music_sub_clair_kpop",
            title = "Sub Clair K-pop",
            category = "MUSIC_HIPHOP",
            duration = "00:24",
            audioUrl = "https://assets.mixkit.co/music/preview/mixkit-hip-hop-02-738.mp3",
            templateType = "MUSIC"
        ),
        TemplateModel(
            id = "music_summer_chill",
            title = "Summer Chill",
            category = "MUSIC_POP",
            duration = "00:26",
            audioUrl = "https://assets.mixkit.co/music/preview/mixkit-valley-sunset-127.mp3",
            templateType = "MUSIC"
        )
    )

    fun getDefaultDancingCategories(): List<CategoryModel> = listOf(
        CategoryModel.ALL,
        CategoryModel(code = "HOT_DANCE", name = "Hot Trend", groupCode = "DANCING"),
        CategoryModel(code = "TIKTOK_DANCE", name = "TikTok Dance", groupCode = "DANCING"),
        CategoryModel(code = "KPOP_DANCE", name = "K-Pop", groupCode = "DANCING"),
        CategoryModel(code = "HIPHOP_DANCE", name = "Hip Hop", groupCode = "DANCING"),
        CategoryModel(code = "SHUFFLE_DANCE", name = "Shuffle", groupCode = "DANCING"),
        CategoryModel(code = "STREET_DANCE", name = "Street Dance", groupCode = "DANCING")
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

    suspend fun getCategoriesByGroup(groupCode: String): List<CategoryModel> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val client = getClient()
            if (client == null) {
                if (continuation.isActive) continuation.resume(emptyList())
                return@suspendCancellableCoroutine
            }
            client.getImageEditingCategories(page = 0, size = 50, groupCode = groupCode) { jsonElement, throwable ->
                if (!continuation.isActive) return@getImageEditingCategories
                if (throwable != null || jsonElement == null) {
                    continuation.resume(emptyList())
                } else {
                    try {
                        val categories = parseCategories(jsonElement)
                        val activeCategories = categories.filter { it.active && it.name.isNotBlank() && !it.isAiTool }
                            .sortedBy { it.orderPosition }
                        continuation.resume(activeCategories)
                    } catch (e: Exception) {
                        continuation.resume(emptyList())
                    }
                }
            }
        }
    }

    /**
     * Fetch dancing categories from API.
     */
    suspend fun getDancingCategories(): List<CategoryModel> = withContext(Dispatchers.IO) {
        val client = getClient()
        if (client == null) {
            return@withContext getDefaultDancingCategories()
        }

        // 1. Try groupCode = "DANCING"
        var dancingCats = getCategoriesByGroup("DANCING")
        if (dancingCats.isEmpty()) {
            // 2. Try groupCode = "DANCE"
            dancingCats = getCategoriesByGroup("DANCE")
        }
        if (dancingCats.isEmpty()) {
            // 3. Fallback: filter general categories from API
            val allApi = getCategories().filter { it.code != "ALL" }
            val byDance = allApi.filter { it.isDanceCategory }
            dancingCats = if (byDance.isNotEmpty()) byDance else allApi.filter { !it.isMusicCategory && !it.isAiTool }
        }

        val resultList = mutableListOf(CategoryModel.ALL)
        if (dancingCats.isNotEmpty()) {
            val existingCodes = mutableSetOf("all")
            dancingCats.forEach { cat ->
                if (existingCodes.add(cat.code.lowercase())) {
                    resultList.add(cat)
                }
            }
        } else {
            resultList.addAll(getDefaultDancingCategories().filter { it.code != "ALL" })
        }
        resultList
    }

    /**
     * Fetch dancing templates from API. Excludes pure music items.
     */
    suspend fun getDancingTemplates(
        categoryCode: String? = null,
        categoryName: String? = null
    ): List<TemplateModel> = withContext(Dispatchers.IO) {
        val client = getClient()
        if (client == null) {
            return@withContext emptyList()
        }

        val isAll = categoryCode.isNullOrBlank() ||
                categoryCode.equals("ALL", ignoreCase = true) ||
                categoryCode.equals("Tất cả", ignoreCase = true)

        suspendCancellableCoroutine { continuation ->
            val filterParam = if (isAll) "" else categoryCode ?: ""
            client.getTemplateApps(page = 0, size = 100, category = "") { jsonElement, throwable ->
                if (!continuation.isActive) return@getTemplateApps

                if (throwable != null || jsonElement == null) {
                    Log.e(TAG, "Failed to fetch dancing templates: ${throwable?.message}")
                    continuation.resume(emptyList())
                } else {
                    try {
                        val allTemplates = parseTemplates(jsonElement)
                        val visualTemplates = allTemplates.filter { !it.isMusic }
                        val pool = if (visualTemplates.isNotEmpty()) visualTemplates else allTemplates.filter { it.isDancingTemplate }

                        if (pool.isEmpty()) {
                            continuation.resume(emptyList())
                            return@getTemplateApps
                        }

                        if (!isAll && filterParam.isNotBlank()) {
                            val targetCode = filterParam.trim().lowercase()
                            val targetName = categoryName?.trim()?.lowercase()
                            val filtered = pool.filter { item ->
                                val itemCat = item.category.trim().lowercase()
                                itemCat == targetCode ||
                                        (!targetName.isNullOrBlank() && itemCat == targetName) ||
                                        itemCat.contains(targetCode) ||
                                        (!targetName.isNullOrBlank() && itemCat.contains(targetName))
                            }
                            continuation.resume(if (filtered.isNotEmpty()) filtered else pool)
                            return@getTemplateApps
                        }

                        continuation.resume(pool)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing dancing templates: ${e.message}", e)
                        continuation.resume(emptyList())
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
        val fromApi = all.filter { it.code != "ALL" && it.isMusicCategory && !it.isAiTool }
        if (fromApi.isNotEmpty()) {
            musicCats.addAll(fromApi)
        } else {
            musicCats.addAll(getDefaultMusicCategories().filter { it.code != "ALL" })
        }
        return musicCats
    }

    /**
     * Fetch ALL real template items from API (both visual templates and music templates).
     */
    suspend fun getAllTemplateApps(): List<TemplateModel> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val client = getClient()
            if (client == null) {
                if (continuation.isActive) continuation.resume(emptyList())
                return@suspendCancellableCoroutine
            }
            client.getTemplateApps(page = 0, size = 100, category = "") { jsonElement, throwable ->
                if (!continuation.isActive) return@getTemplateApps
                if (throwable != null || jsonElement == null) {
                    Log.e(TAG, "Failed to fetch all templates: ${throwable?.message}")
                    continuation.resume(emptyList())
                } else {
                    try {
                        val allTemplates = parseTemplates(jsonElement)
                        Log.d(TAG, "getAllTemplateApps: loaded ${allTemplates.size} real templates from API")
                        continuation.resume(allTemplates)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in getAllTemplateApps: ${e.message}", e)
                        continuation.resume(emptyList())
                    }
                }
            }
        }
    }

    /**
     * Fetch ONLY template categories (excludes MUSIC and AI TOOL categories).
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

    // In-memory cache for music tracks
    private var cachedMusicTracksList: List<TemplateModel>? = null

    private suspend fun fetchMusicTracksForCategory(catCode: String): List<TemplateModel> = withContext(Dispatchers.IO) {
        val client = getClient() ?: return@withContext emptyList()
        suspendCancellableCoroutine { continuation ->
            client.getTemplateApps(page = 0, size = 100, category = catCode) { jsonElement, throwable ->
                if (!continuation.isActive) return@getTemplateApps
                if (throwable != null || jsonElement == null) {
                    continuation.resume(emptyList())
                } else {
                    try {
                        val items = parseTemplates(jsonElement)
                        val result = items.map { item ->
                            if (item.category.isBlank()) item.copy(category = catCode) else item
                        }
                        continuation.resume(result)
                    } catch (e: Exception) {
                        continuation.resume(emptyList())
                    }
                }
            }
        }
    }

    /**
     * Fetch music tracks for the choose music section.
     * Truly fetches from MUSIC categories, NOT from visual templates!
     */
    suspend fun getMusicTracks(
        categoryCode: String? = null,
        forceRefresh: Boolean = false
    ): List<TemplateModel> = withContext(Dispatchers.IO) {
        val isAll = categoryCode.isNullOrBlank() ||
                categoryCode.equals("ALL", ignoreCase = true) ||
                categoryCode.equals("Tất cả", ignoreCase = true)

        if (isAll) {
            val cached = cachedMusicTracksList
            if (!forceRefresh && cached != null && cached.isNotEmpty()) {
                return@withContext cached
            }

            val musicCats = getMusicCategories().filter { it.code != "ALL" }
            if (musicCats.isEmpty()) {
                return@withContext emptyList<TemplateModel>()
            }

            val combined: List<TemplateModel> = coroutineScope {
                val deferreds = musicCats.map { cat ->
                    async { fetchMusicTracksForCategory(cat.code) }
                }
                deferreds.awaitAll().flatten()
            }

            val seen = mutableSetOf<String>()
            val deduplicated = combined.filter { track ->
                val id = track.id.ifBlank { track.title }
                seen.add(id)
            }
            Log.d(TAG, "getMusicTracks: loaded ${deduplicated.size} music tracks across ${musicCats.size} categories")
            val finalTracks = if (deduplicated.isNotEmpty()) {
                cachedMusicTracksList = deduplicated
                deduplicated
            } else {
                getDefaultMusicTracks()
            }
            finalTracks
        } else {
            val targetCode = categoryCode!!.trim()
            cachedMusicTracksList?.let { cached ->
                val cleanTarget = targetCode.removePrefix("MUSIC_").lowercase()
                val fromCache = cached.filter {
                    it.category.equals(targetCode, ignoreCase = true) ||
                            it.category.removePrefix("MUSIC_").lowercase() == cleanTarget
                }
                if (fromCache.isNotEmpty()) return@withContext fromCache
            }

            var tracks = fetchMusicTracksForCategory(targetCode)
            if (tracks.isEmpty() && !targetCode.startsWith("MUSIC_", ignoreCase = true)) {
                tracks = fetchMusicTracksForCategory("MUSIC_$targetCode")
            }
            if (tracks.isEmpty()) {
                val cleanTarget = targetCode.removePrefix("MUSIC_").lowercase()
                tracks = getDefaultMusicTracks().filter {
                    it.category.equals(targetCode, ignoreCase = true) ||
                            it.category.removePrefix("MUSIC_").lowercase() == cleanTarget
                }
            }
            tracks
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

            Log.i(TAG_VIDEOGEN, "[API Request] Calling processImageEditing: code='$code', file='${file.name}' (${file.length() / 1024} KB)")
            client.processImageEditing(file, code, options, attachmentFiles, prompt) { result, error ->
                if (!continuation.isActive) return@processImageEditing
                if (error != null) {
                    Log.e(TAG_VIDEOGEN, "[API Response] processImageEditing FAILED: ${error.message}")
                    continuation.resume(Result.failure(error))
                } else if (result != null) {
                    Log.i(TAG_VIDEOGEN, "[API Response] processImageEditing SUCCEEDED: $result")
                    continuation.resume(Result.success(result))
                } else {
                    Log.e(TAG_VIDEOGEN, "[API Response] processImageEditing returned null result and error")
                    continuation.resume(Result.failure(Exception("Unknown error processing image")))
                }
            }
        }
    }

    /**
     * Generate static video using ArtMagicClient SDK backend.
     */
    suspend fun generateStaticVideo(
        code: String,
        file: java.io.File,
        options: String = "{}",
        prompt: String? = null
    ): Result<JsonElement> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val client = getClient()
            if (client == null) {
                if (continuation.isActive) continuation.resume(Result.failure(Exception("ArtMagicClient is not initialized")))
                return@suspendCancellableCoroutine
            }

            Log.i(TAG_VIDEOGEN, "[API Request] Calling generateStaticVideo: code='$code', file='${file.name}' (${file.length() / 1024} KB)")
            client.generateStaticVideo(code, file, options, prompt) { result, error ->
                if (!continuation.isActive) return@generateStaticVideo
                if (error != null) {
                    Log.e(TAG_VIDEOGEN, "[API Response] generateStaticVideo FAILED: ${error.message}")
                    continuation.resume(Result.failure(error))
                } else if (result != null) {
                    Log.i(TAG_VIDEOGEN, "[API Response] generateStaticVideo SUCCEEDED: $result")
                    continuation.resume(Result.success(result))
                } else {
                    continuation.resume(Result.failure(Exception("Unknown error generating static video")))
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

        Log.i(TAG_VIDEOGEN, "[Polling] Starting polling for requestId='$requestId', timeout=${timeoutMillis / 1000}s...")

        // Initial delay before 1st poll
        kotlinx.coroutines.delay(4_000L)

        val pollResult = kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
            while (true) {
                attempt++
                val res = getImageEditingResult(requestId)
                if (res.isSuccess) {
                    val data = res.getOrNull()
                    if (data != null && isImageEditingResultReady(data)) {
                        Log.i(TAG_VIDEOGEN, "[Polling] SUCCESS! Result ready on attempt #$attempt (elapsed ${(System.currentTimeMillis() - startTime) / 1000}s): $data")
                        return@withTimeoutOrNull res
                    }
                    Log.d(TAG_VIDEOGEN, "[Polling] Attempt #$attempt: Result not ready yet, waiting ${currentInterval}ms (elapsed ${(System.currentTimeMillis() - startTime) / 1000}s)...")
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

                    Log.w(TAG_VIDEOGEN, "[Polling] Attempt #$attempt: Server response: $msg (isRetriable=$isRetriable)")

                    if (!isRetriable) {
                        Log.e(TAG_VIDEOGEN, "[Polling] Fatal non-retriable error for requestId=$requestId: $msg")
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

        if (pollResult == null) {
            Log.e(TAG_VIDEOGEN, "[Polling] TIMEOUT after ${timeoutMillis / 1000}s for requestId=$requestId!")
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

    fun extractRequestIdFromError(errorMessage: String?): String? {
        if (errorMessage.isNullOrBlank()) return null
        val uuidMatch = Regex("([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})").find(errorMessage)
        return uuidMatch?.groupValues?.get(1)
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

    private fun parseSingleTemplate(jsonElement: JsonElement): TemplateModel? {
        return try {
            if (jsonElement.isJsonObject) {
                val obj = jsonElement.asJsonObject
                val data = obj.get("data")
                if (data != null && data.isJsonObject) {
                    return gson.fromJson(data, TemplateModel::class.java)
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing single template json: ${e.message}")
            null
        }
    }

    /**
     * Fetch a specific template detail by its code (calls client.getDetailTemplate).
     */
    suspend fun getTemplate(templateCode: String): TemplateModel? = withContext(Dispatchers.IO) {
        val client = getClient() ?: return@withContext null
        suspendCancellableCoroutine { continuation ->
            client.getDetailTemplate(templateCode) { jsonElement, throwable ->
                if (!continuation.isActive) return@getDetailTemplate
                if (throwable != null || jsonElement == null) {
                    Log.w(TAG, "getDetailTemplate failed for code='$templateCode': ${throwable?.message}")
                    continuation.resume(null)
                } else {
                    try {
                        val parsed = parseSingleTemplate(jsonElement)
                        continuation.resume(parsed)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing detail template for '$templateCode': ${e.message}", e)
                        continuation.resume(null)
                    }
                }
            }
        }
    }

    /**
     * Helper to extract a web URL from string sources (e.g., description, name, title).
     */
    fun extractUrlFromText(vararg sources: String?): String? {
        val urlRegex = Regex("""https?://[^\s"'<>]+""")
        for (src in sources) {
            if (!src.isNullOrBlank()) {
                val trimmed = src.trim()
                if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                    return trimmed
                }
                val match = urlRegex.find(trimmed)
                if (match != null) {
                    return match.value
                }
            }
        }
        return null
    }

    /**
     * Fetches legal links (Privacy Policy & Terms of Service) from CMS category APP_LEGAL_LINKS
     * using template codes APP_POLICIES_LINK and APP_TERMS_LINK.
     */
    suspend fun getLegalLinks(context: Context? = null): LegalLinks = withContext(Dispatchers.IO) {
        val client = getClient()
        var policyUrl: String? = null
        var termsUrl: String? = null

        // 1. Fetch templates in category APP_LEGAL_LINKS
        val legalTemplates: List<TemplateModel> = suspendCancellableCoroutine { continuation ->
            if (client == null) {
                continuation.resume(emptyList())
                return@suspendCancellableCoroutine
            }
            client.getTemplateApps(page = 0, size = 50, category = "APP_LEGAL_LINKS") { jsonElement, throwable ->
                if (!continuation.isActive) return@getTemplateApps
                if (throwable != null || jsonElement == null) {
                    Log.w(TAG, "Failed to fetch APP_LEGAL_LINKS: ${throwable?.message}")
                    continuation.resume(emptyList())
                } else {
                    try {
                        val templates = parseTemplates(jsonElement)
                        continuation.resume(templates)
                    } catch (e: Exception) {
                        continuation.resume(emptyList())
                    }
                }
            }
        }

        val policyItem = legalTemplates.find { it.id.equals("APP_POLICIES_LINK", ignoreCase = true) }
        val termsItem = legalTemplates.find { it.id.equals("APP_TERMS_LINK", ignoreCase = true) }

        policyUrl = extractUrlFromText(policyItem?.description, policyItem?.title, policyItem?.imageUrl, policyItem?.videoUrl)
        termsUrl = extractUrlFromText(termsItem?.description, termsItem?.title, termsItem?.imageUrl, termsItem?.videoUrl)

        // 2. If policyUrl wasn't in template list, try getDetailTemplate
        if (policyUrl.isNullOrBlank()) {
            val detail = getTemplate("APP_POLICIES_LINK")
            policyUrl = extractUrlFromText(detail?.description, detail?.title, detail?.imageUrl, detail?.videoUrl)
        }

        // 3. If termsUrl wasn't in template list, try getDetailTemplate
        if (termsUrl.isNullOrBlank()) {
            val detail = getTemplate("APP_TERMS_LINK")
            termsUrl = extractUrlFromText(detail?.description, detail?.title, detail?.imageUrl, detail?.videoUrl)
        }

        // 4. Fallback check: If policyUrl is still null, but termsItem has a policy URL (e.g. policies.html)
        if (policyUrl.isNullOrBlank() && termsUrl != null && (termsUrl.contains("policy", ignoreCase = true) || termsUrl.contains("policies", ignoreCase = true))) {
            policyUrl = termsUrl
        }

        Log.i(TAG, "getLegalLinks: resolved policyUrl='$policyUrl', termsUrl='$termsUrl'")

        // Save to preferences if context provided
        if (context != null) {
            val prefs = PreferencesManager.getInstance(context)
            if (!policyUrl.isNullOrBlank()) {
                prefs.legalPolicyUrl = policyUrl
            }
            if (!termsUrl.isNullOrBlank()) {
                prefs.legalTermsUrl = termsUrl
            }
        }

        val fallbackPrefs = context?.let { PreferencesManager.getInstance(it) }
        val finalPolicy = policyUrl ?: fallbackPrefs?.legalPolicyUrl ?: PreferencesManager.DEFAULT_POLICY_URL
        val finalTerms = termsUrl ?: fallbackPrefs?.legalTermsUrl ?: PreferencesManager.DEFAULT_TERMS_URL

        LegalLinks(policyUrl = finalPolicy, termsUrl = finalTerms)
    }

    suspend fun getPrivacyPolicyUrl(context: Context? = null): String {
        return getLegalLinks(context).policyUrl
    }

    suspend fun getTermsOfServiceUrl(context: Context? = null): String {
        return getLegalLinks(context).termsUrl
    }
}

data class LegalLinks(
    val policyUrl: String,
    val termsUrl: String
)
