package com.example.phortart_video_990.core.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object MediaSaveUtils {

    private const val TAG = "MediaSaveUtils"

    /**
     * Chuyển đổi bất kỳ Drawable nào (kể cả CrossfadeDrawable, TransitionDrawable) thành Bitmap phần mềm (software bitmap).
     */
    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val b = drawable.bitmap
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && b.config == Bitmap.Config.HARDWARE) {
                b.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                b
            }
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1080
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1080
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Trích xuất Bitmap từ fallbackDrawable hoặc imageSource (URL, File, Uri, Base64).
     */
    suspend fun extractBitmap(
        context: Context,
        imageSource: String,
        fallbackDrawable: Drawable? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        // 1. Ưu tiên lấy trực tiếp từ ImageView drawable đã hiển thị sẵn trên màn hình
        if (fallbackDrawable != null) {
            try {
                val bmp = drawableToBitmap(fallbackDrawable)
                if (bmp.width > 0 && bmp.height > 0) {
                    return@withContext bmp
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi lấy bitmap từ fallbackDrawable: ${e.message}")
            }
        }

        if (imageSource.isBlank()) return@withContext null

        // 2. Base64 String
        if (imageSource.startsWith("data:image") || (!imageSource.startsWith("http") && !imageSource.startsWith("/") && !imageSource.startsWith("content:") && imageSource.length > 200)) {
            try {
                val cleanBase64 = if (imageSource.contains(",")) imageSource.substringAfter(",") else imageSource
                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) return@withContext bmp
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi giải mã base64: ${e.message}")
            }
        }

        // 3. Content Uri
        if (imageSource.startsWith("content://")) {
            try {
                val uri = Uri.parse(imageSource)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) return@withContext bmp
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi đọc content uri: ${e.message}")
            }
        }

        // 4. Local File path
        if (imageSource.startsWith("/") || imageSource.startsWith("file://")) {
            try {
                val path = imageSource.removePrefix("file://")
                val f = File(path)
                if (f.exists() && f.length() > 0) {
                    val bmp = BitmapFactory.decodeFile(f.absolutePath)
                    if (bmp != null) return@withContext bmp
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi đọc file nội bộ: ${e.message}")
            }
        }

        // 5. Tải qua Coil singleton ImageLoader (sử dụng cache sẵn có)
        try {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageSource)
                .allowHardware(false)
                .build()

            val result = loader.execute(request)
            if (result is SuccessResult) {
                return@withContext drawableToBitmap(result.drawable)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi tải ảnh qua Coil: ${e.message}")
        }

        null
    }

    /**
     * Lưu ảnh an toàn vào Thư viện ảnh (Gallery) của thiết bị và thông báo MediaScanner lập chỉ mục ngay lập tức.
     */
    suspend fun saveImageToGallery(
        context: Context,
        imageSource: String,
        fallbackDrawable: Drawable? = null,
        filenamePrefix: String = "PhotoArt_Prompt"
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val bitmap = extractBitmap(context, imageSource, fallbackDrawable) ?: run {
                Log.e(TAG, "Không thể trích xuất Bitmap để lưu!")
                return@withContext null
            }

            saveBitmapToGallery(context, bitmap, filenamePrefix)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi lưu ảnh vào thư viện: ${e.message}", e)
            null
        }
    }

    /**
     * Lưu Bitmap vào Gallery hệ thống (Pictures/PhotoArt).
     */
    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        filenamePrefix: String = "PhotoArt_Prompt"
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val safeBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }

            val filename = "${filenamePrefix}_${System.currentTimeMillis()}.jpg"
            val resolver = context.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhotoArt")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                var imageUri: Uri? = try {
                    resolver.insert(collection, contentValues)
                } catch (e: Exception) {
                    null
                }
                if (imageUri == null) {
                    imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                }

                if (imageUri != null) {
                    resolver.openOutputStream(imageUri)?.use { out ->
                        safeBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        out.flush()
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)

                    @Suppress("DEPRECATION")
                    try {
                        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, imageUri))
                    } catch (_: Exception) {}

                    Log.d(TAG, "Đã lưu ảnh thành công vào MediaStore Q+: $imageUri")
                    return@withContext imageUri
                }
            } else {
                // Android 9 (API 28) trở xuống
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val photoArtDir = File(picturesDir, "PhotoArt").apply { mkdirs() }
                val imageFile = File(photoArtDir, filename)

                FileOutputStream(imageFile).use { out ->
                    safeBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    out.flush()
                }

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(imageFile.absolutePath),
                    arrayOf("image/jpeg")
                ) { path, uri ->
                    Log.d(TAG, "MediaScanner hoàn tất cho file: $path, uri: $uri")
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: Uri.fromFile(imageFile)

                Log.d(TAG, "Đã lưu ảnh thành công vào Legacy Storage: $uri")
                return@withContext uri
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi ghi file vào thư viện: ${e.message}", e)
            null
        }
    }
}
