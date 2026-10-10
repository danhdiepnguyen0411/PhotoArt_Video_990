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

data class PromptGenerateResult(
    val outputUrl: String
)

class AIPromptRepository(private val context: Context) {

    private val TAG = "AIPromptProcess"

    suspend fun generateImageWithPrompt(prompt: String): Result<PromptGenerateResult> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                Log.d(TAG, " test  chạy tại ảnh:)")
                val client = ArtSdkManager.getClient()

                if (client == null) {
                    val errorMsg = "ArtMagicClient chưa được khởi tạo!"
                    Log.e(TAG, "🔥 CODE: NullClient")
                    Log.e(TAG, "🔥 MESSAGE: $errorMsg")
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(IllegalStateException(errorMsg)))
                    }
                    return@suspendCancellableCoroutine
                }


                client.processImageEditing(
                    file = null,
                    code = "ai_generate_image_with_prompt",
                    options = "{}",
                    attachmentFiles = emptyList(),
                    prompt = prompt,
                    callback = { jsonElement: JsonElement?, throwable: Throwable? ->
                        if (!continuation.isActive) return@processImageEditing

                        if (throwable != null) {
                            Log.e(TAG, "🔥 CODE: ${throwable.javaClass.simpleName}")
                            Log.e(TAG, "🔥 MESSAGE: ${throwable.message}")
                            Log.e(TAG, "🔥 DETAILS: ${throwable.stackTraceToString()}")
                            continuation.resume(Result.failure(throwable))
                        } else if (jsonElement != null) {
                            Log.d(TAG, "KẾT QUẢ: $jsonElement")
                            val outputUrl = extractOutputUrl(jsonElement)
                            if (!outputUrl.isNullOrBlank()) {
                                continuation.resume(Result.success(PromptGenerateResult(outputUrl)))
                            } else {
                                Log.w(TAG, "Json result does not contain output_url")
                                continuation.resume(Result.failure(Exception("Không tìm thấy output_url trong phản hồi")))
                            }
                        } else {
                            Log.e(TAG, "🔥 MESSAGE: Kết quả trả về rỗng")
                            continuation.resume(Result.failure(Exception("Kết quả trả về rỗng")))
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "🔥 CODE: Exception")
                Log.e(TAG, "🔥 MESSAGE: ${e.message}")
                Log.e(TAG, "🔥 DETAILS: ${e.stackTraceToString()}")
                if (continuation.isActive) {
                    continuation.resume(Result.failure(e))
                }
            }
        }
    }

    private fun extractOutputUrl(jsonElement: JsonElement): String? {
        try {
            if (jsonElement.isJsonObject) {
                val root = jsonElement.asJsonObject
                val dataObj = if (root.has("data") && root.get("data").isJsonObject) {
                    root.getAsJsonObject("data")
                } else {
                    root
                }

                if (dataObj.has("output_url") && !dataObj.get("output_url").isJsonNull) {
                    return dataObj.get("output_url").asString
                }
                if (dataObj.has("imageUrl") && !dataObj.get("imageUrl").isJsonNull) {
                    return dataObj.get("imageUrl").asString
                }
                if (dataObj.has("url") && !dataObj.get("url").isJsonNull) {
                    return dataObj.get("url").asString
                }
                if (dataObj.has("imageBase64") && !dataObj.get("imageBase64").isJsonNull) {
                    return dataObj.get("imageBase64").asString
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi trích xuất output_url: ${e.message}")
        }
        return null
    }

    suspend fun saveImageToGallery(imageSource: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageSource)
                .allowHardware(false)
                .build()

            val result = (loader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (result as? BitmapDrawable)?.bitmap ?: return@withContext null

            val filename = "PhotoArt_Prompt_${System.currentTimeMillis()}.jpg"
            var outputStream: OutputStream? = null
            var imageUri: Uri? = null

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhotoArt")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (imageUri != null) {
                outputStream = resolver.openOutputStream(imageUri)
                outputStream?.let { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                outputStream?.flush()
                outputStream?.close()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
            }

            imageUri
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi lưu ảnh: ${e.message}", e)
            null
        }
    }
}
