package com.aiart.photo.beauti.editor.ui.screen.feature.memories

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
import com.aiart.photo.beauti.editor.databinding.FragmentVideoResultBinding
import android.os.SystemClock
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
    private var musicTrackId: String? = null
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
    private var lastShareClickTime = 0L

    override fun initView() {
        binding.header.applyTitleGradient()

        val args = arguments
        videoPath = args?.getString(ARG_VIDEO_PATH)
        photoUris = args?.getStringArrayList(ARG_PHOTOS) ?: emptyList()
        musicTrackId = args?.getString(ARG_TRACK_ID) ?: args?.getString("selected_track_id")
        musicTitle = args?.getString(ARG_TRACK)
        musicAudioUrl = args?.getString(ARG_AUDIO_URL)
        musicAudioFilePath = args?.getString(ARG_AUDIO_PATH)
        musicImageUrl = args?.getString(ARG_IMAGE_URL)

        binding.cardVideoInfo.isClickable = false
        binding.cardVideoInfo.isFocusable = false
        binding.flVideoNote.isClickable = false
        binding.flVideoNote.isFocusable = false

        updateMusicInfoUi()
        updatePlayStateUi(false)
        initializeVideo()
        autoSaveVideoToHistory()

        // Lắng nghe đổi bài hát mới từ MusicFragment
        val stateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        stateHandle?.getLiveData<String>("selected_music_title")?.observe(viewLifecycleOwner) { title ->
                val newTrackId = stateHandle.get<String?>("selected_track_id")
                val newAudioUrl = stateHandle.get<String?>("selected_music_audio_url")
                val newFilePath = stateHandle.get<String?>("selected_music_file_path")
                val newImgUrl = stateHandle.get<String?>("selected_music_image_url")

                // Clear keys ngay lập tức tránh trigger lặp lại
                stateHandle.remove<String>("selected_music_title")
                stateHandle.remove<String?>("selected_track_id")
                stateHandle.remove<String?>("selected_music_audio_url")
                stateHandle.remove<String?>("selected_music_file_path")
                stateHandle.remove<String?>("selected_music_image_url")

                if (!newTrackId.isNullOrBlank()) {
                    musicTrackId = newTrackId
                }
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

    private var hasAutoSavedHistory = false

    private fun autoSaveVideoToHistory() {
        if (hasAutoSavedHistory) return
        val path = videoPath ?: return
        val localFile = File(path)
        if (!localFile.exists()) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val savedDoc = VideoGenerator.saveVideoToAppDocuments(
                    context = requireContext(),
                    sourceFile = localFile,
                    title = "Memories_${System.currentTimeMillis()}"
                )
                appDocumentVideoFile = savedDoc

                val videoTitle = if (!musicTitle.isNullOrBlank()) {
                    getString(R.string.video_title_with_music, photoUris.size.coerceAtLeast(1), musicTitle)
                } else {
                    getString(R.string.video_title_no_music, photoUris.size.coerceAtLeast(1))
                }

                val currentTime = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
                val currentDate = dateFormat.format(Date(currentTime))

                val historyItem = HistoryItemModel(
                    type = "Gom ảnh",
                    title = videoTitle,
                    date = currentDate,
                    imageUri = savedDoc.absolutePath,
                    duration = formatTime(videoDurationSec),
                    photoCount = photoUris.size.toString(),
                    timestamp = currentTime
                )
                historyRepository.addHistoryItem(historyItem)
                hasAutoSavedHistory = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateMusicInfoUi() {
        val title = musicTitle?.takeIf { it.isNotBlank() }
        if (!title.isNullOrBlank()) {
            binding.tvVideoTitle.text = title
            binding.tvVideoSubtitle.text = getString(R.string.video_subtitle_no_music_format, formatTime(videoDurationSec))
        } else {
            binding.tvVideoTitle.text = getString(R.string.memories_of_us)
            binding.tvVideoSubtitle.text = getString(R.string.video_subtitle_no_music_format, formatTime(videoDurationSec))
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
            Toast.makeText(requireContext(), getString(R.string.toast_video_file_not_found), Toast.LENGTH_SHORT).show()
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
            updateMusicInfoUi()

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
            Toast.makeText(requireContext(), getString(R.string.toast_video_play_error), Toast.LENGTH_SHORT).show()
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
                noteIconAnimator = ObjectAnimator.ofFloat(binding.ivNoteMusic, View.ROTATION, -12f, 12f).apply {
                    duration = 380
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                }
            }
            if (noteIconAnimator?.isStarted != true) {
                noteIconAnimator?.start()
            }
            binding.ivNoteMusic.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
        } else {
            stopNoteMusicAnimation()
        }
    }

    private fun stopNoteMusicAnimation() {
        noteIconAnimator?.cancel()
        noteIconAnimator = null
        bindingOrNull?.ivNoteMusic?.animate()?.rotation(0f)?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(200)?.start()
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
        return String.format(Locale.getDefault(), "%d:%02d", m, s)
    }

    private fun showExportLoading(show: Boolean, status: String? = null, percent: Int = 0) {
        val b = bindingOrNull ?: return
        val resolvedStatus = status ?: getString(R.string.loading_creating_video)
        b.flExportLoadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        b.tvExportStatus.text = resolvedStatus
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
                showExportLoading(true, getString(R.string.loading_merging_audio_to_video), 60)
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
            val videoFile = appDocumentVideoFile ?: prepareFinalVideoFile(getString(R.string.loading_preparing_save_video))
            if (videoFile != null && videoFile.exists()) {
                val videoTitle = if (!musicTitle.isNullOrBlank()) {
                    getString(R.string.video_title_with_music, photoUris.size.coerceAtLeast(1), musicTitle)
                } else {
                    getString(R.string.video_title_no_music, photoUris.size.coerceAtLeast(1))
                }

                // 1. Lưu bản gốc vào App Documents riêng nếu chưa có
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

                AppDialogHelper.showSaveSuccessDialog(requireActivity())
            } else {
                Toast.makeText(requireContext(), getString(R.string.toast_cannot_save_video), Toast.LENGTH_SHORT).show()
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
                val temp = prepareFinalVideoFile(getString(R.string.loading_preparing_share_video))
                tempShareCacheFile = temp
                temp
            }

            if (videoToShare != null && videoToShare.exists()) {
                val success = ShareUtils.shareVideo(
                    context = requireContext(),
                    filePath = videoToShare.absolutePath,
                    chooserTitle = getString(R.string.share_video_memories_title)
                )
                if (!success) {
                    Toast.makeText(requireContext(), getString(R.string.toast_cannot_share_video), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.toast_video_not_ready), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun initListener() {
        binding.header.setOnBackClickListener {
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
            if (SystemClock.elapsedRealtime() - lastShareClickTime < 1000L) return@setOnClickListener
            lastShareClickTime = SystemClock.elapsedRealtime()
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                it.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                handleShare()
            }.start()
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
        const val ARG_TRACK_ID = "arg_track_id"
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
            imageUrl: String? = null,
            trackId: String? = null
        ): Bundle {
            return Bundle().apply {
                putString(ARG_VIDEO_PATH, videoPath)
                putStringArrayList(ARG_PHOTOS, ArrayList(photos))
                putString(ARG_TRACK_ID, trackId)
                putString(ARG_TRACK, track)
                putString(ARG_AUDIO_URL, audioUrl)
                putString(ARG_AUDIO_PATH, audioPath)
                putString(ARG_IMAGE_URL, imageUrl)
            }
        }
    }
}
