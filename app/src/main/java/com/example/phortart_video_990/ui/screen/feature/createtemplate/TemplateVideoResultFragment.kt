package com.example.phortart_video_990.ui.screen.feature.createtemplate

import android.content.Intent
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
import com.example.phortart_video_990.databinding.FragmentTemplateVideoResultBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TemplateVideoResultFragment : BaseFragment<FragmentTemplateVideoResultBinding>(
    FragmentTemplateVideoResultBinding::inflate
) {

    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private var rawVideoPathOrUrl: String = ""
    private var currentPlayingVideoFile: File? = null

    private var musicTitle: String? = null
    private var musicAudioUrl: String? = null
    private var musicAudioFilePath: String? = null
    private var musicImageUrl: String? = null

    private var appDocumentVideoFile: File? = null
    private var tempShareCacheFile: File? = null
    private var isExporting = false

    // Playback state
    private var isPlaying = false
    private var videoDurationSec = 15f
    private var progressPollingJob: Job? = null
    private var externalAudioPlayer: MediaPlayer? = null
    private var videoMediaPlayer: MediaPlayer? = null

    override fun initView() {
        val args = arguments
        rawVideoPathOrUrl = args?.getString(ARG_VIDEO_URL).orEmpty()
        musicTitle = args?.getString(ARG_MUSIC_TITLE)
        musicAudioUrl = args?.getString(ARG_MUSIC_AUDIO_URL)
        musicAudioFilePath = args?.getString(ARG_MUSIC_FILE_PATH)
        musicImageUrl = args?.getString(ARG_MUSIC_IMAGE_URL)

        updateMusicInfoUi()

        // Khởi tạo và phát video
        initializeVideo()

        // Lắng nghe khi người dùng đổi nhạc từ MusicFragment
        setupMusicSelectionObserver()
    }

    private fun setupMusicSelectionObserver() {
        val stateHandle = findNavController().currentBackStackEntry?.savedStateHandle ?: return

        stateHandle.getLiveData<String>("selected_music_title").observe(viewLifecycleOwner) { title ->
            if (!title.isNullOrBlank()) {
                val newAudioUrl = stateHandle.get<String?>("selected_music_audio_url")
                val newFilePath = stateHandle.get<String?>("selected_music_file_path")
                val newImgUrl = stateHandle.get<String?>("selected_music_image_url")

                // Clear saved state handle keys ngay lập tức để không bị trigger lại thừa
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
        val title = musicTitle?.takeIf { it.isNotBlank() } ?: "Âm thanh gốc"
        binding.tvMusicTitle.text = title
        binding.tvMusicSubtitle.text = if (musicTitle.isNullOrBlank()) "Chạm để chọn nhạc nền AI" else "Đang phát bài này làm nhạc nền"

        if (!musicImageUrl.isNullOrBlank()) {
            binding.ivMusicCover.visibility = View.VISIBLE
            binding.ivDefaultThumbIcon.visibility = View.GONE
            binding.ivMusicCover.load(musicImageUrl) { crossfade(true) }
        } else {
            binding.ivMusicCover.visibility = View.GONE
            binding.ivDefaultThumbIcon.visibility = View.VISIBLE
        }
    }

    private fun initializeVideo() {
        if (rawVideoPathOrUrl.isBlank()) return

        viewLifecycleOwner.lifecycleScope.launch {
            binding.pbVideoLoading.visibility = View.VISIBLE

            // Nếu raw video là file cục bộ
            val localFile = if (File(rawVideoPathOrUrl).exists()) {
                File(rawVideoPathOrUrl)
            } else {
                // Tải video từ backend URL về máy cục bộ để xử lý phát mượt và ghép nhạc
                val temp = File(requireContext().cacheDir, "backend_video_${System.currentTimeMillis()}.mp4")
                val ok = VideoGenerator.downloadVideoFile(rawVideoPathOrUrl, temp)
                if (ok && temp.exists()) temp else null
            }

            binding.pbVideoLoading.visibility = View.GONE

            if (localFile != null && localFile.exists()) {
                currentPlayingVideoFile = localFile
                startVideoPlayback(localFile)
            } else {
                // Fallback phát trực tiếp từ URL nếu tải về lỗi
                startVideoPlaybackFromUri(Uri.parse(rawVideoPathOrUrl))
            }
        }
    }

    private fun startVideoPlayback(videoFile: File) {
        startVideoPlaybackFromUri(Uri.fromFile(videoFile))
    }

    private fun startVideoPlaybackFromUri(uri: Uri) {
        binding.videoView.setVideoURI(uri)
        binding.videoView.setOnPreparedListener { mp ->
            videoMediaPlayer = mp
            mp.isLooping = true

            // Nếu người dùng có chọn nhạc ngoài -> Mute âm thanh gốc của video và phát nhạc ngoài
            if (!musicAudioUrl.isNullOrBlank() || !musicAudioFilePath.isNullOrBlank()) {
                mp.setVolume(0f, 0f)
                playExternalAudioSync()
            } else {
                mp.setVolume(1f, 1f)
                stopExternalAudio()
            }

            val dur = mp.duration / 1000f
            if (dur > 0) videoDurationSec = dur

            binding.videoView.start()
            isPlaying = true
            updatePlayStateUi(true)
            startProgressPolling()
        }

        // Đồng bộ vòng lặp giữa video và nhạc ngoài
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
        } catch (_: Exception) {}
    }

    private fun handleMusicChanged(newAudioUrl: String?, newFilePath: String?) {
        // Tạm dừng và giải phóng audio cũ
        stopExternalAudio()

        if (newAudioUrl.isNullOrBlank() && newFilePath.isNullOrBlank()) {
            // Quay lại âm thanh gốc của video
            safeSetVideoVolume(1f, 1f)
            return
        }

        // Mute video gốc ngay lập tức
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

            // Đồng bộ phát âm thanh mới theo vị trí của video hiện tại
            playExternalAudioSync()

            // Đảm bảo video đang chạy nếu đang ở trạng thái playing
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
            val player = MediaPlayer()
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
        if (playing) {
            binding.btnPlayPause.alpha = 0f
            binding.btnPlayPause.isClickable = false
            binding.ivPlayPauseIcon.setImageResource(R.drawable.ic_pause_small)
        } else {
            binding.btnPlayPause.alpha = 1f
            binding.btnPlayPause.isClickable = true
            binding.ivPlayPauseIcon.setImageResource(R.drawable.ic_play_small)
        }
    }

    private fun updateTimeUi(timeSec: Float) {
        binding.tvVideoTime.text = "${formatTime(timeSec)} / ${formatTime(videoDurationSec)}"
        val progress = if (videoDurationSec > 0) ((timeSec / videoDurationSec) * 1000).toInt() else 0
        binding.videoProgressBar.progress = progress
    }

    private fun formatTime(seconds: Float): String {
        val totalSecs = seconds.toInt()
        val m = totalSecs / 60
        val s = totalSecs % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
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

        binding.cardMusicInfo.setOnClickListener {
            binding.videoView.pause()
            externalAudioPlayer?.pause()
            isPlaying = false
            updatePlayStateUi(false)
            findNavController().navigate(R.id.action_templateVideoResultFragment_to_musicFragment)
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
    }

    /**
     * Chuẩn bị file video hoàn chỉnh: Nếu có chọn nhạc -> ghép nhạc vào video rồi trả về file MP4
     */
    private suspend fun prepareFinalVideoFile(status: String): File? {
        showExportLoading(true, status, 15)
        isExporting = true

        val baseVideo = currentPlayingVideoFile ?: run {
            val temp = File(requireContext().cacheDir, "base_video_${System.currentTimeMillis()}.mp4")
            val ok = VideoGenerator.downloadVideoFile(rawVideoPathOrUrl, temp)
            if (ok) temp else null
        }

        if (baseVideo == null || !baseVideo.exists()) {
            showExportLoading(false)
            isExporting = false
            return null
        }

        val audioSource = musicAudioFilePath?.takeIf { File(it).exists() } ?: musicAudioUrl

        // Nếu không có nhạc ngoài thì dùng luôn base video
        if (audioSource.isNullOrBlank()) {
            showExportLoading(false)
            isExporting = false
            return baseVideo
        }

        // Có chọn nhạc ngoài -> Mux ghép nhạc vào video
        showExportLoading(true, "Đang ghép âm thanh mới vào video...", 60)
        val mergedFile = File(requireContext().cacheDir, "merged_template_${System.currentTimeMillis()}.mp4")
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

    private fun handleSave() {
        if (isExporting) return
        binding.videoView.pause()
        externalAudioPlayer?.pause()
        isPlaying = false
        updatePlayStateUi(false)

        viewLifecycleOwner.lifecycleScope.launch {
            val videoFile = appDocumentVideoFile ?: prepareFinalVideoFile("Đang chuẩn bị video để lưu...")

            if (videoFile != null && videoFile.exists()) {
                // 1. Lưu bản gốc vào App Documents
                val docFile = if (appDocumentVideoFile == null) {
                    val savedDoc = VideoGenerator.saveVideoToAppDocuments(
                        context = requireContext(),
                        sourceFile = videoFile,
                        title = "Template_AI_${System.currentTimeMillis()}"
                    )
                    appDocumentVideoFile = savedDoc
                    savedDoc
                } else appDocumentVideoFile!!

                // 2. Lưu vào Gallery của điện thoại
                VideoGenerator.copyVideoToGallery(
                    context = requireContext(),
                    videoFile = docFile,
                    title = "PhotoArt_Template_Video"
                )

                // 3. Cập nhật vào Lịch sử
                val dateFormat = SimpleDateFormat("d 'thg' M, yyyy • HH:mm", Locale.getDefault())
                val title = if (!musicTitle.isNullOrBlank()) "Mẫu AI • Nhạc: $musicTitle" else "Video Mẫu AI"
                val item = HistoryItemModel(
                    type = "Mẫu AI",
                    title = title,
                    date = dateFormat.format(Date()),
                    imageUri = docFile.absolutePath
                )
                historyRepository.addHistoryItem(item)

                Toast.makeText(requireContext(), "Đã lưu video thành công vào thư viện ảnh!", Toast.LENGTH_SHORT).show()
                // Tiếp tục ở lại màn hình để người dùng có thể xem lại hoặc chia sẻ tiếp
            } else {
                Toast.makeText(requireContext(), "Không thể xuất video!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleShare() {
        if (isExporting) return
        binding.videoView.pause()
        externalAudioPlayer?.pause()
        isPlaying = false
        updatePlayStateUi(false)

        viewLifecycleOwner.lifecycleScope.launch {
            val videoToShare = appDocumentVideoFile ?: run {
                val temp = prepareFinalVideoFile("Đang chuẩn bị chia sẻ video...")
                tempShareCacheFile = temp
                temp
            }

            if (videoToShare != null && videoToShare.exists()) {
                val success = ShareUtils.shareVideo(
                    context = requireContext(),
                    filePath = videoToShare.absolutePath,
                    chooserTitle = "Chia sẻ video AI"
                )
                if (!success) {
                    Toast.makeText(requireContext(), "Không thể chia sẻ video lúc này", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Video chưa sẵn sàng để chia sẻ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showExportLoading(show: Boolean, status: String = "Đang xử lý video...", percent: Int = 0) {
        binding.flExportLoadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvExportStatus.text = status
        binding.pbExportHorizontal.progress = percent
        binding.tvExportPercent.text = "$percent%"
        if (show) {
            binding.flExportLoadingOverlay.bringToFront()
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
        binding.videoView.stopPlayback()
        videoMediaPlayer = null
        tempShareCacheFile?.delete()
        tempShareCacheFile = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_VIDEO_URL = "arg_video_url"
        const val ARG_MUSIC_TITLE = "arg_music_title"
        const val ARG_MUSIC_AUDIO_URL = "arg_music_audio_url"
        const val ARG_MUSIC_FILE_PATH = "arg_music_file_path"
        const val ARG_MUSIC_IMAGE_URL = "arg_music_image_url"
    }
}
