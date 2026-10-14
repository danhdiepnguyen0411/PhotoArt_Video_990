package com.example.phortart_video_990.core.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object AudioCacheManager {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun getCacheFolder(context: Context): File {
        val folder = File(context.cacheDir, "audio_cache")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return folder
    }

    private fun hashUrl(url: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(url.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            url.hashCode().toString()
        }
    }

    /**
     * Lấy file cache tương ứng với URL (có thể tồn tại hoặc chưa).
     */
    fun getCachedFile(context: Context, url: String): File {
        val hash = hashUrl(url)
        return File(getCacheFolder(context), "$hash.mp3")
    }

    private const val TAG = "PhotoArt_VideoGen"

    /**
     * Kiểm tra xem file audio tương ứng với URL đã được tải về cache chưa.
     */
    fun isCached(context: Context, url: String): Boolean {
        val file = getCachedFile(context, url)
        return file.exists() && file.length() > 0
    }

    /**
     * Tải hoặc lấy file audio từ cache. Đảm bảo chạy trên IO Dispatcher.
     * Trả về null nếu tải thất bại.
     */
    suspend fun getOrDownloadAudio(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null

        val targetFile = getCachedFile(context, url)
        if (targetFile.exists() && targetFile.length() > 0) {
            android.util.Log.d(TAG, "[AudioCache] Cache hit: ${targetFile.absolutePath} (${targetFile.length() / 1024} KB)")
            return@withContext targetFile
        }

        android.util.Log.i(TAG, "[AudioCache] Downloading audio from: $url")
        val startTime = System.currentTimeMillis()
        val tempFile = File(getCacheFolder(context), "${hashUrl(url)}_temp.download")
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.e(TAG, "[AudioCache] HTTP error ${response.code} downloading audio: $url")
                    return@withContext null
                }
                val body = response.body ?: run {
                    android.util.Log.e(TAG, "[AudioCache] Response body is null for: $url")
                    return@withContext null
                }

                FileOutputStream(tempFile).use { out ->
                    body.byteStream().buffered(32 * 1024).copyTo(out, bufferSize = 32 * 1024)
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                android.util.Log.i(TAG, "[AudioCache] Downloaded successfully: ${targetFile.absolutePath} (${targetFile.length() / 1024} KB) in ${System.currentTimeMillis() - startTime}ms")
                return@withContext targetFile
            } else {
                tempFile.delete()
                android.util.Log.e(TAG, "[AudioCache] Downloaded file is empty or missing: $url")
                return@withContext null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "[AudioCache] Exception downloading audio: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
            return@withContext null
        }
    }
}
