package com.example.phortart_video_990.core.utils

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ShareUtils {

    fun shareVideo(
        context: Context,
        filePath: String?,
        chooserTitle: String = "Chia sẻ video",
        thumbnailPath: String? = null
    ): Boolean {
        val cleanVideoPath = filePath?.removePrefix("file://")?.takeIf { it.isNotBlank() } ?: return false
        val videoFile = File(cleanVideoPath)
        if (!videoFile.exists()) return false

        return try {
            val shareFile = ensureVideoExtension(context, videoFile)
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, shareFile)

            val thumbFile = if (!thumbnailPath.isNullOrBlank()) {
                val cleanThumb = thumbnailPath.removePrefix("file://")
                val f = File(cleanThumb)
                if (f.exists() && f.length() > 0) f else extractVideoThumbnail(context, shareFile)
            } else {
                extractVideoThumbnail(context, shareFile)
            }

            val thumbUri = if (thumbFile != null && thumbFile.exists()) {
                try {
                    FileProvider.getUriForFile(context, authority, thumbFile)
                } catch (e: Exception) {
                    null
                }
            } else null

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TITLE, chooserTitle)
                val item = ClipData.Item(contentUri)
                clipData = ClipData(chooserTitle, arrayOf("video/mp4"), item)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(sendIntent, chooserTitle).apply {
                putExtra(Intent.EXTRA_TITLE, chooserTitle)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            context.startActivity(chooserIntent)
            true
        } catch (e: Exception) {
            Log.e("ShareUtils", "Failed to share video", e)
            false
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
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
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
