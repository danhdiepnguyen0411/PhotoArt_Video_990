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
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
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

    private const val MIME_TYPE = "video/avc" // H.264
    private const val FRAME_RATE = 30
    private const val I_FRAME_INTERVAL = 1
    private const val BIT_RATE = 4_000_000 // 4 Mbps
    private const val VIDEO_WIDTH = 720
    private const val VIDEO_HEIGHT = 1280

    // Thời gian của mỗi hiệu ứng chuyển cảnh (15 frames = 0.5s ở 30fps)
    private const val TRANSITION_FRAMES = 15

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
     * Tính toán tổng thời lượng video sao cho mỗi ảnh hiển thị chia đều trong khoảng:
     * - Tối thiểu (min): 1.0 giây / ảnh
     * - Tối đa (max): 2.5 giây / ảnh
     * - Mục tiêu chuẩn: ~2.0 giây / ảnh trong phạm vi 10s - 30s
     */
    fun calculateVideoDuration(photoCount: Int): Float {
        if (photoCount <= 0) return 15f
        val calculated = (photoCount * 2.0f).coerceIn(photoCount * 1.0f, photoCount * 2.5f)
        return calculated.coerceIn(5f, 60f)
    }

    fun calculateSecondsPerPhoto(durationSec: Float, photoCount: Int): Float {
        if (photoCount <= 0) return 2.0f
        return (durationSec / photoCount).coerceIn(1.0f, 2.5f)
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
        onProgress: (progress: Int, status: String) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.cacheDir, "videos").apply { mkdirs() }
        val rawVideoFile = File(outputDir, "raw_${System.currentTimeMillis()}.mp4")

        withContext(Dispatchers.Main) {
            onProgress(5, "Đang xử lý hình ảnh...")
        }

        // 1. Tải và giải mã hình ảnh
        val loadedBitmaps = mutableListOf<Bitmap>()
        for (uriStr in photoUris) {
            val bmp = decodeSampledBitmap(context, Uri.parse(uriStr), VIDEO_WIDTH, VIDEO_HEIGHT)
            if (bmp != null) {
                loadedBitmaps.add(bmp)
            }
        }

        if (loadedBitmaps.isEmpty()) {
            throw IllegalArgumentException("Không thể giải mã bất kỳ ảnh nào để tạo video")
        }

        withContext(Dispatchers.Main) {
            onProgress(15, "Đang mã hóa khung hình video...")
        }

        // 2. Chuẩn bị danh sách chuyển cảnh ngẫu nhiên (nếu chưa có)
        val transitionList = transitions ?: generateRandomTransitions(loadedBitmaps.size)

        val totalFrames = (durationSec * FRAME_RATE).toInt().coerceAtLeast(FRAME_RATE)
        val framesPerPhoto = totalFrames.toFloat() / loadedBitmaps.size

        val format = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        val encoder = MediaCodec.createEncoderByType(MIME_TYPE)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(rawVideoFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            for (frameIndex in 0 until totalFrames) {
                val photoIndex = ((frameIndex / framesPerPhoto).toInt()).coerceIn(0, loadedBitmaps.lastIndex)
                val currentBitmap = loadedBitmaps[photoIndex]

                val frameWithinPhoto = frameIndex - (photoIndex * framesPerPhoto).toInt()
                val remainingFramesInPhoto = framesPerPhoto.toInt() - frameWithinPhoto

                val nextPhotoIndex = (photoIndex + 1).coerceAtMost(loadedBitmaps.lastIndex)
                val isTransitioning = remainingFramesInPhoto <= TRANSITION_FRAMES && photoIndex < loadedBitmaps.lastIndex

                val canvas: Canvas = inputSurface.lockHardwareCanvas()
                try {
                    canvas.drawColor(Color.BLACK)

                    if (isTransitioning) {
                        val nextBitmap = loadedBitmaps[nextPhotoIndex]
                        val progress = 1f - (remainingFramesInPhoto.toFloat() / TRANSITION_FRAMES)
                        val transition = transitionList.getOrElse(photoIndex) { TransitionType.CROSS_FADE }

                        drawTransitionFrame(canvas, currentBitmap, nextBitmap, transition, progress, paint)
                    } else {
                        drawBitmapCenterCrop(canvas, currentBitmap, paint)
                    }
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                drainEncoder(encoder, muxer, bufferInfo, false) {
                    muxerStarted = true
                }

                val percent = 15 + ((frameIndex.toFloat() / totalFrames) * 60).toInt()
                withContext(Dispatchers.Main) {
                    onProgress(percent, "Đang mã hóa khung hình video ($percent%)...")
                }
            }

            encoder.signalEndOfInputStream()
            drainEncoder(encoder, muxer, bufferInfo, true) {
                muxerStarted = true
            }
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
        }

        // 3. Nếu không có nhạc thì trả về video thô
        if (audioUrl.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                onProgress(100, "Hoàn tất video!")
            }
            return@withContext rawVideoFile
        }

        // 4. Ghép nhạc nền an toàn (Safe Audio Muxing)
        withContext(Dispatchers.Main) {
            onProgress(80, "Đang chuẩn bị ghép nhạc nền...")
        }

        val finalWithAudioFile = File(outputDir, "PhotoArt_${System.currentTimeMillis()}.mp4")
        var tempDownloadedAudioFile: File? = null

        try {
            val audioSourceFile: File = if (File(audioUrl).exists()) {
                File(audioUrl)
            } else if (AudioCacheManager.isCached(context, audioUrl)) {
                AudioCacheManager.getCachedFile(context, audioUrl)
            } else {
                withContext(Dispatchers.Main) {
                    onProgress(82, "Đang tải nhạc nền...")
                }
                val downloaded = AudioCacheManager.getOrDownloadAudio(context, audioUrl)
                if (downloaded != null && downloaded.exists() && downloaded.length() > 0) {
                    downloaded
                } else {
                    val temp = File(outputDir, "temp_audio_${System.currentTimeMillis()}.mp3")
                    tempDownloadedAudioFile = temp
                    downloadAudioFile(audioUrl, temp)
                    temp
                }
            }

            withContext(Dispatchers.Main) {
                onProgress(90, "Đang trộn âm thanh vào video...")
            }

            val muxSuccess = muxVideoAndAudio(
                videoInput = rawVideoFile,
                audioInput = audioSourceFile,
                output = finalWithAudioFile,
                maxDurationUs = (durationSec * 1_000_000L).toLong()
            )

            // Dọn sạch file video raw và file temp (nếu có tạo mới ngoài cache)
            rawVideoFile.delete()
            tempDownloadedAudioFile?.delete()

            withContext(Dispatchers.Main) {
                onProgress(100, "Hoàn tất video!")
            }

            if (muxSuccess && finalWithAudioFile.exists() && finalWithAudioFile.length() > 0) {
                finalWithAudioFile
            } else {
                rawVideoFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback an toàn nếu ghép nhạc gặp lỗi
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

            val success = muxVideoAndAudio(
                videoInput = videoInputFile,
                audioInput = audioSourceFile,
                output = outputFile,
                maxDurationUs = durationUs
            )

            tempDownloadedAudioFile?.delete()
            success && outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
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
        paint: Paint
    ) {
        val p = progress.coerceIn(0f, 1f)
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        when (transition) {
            TransitionType.CROSS_FADE -> {
                // Ảnh cũ mờ dần
                paint.alpha = ((1f - p) * 255).toInt()
                drawBitmapCenterCrop(canvas, current, paint)
                // Ảnh mới hiện dần
                paint.alpha = (p * 255).toInt()
                drawBitmapCenterCrop(canvas, next, paint)
                paint.alpha = 255
            }
            TransitionType.SLIDE_LEFT -> {
                // Trượt sang trái
                canvas.save()
                canvas.translate(-p * w, 0f)
                drawBitmapCenterCrop(canvas, current, paint)
                canvas.restore()

                canvas.save()
                canvas.translate((1f - p) * w, 0f)
                drawBitmapCenterCrop(canvas, next, paint)
                canvas.restore()
            }
            TransitionType.ZOOM_IN -> {
                // Ảnh cũ zoom nhẹ và fade out
                canvas.save()
                val scale = 1f + (p * 0.25f)
                canvas.scale(scale, scale, w / 2f, h / 2f)
                paint.alpha = ((1f - p) * 255).toInt()
                drawBitmapCenterCrop(canvas, current, paint)
                canvas.restore()

                // Ảnh mới fade in
                paint.alpha = (p * 255).toInt()
                drawBitmapCenterCrop(canvas, next, paint)
                paint.alpha = 255
            }
            TransitionType.WIPE_DOWN -> {
                // Vẽ ảnh cũ dưới
                drawBitmapCenterCrop(canvas, current, paint)

                // Clip và vẽ ảnh mới từ trên quét xuống
                canvas.save()
                canvas.clipRect(0f, 0f, w, p * h)
                drawBitmapCenterCrop(canvas, next, paint)
                canvas.restore()
            }
            TransitionType.FLASH_PULSE -> {
                // Nửa đầu chớp sáng trắng, nửa sau hiện ảnh mới
                if (p < 0.5f) {
                    drawBitmapCenterCrop(canvas, current, paint)
                    paint.alpha = ((p * 2f) * 200).toInt()
                    canvas.drawColor(Color.WHITE)
                    paint.alpha = 255
                } else {
                    drawBitmapCenterCrop(canvas, next, paint)
                    paint.alpha = (((1f - p) * 2f) * 200).toInt()
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

    private fun muxVideoAndAudio(
        videoInput: File,
        audioInput: File,
        output: File,
        maxDurationUs: Long
    ): Boolean {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var audioDecoder: MediaCodec? = null
        var audioEncoder: MediaCodec? = null

        return try {
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

            if (videoTrackSourceIndex < 0 || videoFormat == null) return false

            val audioMime = audioSourceFormat?.getString(MediaFormat.KEY_MIME) ?: ""
            val isAac = audioMime.equals("audio/mp4a-latm", ignoreCase = true) || audioMime.equals("audio/aac", ignoreCase = true)

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val videoTrackDestIndex = muxer.addTrack(videoFormat)

            // Nếu file audio đầu vào đã là AAC, MediaMuxer hỗ trợ addTrack trực tiếp
            if (isAac && audioSourceFormat != null) {
                val audioTrackDestIndex = muxer.addTrack(audioSourceFormat)
                muxer.start()

                // Sao chép video samples
                copyVideoSamples(videoExtractor, videoTrackSourceIndex, muxer, videoTrackDestIndex)

                // Sao chép audio samples
                copyDirectAudioSamples(audioExtractor, audioTrackSourceIndex, muxer, audioTrackDestIndex, maxDurationUs)
                true
            } else if (audioTrackSourceIndex >= 0 && audioSourceFormat != null) {
                // Nhạc đầu vào là MP3 hoặc định dạng khác -> Cần decode ra PCM và encode thành AAC
                val sampleRate = if (audioSourceFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    audioSourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                } else 44100
                val channelCount = if (audioSourceFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    audioSourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else 2

                val aacFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channelCount).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
                }

                val enc = MediaCodec.createEncoderByType("audio/mp4a-latm")
                audioEncoder = enc
                enc.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                enc.start()

                val dec = MediaCodec.createDecoderByType(audioMime)
                audioDecoder = dec
                dec.configure(audioSourceFormat, null, null, 0)
                dec.start()

                // Thu thập format đầu ra thực tế của AAC Encoder để addTrack vào Muxer
                var audioTrackDestIndex = -1
                val encBufferInfo = MediaCodec.BufferInfo()
                var formatFound = false

                // Chờ encoder nhả output format
                for (attempt in 0 until 50) {
                    val status = enc.dequeueOutputBuffer(encBufferInfo, 10000L)
                    if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        audioTrackDestIndex = muxer.addTrack(enc.outputFormat)
                        formatFound = true
                        break
                    }
                }

                if (!formatFound) {
                    // Fallback thử cấu hình sẵn
                    audioTrackDestIndex = muxer.addTrack(enc.outputFormat)
                }

                muxer.start()

                // 1. Sao chép video samples
                copyVideoSamples(videoExtractor, videoTrackSourceIndex, muxer, videoTrackDestIndex)

                // 2. Transcode MP3 sang AAC rồi ghi vào audioTrackDestIndex
                audioExtractor.selectTrack(audioTrackSourceIndex)
                transcodeAudioToMuxer(
                    extractor = audioExtractor,
                    decoder = dec,
                    encoder = enc,
                    muxer = muxer,
                    audioTrackDestIndex = audioTrackDestIndex,
                    maxDurationUs = maxDurationUs
                )
                true
            } else {
                muxer.start()
                copyVideoSamples(videoExtractor, videoTrackSourceIndex, muxer, videoTrackDestIndex)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { videoExtractor.release() } catch (_: Exception) {}
            try { audioExtractor.release() } catch (_: Exception) {}
            try { audioDecoder?.stop(); audioDecoder?.release() } catch (_: Exception) {}
            try { audioEncoder?.stop(); audioEncoder?.release() } catch (_: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
        }
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

        while (true) {
            bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) break
            bufferInfo.presentationTimeUs = videoExtractor.sampleTime
            bufferInfo.flags = videoExtractor.sampleFlags
            muxer.writeSampleData(videoTrackDestIndex, buffer, bufferInfo)
            videoExtractor.advance()
        }
    }

    private fun copyDirectAudioSamples(
        audioExtractor: MediaExtractor,
        audioTrackSourceIndex: Int,
        muxer: MediaMuxer,
        audioTrackDestIndex: Int,
        maxDurationUs: Long
    ) {
        audioExtractor.selectTrack(audioTrackSourceIndex)
        val buffer = ByteBuffer.allocateDirect(256 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()
        var audioTimeOffsetUs = 0L
        var lastSampleTimeInLoop = 0L
        val maxLoopCount = 100
        var loopIndex = 0

        while (audioTimeOffsetUs + lastSampleTimeInLoop < maxDurationUs && loopIndex < maxLoopCount) {
            var readAnySampleInLoop = false
            while (true) {
                bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                readAnySampleInLoop = true
                val sampleTime = audioExtractor.sampleTime
                if (sampleTime >= 0) {
                    lastSampleTimeInLoop = sampleTime
                }
                val presentationTimeUs = audioTimeOffsetUs + sampleTime
                if (presentationTimeUs > maxDurationUs) break

                bufferInfo.presentationTimeUs = presentationTimeUs
                bufferInfo.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(audioTrackDestIndex, buffer, bufferInfo)
                audioExtractor.advance()
            }

            if (audioTimeOffsetUs + lastSampleTimeInLoop >= maxDurationUs || !readAnySampleInLoop) break

            audioTimeOffsetUs += (lastSampleTimeInLoop + 23219L)
            audioExtractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            loopIndex++
        }
    }

    private fun transcodeAudioToMuxer(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        encoder: MediaCodec,
        muxer: MediaMuxer,
        audioTrackDestIndex: Int,
        maxDurationUs: Long
    ) {
        val decBufferInfo = MediaCodec.BufferInfo()
        val encBufferInfo = MediaCodec.BufferInfo()
        var isExtractorEOS = false
        var isDecoderEOS = false
        var isEncoderEOS = false
        val timeoutUs = 5000L

        var audioTimeOffsetUs = 0L
        var lastSampleTimeInLoop = 0L
        var loopCount = 0

        while (!isEncoderEOS && loopCount < 50) {
            // 1. Đưa compressed input data từ MediaExtractor vào Decoder
            if (!isExtractorEOS) {
                val inputIndex = decoder.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            if (audioTimeOffsetUs + lastSampleTimeInLoop < maxDurationUs) {
                                // Lặp lại bài hát nếu video chưa hết
                                audioTimeOffsetUs += (lastSampleTimeInLoop + 23219L)
                                extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                                loopCount++
                            } else {
                                isExtractorEOS = true
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                        } else {
                            val sampleTime = extractor.sampleTime
                            if (sampleTime >= 0) lastSampleTimeInLoop = sampleTime
                            val pts = audioTimeOffsetUs + sampleTime
                            if (pts > maxDurationUs) {
                                isExtractorEOS = true
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            } else {
                                decoder.queueInputBuffer(inputIndex, 0, sampleSize, pts, 0)
                                extractor.advance()
                            }
                        }
                    }
                }
            }

            // 2. Lấy decoded PCM data từ Decoder và nạp sang Encoder
            var decOutIndex = decoder.dequeueOutputBuffer(decBufferInfo, timeoutUs)
            while (decOutIndex >= 0) {
                val pcmBuffer = decoder.getOutputBuffer(decOutIndex)
                if (pcmBuffer != null && decBufferInfo.size > 0) {
                    // Đẩy PCM vào encoder
                    val encInIndex = encoder.dequeueInputBuffer(timeoutUs)
                    if (encInIndex >= 0) {
                        val encInputBuffer = encoder.getInputBuffer(encInIndex)
                        if (encInputBuffer != null) {
                            encInputBuffer.clear()
                            val toCopy = Math.min(decBufferInfo.size, encInputBuffer.remaining())
                            pcmBuffer.position(decBufferInfo.offset)
                            pcmBuffer.limit(decBufferInfo.offset + toCopy)
                            encInputBuffer.put(pcmBuffer)
                            encoder.queueInputBuffer(encInIndex, 0, toCopy, decBufferInfo.presentationTimeUs, 0)
                        }
                    }
                }

                if ((decBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isDecoderEOS = true
                    val encInIndex = encoder.dequeueInputBuffer(timeoutUs)
                    if (encInIndex >= 0) {
                        encoder.queueInputBuffer(encInIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                }

                decoder.releaseOutputBuffer(decOutIndex, false)
                if (isDecoderEOS) break
                decOutIndex = decoder.dequeueOutputBuffer(decBufferInfo, 0L)
            }

            // 3. Lấy encoded AAC data từ Encoder và ghi vào Muxer
            var encOutIndex = encoder.dequeueOutputBuffer(encBufferInfo, timeoutUs)
            while (encOutIndex >= 0) {
                val aacBuffer = encoder.getOutputBuffer(encOutIndex)
                if (aacBuffer != null) {
                    if ((encBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        encBufferInfo.size = 0
                    }
                    if (encBufferInfo.size != 0 && audioTrackDestIndex >= 0) {
                        aacBuffer.position(encBufferInfo.offset)
                        aacBuffer.limit(encBufferInfo.offset + encBufferInfo.size)
                        muxer.writeSampleData(audioTrackDestIndex, aacBuffer, encBufferInfo)
                    }
                }

                if ((encBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isEncoderEOS = true
                }

                encoder.releaseOutputBuffer(encOutIndex, false)
                if (isEncoderEOS) break
                encOutIndex = encoder.dequeueOutputBuffer(encBufferInfo, 0L)
            }
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onMuxerStart: () -> Unit
    ) {
        val timeoutUs = 10000L
        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = encoder.outputFormat
                muxer.addTrack(newFormat)
                muxer.start()
                onMuxerStart()
            } else if (outputBufferIndex >= 0) {
                val encodedData = encoder.getOutputBuffer(outputBufferIndex) ?: continue

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size != 0) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(0, encodedData, bufferInfo)
                }

                encoder.releaseOutputBuffer(outputBufferIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    private fun drawBitmapCenterCrop(canvas: Canvas, bitmap: Bitmap, paint: Paint) {
        val canvasWidth = canvas.width
        val canvasHeight = canvas.height
        val bmpWidth = bitmap.width
        val bmpHeight = bitmap.height

        val scale = maxOf(canvasWidth.toFloat() / bmpWidth, canvasHeight.toFloat() / bmpHeight)
        val scaledWidth = bmpWidth * scale
        val scaledHeight = bmpHeight * scale

        val left = (canvasWidth - scaledWidth) / 2f
        val top = (canvasHeight - scaledHeight) / 2f

        val destRect = Rect(left.toInt(), top.toInt(), (left + scaledWidth).toInt(), (top + scaledHeight).toInt())
        canvas.drawBitmap(bitmap, null, destRect, paint)
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            var stream: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(stream, null, options)
            stream?.close()

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            stream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(stream, null, options)
            stream?.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
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
