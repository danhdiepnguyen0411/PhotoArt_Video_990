package com.example.phortart_video_990.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.phortart_video_990.core.sdk.ArtSdkManager
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.coroutines.resume

data class RestoreResultData(
    val inputUrl: String?,
    val outputUrl: String
)

class RestoreRepository(private val context: Context) {

    private val TAG = "RestoreProcess"

    /**
     * Copy input Uri to local temporary File for SDK processing on Dispatchers.IO,
     * ensuring EXIF rotation is normalized and compressed to prevent OOM/freezing.
     */
    suspend fun createTempFileFromUri(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val scheme = uri.scheme
            var fileToProcess: File? = null

            if (scheme == "file") {
                val f = File(uri.path ?: "")
                if (f.exists() && f.length() > 0) {
                    fileToProcess = f
                }
            }

            if (fileToProcess == null) {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                val rawFile = File(context.cacheDir, "restore_raw_${System.currentTimeMillis()}.jpg")
                FileOutputStream(rawFile).use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
                fileToProcess = rawFile
            }

            // Chuẩn hoá hướng xoay (EXIF rotation) và nén ảnh hợp lý (2048px, max 2MB)
            val normalizedFile = com.example.phortart_video_990.core.utils.ImageCompressor.compressIfNeeded(
                context = context,
                srcFile = fileToProcess,
                maxSizeBytes = 2 * 1024 * 1024L,
                maxDimension = 2048
            )

            if (rawFileExists(fileToProcess) && fileToProcess.absolutePath != normalizedFile.absolutePath && fileToProcess.name.startsWith("restore_raw_")) {
                try { fileToProcess.delete() } catch (_: Exception) {}
            }

            normalizedFile
        } catch (t: Throwable) {
            Log.e(TAG, "Error copying uri to file: ${t.message}", t)
            null
        }
    }

    private fun rawFileExists(file: File?): Boolean = file != null && file.exists()

    /**
     * Calls the ArtMagicClient SDK with code = "ai_restore_image" with timeout protection.
     */
    suspend fun restoreImage(userImageFile: File): Result<RestoreResultData> = withContext(Dispatchers.IO) {
        val timeoutResult = kotlinx.coroutines.withTimeoutOrNull(90_000L) {
            suspendCancellableCoroutine<Result<RestoreResultData>> { continuation ->
                try {
                    Log.d(TAG, "Bắt đầu khôi phục ảnh dung lượng ${userImageFile.length() / 1024} KB...")
                    val client = ArtSdkManager.getClient()

                    if (client == null) {
                        val errorMsg = "ArtMagicClient chưa được khởi tạo!"
                        Log.e(TAG, "🔥 CODE: NullClient - $errorMsg")
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(IllegalStateException(errorMsg)))
                        }
                        return@suspendCancellableCoroutine
                    }

                    client.processImageEditing(
                        file = userImageFile,
                        code = "ai_restore_image",
                        options = "{}",
                        attachmentFiles = emptyList(),
                        prompt = null
                    ) { jsonElement: JsonElement?, throwable: Throwable? ->
                        if (!continuation.isActive) return@processImageEditing

                        if (throwable != null) {
                            Log.e(TAG, "🔥 CODE: ${throwable.javaClass.simpleName}, MESSAGE: ${throwable.message}")
                            continuation.resume(Result.failure(throwable))
                        } else if (jsonElement != null) {
                            Log.d(TAG, "KẾT QUẢ: $jsonElement")
                            val resultData = extractRestoreResult(jsonElement)
                            if (resultData != null) {
                                continuation.resume(Result.success(resultData))
                            } else {
                                Log.w(TAG, "Json result does not contain output_url, fallback to local path")
                                continuation.resume(
                                    Result.success(
                                        RestoreResultData(
                                            inputUrl = null,
                                            outputUrl = userImageFile.absolutePath
                                        )
                                    )
                                )
                            }
                        } else {
                            Log.e(TAG, "🔥 MESSAGE: Kết quả trả về rỗng")
                            continuation.resume(Result.failure(Exception("Kết quả trả về rỗng")))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "🔥 Exception: ${e.message}", e)
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(e))
                    }
                }
            }
        }

        timeoutResult ?: Result.failure(java.util.concurrent.TimeoutException("Quá trình khôi phục ảnh quá thời gian chờ, vui lòng thử lại!"))
    }

    private fun extractRestoreResult(jsonElement: JsonElement): RestoreResultData? {
        try {
            if (!jsonElement.isJsonObject) return null
            val root = jsonElement.asJsonObject
            val dataObj = if (root.has("data") && root.get("data").isJsonObject) {
                root.getAsJsonObject("data")
            } else {
                root
            }

            var outputUrl: String? = null
            var inputUrl: String? = null

            val urlKeys = listOf(
                "output_url", "outputUrl", "imageUrl", "image_url", "url",
                "result_url", "resultUrl", "download_url", "downloadUrl",
                "file_url", "fileUrl", "media_url", "mediaUrl"
            )
            for (k in urlKeys) {
                val elem = dataObj.get(k)
                if (elem != null && !elem.isJsonNull) {
                    val s = elem.asString.trim()
                    if (s.isNotBlank()) {
                        outputUrl = s
                        break
                    }
                }
            }

            if (outputUrl == null) {
                val base64Keys = listOf("imageBase64", "image_base64", "base64", "image")
                for (k in base64Keys) {
                    val elem = dataObj.get(k)
                    if (elem != null && !elem.isJsonNull) {
                        val s = elem.asString.trim()
                        if (s.isNotBlank() && (s.startsWith("data:image") || s.length > 200)) {
                            outputUrl = saveBase64ToFile(s)
                            break
                        }
                    }
                }
            }

            if (dataObj.has("input_url") && !dataObj.get("input_url").isJsonNull) {
                inputUrl = dataObj.get("input_url").asString
            }

            if (!outputUrl.isNullOrBlank()) {
                return RestoreResultData(
                    inputUrl = inputUrl,
                    outputUrl = outputUrl
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi trích xuất url ảnh: ${e.message}")
        }
        return null
    }

    private fun saveBase64ToFile(base64Str: String): String? {
        return try {
            val cleanBase64 = if (base64Str.contains(",")) base64Str.substringAfter(",") else base64Str
            val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
            val file = File(context.cacheDir, "restore_out_${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi lưu base64 thành file: ${e.message}")
            null
        }
    }

    /**
     * Saves the restored image to device media storage.
     */
    suspend fun saveImageToGallery(
        imageSource: String,
        fallbackDrawable: android.graphics.drawable.Drawable? = null
    ): Uri? {
        return com.example.phortart_video_990.core.utils.MediaSaveUtils.saveImageToGallery(
            context = context,
            imageSource = imageSource,
            fallbackDrawable = fallbackDrawable,
            filenamePrefix = "PhotoArt_Restore"
        )
    }
}
