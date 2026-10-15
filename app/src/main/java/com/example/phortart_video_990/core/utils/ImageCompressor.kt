package com.example.phortart_video_990.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Resize + nén ảnh thông minh trước khi upload lên backend AI.
 */
object ImageCompressor {

    private const val MAX_SIZE_BYTES = 1 * 1024 * 1024L // 1MB
    private const val DEFAULT_MAX_DIMENSION = 1920 // 1080p / 2K max dimension
    private const val MIN_ALLOWED_QUALITY = 75
    private const val QUALITY_STEP = 5
    private const val START_QUALITY = 92

    fun compressIfNeeded(
        context: Context,
        srcFile: File,
        maxSizeBytes: Long = MAX_SIZE_BYTES,
        maxDimension: Int = DEFAULT_MAX_DIMENSION
    ): File {
        if (!srcFile.exists()) return srcFile

        val rotation = getExifRotation(srcFile)

        if (srcFile.length() <= maxSizeBytes && rotation == 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(srcFile.absolutePath, bounds)
            val longSide = maxOf(bounds.outWidth, bounds.outHeight)
            if (longSide in 1..maxDimension) {
                return srcFile
            }
        }

        return compress(context, srcFile, maxSizeBytes, maxDimension)
    }

    fun compress(
        context: Context,
        srcFile: File,
        maxSizeBytes: Long = MAX_SIZE_BYTES,
        targetMaxDimension: Int = DEFAULT_MAX_DIMENSION
    ): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(srcFile.absolutePath, bounds)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return srcFile
        }

        val currentMaxDim = targetMaxDimension
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, currentMaxDim)

        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        var bitmap = try {
            BitmapFactory.decodeFile(srcFile.absolutePath, opts)
        } catch (oom: OutOfMemoryError) {
            System.gc()
            opts.inSampleSize = sampleSize * 2
            try {
                BitmapFactory.decodeFile(srcFile.absolutePath, opts)
            } catch (_: Throwable) {
                null
            }
        } ?: return srcFile

        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide > currentMaxDim) {
            val ratio = currentMaxDim.toFloat() / longSide
            val newW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
            val newH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
            try {
                val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
                if (scaled != bitmap) bitmap.recycle()
                bitmap = scaled
            } catch (oom: OutOfMemoryError) {
                System.gc()
            }
        }

        bitmap = fixOrientation(srcFile, bitmap)

        var quality = START_QUALITY
        val stream = ByteArrayOutputStream()

        do {
            stream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            if (stream.size() <= maxSizeBytes) break
            quality -= QUALITY_STEP
        } while (quality >= MIN_ALLOWED_QUALITY)

        while (stream.size() > maxSizeBytes && bitmap.width > 720 && bitmap.height > 720) {
            val newW = (bitmap.width * 0.85f).toInt().coerceAtLeast(1)
            val newH = (bitmap.height * 0.85f).toInt().coerceAtLeast(1)
            try {
                val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
                if (scaled != bitmap) bitmap.recycle()
                bitmap = scaled
            } catch (oom: OutOfMemoryError) {
                System.gc()
                break
            }

            stream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            if (stream.size() <= maxSizeBytes) break
        }

        val outFile = File(context.cacheDir, "compressed_input_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { fos ->
            stream.writeTo(fos)
        }
        bitmap.recycle()
        return outFile
    }

    private fun calculateInSampleSize(width: Int, height: Int, target: Int): Int {
        var inSampleSize = 1
        val longSide = maxOf(width, height)
        while (longSide / inSampleSize > target * 1.2f) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    fun getExifRotation(file: File): Int {
        return try {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_TRANSPOSE -> 90
                ExifInterface.ORIENTATION_ROTATE_180,
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
                ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_TRANSVERSE -> 270
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> -1
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    fun fixOrientation(file: File, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
                return bitmap
            }
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (e: Exception) {
            bitmap
        }
    }
}
