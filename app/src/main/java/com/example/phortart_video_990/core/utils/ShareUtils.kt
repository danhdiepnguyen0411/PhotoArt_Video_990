package com.example.phortart_video_990.core.utils

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.phortart_video_990.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ShareUtils {

    private const val TAG = "ShareUtils"
    private var lastIntentLaunchTime = 0L
    private const val SHARE_DEBOUNCE_MS = 1200L

    /**
     * Chống spam click share liên tục để tránh mở trùng đè nhiều Bottom Sheet chia sẻ
     */
    @Synchronized
    fun checkAndMarkLaunch(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastIntentLaunchTime < SHARE_DEBOUNCE_MS) {
            return false
        }
        lastIntentLaunchTime = now
        return true
    }

    /**
     * Chia sẻ Video kèm ảnh thumbnail preview hiển thị trên Bottom Sheet
     */
    fun shareVideo(
        context: Context,
        filePath: String?,
        chooserTitle: String? = null,
        thumbnailPath: String? = null
    ): Boolean {
        if (!checkAndMarkLaunch()) return false

        val effectiveTitle = chooserTitle ?: context.getString(R.string.share_video_ai_title)
        val cleanVideoPath = filePath?.removePrefix("file://")?.takeIf { it.isNotBlank() } ?: return false
        val videoFile = File(cleanVideoPath)
        if (!videoFile.exists()) return false

        return try {
            val shareFile = ensureVideoExtension(context, videoFile)
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, shareFile)

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                clipData = ClipData.newUri(context.contentResolver, "Video", contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(sendIntent, effectiveTitle).apply {
                putExtra(Intent.EXTRA_TITLE, effectiveTitle)
                clipData = sendIntent.clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            try {
                val resInfoList = context.packageManager.queryIntentActivities(chooserIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                for (resolveInfo in resInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    context.grantUriPermission(packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.grantUriPermission("android", contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}

            context.startActivity(chooserIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share video", e)
            false
        }
    }

    /**
     * Chia sẻ duy nhất file Ảnh (không kèm đường dẫn URL hay text caption)
     */
    suspend fun shareImage(
        context: Context,
        imageSource: Any?,
        chooserTitle: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!checkAndMarkLaunch()) return@withContext false
        if (imageSource == null) return@withContext false

        try {
            val authority = "${context.packageName}.fileprovider"
            val localFile = prepareLocalImageFile(context, imageSource) ?: return@withContext false
            val contentUri = FileProvider.getUriForFile(context, authority, localFile)

            val effectiveTitle = chooserTitle ?: context.getString(R.string.share_artwork_title)
            val mimeType = when (localFile.extension.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }

            // Chỉ gửi duy nhất tệp ảnh (EXTRA_STREAM), không kèm EXTRA_TEXT hay đường dẫn link
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                clipData = ClipData.newUri(context.contentResolver, "Image", contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(sendIntent, effectiveTitle).apply {
                putExtra(Intent.EXTRA_TITLE, effectiveTitle)
                clipData = sendIntent.clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            try {
                val resInfoList = context.packageManager.queryIntentActivities(
                    chooserIntent,
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                )
                for (resolveInfo in resInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    context.grantUriPermission(packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.grantUriPermission("android", contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                context.startActivity(chooserIntent)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share image", e)
            false
        }
    }

    private suspend fun prepareLocalImageFile(context: Context, imageSource: Any): File? = withContext(Dispatchers.IO) {
        try {
            val sharedDir = File(context.cacheDir, "shared_images").apply { mkdirs() }

            if (imageSource is Bitmap) {
                val targetFile = File(sharedDir, "share_img_${System.currentTimeMillis()}.jpg")
                FileOutputStream(targetFile).use { out ->
                    val safeBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && imageSource.config == Bitmap.Config.HARDWARE) {
                        imageSource.copy(Bitmap.Config.ARGB_8888, false)
                    } else {
                        imageSource
                    }
                    safeBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                return@withContext if (targetFile.exists() && targetFile.length() > 0) targetFile else null
            }

            if (imageSource is File && imageSource.exists() && imageSource.length() > 0) {
                val ext = imageSource.extension.ifBlank { "jpg" }
                val targetFile = File(sharedDir, "share_${imageSource.nameWithoutExtension}.$ext")
                if (!targetFile.exists() || targetFile.length() != imageSource.length()) {
                    imageSource.copyTo(targetFile, overwrite = true)
                }
                return@withContext targetFile
            }

            if (imageSource is String) {
                val cleanPath = imageSource.removePrefix("file://")
                val existing = File(cleanPath)
                if (existing.exists() && existing.length() > 0) {
                    val ext = existing.extension.ifBlank { "jpg" }
                    val targetFile = File(sharedDir, "share_${existing.nameWithoutExtension}.$ext")
                    if (!targetFile.exists() || targetFile.length() != existing.length()) {
                        existing.copyTo(targetFile, overwrite = true)
                    }
                    return@withContext targetFile
                }
            }

            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageSource)
                .allowHardware(false)
                .build()

            val result = (loader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (result as? BitmapDrawable)?.bitmap ?: return@withContext null

            val targetFile = File(sharedDir, "share_img_${System.currentTimeMillis()}.jpg")
            FileOutputStream(targetFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            if (targetFile.exists() && targetFile.length() > 0) targetFile else null
        } catch (e: Exception) {
            Log.e(TAG, "prepareLocalImageFile error: ${e.message}", e)
            null
        }
    }

    private fun extractVideoThumbnail(context: Context, videoFile: File): File? {
        if (!videoFile.exists() || videoFile.length() <= 0L) return null
        return try {
            val thumbDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val thumbFile = File(thumbDir, "thumb_${videoFile.nameWithoutExtension}.jpg")
            if (thumbFile.exists() && thumbFile.length() > 0L) {
                return thumbFile
            }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(videoFile.absolutePath)
                val bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.frameAtTime
                    ?: return null
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                if (thumbFile.exists() && thumbFile.length() > 0L) thumbFile else null
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun ensureVideoExtension(context: Context, file: File): File {
        if (file.extension.equals("mp4", ignoreCase = true)) return file
        val dir = File(context.cacheDir, "shared_videos").apply { mkdirs() }
        val target = File(dir, "${file.nameWithoutExtension.ifBlank { "video" }}.mp4")
        if (!target.exists() || target.length() != file.length()) {
            try {
                file.copyTo(target, overwrite = true)
            } catch (_: Exception) {
                return file
            }
        }
        return target
    }
}
