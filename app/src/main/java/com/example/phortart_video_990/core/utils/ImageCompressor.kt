package com.example.phortart_video_990.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Resize + nén ảnh thông minh trước khi upload lên backend AI.
 * Tối ưu hóa bộ nhớ RAM, chống tràn bộ nhớ (OutOfMemoryError),
 * giải phóng bộ nhớ ngay sau khi sử dụng và hỗ trợ giảm chất lượng ảnh
 * an toàn để server xử lý nhanh chóng và ổn định.
 */
object ImageCompressor {

    private const val TAG = "ImageCompressor"
    private const val MAX_SIZE_BYTES = 1024 * 1024L // 1MB max
    private const val DEFAULT_MAX_DIMENSION = 1280 // 1280px max dimension
    private const val MIN_ALLOWED_QUALITY = 65
    private const val QUALITY_STEP = 6
    private const val START_QUALITY = 82

    fun compressIfNeeded(
        context: Context,
        srcFile: File,
        maxSizeBytes: Long = MAX_SIZE_BYTES,
        maxDimension: Int = DEFAULT_MAX_DIMENSION
    ): File {
        if (!srcFile.exists() || srcFile.length() <= 0) return srcFile

        val rotation = getExifRotation(srcFile)

        if (srcFile.length() <= maxSizeBytes && rotation == 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            try {
                BitmapFactory.decodeFile(srcFile.absolutePath, bounds)
                val longSide = maxOf(bounds.outWidth, bounds.outHeight)
                if (longSide in 1..maxDimension) {
                    return srcFile
                }
            } catch (_: Throwable) {
                // Tiếp tục nén an toàn nếu đọc bounds lỗi
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
        try {
            BitmapFactory.decodeFile(srcFile.absolutePath, bounds)
        } catch (t: Throwable) {
            Log.e(TAG, "Lỗi đọc bounds: ${t.message}")
            return srcFile
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return srcFile
        }

        val rotation = getExifRotation(srcFile)
        val isRotated90or270 = (rotation == 90 || rotation == 270)
        val orientedWidth = if (isRotated90or270) bounds.outHeight else bounds.outWidth
        val orientedHeight = if (isRotated90or270) bounds.outWidth else bounds.outHeight
        val longSide = maxOf(orientedWidth, orientedHeight)

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetMaxDimension)

        // Sử dụng RGB_565 cho ảnh JPEG để giảm 50% RAM so với ARGB_8888
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        var bitmap: Bitmap? = null
        try {
            bitmap = BitmapFactory.decodeFile(srcFile.absolutePath, opts)
        } catch (t: Throwable) {
            Log.w(TAG, "OutOfMemory hoặc lỗi khi decode với sampleSize=$sampleSize: ${t.message}")
            System.gc()
            opts.inSampleSize = sampleSize * 2
            try {
                bitmap = BitmapFactory.decodeFile(srcFile.absolutePath, opts)
            } catch (t2: Throwable) {
                Log.e(TAG, "Không thể decode ảnh: ${t2.message}")
            }
        }

        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
            return srcFile
        }

        // Tạo Matrix kết hợp cả xoay hướng EXIF lẫn scale về targetMaxDimension trong 1 bước duy nhất
        val matrix = getExifMatrix(srcFile)
        val currentLongSide = if (isRotated90or270) maxOf(bitmap.height, bitmap.width) else maxOf(bitmap.width, bitmap.height)
        if (currentLongSide > targetMaxDimension) {
            val scale = targetMaxDimension.toFloat() / currentLongSide
            matrix.postScale(scale, scale)
        }

        var transformedBitmap: Bitmap = bitmap
        if (!matrix.isIdentity) {
            try {
                val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (transformed != bitmap) {
                    bitmap.recycle()
                }
                transformedBitmap = transformed
            } catch (t: Throwable) {
                Log.w(TAG, "Lỗi áp dụng Matrix: ${t.message}")
                System.gc()
                transformedBitmap = bitmap
            }
        }

        // Nén trực tiếp vào FileOutputStream trên đĩa để không chiếm dụng byte[] lớn trên bộ nhớ Heap
        val outFile = File(context.cacheDir, "compressed_input_${System.currentTimeMillis()}.jpg")
        var quality = START_QUALITY

        try {
            do {
                FileOutputStream(outFile).use { fos ->
                    transformedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
                }
                if (outFile.length() <= maxSizeBytes) break
                quality -= QUALITY_STEP
            } while (quality >= MIN_ALLOWED_QUALITY)

            // Nếu file vẫn vượt kích thước, tiến hành giảm tỉ lệ ảnh tiếp
            if (outFile.length() > maxSizeBytes && transformedBitmap.width > 640 && transformedBitmap.height > 640) {
                var currentBmp = transformedBitmap
                while (outFile.length() > maxSizeBytes && currentBmp.width > 640 && currentBmp.height > 640) {
                    val newW = (currentBmp.width * 0.82f).toInt().coerceAtLeast(1)
                    val newH = (currentBmp.height * 0.82f).toInt().coerceAtLeast(1)
                    val scaled = try {
                        Bitmap.createScaledBitmap(currentBmp, newW, newH, true)
                    } catch (t: Throwable) {
                        System.gc()
                        break
                    }
                    if (scaled != currentBmp && currentBmp != transformedBitmap) {
                        currentBmp.recycle()
                    }
                    currentBmp = scaled
                    FileOutputStream(outFile).use { fos ->
                        currentBmp.compress(Bitmap.CompressFormat.JPEG, 70, fos)
                    }
                }
                if (currentBmp != transformedBitmap) {
                    currentBmp.recycle()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Lỗi khi ghi file nén: ${t.message}", t)
        } finally {
            try {
                if (!transformedBitmap.isRecycled) {
                    transformedBitmap.recycle()
                }
            } catch (_: Throwable) {}
        }

        return if (outFile.exists() && outFile.length() > 0) outFile else srcFile
    }

    private fun calculateInSampleSize(width: Int, height: Int, target: Int): Int {
        var inSampleSize = 1
        val maxDim = maxOf(width, height)
        while ((maxDim / inSampleSize) > target) {
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
        } catch (_: Throwable) {
            0
        }
    }

    fun getExifMatrix(file: File): Matrix {
        val matrix = Matrix()
        return try {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.apply { postRotate(90f) }
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.apply { postRotate(180f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.apply { postRotate(270f) }
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.apply { postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.apply { postScale(1f, -1f) }
                ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply { postRotate(270f); postScale(-1f, 1f) }
                else -> matrix
            }
        } catch (_: Throwable) {
            matrix
        }
    }

    fun fixOrientation(file: File, bitmap: Bitmap): Bitmap {
        val matrix = getExifMatrix(file)
        if (matrix.isIdentity) return bitmap
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (t: Throwable) {
            Log.w(TAG, "fixOrientation lỗi: ${t.message}")
            bitmap
        }
    }
}
