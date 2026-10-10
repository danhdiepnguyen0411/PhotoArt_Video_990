package com.example.phortart_video_990.ui.screen.feature.memories

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.AudioCacheManager
import com.example.phortart_video_990.core.utils.ShareUtils
import com.example.phortart_video_990.core.utils.VideoGenerator
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.databinding.FragmentVideoResultBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoResultFragment : BaseFragment<FragmentVideoResultBinding>(FragmentVideoResultBinding::inflate) {

    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private var videoPath: String? = null
    private var photoUris: List<String> = emptyList()
    private var musicTitle: String? = null
    private var musicAudioUrl: String? = null
    private var musicAudioFilePath: String? = null
    private var musicImageUrl: String? = null

    // Video & Audio state
    private var videoDurationSec = 15f
    private var isPlaying = false
    private var videoMediaPlayer: MediaPlayer? = null
    private var externalAudioPlayer: MediaPlayer? = null
    private var progressPollingJob: Job? = null
    private var noteIconAnimator: ObjectAnimator? = null

    // Track active playing video file & export
    private var currentPlayingVideoFile: File? = null
    private var appDocumentVideoFile: File? = null
    private var tempShareCacheFile: File? = null
    private var isExporting = false

    override fun initView() {
        val args = arguments
        videoPath = args?.getString(ARG_VIDEO_PATH)
        photoUris = args?.getStringArrayList(ARG_PHOTOS) ?: emptyList()
        musicTitle = args?.getString(ARG_TRACK)
        musicAudioUrl = args?.getString(ARG_AUDIO_URL)
        musicAudioFilePath = args?.getString(ARG_AUDIO_PATH)
        musicImageUrl = args?.getString(ARG_IMAGE_URL)

        updateMusicInfoUi()
        updatePlayStateUi(false)
        initializeVideo()

        // Lắng nghe đổi bài hát mới từ MusicFragment
        val stateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        stateHandle?.getLiveData<String>("selected_music_title")?.observe(viewLifecycleOwner) { title ->
            if (!title.isNullOrBlank()) {
                val newAudioUrl = stateHandle.get<String?>("selected_music_audio_url")
                val newFilePath = stateHandle.get<String?>("selected_music_file_path")
                val newImgUrl = stateHandle.get<String?>("selected_music_image_url")

                // Clear keys ngay lập tức tránh trigger lặp lại
                stateHandle.remove<String>("selected_music_title")
                stateHandle.remove<String?>("selected_music_audio_url")
                stateHandle.remove<String?>("selected_music_file_path")
                stateHandle.remove<String?>("selected_music_image_url")

                musicTitle = title
                musicAudioUrl = newAudioUrl
                musicAudioFilePath = newFilePath
                musicImageUrl = newImgUrl

                // Invalidate bản video đã xuất trước đó vì đã đổi nhạc mới
                appDocumentVideoFile = null
                tempShareCacheFile?.delete()
                tempShareCacheFile = null

                updateMusicInfoUi()
                handleMusicChanged(newAudioUrl, newFilePath)
            }
        }
    }

    private fun updateMusicInfoUi() {
        val title = musicTitle?.takeIf { it.isNotBlank() } ?: "Chưa chọn nhạc"
        binding.tvVideoTitle.text = title
        binding.tvVideoSubtitle.text = formatTime(videoDurationSec)

        val imageUrl = musicImageUrl
        if (!imageUrl.isNullOrBlank()) {
            binding.ivMusicCover.visibility = View.VISIBLE
            binding.ivDefaultThumbIcon.visibility = View.GONE
            binding.ivMusicCover.load(imageUrl) {
                crossfade(true)
            }
        } else {
            binding.ivMusicCover.visibility = View.GONE
            binding.ivDefaultThumbIcon.visibility = View.VISIBLE
        }
    }

    private fun initializeVideo() {
        val path = videoPath
        if (path.isNullOrBlank()) return

        val localFile = File(path)
        if (localFile.exists()) {
            currentPlayingVideoFile = localFile
            startVideoPlayback(localFile)
        } else {
            Toast.makeText(requireContext(), "Không tìm thấy file video!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVideoPlayback(videoFile: File) {
        startVideoPlaybackFromUri(Uri.fromFile(videoFile))
    }

    private fun startVideoPlaybackFromUri(uri: Uri) {
        binding.videoView.setVideoURI(uri)
        binding.videoView.setOnPreparedListener { mp ->
            videoMediaPlayer = mp
            try {
                mp.isLooping = true
            } catch (_: Exception) {}

            // Nếu người dùng chọn đổi nhạc ngoài -> Mute âm thanh video và phát nhạc ngoài
            if (externalAudioPlayer != null || (!musicAudioUrl.isNullOrBlank() && musicAudioFilePath != null && currentPlayingVideoFile == null)) {
                safeSetVideoVolume(0f, 0f)
                playExternalAudioSync()
            } else {
                // Video đã được render sẵn nhạc từ MemoriesFragment
                safeSetVideoVolume(1f, 1f)
                stopExternalAudio()
            }

            val dur = mp.duration / 1000f
            if (dur > 0) videoDurationSec = dur
            binding.tvVideoSubtitle.text = formatTime(videoDurationSec)

            binding.videoView.start()
            isPlaying = true
            updatePlayStateUi(true)
            startProgressPolling()
        }

        binding.videoView.setOnCompletionListener {
            binding.videoView.seekTo(0)
            binding.videoView.start()
            externalAudioPlayer?.let { ap ->
                try {
                    ap.seekTo(0)
                    ap.start()
                } catch (_: Exception) {}
            }
        }

        binding.videoView.setOnErrorListener { _, _, _ ->
            Toast.makeText(requireContext(), "Lỗi khi phát video", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun safeSetVideoVolume(leftVolume: Float, rightVolume: Float) {
        try {
            videoMediaPlayer?.setVolume(leftVolume, rightVolume)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleMusicChanged(newAudioUrl: String?, newFilePath: String?) {
        stopExternalAudio()

        if (newAudioUrl.isNullOrBlank() && newFilePath.isNullOrBlank()) {
            safeSetVideoVolume(1f, 1f)
            stopNoteMusicAnimation()
            return
        }

        safeSetVideoVolume(0f, 0f)

        viewLifecycleOwner.lifecycleScope.launch {
            binding.pbVideoLoading.visibility = View.VISIBLE
            val ctx = requireContext()
            val audioFile = if (!newFilePath.isNullOrBlank() && File(newFilePath).exists()) {
                File(newFilePath)
            } else if (!newAudioUrl.isNullOrBlank()) {
                AudioCacheManager.getOrDownloadAudio(ctx, newAudioUrl)
            } else null
            binding.pbVideoLoading.visibility = View.GONE

            if (audioFile != null && audioFile.exists()) {
                musicAudioFilePath = audioFile.absolutePath
            }

            safeSetVideoVolume(0f, 0f)
            playExternalAudioSync()

            if (!binding.videoView.isPlaying) {
                binding.videoView.start()
                isPlaying = true
                updatePlayStateUi(true)
                startProgressPolling()
            }
        }
    }

    private fun playExternalAudioSync() {
        stopExternalAudio()
        val audioPath = musicAudioFilePath
        val audioUrl = musicAudioUrl

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )
            }
            externalAudioPlayer = player

            if (!audioPath.isNullOrBlank() && File(audioPath).exists()) {
                player.setDataSource(audioPath)
            } else if (!audioUrl.isNullOrBlank()) {
                player.setDataSource(audioUrl)
            } else return

            player.isLooping = true
            player.setOnPreparedListener {
                val currentPos = binding.videoView.currentPosition
                if (currentPos > 0) {
                    player.seekTo(currentPos)
                }
                player.start()
                isPlaying = true
                updatePlayStateUi(true)
            }
            player.prepareAsync()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopExternalAudio() {
        try {
            externalAudioPlayer?.stop()
            externalAudioPlayer?.release()
        } catch (_: Exception) {}
        externalAudioPlayer = null
    }

    private fun startProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isPlaying) {
                if (binding.videoView.isPlaying) {
                    val currentSec = binding.videoView.currentPosition / 1000f
                    updateTimeUi(currentSec)
                }
                delay(200)
            }
        }
    }

    private fun togglePlayback() {
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
            externalAudioPlayer?.pause()
            isPlaying = false
            updatePlayStateUi(false)
        } else {
            binding.videoView.start()
            externalAudioPlayer?.start()
            isPlaying = true
            updatePlayStateUi(true)
            startProgressPolling()
        }
    }

    private fun updatePlayStateUi(playing: Boolean) {
        val b = bindingOrNull ?: return
        if (playing) {
            b.btnPlayPause.alpha = 0f
            b.btnPlayPause.isClickable = false
            b.ivPlayPauseIcon.setImageResource(R.drawable.ic_pause_small)
            startNoteMusicAnimation()
        } else {
            b.btnPlayPause.alpha = 1f
            b.btnPlayPause.isClickable = true
            b.ivPlayPauseIcon.setImageResource(R.drawable.ic_play_small)
            stopNoteMusicAnimation()
        }
    }

    private fun startNoteMusicAnimation() {
        if (!musicTitle.isNullOrBlank() || !musicAudioUrl.isNullOrBlank()) {
            if (noteIconAnimator == null) {
                noteIconAnimator = ObjectAnimator.ofFloat(binding.flVideoNote, View.ROTATION, -15f, 15f).apply {
                    duration = 380
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                }
            }
            if (noteIconAnimator?.isStarted != true) {
                noteIconAnimator?.start()
            }
            binding.flVideoNote.animate().scaleX(1.15f).scaleY(1.15f).setDuration(200).start()
        } else {
            stopNoteMusicAnimation()
        }
    }

    private fun stopNoteMusicAnimation() {
        noteIconAnimator?.cancel()
        noteIconAnimator = null
        bindingOrNull?.flVideoNote?.animate()?.rotation(0f)?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(200)?.start()
    }

    private fun updateTimeUi(timeSec: Float) {
        val b = bindingOrNull ?: return
        b.tvVideoTime.text = "${formatTime(timeSec)} / ${formatTime(videoDurationSec)}"
        val progress = if (videoDurationSec > 0) ((timeSec / videoDurationSec) * 1000).toInt() else 0
        b.videoProgressBar.progress = progress
    }

    private fun formatTime(seconds: Float): String {
        val totalSecs = seconds.toInt()
        val m = totalSecs / 60
        val s = totalSecs % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    private fun showExportLoading(show: Boolean, status: String = "Đang xuất video...", percent: Int = 0) {
        val b = bindingOrNull ?: return
        b.flExportLoadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        b.tvExportStatus.text = status
        b.pbExportHorizontal.progress = percent
        b.tvExportPercent.text = "$percent%"
        if (show) {
            b.flExportLoadingOverlay.bringToFront()
        }
    }

    /**
     * Chuẩn bị file video hoàn chỉnh: Nếu người dùng đã đổi nhạc tại màn này -> ghép nhạc mới vào video.
     * Ngược lại dùng luôn file MP4 đã render từ trước.
     */
    private suspend fun prepareFinalVideoFile(status: String): File? {
        showExportLoading(true, status, 15)
        isExporting = true

        val baseVideo = currentPlayingVideoFile
        if (baseVideo == null || !baseVideo.exists()) {
            showExportLoading(false)
            isExporting = false
            return null
        }

        // Nếu có đổi bài hát ngoài khác với bản base video
        if (externalAudioPlayer != null) {
            val audioSource = musicAudioFilePath?.takeIf { File(it).exists() } ?: musicAudioUrl
            if (!audioSource.isNullOrBlank()) {
                showExportLoading(true, "Đang ghép âm thanh mới vào video...", 60)
                val mergedFile = File(requireContext().cacheDir, "merged_memories_${System.currentTimeMillis()}.mp4")
                val success = VideoGenerator.mergeAudioIntoVideo(
                    context = requireContext(),
                    videoInputFile = baseVideo,
                    audioSource = audioSource,
                    outputFile = mergedFile
                )
                showExportLoading(false)
                isExporting = false
                return if (success && mergedFile.exists()) mergedFile else baseVideo
            }
        }

        showExportLoading(false)
        isExporting = false
        return baseVideo
    }

    /**
     * Bấm LƯU: Video MP4 đã sẵn sàng, lưu vào App Documents riêng và copy sang Thư viện máy (Gallery).
     */
    private fun handleSave() {
        if (isExporting) return
        binding.videoView.pause()
        externalAudioPlayer?.pause()
        isPlaying = false
        updatePlayStateUi(false)

        viewLifecycleOwner.lifecycleScope.launch {
            val videoFile = appDocumentVideoFile ?: prepareFinalVideoFile("Đang chuẩn bị video để lưu...")
            if (videoFile != null && videoFile.exists()) {
                val videoTitle = if (!musicTitle.isNullOrBlank()) {
                    "Video tạo từ ${photoUris.size.coerceAtLeast(1)} ảnh AI • Nhạc: $musicTitle"
                } else {
                    "Video tạo từ ${photoUris.size.coerceAtLeast(1)} ảnh AI"
                }

                // 1. Lưu bản gốc vào App Documents riêng
                val docFile = if (appDocumentVideoFile == null) {
                    val savedDoc = VideoGenerator.saveVideoToAppDocuments(
                        context = requireContext(),
                        sourceFile = videoFile,
                        title = "Memories_${System.currentTimeMillis()}"
                    )
                    appDocumentVideoFile = savedDoc
                    savedDoc
                } else appDocumentVideoFile!!

                // 2. Tạo bản sao ra MediaStore Gallery (Album thiết bị)
                VideoGenerator.copyVideoToGallery(
                    context = requireContext(),
                    videoFile = docFile,
                    title = "PhotoArt_Memories_Video"
                )

                // 3. Lưu vào Lịch sử (History) của app
                val dateFormat = SimpleDateFormat("d 'thg' M, yyyy • HH:mm", Locale.getDefault())
                val currentDate = dateFormat.format(Date())

                val historyItem = HistoryItemModel(
                    type = "Gom ảnh",
                    title = videoTitle,
                    date = currentDate,
                    imageUri = docFile.absolutePath
                )
                historyRepository.addHistoryItem(historyItem)

                Toast.makeText(requireContext(), "Đã lưu video thành công vào ứng dụng và thư viện ảnh!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Không thể lưu video!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Bấm SHARE: Sử dụng file đã tạo sẵn, chia sẻ ngay lập tức
     */
    private fun handleShare() {
        if (isExporting) return
        binding.videoView.pause()
        externalAudioPlayer?.pause()
        isPlaying = false
        updatePlayStateUi(false)

        viewLifecycleOwner.lifecycleScope.launch {
            val videoToShare = appDocumentVideoFile ?: run {
                val temp = prepareFinalVideoFile("Đang chuẩn bị video để chia sẻ...")
                tempShareCacheFile = temp
                temp
            }

            if (videoToShare != null && videoToShare.exists()) {
                val success = ShareUtils.shareVideo(
                    context = requireContext(),
                    filePath = videoToShare.absolutePath,
                    chooserTitle = "Chia sẻ video kỷ niệm"
                )
                if (!success) {
                    Toast.makeText(requireContext(), "Không thể chia sẻ video lúc này", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Video chưa sẵn sàng để chia sẻ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.cardVideoStage.setOnClickListener {
            togglePlayback()
        }

        binding.btnPlayPause.setOnClickListener {
            togglePlayback()
        }

        binding.btnSave.setOnClickListener {
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                it.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                handleSave()
            }.start()
        }

        binding.btnShare.setOnClickListener {
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                it.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                handleShare()
            }.start()
        }

        binding.cardVideoInfo.setOnClickListener {
            binding.videoView.pause()
            externalAudioPlayer?.pause()
            isPlaying = false
            updatePlayStateUi(false)
            findNavController().navigate(R.id.action_videoResultFragment_to_musicFragment)
        }
    }

    override fun onPause() {
        super.onPause()
        binding.videoView.pause()
        externalAudioPlayer?.pause()
        isPlaying = false
        updatePlayStateUi(false)
    }

    override fun onDestroyView() {
        progressPollingJob?.cancel()
        progressPollingJob = null
        stopExternalAudio()
        stopNoteMusicAnimation()
        try {
            bindingOrNull?.videoView?.stopPlayback()
        } catch (_: Exception) {}
        videoMediaPlayer = null
        tempShareCacheFile?.let { temp ->
            if (temp.exists() && temp != appDocumentVideoFile && temp != currentPlayingVideoFile) {
                temp.delete()
            }
        }
        tempShareCacheFile = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_VIDEO_PATH = "arg_video_path"
        const val ARG_PHOTOS = "arg_photos"
        const val ARG_TRACK = "arg_track"
        const val ARG_AUDIO_URL = "arg_audio_url"
        const val ARG_AUDIO_PATH = "arg_audio_path"
        const val ARG_IMAGE_URL = "arg_image_url"

        fun createBundle(
            videoPath: String,
            photos: List<String>,
            track: String?,
            audioUrl: String? = null,
            audioPath: String? = null,
            imageUrl: String? = null
        ): Bundle {
            return Bundle().apply {
                putString(ARG_VIDEO_PATH, videoPath)
                putStringArrayList(ARG_PHOTOS, ArrayList(photos))
                putString(ARG_TRACK, track)
                putString(ARG_AUDIO_URL, audioUrl)
                putString(ARG_AUDIO_PATH, audioPath)
                putString(ARG_IMAGE_URL, imageUrl)
            }
        }
    }
}
