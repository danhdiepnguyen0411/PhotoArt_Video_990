package com.example.phortart_video_990.core.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object VideoGenerator {

    private const val TAG = "PhotoArt_VideoGen"
    private const val MIME_TYPE = "video/avc" // H.264
    private const val FRAME_RATE = 30
    private const val I_FRAME_INTERVAL = 1
    private const val BIT_RATE = 4_000_000 // 4 Mbps
    private const val VIDEO_WIDTH = 720
    private const val VIDEO_HEIGHT = 1280

    // Thời gian của mỗi hiệu ứng chuyển cảnh (12 frames = 0.4s ở 30fps)
    private const val TRANSITION_FRAMES = 12

    enum class TransitionType {
        CROSS_FADE,
        SLIDE_LEFT,
        ZOOM_IN,
        WIPE_DOWN,
        FLASH_PULSE
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Tính toán tổng thời lượng video tối ưu theo số ảnh:
     * - 1 ảnh: 4.0s
     * - 2-3 ảnh: 2.5s / ảnh (5.0s - 7.5s)
     * - 4-6 ảnh: 2.2s / ảnh (8.8s - 13.2s)
     * - 7+ ảnh: 2.0s / ảnh (14.0s - 20.0s)
     * Giúp video ngắn gọn, sinh nhanh và mượt mà cho story/reels.
     */
    fun calculateVideoDuration(photoCount: Int): Float {
        if (photoCount <= 0) return 15f
        val calculated = when {
            photoCount == 1 -> 4.0f
            photoCount in 2..3 -> photoCount * 2.5f
            photoCount in 4..6 -> photoCount * 2.2f
            else -> photoCount * 2.0f
        }
        return calculated.coerceIn(4f, 30f)
    }

    fun calculateSecondsPerPhoto(durationSec: Float, photoCount: Int): Float {
        if (photoCount <= 0) return 2.0f
        return (durationSec / photoCount).coerceAtLeast(1.5f)
    }

    /**
     * Tạo video MP4 từ danh sách URI ảnh và tùy chọn ghép nhạc nền.
     */
    suspend fun generateMp4FromPhotos(
        context: Context,
        photoUris: List<String>,
        durationSec: Float = 15f,
        audioUrl: String? = null,
        transitions: List<TransitionType>? = null,
        aspectRatio: String = "9:16",
        onProgress: (progress: Int, status: String) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val overallStartTime = System.currentTimeMillis()
        val (videoWidth, videoHeight) = when (aspectRatio) {
            "1:1" -> Pair(720, 720)
            "16:9" -> Pair(1280, 720)
            else -> Pair(720, 1280)
        }

        val runtime = Runtime.getRuntime()
        val freeMemMb = runtime.freeMemory() / (1024 * 1024)
        val totalMemMb = runtime.totalMemory() / (1024 * 1024)
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)

        Log.i(TAG, "==========================================================")
        Log.i(TAG, ">>> [START VIDEO GENERATION]")
        Log.i(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT}, Android ${Build.VERSION.RELEASE})")
        Log.i(TAG, "RAM: Free=${freeMemMb}MB | Total=${totalMemMb}MB | Max=${maxMemMb}MB")
        Log.i(TAG, "Params: Photos=${photoUris.size} | Duration=${durationSec}s | Ratio=$aspectRatio | Resolution=${videoWidth}x${videoHeight}")
        Log.i(TAG, "AudioUrl: ${audioUrl ?: "<NONE>"}")
        Log.i(TAG, "==========================================================")

        val outputDir = File(context.cacheDir, "videos").apply { mkdirs() }
        val rawVideoFile = File(outputDir, "raw_${System.currentTimeMillis()}.mp4")

        withContext(Dispatchers.Main) {
            onProgress(5, "Đang xử lý hình ảnh...")
        }

        // 1. Tải và tiền xử lý hình ảnh (Scale chuẩn kích thước khung hình video để vẽ siêu nhanh & tránh tràn RAM)
        Log.i(TAG, "[Phase 1/4] Starting image decoding for ${photoUris.size} photos...")
        val decodeStartTime = System.currentTimeMillis()
        val loadedBitmaps = mutableListOf<Bitmap>()
        for ((idx, uriStr) in photoUris.withIndex()) {
            val photoStart = System.currentTimeMillis()
            val bmp = decodeAndPrepareBitmap(context, Uri.parse(uriStr), videoWidth, videoHeight, idx)
            if (bmp != null) {
                loadedBitmaps.add(bmp)
                Log.d(TAG, "  Photo [$idx/${photoUris.size}] decoded in ${System.currentTimeMillis() - photoStart}ms (${bmp.width}x${bmp.height})")
            } else {
                Log.w(TAG, "  Photo [$idx/${photoUris.size}] FAILED to decode: $uriStr")
            }
        }

        val decodeElapsed = System.currentTimeMillis() - decodeStartTime
        Log.i(TAG, "[Phase 1/4] Image decoding finished in ${decodeElapsed}ms. Loaded ${loadedBitmaps.size}/${photoUris.size} bitmaps.")

        if (loadedBitmaps.isEmpty()) {
            val errorMsg = "Không thể giải mã bất kỳ ảnh nào để tạo video"
            Log.e(TAG, "!!! FATAL: $errorMsg")
            throw IllegalArgumentException(errorMsg)
        }

        withContext(Dispatchers.Main) {
            onProgress(15, "Đang mã hóa khung hình video...")
        }

        // 2. Chuẩn bị danh sách chuyển cảnh ngẫu nhiên (nếu chưa có)
        val transitionList = transitions ?: generateRandomTransitions(loadedBitmaps.size)

        val totalFrames = (durationSec * FRAME_RATE).toInt().coerceAtLeast(FRAME_RATE)
        val framesPerPhoto = totalFrames.toFloat() / loadedBitmaps.size

        val format = MediaFormat.createVideoFormat(MIME_TYPE, videoWidth, videoHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        Log.i(TAG, "[Phase 2/4] Initializing MediaCodec encoder: MIME=$MIME_TYPE, ${videoWidth}x${videoHeight}, bitRate=$BIT_RATE, fps=$FRAME_RATE, totalFrames=$totalFrames (${framesPerPhoto} frames/photo)")
        val encoder = MediaCodec.createEncoderByType(MIME_TYPE)
        Log.i(TAG, "Selected MediaCodec encoder name: '${encoder.name}'")
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(rawVideoFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var videoTrackIndex = -1
        val bufferInfo = MediaCodec.BufferInfo()
        var writtenFrameCount = 0L

        val encodeStartTime = System.currentTimeMillis()
        Log.i(TAG, "[Phase 2/4] Starting video frame rendering loop ($totalFrames frames)...")

        try {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            var lastReportedPercent = -1

            for (frameIndex in 0 until totalFrames) {
                // Hỗ trợ hủy tiến trình ngay khi người dùng nhấn thoát / hủy
                kotlinx.coroutines.currentCoroutineContext().ensureActive()

                // QUAN TRỌNG: Xả buffer encoder TRƯỚC KHI lockCanvas.
                // Tránh deadlock BufferQueue trên các dòng chip chậm/cũ (Exynos, Mali, Unisoc)
                // vì lockCanvas sẽ bị treo vô tận nếu tất cả buffer của Surface đang bị encoder giữ.
                drainEncoder(
                    encoder = encoder,
                    muxer = muxer,
                    bufferInfo = bufferInfo,
                    endOfStream = false,
                    timeoutUs = 2500L,
                    onMuxerStart = { track ->
                        muxerStarted = true
                        videoTrackIndex = track
                        Log.i(TAG, "MediaMuxer started with video track index: $track")
                    },
                    isMuxerStarted = { muxerStarted },
                    getVideoTrackIndex = { videoTrackIndex },
                    getFramePtsUs = { (writtenFrameCount++ * 1_000_000L) / FRAME_RATE }
                )

                val photoIndex = ((frameIndex / framesPerPhoto).toInt()).coerceIn(0, loadedBitmaps.lastIndex)
                val currentBitmap = loadedBitmaps[photoIndex]

                val frameWithinPhoto = frameIndex - (photoIndex * framesPerPhoto).toInt()
                val remainingFramesInPhoto = framesPerPhoto.toInt() - frameWithinPhoto

                val nextPhotoIndex = (photoIndex + 1).coerceAtMost(loadedBitmaps.lastIndex)
                val isTransitioning = remainingFramesInPhoto <= TRANSITION_FRAMES && photoIndex < loadedBitmaps.lastIndex

                // Vẽ an toàn qua lockCanvas(null) - trực tiếp trên bộ đệm bộ nhớ
                val canvas: Canvas = inputSurface.lockCanvas(null)
                try {
                    if (isTransitioning) {
                        val nextBitmap = loadedBitmaps[nextPhotoIndex]
                        val progress = 1f - (remainingFramesInPhoto.toFloat() / TRANSITION_FRAMES)
                        val transition = transitionList.getOrElse(photoIndex) { TransitionType.CROSS_FADE }

                        drawTransitionFrame(canvas, currentBitmap, nextBitmap, transition, progress, paint, videoWidth.toFloat(), videoHeight.toFloat())
                    } else {
                        canvas.drawBitmap(currentBitmap, 0f, 0f, paint)
                    }
                } finally {
                    try {
                        inputSurface.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in unlockCanvasAndPost on frame $frameIndex", e)
                    }
                }

                // Xả tiếp sau khi vừa post frame
                drainEncoder(
                    encoder = encoder,
                    muxer = muxer,
                    bufferInfo = bufferInfo,
                    endOfStream = false,
                    timeoutUs = 2500L,
                    onMuxerStart = { track ->
                        muxerStarted = true
                        videoTrackIndex = track
                        Log.i(TAG, "MediaMuxer started with video track index: $track")
                    },
                    isMuxerStarted = { muxerStarted },
                    getVideoTrackIndex = { videoTrackIndex },
                    getFramePtsUs = { (writtenFrameCount++ * 1_000_000L) / FRAME_RATE }
                )

                // Log định kỳ tiến độ mã hóa (khung đầu, mỗi 30 khung ~ 1s, và khung cuối)
                if (frameIndex == 0 || frameIndex % 30 == 0 || frameIndex == totalFrames - 1) {
                    val elapsed = System.currentTimeMillis() - encodeStartTime
                    val fps = if (elapsed > 0) (frameIndex * 1000f / elapsed) else 0f
                    Log.d(TAG, "  [Frame $frameIndex/$totalFrames] (${(frameIndex * 100 / totalFrames)}%) | Photo #$photoIndex | Trans=$isTransitioning | Packets=$writtenFrameCount | Elapsed=${elapsed}ms (~${String.format("%.1f", fps)} fps)")
                }

                val percent = 15 + ((frameIndex.toFloat() / totalFrames) * 60).toInt()
                if (percent != lastReportedPercent) {
                    lastReportedPercent = percent
                    withContext(Dispatchers.Main) {
                        onProgress(percent, "Đang mã hóa khung hình video ($percent%)...")
                    }
                }
            }

            Log.i(TAG, "[Phase 2/4] Signaling EOS on input surface... Total frames drawn: $totalFrames in ${System.currentTimeMillis() - encodeStartTime}ms")
            encoder.signalEndOfInputStream()
            drainEncoder(
                encoder = encoder,
                muxer = muxer,
                bufferInfo = bufferInfo,
                endOfStream = true,
                timeoutUs = 10000L,
                onMuxerStart = { track ->
                    muxerStarted = true
                    videoTrackIndex = track
                    Log.i(TAG, "MediaMuxer started on EOS with track index: $track")
                },
                isMuxerStarted = { muxerStarted },
                getVideoTrackIndex = { videoTrackIndex },
                getFramePtsUs = { (writtenFrameCount++ * 1_000_000L) / FRAME_RATE }
            )
            Log.i(TAG, "[Phase 2/4] Raw video encoded successfully! Written packets: $writtenFrameCount | FileSize: ${rawVideoFile.length() / 1024} KB")
        } catch (e: Exception) {
            Log.e(TAG, "!!! Exception during video frame encoding: ${e.message}", e)
            throw e
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            try { encoder.release() } catch (_: Exception) {}
            try { inputSurface.release() } catch (_: Exception) {}
            try {
                if (muxerStarted) muxer.stop()
                muxer.release()
            } catch (_: Exception) {}

            loadedBitmaps.forEach {
                if (!it.isRecycled) it.recycle()
            }

            // Nếu coroutine bị hủy bởi người dùng, dọn dẹp file tạm ngay
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                Log.w(TAG, "Coroutine cancelled by user. Deleting raw video file: ${rawVideoFile.absolutePath}")
                try { rawVideoFile.delete() } catch (_: Exception) {}
            }
        }

        kotlinx.coroutines.currentCoroutineContext().ensureActive()

        // 3. Nếu không có nhạc thì trả về video thô
        if (audioUrl.isNullOrBlank()) {
            val totalTimeMs = System.currentTimeMillis() - overallStartTime
            Log.i(TAG, "==========================================================")
            Log.i(TAG, ">>> [VIDEO GENERATION COMPLETE - NO AUDIO] in ${totalTimeMs}ms (${totalTimeMs / 1000}s)!")
            Log.i(TAG, "Output file: ${rawVideoFile.absolutePath} (${rawVideoFile.length() / 1024} KB)")
            Log.i(TAG, "==========================================================")
            withContext(Dispatchers.Main) {
                onProgress(100, "Hoàn tất video!")
            }
            return@withContext rawVideoFile
        }

        // 4. Ghép nhạc nền an toàn (Safe Audio Muxing)
        Log.i(TAG, "[Phase 3/4] Preparing background audio: $audioUrl")
        withContext(Dispatchers.Main) {
            onProgress(80, "Đang chuẩn bị ghép nhạc nền...")
        }

        val finalWithAudioFile = File(outputDir, "PhotoArt_${System.currentTimeMillis()}.mp4")
        var tempDownloadedAudioFile: File? = null

        try {
            val audioSourceFile: File = if (File(audioUrl).exists()) {
                Log.d(TAG, "  Audio found on local disk: $audioUrl")
                File(audioUrl)
            } else if (AudioCacheManager.isCached(context, audioUrl)) {
                val cached = AudioCacheManager.getCachedFile(context, audioUrl)
                Log.d(TAG, "  Audio found in cache: ${cached.absolutePath} (${cached.length() / 1024} KB)")
                cached
            } else {
                withContext(Dispatchers.Main) {
                    onProgress(82, "Đang tải nhạc nền...")
                }
                Log.i(TAG, "  Downloading audio: $audioUrl")
                val downloadStart = System.currentTimeMillis()
                val downloaded = AudioCacheManager.getOrDownloadAudio(context, audioUrl)
                if (downloaded != null && downloaded.exists() && downloaded.length() > 0) {
                    Log.i(TAG, "  Audio downloaded via cache manager in ${System.currentTimeMillis() - downloadStart}ms")
                    downloaded
                } else {
                    val temp = File(outputDir, "temp_audio_${System.currentTimeMillis()}.mp3")
                    tempDownloadedAudioFile = temp
                    downloadAudioFile(audioUrl, temp)
                    Log.i(TAG, "  Audio downloaded directly in ${System.currentTimeMillis() - downloadStart}ms (${temp.length() / 1024} KB)")
                    temp
                }
            }

            kotlinx.coroutines.currentCoroutineContext().ensureActive()

            withContext(Dispatchers.Main) {
                onProgress(90, "Đang trộn âm thanh vào video...")
            }

            Log.i(TAG, "[Phase 4/4] Muxing video and audio tracks...")
            val muxStart = System.currentTimeMillis()
            val muxSuccess = try {
                muxVideoAndAudio(
                    videoInput = rawVideoFile,
                    audioInput = audioSourceFile,
                    output = finalWithAudioFile,
                    maxDurationUs = (durationSec * 1_000_000L).toLong()
                )
            } catch (e: Exception) {
                Log.e(TAG, "!!! Exception in muxVideoAndAudio: ${e.message}", e)
                false
            }
            Log.i(TAG, "[Phase 4/4] Audio muxing finished in ${System.currentTimeMillis() - muxStart}ms. Success=$muxSuccess")

            tempDownloadedAudioFile?.delete()

            withContext(Dispatchers.Main) {
                onProgress(100, "Hoàn tất video!")
            }

            val totalTimeMs = System.currentTimeMillis() - overallStartTime
            // Chỉ xóa file thô khi file ghép nhạc đã được tạo thành công
            if (muxSuccess && finalWithAudioFile.exists() && finalWithAudioFile.length() > 0) {
                try { rawVideoFile.delete() } catch (_: Exception) {}
                Log.i(TAG, "==========================================================")
                Log.i(TAG, ">>> [VIDEO GENERATION COMPLETE WITH AUDIO] in ${totalTimeMs}ms (${totalTimeMs / 1000}s)!")
                Log.i(TAG, "Final output file: ${finalWithAudioFile.absolutePath} (${finalWithAudioFile.length() / 1024} KB)")
                Log.i(TAG, "==========================================================")
                finalWithAudioFile
            } else {
                Log.w(TAG, "Audio muxing failed or output empty. Falling back to raw video file: ${rawVideoFile.absolutePath}")
                Log.i(TAG, "==========================================================")
                Log.i(TAG, ">>> [VIDEO GENERATION COMPLETE (FALLBACK RAW)] in ${totalTimeMs}ms (${totalTimeMs / 1000}s)!")
                Log.i(TAG, "Raw file: ${rawVideoFile.absolutePath} (${rawVideoFile.length() / 1024} KB)")
                Log.i(TAG, "==========================================================")
                rawVideoFile
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.w(TAG, "Video generation cancelled during audio phase.")
            try { tempDownloadedAudioFile?.delete() } catch (_: Exception) {}
            try { finalWithAudioFile.delete() } catch (_: Exception) {}
            try { rawVideoFile.delete() } catch (_: Exception) {}
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "!!! Exception during audio preparation/muxing: ${e.message}", e)
            try { tempDownloadedAudioFile?.delete() } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
                onProgress(100, "Hoàn tất video!")
            }
            rawVideoFile
        }
    }

    /**
     * Ghép file âm thanh (MP3/AAC/M4A) vào một video MP4 đã có sẵn.
     */
    suspend fun mergeAudioIntoVideo(
        context: Context,
        videoInputFile: File,
        audioSource: String,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.i(TAG, ">>> [START mergeAudioIntoVideo]")
        Log.i(TAG, "VideoInput: ${videoInputFile.absolutePath} (${videoInputFile.length() / 1024} KB)")
        Log.i(TAG, "AudioSource: $audioSource")
        Log.i(TAG, "OutputFile: ${outputFile.absolutePath}")

        val outputDir = File(context.cacheDir, "videos").apply { mkdirs() }
        var tempDownloadedAudioFile: File? = null

        try {
            val audioSourceFile: File = if (File(audioSource).exists()) {
                File(audioSource)
            } else if (AudioCacheManager.isCached(context, audioSource)) {
                AudioCacheManager.getCachedFile(context, audioSource)
            } else {
                val temp = File(outputDir, "temp_merge_audio_${System.currentTimeMillis()}.mp3")
                tempDownloadedAudioFile = temp
                downloadAudioFile(audioSource, temp)
                temp
            }

            // Đo thời lượng video hiện tại bằng MediaExtractor
            val durationUs = getVideoDurationUs(videoInputFile)
            Log.d(TAG, "Measured video duration: ${durationUs / 1_000_000.0}s")

            val success = muxVideoAndAudio(
                videoInput = videoInputFile,
                audioInput = audioSourceFile,
                output = outputFile,
                maxDurationUs = durationUs
            )

            tempDownloadedAudioFile?.delete()
            val totalTime = System.currentTimeMillis() - startTime
            val finalSuccess = success && outputFile.exists() && outputFile.length() > 0
            Log.i(TAG, "mergeAudioIntoVideo finished in ${totalTime}ms | Success=$finalSuccess | Size=${outputFile.length() / 1024} KB")
            finalSuccess
        } catch (e: Exception) {
            Log.e(TAG, "!!! mergeAudioIntoVideo failed with error: ${e.message}", e)
            tempDownloadedAudioFile?.delete()
            false
        }
    }

    private fun getVideoDurationUs(videoFile: File): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(videoFile.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        return format.getLong(MediaFormat.KEY_DURATION)
                    }
                }
            }
            15_000_000L
        } catch (_: Exception) {
            15_000_000L
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    /**
     * Tải file video từ URL về file cục bộ.
     */
    suspend fun downloadVideoFile(url: String, targetFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                FileOutputStream(targetFile).use { out ->
                    body.byteStream().copyTo(out)
                }
            }
            targetFile.exists() && targetFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Sinh danh sách chuyển cảnh ngẫu nhiên, đảm bảo 2 hiệu ứng liên tiếp không bị trùng.
     */
    fun generateRandomTransitions(photoCount: Int): List<TransitionType> {
        val types = TransitionType.values()
        val result = mutableListOf<TransitionType>()
        var lastType: TransitionType? = null

        for (i in 0 until photoCount) {
            val available = types.filter { it != lastType }
            val chosen = available[Random.nextInt(available.size)]
            result.add(chosen)
            lastType = chosen
        }
        return result
    }

    /**
     * Vẽ khung hình chuyển tiếp giữa bitmap hiện tại và bitmap tiếp theo trên Canvas
     */
    private fun drawTransitionFrame(
        canvas: Canvas,
        current: Bitmap,
        next: Bitmap,
        transition: TransitionType,
        progress: Float, // 0.0 -> 1.0
        paint: Paint,
        w: Float,
        h: Float
    ) {
        val p = progress.coerceIn(0f, 1f)

        when (transition) {
            TransitionType.CROSS_FADE -> {
                // Ảnh cũ mờ dần
                paint.alpha = ((1f - p) * 255).toInt()
                canvas.drawBitmap(current, 0f, 0f, paint)
                // Ảnh mới hiện dần
                paint.alpha = (p * 255).toInt()
                canvas.drawBitmap(next, 0f, 0f, paint)
                paint.alpha = 255
            }
            TransitionType.SLIDE_LEFT -> {
                // Trượt sang trái
                canvas.drawBitmap(current, -p * w, 0f, paint)
                canvas.drawBitmap(next, (1f - p) * w, 0f, paint)
            }
            TransitionType.ZOOM_IN -> {
                // Ảnh cũ zoom nhẹ và fade out
                canvas.save()
                val scale = 1f + (p * 0.2f)
                canvas.scale(scale, scale, w / 2f, h / 2f)
                paint.alpha = ((1f - p) * 255).toInt()
                canvas.drawBitmap(current, 0f, 0f, paint)
                canvas.restore()

                // Ảnh mới fade in
                paint.alpha = (p * 255).toInt()
                canvas.drawBitmap(next, 0f, 0f, paint)
                paint.alpha = 255
            }
            TransitionType.WIPE_DOWN -> {
                // Vẽ ảnh cũ dưới
                canvas.drawBitmap(current, 0f, 0f, paint)

                // Clip và vẽ ảnh mới từ trên quét xuống
                canvas.save()
                canvas.clipRect(0f, 0f, w, p * h)
                canvas.drawBitmap(next, 0f, 0f, paint)
                canvas.restore()
            }
            TransitionType.FLASH_PULSE -> {
                // Nửa đầu chớp sáng trắng, nửa sau hiện ảnh mới
                if (p < 0.5f) {
                    canvas.drawBitmap(current, 0f, 0f, paint)
                    paint.alpha = ((p * 2f) * 220).toInt()
                    canvas.drawColor(Color.WHITE)
                    paint.alpha = 255
                } else {
                    canvas.drawBitmap(next, 0f, 0f, paint)
                    paint.alpha = (((1f - p) * 2f) * 220).toInt()
                    canvas.drawColor(Color.WHITE)
                    paint.alpha = 255
                }
            }
        }
    }

    private fun downloadAudioFile(url: String, targetFile: File) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to download audio: ${response.code}")
            val body = response.body ?: throw Exception("Empty response body")
            FileOutputStream(targetFile).use { out ->
                body.byteStream().copyTo(out)
            }
        }
    }

    private fun transcodeAudioToAac(audioInput: File, audioOutput: File): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        return try {
            val transcodeStart = System.currentTimeMillis()
            extractor.setDataSource(audioInput.absolutePath)
            var audioTrackIndex = -1
            var audioSourceFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioSourceFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioSourceFormat == null) {
                Log.e(TAG, "transcodeAudioToAac: No audio track found in ${audioInput.name}")
                return false
            }

            val inputMime = audioSourceFormat.getString(MediaFormat.KEY_MIME) ?: ""
            val sampleRate = if (audioSourceFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioSourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val channelCount = if (audioSourceFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioSourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            Log.i(TAG, "transcodeAudioToAac: Start transcoding ${audioInput.name} ($inputMime, ${sampleRate}Hz, ${channelCount}ch) -> AAC (.m4a)")

            val aacFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }

            val enc = MediaCodec.createEncoderByType("audio/mp4a-latm")
            encoder = enc
            enc.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()

            val dec = MediaCodec.createDecoderByType(inputMime)
            decoder = dec
            dec.configure(audioSourceFormat, null, null, 0)
            dec.start()

            extractor.selectTrack(audioTrackIndex)

            val decBufferInfo = MediaCodec.BufferInfo()
            val encBufferInfo = MediaCodec.BufferInfo()

            var extractorEOS = false
            var decoderEOS = false
            var encoderInputEOS = false
            var encoderOutputEOS = false
            var muxerAudioTrack = -1

            val timeoutUs = 5000L
            var noProgressCount = 0
            val maxNoProgressCount = 400 // ~2s without progress -> break to prevent hang

            fun drainEncoderOutput(): Boolean {
                var didWork = false
                while (true) {
                    val encOutIndex = enc.dequeueOutputBuffer(encBufferInfo, 0L)
                    if (encOutIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    } else if (encOutIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            val m = MediaMuxer(audioOutput.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                            muxer = m
                            muxerAudioTrack = m.addTrack(enc.outputFormat)
                            m.start()
                            muxerStarted = true
                            didWork = true
                            Log.d(TAG, "transcodeAudioToAac: Muxer started with AAC format: ${enc.outputFormat}")
                        }
                    } else if (encOutIndex >= 0) {
                        val encodedBuffer = enc.getOutputBuffer(encOutIndex)
                        if (encodedBuffer != null && muxerStarted && muxerAudioTrack >= 0) {
                            if ((encBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                encBufferInfo.size = 0
                            }
                            if (encBufferInfo.size > 0) {
                                encodedBuffer.position(encBufferInfo.offset)
                                encodedBuffer.limit(encBufferInfo.offset + encBufferInfo.size)
                                muxer?.writeSampleData(muxerAudioTrack, encodedBuffer, encBufferInfo)
                                didWork = true
                            }
                        }
                        if ((encBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderOutputEOS = true
                            didWork = true
                            Log.d(TAG, "transcodeAudioToAac: Encoder output reached EOS")
                        }
                        enc.releaseOutputBuffer(encOutIndex, false)
                        if (encoderOutputEOS) break
                    }
                }
                return didWork
            }

            while (!encoderOutputEOS && noProgressCount < maxNoProgressCount) {
                var hadProgress = false

                // 1. Extractor -> Decoder
                if (!extractorEOS) {
                    val inIndex = dec.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inBuffer = dec.getInputBuffer(inIndex)
                        if (inBuffer != null) {
                            val sampleSize = extractor.readSampleData(inBuffer, 0)
                            if (sampleSize < 0) {
                                extractorEOS = true
                                dec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                Log.d(TAG, "transcodeAudioToAac: Extractor reached EOS -> queued to decoder")
                            } else {
                                dec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                            hadProgress = true
                        }
                    }
                }

                // 2. Decoder -> Encoder
                if (!decoderEOS) {
                    val decOutIndex = dec.dequeueOutputBuffer(decBufferInfo, timeoutUs)
                    if (decOutIndex >= 0) {
                        hadProgress = true
                        val isEos = (decBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val pcmBuffer = dec.getOutputBuffer(decOutIndex)

                        if (pcmBuffer != null && decBufferInfo.size > 0) {
                            var pcmOffset = decBufferInfo.offset
                            var pcmRemaining = decBufferInfo.size
                            var retries = 0

                            while (pcmRemaining > 0 && retries < 40) {
                                val encInIndex = enc.dequeueInputBuffer(timeoutUs)
                                if (encInIndex >= 0) {
                                    val encInBuf = enc.getInputBuffer(encInIndex)
                                    if (encInBuf != null) {
                                        encInBuf.clear()
                                        val toCopy = minOf(pcmRemaining, encInBuf.remaining())
                                        pcmBuffer.position(pcmOffset)
                                        pcmBuffer.limit(pcmOffset + toCopy)
                                        encInBuf.put(pcmBuffer)
                                        enc.queueInputBuffer(encInIndex, 0, toCopy, decBufferInfo.presentationTimeUs, 0)
                                        pcmOffset += toCopy
                                        pcmRemaining -= toCopy
                                    }
                                } else {
                                    retries++
                                    drainEncoderOutput()
                                }
                            }
                        }

                        if (isEos) {
                            decoderEOS = true
                            Log.d(TAG, "transcodeAudioToAac: Decoder output reached EOS")
                        }
                        dec.releaseOutputBuffer(decOutIndex, false)
                    }
                }

                // 3. Cho encoder nhận cờ EOS khi decoder đã xong
                if (decoderEOS && !encoderInputEOS) {
                    val encInIndex = enc.dequeueInputBuffer(timeoutUs)
                    if (encInIndex >= 0) {
                        enc.queueInputBuffer(encInIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        encoderInputEOS = true
                        hadProgress = true
                        Log.d(TAG, "transcodeAudioToAac: Encoder input EOS queued successfully")
                    } else {
                        drainEncoderOutput()
                    }
                }

                // 4. Drain Encoder sang MediaMuxer
                if (drainEncoderOutput()) {
                    hadProgress = true
                }

                if (hadProgress) {
                    noProgressCount = 0
                } else {
                    noProgressCount++
                }
            }

            // Flush nốt những frame cuối của encoder nếu chưa gặp EOS
            var flushRounds = 0
            while (!encoderOutputEOS && flushRounds++ < 20) {
                if (!drainEncoderOutput()) {
                    try { Thread.sleep(10) } catch (_: Exception) {}
                }
            }

            val elapsed = System.currentTimeMillis() - transcodeStart
            val success = audioOutput.exists() && audioOutput.length() > 0
            Log.i(TAG, "transcodeAudioToAac finished in ${elapsed}ms: success=$success, size=${audioOutput.length() / 1024} KB")
            success
        } catch (e: Exception) {
            Log.e(TAG, "!!! Exception in transcodeAudioToAac: ${e.message}", e)
            false
        } finally {
            try { extractor.release() } catch (_: Exception) {}
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop(); encoder?.release() } catch (_: Exception) {}
            try {
                if (muxerStarted) muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
        }
    }

    private fun muxVideoAndAudio(
        videoInput: File,
        audioInput: File,
        output: File,
        maxDurationUs: Long
    ): Boolean {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var tempAacExtractor: MediaExtractor? = null
        var tempTranscodedAacFile: File? = null
        var muxer: MediaMuxer? = null

        return try {
            Log.d(TAG, "muxVideoAndAudio: video=${videoInput.name} (${videoInput.length() / 1024} KB), audio=${audioInput.name} (${audioInput.length() / 1024} KB), maxDurationUs=$maxDurationUs")
            videoExtractor.setDataSource(videoInput.absolutePath)
            audioExtractor.setDataSource(audioInput.absolutePath)

            var videoTrackSourceIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackSourceIndex = i
                    videoFormat = format
                    break
                }
            }

            var audioTrackSourceIndex = -1
            var audioSourceFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackSourceIndex = i
                    audioSourceFormat = format
                    break
                }
            }

            if (videoTrackSourceIndex < 0 || videoFormat == null) {
                Log.e(TAG, "muxVideoAndAudio: No video track found in ${videoInput.absolutePath}!")
                return false
            }

            val audioMime = audioSourceFormat?.getString(MediaFormat.KEY_MIME) ?: ""
            val isAac = audioMime.equals("audio/mp4a-latm", ignoreCase = true) || audioMime.equals("audio/aac", ignoreCase = true)
            Log.i(TAG, "muxVideoAndAudio: Audio MIME='$audioMime', isAac=$isAac, audioTrackIndex=$audioTrackSourceIndex")

            val effectiveAudioExtractor: MediaExtractor
            val effectiveAudioTrackIndex: Int
            val effectiveAudioFormat: MediaFormat?

            if (isAac && audioSourceFormat != null && audioTrackSourceIndex >= 0) {
                // Định dạng AAC đã sẵn sàng: dùng trực tiếp không cần giải mã
                effectiveAudioExtractor = audioExtractor
                effectiveAudioTrackIndex = audioTrackSourceIndex
                effectiveAudioFormat = audioSourceFormat
            } else if (audioTrackSourceIndex >= 0 && audioSourceFormat != null) {
                // Định dạng MP3/khác: transcode trước sang AAC (.m4a) riêng biệt
                val parentDir = output.parentFile ?: audioInput.parentFile ?: videoInput.parentFile
                val tempAac = File(parentDir, "temp_transcoded_${System.currentTimeMillis()}.m4a")
                tempTranscodedAacFile = tempAac

                val transcodeSuccess = transcodeAudioToAac(audioInput, tempAac)
                if (transcodeSuccess && tempAac.exists() && tempAac.length() > 0) {
                    val aacExt = MediaExtractor()
                    tempAacExtractor = aacExt
                    aacExt.setDataSource(tempAac.absolutePath)

                    var aacTrackIdx = -1
                    var aacFmt: MediaFormat? = null
                    for (i in 0 until aacExt.trackCount) {
                        val fmt = aacExt.getTrackFormat(i)
                        val m = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                        if (m.startsWith("audio/")) {
                            aacTrackIdx = i
                            aacFmt = fmt
                            break
                        }
                    }

                    effectiveAudioExtractor = aacExt
                    effectiveAudioTrackIndex = aacTrackIdx
                    effectiveAudioFormat = aacFmt
                } else {
                    Log.w(TAG, "transcodeAudioToAac failed. Continuing with video-only.")
                    effectiveAudioExtractor = audioExtractor
                    effectiveAudioTrackIndex = -1
                    effectiveAudioFormat = null
                }
            } else {
                effectiveAudioExtractor = audioExtractor
                effectiveAudioTrackIndex = -1
                effectiveAudioFormat = null
            }

            if (effectiveAudioTrackIndex >= 0 && effectiveAudioFormat != null) {
                Log.i(TAG, "muxVideoAndAudio: Running fast copyInterleavedSamples with AAC audio...")
                val createdMuxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer = createdMuxer
                val videoTrackDestIndex = createdMuxer.addTrack(videoFormat)
                val audioTrackDestIndex = createdMuxer.addTrack(effectiveAudioFormat)
                createdMuxer.start()

                copyInterleavedSamples(
                    videoExtractor = videoExtractor,
                    videoTrackSourceIndex = videoTrackSourceIndex,
                    audioExtractor = effectiveAudioExtractor,
                    audioTrackSourceIndex = effectiveAudioTrackIndex,
                    muxer = createdMuxer,
                    videoTrackDestIndex = videoTrackDestIndex,
                    audioTrackDestIndex = audioTrackDestIndex,
                    maxDurationUs = maxDurationUs
                )
                output.exists() && output.length() > 0
            } else {
                Log.w(TAG, "No audio track available. Copying video track only.")
                val createdMuxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer = createdMuxer
                val videoTrackDestIndex = createdMuxer.addTrack(videoFormat)
                createdMuxer.start()
                copyVideoSamples(videoExtractor, videoTrackSourceIndex, createdMuxer, videoTrackDestIndex)
                output.exists() && output.length() > 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "!!! Exception in muxVideoAndAudio: ${e.message}", e)
            false
        } finally {
            try { videoExtractor.release() } catch (_: Exception) {}
            try { audioExtractor.release() } catch (_: Exception) {}
            try { tempAacExtractor?.release() } catch (_: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
            try {
                tempTranscodedAacFile?.delete()
            } catch (_: Exception) {}
        }
    }

    private fun copyInterleavedSamples(
        videoExtractor: MediaExtractor,
        videoTrackSourceIndex: Int,
        audioExtractor: MediaExtractor,
        audioTrackSourceIndex: Int,
        muxer: MediaMuxer,
        videoTrackDestIndex: Int,
        audioTrackDestIndex: Int,
        maxDurationUs: Long
    ) {
        val startMuxTime = System.currentTimeMillis()
        videoExtractor.selectTrack(videoTrackSourceIndex)
        audioExtractor.selectTrack(audioTrackSourceIndex)

        val videoBuffer = ByteBuffer.allocateDirect(1024 * 1024)
        val audioBuffer = ByteBuffer.allocateDirect(256 * 1024)
        val videoBufferInfo = MediaCodec.BufferInfo()
        val audioBufferInfo = MediaCodec.BufferInfo()

        var videoDone = false
        var audioDone = false
        var audioTimeOffsetUs = 0L
        var lastAudioSampleTime = 0L
        var audioLoopCount = 0
        val maxAudioLoops = 50
        var totalVideoPackets = 0L
        var totalAudioPackets = 0L
        var watchdogCount = 0
        val maxWatchdog = 300_000

        while (!videoDone || !audioDone) {
            if (watchdogCount++ > maxWatchdog) {
                Log.w(TAG, "copyInterleavedSamples watchdog triggered ($maxWatchdog iterations). Breaking to prevent hang.")
                break
            }

            val vPts = if (videoDone) Long.MAX_VALUE else videoExtractor.sampleTime
            val rawAudioTime = if (audioDone) -1L else audioExtractor.sampleTime
            val aPts = if (audioDone || rawAudioTime < 0) Long.MAX_VALUE else (audioTimeOffsetUs + rawAudioTime)

            if (!videoDone && (vPts <= aPts || audioDone)) {
                videoBufferInfo.size = videoExtractor.readSampleData(videoBuffer, 0)
                if (videoBufferInfo.size < 0) {
                    videoDone = true
                } else {
                    videoBufferInfo.presentationTimeUs = videoExtractor.sampleTime
                    videoBufferInfo.flags = videoExtractor.sampleFlags
                    muxer.writeSampleData(videoTrackDestIndex, videoBuffer, videoBufferInfo)
                    totalVideoPackets++
                    videoExtractor.advance()
                }
            } else if (!audioDone) {
                audioBufferInfo.size = audioExtractor.readSampleData(audioBuffer, 0)
                if (audioBufferInfo.size < 0) {
                    val safeLastAudio = if (lastAudioSampleTime > 0) lastAudioSampleTime else 50_000L
                    if (audioTimeOffsetUs + safeLastAudio < maxDurationUs && audioLoopCount < maxAudioLoops) {
                        audioTimeOffsetUs += (safeLastAudio + 23219L)
                        audioExtractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        audioLoopCount++
                        Log.d(TAG, "copyInterleavedSamples: looping audio (#$audioLoopCount) new offset=${audioTimeOffsetUs / 1000}ms")
                    } else {
                        audioDone = true
                    }
                } else {
                    val sTime = audioExtractor.sampleTime
                    if (sTime >= 0) lastAudioSampleTime = sTime
                    val pts = audioTimeOffsetUs + sTime
                    if (pts > maxDurationUs) {
                        audioDone = true
                    } else {
                        audioBufferInfo.presentationTimeUs = pts
                        audioBufferInfo.flags = audioExtractor.sampleFlags
                        muxer.writeSampleData(audioTrackDestIndex, audioBuffer, audioBufferInfo)
                        totalAudioPackets++
                        audioExtractor.advance()
                    }
                }
            }
        }
        val elapsed = System.currentTimeMillis() - startMuxTime
        Log.i(TAG, "copyInterleavedSamples finished in ${elapsed}ms: videoPackets=$totalVideoPackets, audioPackets=$totalAudioPackets, audioLoops=$audioLoopCount")
    }

    private fun copyVideoSamples(
        videoExtractor: MediaExtractor,
        videoTrackSourceIndex: Int,
        muxer: MediaMuxer,
        videoTrackDestIndex: Int
    ) {
        videoExtractor.selectTrack(videoTrackSourceIndex)
        val buffer = ByteBuffer.allocateDirect(1024 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()
        var packetCount = 0

        while (true) {
            bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) break
            bufferInfo.presentationTimeUs = videoExtractor.sampleTime
            bufferInfo.flags = videoExtractor.sampleFlags
            muxer.writeSampleData(videoTrackDestIndex, buffer, bufferInfo)
            packetCount++
            videoExtractor.advance()
        }
        Log.d(TAG, "copyVideoSamples finished: $packetCount video packets copied")
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        timeoutUs: Long = 0L,
        onMuxerStart: (Int) -> Unit,
        isMuxerStarted: () -> Boolean,
        getVideoTrackIndex: () -> Int,
        getFramePtsUs: () -> Long
    ) {
        var noOutputCount = 0
        val maxNoOutput = if (endOfStream) 50 else 1

        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
                noOutputCount++
                if (noOutputCount >= maxNoOutput) {
                    Log.d(TAG, "drainEncoder: EOS drain completed (noOutputCount reached $maxNoOutput)")
                    break
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!isMuxerStarted()) {
                    val newFormat = encoder.outputFormat
                    Log.i(TAG, "drainEncoder: MediaCodec output format changed: $newFormat")
                    val trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    onMuxerStart(trackIndex)
                }
            } else if (outputBufferIndex >= 0) {
                noOutputCount = 0
                val encodedData = encoder.getOutputBuffer(outputBufferIndex) ?: continue

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size != 0 && isMuxerStarted()) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    bufferInfo.presentationTimeUs = getFramePtsUs()
                    val track = getVideoTrackIndex().coerceAtLeast(0)
                    muxer.writeSampleData(track, encodedData, bufferInfo)
                }

                encoder.releaseOutputBuffer(outputBufferIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.i(TAG, "drainEncoder: Received BUFFER_FLAG_END_OF_STREAM")
                    break
                }
            }
        }
    }

    private fun openInputStreamForUri(context: Context, uri: Uri): InputStream? {
        return try {
            val scheme = uri.scheme
            if (scheme == null || scheme == "file") {
                val path = uri.path ?: uri.toString()
                val file = File(path)
                if (file.exists()) FileInputStream(file) else null
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open stream for: $uri", e)
            null
        }
    }

    private fun decodeAndPrepareBitmap(
        context: Context,
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int,
        photoIndex: Int = -1
    ): Bitmap? {
        return try {
            // Đọc bytes một lần duy nhất để tránh việc stream bị đóng hoặc không thể mở lại lần 2
            val readStart = System.currentTimeMillis()
            val bytes = openInputStreamForUri(context, uri)?.use { it.readBytes() }
            if (bytes == null || bytes.isEmpty()) {
                Log.e(TAG, "Photo #$photoIndex: Empty or unreadable bytes for: $uri")
                return null
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Log.e(TAG, "Photo #$photoIndex: Invalid bounds for: $uri (${options.outWidth}x${options.outHeight})")
                return null
            }

            val inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inSampleSize = inSampleSize
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (rawBitmap == null) {
                Log.e(TAG, "Photo #$photoIndex: BitmapFactory decode failed for: $uri")
                return null
            }

            val preparedBitmap = createCenterCropBitmap(rawBitmap, reqWidth, reqHeight)
            if (preparedBitmap != rawBitmap && !rawBitmap.isRecycled) {
                rawBitmap.recycle()
            }
            Log.d(TAG, "Photo #$photoIndex: Decoded source ${options.outWidth}x${options.outHeight} -> sampleSize=$inSampleSize -> prepared to ${preparedBitmap.width}x${preparedBitmap.height} in ${System.currentTimeMillis() - readStart}ms")
            preparedBitmap
        } catch (t: Throwable) {
            Log.e(TAG, "Photo #$photoIndex: Error preparing bitmap for: $uri", t)
            null
        }
    }

    private fun createCenterCropBitmap(src: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (src.width == targetWidth && src.height == targetHeight) return src
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        val scale = maxOf(targetWidth.toFloat() / src.width, targetHeight.toFloat() / src.height)
        val scaledWidth = src.width * scale
        val scaledHeight = src.height * scale
        val left = (targetWidth - scaledWidth) / 2f
        val top = (targetHeight - scaledHeight) / 2f

        val destRect = Rect(left.toInt(), top.toInt(), (left + scaledWidth).toInt(), (top + scaledHeight).toInt())
        canvas.drawBitmap(src, null, destRect, paint)
        return output
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight || (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * 1. Lưu bản gốc video vào thư mục App Documents riêng của ứng dụng
     */
    suspend fun saveVideoToAppDocuments(context: Context, sourceFile: File, title: String): File = withContext(Dispatchers.IO) {
        val docsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "PhotoArtVideos").apply { mkdirs() }
        val targetFile = File(docsDir, "${title.replace(" ", "_")}_${System.currentTimeMillis()}.mp4")
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
        targetFile
    }

    /**
     * 2. Tạo bản sao video vào Thư viện hệ thống (MediaStore Gallery)
     */
    suspend fun copyVideoToGallery(context: Context, videoFile: File, title: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "${title.replace(" ", "_")}_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/PhotoArt")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = contentResolver.insert(collection, contentValues) ?: return@withContext null

            contentResolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(videoFile).use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                contentResolver.update(itemUri, contentValues, null, null)
            }

            itemUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Lấy Uri FileProvider để chia sẻ file video sang ứng dụng khác
     */
    fun getShareableUri(context: Context, videoFile: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            videoFile
        )
    }
}
