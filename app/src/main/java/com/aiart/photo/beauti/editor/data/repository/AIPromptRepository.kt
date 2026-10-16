package com.aiart.photo.beauti.editor.data.repository

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
import com.aiart.photo.beauti.editor.core.sdk.ArtSdkManager
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
            if (!jsonElement.isJsonObject) return null
            val root = jsonElement.asJsonObject

            fun findUrlInObj(obj: com.google.gson.JsonObject?): String? {
                if (obj == null) return null
                val keys = listOf(
                    "output_url", "outputUrl", "imageUrl", "image_url", "url",
                    "result_url", "resultUrl", "download_url", "downloadUrl",
                    "file_url", "fileUrl", "media_url", "mediaUrl"
                )
                for (k in keys) {
                    val elem = obj.get(k)
                    if (elem != null && !elem.isJsonNull) {
                        val str = elem.asString.trim()
                        if (str.isNotBlank()) return str
                    }
                }
                val base64Keys = listOf("imageBase64", "image_base64", "base64", "image")
                for (k in base64Keys) {
                    val elem = obj.get(k)
                    if (elem != null && !elem.isJsonNull) {
                        val str = elem.asString.trim()
                        if (str.isNotBlank() && (str.startsWith("data:image") || str.length > 200)) {
                            return saveBase64ToFile(str)
                        }
                    }
                }
                return null
            }

            // 1. Check data element
            if (root.has("data")) {
                val dataElem = root.get("data")
                if (dataElem.isJsonObject) {
                    val dataObj = dataElem.asJsonObject
                    findUrlInObj(dataObj)?.let { return it }
                    if (dataObj.has("data") && dataObj.get("data").isJsonObject) {
                        findUrlInObj(dataObj.getAsJsonObject("data"))?.let { return it }
                    }
                } else if (dataElem.isJsonArray) {
                    val arr = dataElem.asJsonArray
                    if (arr.size() > 0) {
                        val first = arr[0]
                        if (first.isJsonObject) {
                            findUrlInObj(first.asJsonObject)?.let { return it }
                        } else if (first.isJsonPrimitive && first.asJsonPrimitive.isString) {
                            val str = first.asString.trim()
                            if (str.isNotBlank()) return str
                        }
                    }
                } else if (dataElem.isJsonPrimitive && dataElem.asJsonPrimitive.isString) {
                    val str = dataElem.asString.trim()
                    if (str.isNotBlank()) return str
                }
            }

            // 2. Check root object
            findUrlInObj(root)?.let { return it }

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi trích xuất output_url: ${e.message}")
        }
        return null
    }

    private fun saveBase64ToFile(base64Str: String): String {
        return try {
            val cleanBase64 = if (base64Str.contains(",")) {
                base64Str.substringAfter(",")
            } else {
                base64Str
            }
            val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
            val file = File(context.cacheDir, "prompt_ai_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { it.write(bytes) }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode base64 image: ${e.message}")
            base64Str
        }
    }

    suspend fun saveImageToGallery(
        imageSource: String,
        fallbackDrawable: android.graphics.drawable.Drawable? = null
    ): Uri? {
        return com.aiart.photo.beauti.editor.core.utils.MediaSaveUtils.saveImageToGallery(
            context = context,
            imageSource = imageSource,
            fallbackDrawable = fallbackDrawable,
            filenamePrefix = "PhotoArt_Prompt"
        )
    }
}
