package com.aiart.photo.beauti.editor.ui.screen.feature.createtemplate

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.core.base.BaseFragment
import com.aiart.photo.beauti.editor.core.dialog.AppDialogHelper
import com.aiart.photo.beauti.editor.core.utils.AudioCacheManager
import com.aiart.photo.beauti.editor.core.utils.ShareUtils
import com.aiart.photo.beauti.editor.core.utils.VideoGenerator
import com.aiart.photo.beauti.editor.core.utils.navigateSafe
import com.aiart.photo.beauti.editor.core.utils.safePopBackStack
import com.aiart.photo.beauti.editor.data.model.HistoryItemModel
import com.aiart.photo.beauti.editor.data.repository.HistoryRepository
import com.aiart.photo.beauti.editor.databinding.FragmentTemplateVideoResultBinding
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

    private var musicTrackId: String? = null
    private var musicTitle: String? = null
    private var musicAudioUrl: String? = null
    private var musicAudioFilePath: String? = null
    private var musicImageUrl: String? = null
    private var templateTitle: String? = null
    private var templateThumbUrl: String? = null
    private var templateCode: String? = null
    private var templateCategory: String? = null

    private var appDocumentVideoFile: File? = null
    private var tempShareCacheFile: File? = null
    private var isExporting = false
    private var lastShareClickTime = 0L

    // Playback state
    private var isPlaying = false
    private var videoDurationSec = 15f
    private var progressPollingJob: Job? = null
    private var externalAudioPlayer: MediaPlayer? = null
    private var videoMediaPlayer: MediaPlayer? = null
    private var noteIconAnimator: android.animation.ValueAnimator? = null

    override fun initView() {
        binding.header.applyTitleGradient()

        val args = arguments
        rawVideoPathOrUrl = args?.getString(ARG_VIDEO_URL).orEmpty()
        musicTrackId = args?.getString(ARG_MUSIC_TRACK_ID) ?: args?.getString("selected_track_id")
        musicTitle = args?.getString(ARG_MUSIC_TITLE)
        musicAudioUrl = args?.getString(ARG_MUSIC_AUDIO_URL)
        musicAudioFilePath = args?.getString(ARG_MUSIC_FILE_PATH)
        musicImageUrl = args?.getString(ARG_MUSIC_IMAGE_URL)
        templateTitle = args?.getString(ARG_TEMPLATE_TITLE)
        templateThumbUrl = args?.getString(ARG_TEMPLATE_THUMB)
        templateCode = args?.getString(ARG_TEMPLATE_CODE)
        templateCategory = args?.getString(ARG_TEMPLATE_CATEGORY)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        binding.cardMusicInfo.isClickable = false
        binding.cardMusicInfo.isFocusable = false

        updateMusicInfoUi()

        // Khởi tạo và phát video
        initializeVideo()
    }

    private fun updateMusicInfoUi() {
        val displayTitle = musicTitle?.takeIf { it.isNotBlank() }
            ?: templateTitle?.takeIf { it.isNotBlank() }
            ?: getString(R.string.original_audio)
        binding.tvMusicTitle.text = displayTitle

        val formattedDuration = formatTime(videoDurationSec)
        val subtitle = if (!musicTitle.isNullOrBlank() && !templateTitle.isNullOrBlank()) {
            getString(R.string.video_subtitle_format, formattedDuration, templateTitle)
        } else {
            getString(R.string.video_subtitle_no_music_format, formattedDuration)
        }
        binding.tvMusicSubtitle.text = subtitle

        val thumbToLoad = musicImageUrl?.takeIf { it.isNotBlank() }
            ?: templateThumbUrl?.takeIf { it.isNotBlank() }

        if (!thumbToLoad.isNullOrBlank()) {
            binding.ivMusicCover.visibility = View.VISIBLE
            binding.ivDefaultThumbIcon.visibility = View.GONE
            binding.ivMusicCover.load(thumbToLoad) { crossfade(true) }
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
            if (dur > 0) {
                videoDurationSec = dur
                updateMusicInfoUi()
            }

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
            Toast.makeText(requireContext(), getString(R.string.toast_video_play_error), Toast.LENGTH_SHORT).show()
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
        updateNoteMusicAnimation(playing)
    }

    private fun updateNoteMusicAnimation(playing: Boolean) {
        if (playing) {
            if (noteIconAnimator == null) {
                noteIconAnimator = android.animation.ValueAnimator.ofFloat(-12f, 12f).apply {
                    duration = 800L
                    addUpdateListener { animator ->
                        bindingOrNull?.ivNoteMusic?.rotation = animator.animatedValue as Float
                    }
                    repeatMode = android.animation.ValueAnimator.REVERSE
                    repeatCount = android.animation.ValueAnimator.INFINITE
                }
            }
            if (noteIconAnimator?.isStarted != true) {
                noteIconAnimator?.start()
            }
            binding.ivNoteMusic.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
        } else {
            noteIconAnimator?.cancel()
            noteIconAnimator = null
            bindingOrNull?.ivNoteMusic?.animate()?.rotation(0f)?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(200)?.start()
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
        binding.header.setOnBackClickListener {
            handleBackNavigation()
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
            if (SystemClock.elapsedRealtime() - lastShareClickTime < 1000L) return@setOnClickListener
            lastShareClickTime = SystemClock.elapsedRealtime()
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
        showExportLoading(true, getString(R.string.loading_merging_audio_to_video), 60)
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
            val videoFile = appDocumentVideoFile ?: prepareFinalVideoFile(getString(R.string.loading_preparing_save_video))

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

                AppDialogHelper.showSaveSuccessDialog(requireActivity())
            } else {
                Toast.makeText(requireContext(), getString(R.string.toast_cannot_export_video), Toast.LENGTH_SHORT).show()
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
                val temp = prepareFinalVideoFile(getString(R.string.loading_preparing_share_video))
                tempShareCacheFile = temp
                temp
            }

            if (videoToShare != null && videoToShare.exists()) {
                val success = ShareUtils.shareVideo(
                    context = requireContext(),
                    filePath = videoToShare.absolutePath,
                    chooserTitle = getString(R.string.share_video_ai_title)
                )
                if (!success) {
                    Toast.makeText(requireContext(), getString(R.string.toast_cannot_share_video), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.toast_video_not_ready), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showExportLoading(show: Boolean, status: String? = null, percent: Int = 0) {
        val resolvedStatus = status ?: getString(R.string.loading_creating_video)
        binding.flExportLoadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvExportStatus.text = resolvedStatus
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

    private fun handleBackNavigation() {
        findNavController().previousBackStackEntry?.savedStateHandle?.apply {
            set(ARG_TEMPLATE_CODE, templateCode)
            set(ARG_TEMPLATE_CATEGORY, templateCategory)
            set(ARG_TEMPLATE_TITLE, templateTitle)
            set(ARG_TEMPLATE_THUMB, templateThumbUrl)
        }
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        progressPollingJob?.cancel()
        progressPollingJob = null
        stopExternalAudio()
        binding.videoView.stopPlayback()
        videoMediaPlayer = null
        tempShareCacheFile?.delete()
        tempShareCacheFile = null
        noteIconAnimator?.cancel()
        noteIconAnimator = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_VIDEO_URL = "arg_video_url"
        const val ARG_MUSIC_TRACK_ID = "arg_music_track_id"
        const val ARG_MUSIC_TITLE = "arg_music_title"
        const val ARG_MUSIC_AUDIO_URL = "arg_music_audio_url"
        const val ARG_MUSIC_FILE_PATH = "arg_music_file_path"
        const val ARG_MUSIC_IMAGE_URL = "arg_music_image_url"
        const val ARG_TEMPLATE_TITLE = "template_title"
        const val ARG_TEMPLATE_THUMB = "template_thumb"
        const val ARG_TEMPLATE_CODE = "arg_template_code"
        const val ARG_TEMPLATE_CATEGORY = "arg_template_category"
    }
}
