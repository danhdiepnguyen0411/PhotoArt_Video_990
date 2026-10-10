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
     * Copy input Uri to local temporary File for SDK processing.
     */
    fun createTempFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "restore_input_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying uri to file: ${e.message}", e)
            null
        }
    }

    /**
     * Calls the ArtMagicClient SDK with code = "ai_restore_image".
     */
    suspend fun restoreImage(userImageFile: File): Result<RestoreResultData> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                Log.d(TAG, " test  chạy tại ảnh:)))))")
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
                    file = userImageFile,
                    code = "ai_restore_image",
                    options = "{}",
                    attachmentFiles = emptyList(),
                    prompt = null
                ) { jsonElement: JsonElement?, throwable: Throwable? ->
                    if (!continuation.isActive) return@processImageEditing

                    if (throwable != null) {
                        Log.e(TAG, "🔥 CODE: ${throwable.javaClass.simpleName}")
                        Log.e(TAG, "🔥 MESSAGE: ${throwable.message}")
                        Log.e(TAG, "🔥 DETAILS: ${throwable.stackTraceToString()}")
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
                Log.e(TAG, "🔥 CODE: Exception")
                Log.e(TAG, "🔥 MESSAGE: ${e.message}")
                Log.e(TAG, "🔥 DETAILS: ${e.stackTraceToString()}")
                if (continuation.isActive) {
                    continuation.resume(Result.failure(e))
                }
            }
        }
    }

    private fun extractRestoreResult(jsonElement: JsonElement): RestoreResultData? {
        try {
            if (jsonElement.isJsonObject) {
                val root = jsonElement.asJsonObject
                val dataObj = if (root.has("data") && root.get("data").isJsonObject) {
                    root.getAsJsonObject("data")
                } else {
                    root
                }

                var outputUrl: String? = null
                var inputUrl: String? = null

                if (dataObj.has("output_url") && !dataObj.get("output_url").isJsonNull) {
                    outputUrl = dataObj.get("output_url").asString
                } else if (dataObj.has("imageUrl") && !dataObj.get("imageUrl").isJsonNull) {
                    outputUrl = dataObj.get("imageUrl").asString
                } else if (dataObj.has("url") && !dataObj.get("url").isJsonNull) {
                    outputUrl = dataObj.get("url").asString
                } else if (dataObj.has("imageBase64") && !dataObj.get("imageBase64").isJsonNull) {
                    outputUrl = dataObj.get("imageBase64").asString
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi trích xuất url ảnh: ${e.message}")
        }
        return null
    }

    /**
     * Saves the restored image to device media storage.
     */
    suspend fun saveImageToGallery(imageSource: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageSource)
                .allowHardware(false)
                .build()

            val result = (loader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (result as? BitmapDrawable)?.bitmap ?: return@withContext null

            val filename = "PhotoArt_Restore_${System.currentTimeMillis()}.jpg"
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
            Log.e(TAG, "Lỗi lưu ảnh vào thư viện: ${e.message}", e)
            null
        }
    }
}
