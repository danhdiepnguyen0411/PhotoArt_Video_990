package com.example.phortart_video_990.ui.screen.feature.memories

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.core.animation.doOnEnd
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.VideoGenerator
import com.example.phortart_video_990.core.utils.VideoGenerator.TransitionType
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.databinding.FragmentVideoResultBinding
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoResultFragment : BaseFragment<FragmentVideoResultBinding>(FragmentVideoResultBinding::inflate) {

    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private var photoUris: List<String> = emptyList()
    private var musicTitle: String? = null
    private var musicAudioUrl: String? = null
    private var musicImageUrl: String? = null

    // Danh sách hiệu ứng chuyển cảnh ngẫu nhiên giữa các ảnh
    private var randomTransitions: List<TransitionType> = emptyList()

    // Video playback constants & state (Tính động theo số lượng ảnh: min 1.0s, max 2.5s mỗi ảnh)
    private var videoDurationSec = 15f
    private var secondsPerPhoto = 2.0f
    private var isPlaying = false
    private var currentTimeSec = 0f
    private var currentPhotoIndex = -1

    private var playbackAnimator: ValueAnimator? = null
    private var previewMediaPlayer: android.media.MediaPlayer? = null
    private var noteIconAnimator: ObjectAnimator? = null

    // Lưu trữ file video: đã lưu vào App Documents hay file tạm trong Cache
    private var appDocumentVideoFile: File? = null
    private var tempShareCacheFile: File? = null
    private var isExporting = false

    override fun initView() {
        val args = arguments
        photoUris = args?.getStringArrayList(ARG_PHOTOS) ?: emptyList()
        musicTitle = args?.getString(ARG_TRACK)
        musicAudioUrl = args?.getString(ARG_AUDIO_URL)
        musicImageUrl = args?.getString(ARG_IMAGE_URL)

        // Tính thời lượng video và thời gian mỗi ảnh:
        // Đảm bảo chia đều mỗi ảnh trong khoảng [1.0s .. 2.5s]
        videoDurationSec = VideoGenerator.calculateVideoDuration(photoUris.size)
        secondsPerPhoto = VideoGenerator.calculateSecondsPerPhoto(videoDurationSec, photoUris.size)

        // Sinh danh sách chuyển cảnh ngẫu nhiên cố định cho phiên video này
        if (photoUris.isNotEmpty()) {
            randomTransitions = VideoGenerator.generateRandomTransitions(photoUris.size)
        }

        updateMusicInfoUi()

        if (photoUris.isNotEmpty()) {
            displayPhoto(0)
        }

        updateTimeUi(0f)
        updatePlayStateUi(false)

        // Tự động phát video và nhạc xem trước khi vừa mở màn hình
        if (photoUris.isNotEmpty()) {
            binding.root.post {
                startPlayback()
            }
        }

        // Lắng nghe bài hát mới được chọn trả về từ MusicFragment
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<String>("selected_music_title")
            ?.observe(viewLifecycleOwner) { newMusic ->
                if (!newMusic.isNullOrBlank() && newMusic != musicTitle) {
                    musicTitle = newMusic
                    updateMusicInfoUi()
                    invalidateRenderedVideos()
                    resetPlaybackToStart()
                    binding.root.post {
                        startPlayback()
                    }
                }
            }

        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<String?>("selected_music_audio_url")
            ?.observe(viewLifecycleOwner) { newAudioUrl ->
                if (!newAudioUrl.isNullOrBlank() && newAudioUrl != musicAudioUrl) {
                    musicAudioUrl = newAudioUrl
                    invalidateRenderedVideos()
                    resetPlaybackToStart()
                    binding.root.post {
                        startPlayback()
                    }
                }
            }

        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<String?>("selected_music_image_url")
            ?.observe(viewLifecycleOwner) { newImageUrl ->
                if (newImageUrl != musicImageUrl) {
                    musicImageUrl = newImageUrl
                    updateMusicInfoUi()
                }
            }
    }

    /**
     * Cập nhật thông tin nhạc nền:
     * - Title hiển thị tên nhạc (hoặc "Chưa chọn nhạc")
     * - Subtitle chỉ hiển thị số giây nhạc (ví dụ: "00:15")
     * - Ảnh cover của bài nhạc
     */
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

    /**
     * Hiệu ứng chuyển động (nhịp nốt nhạc) khi video đang chạy và có bài hát
     */
    private fun startNoteMusicAnimation() {
        if (!musicAudioUrl.isNullOrBlank()) {
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

    /**
     * Dừng phát nhạc/video và reset về 00:00 cùng ảnh đầu tiên khi đổi bài hát
     */
    private fun resetPlaybackToStart() {
        pausePlayback()
        stopAudioPreview()
        currentTimeSec = 0f
        updateTimeUi(0f)
        if (photoUris.isNotEmpty()) {
            currentPhotoIndex = -1
            displayPhoto(0)
        }
    }

    private fun invalidateRenderedVideos() {
        appDocumentVideoFile = null
        tempShareCacheFile?.delete()
        tempShareCacheFile = null
    }

    private fun displayPhoto(index: Int) {
        if (index !in photoUris.indices) return
        val prevIndex = currentPhotoIndex
        currentPhotoIndex = index

        val currentUri = Uri.parse(photoUris[index])

        if (prevIndex != -1 && prevIndex != index && randomTransitions.isNotEmpty()) {
            val transition = randomTransitions.getOrElse(prevIndex) { TransitionType.CROSS_FADE }
            playTransitionAnimation(transition, currentUri)
        } else {
            resetPhotoTransformations()
            binding.ivStagePhoto.load(currentUri) {
                crossfade(true)
            }
        }
    }

    private fun resetPhotoTransformations() {
        binding.ivStagePhoto.apply {
            alpha = 1f
            translationX = 0f
            scaleX = 1f
            scaleY = 1f
        }
        binding.ivStagePhotoNext.apply {
            alpha = 0f
            translationX = 0f
            scaleX = 1f
            scaleY = 1f
        }
    }

    /**
     * Mô phỏng hiệu ứng chuyển cảnh trực tiếp trên màn hình xem trước
     */
    private fun playTransitionAnimation(transition: TransitionType, nextPhotoUri: Uri) {
        val stageWidth = binding.cardVideoStage.width.toFloat().coerceAtLeast(600f)

        binding.ivStagePhotoNext.load(nextPhotoUri) {
            crossfade(false)
        }

        when (transition) {
            TransitionType.CROSS_FADE -> {
                binding.ivStagePhotoNext.alpha = 0f
                binding.ivStagePhotoNext.animate()
                    .alpha(1f)
                    .setDuration(450)
                    .withEndAction {
                        binding.ivStagePhoto.load(nextPhotoUri)
                        resetPhotoTransformations()
                    }
                    .start()
            }
            TransitionType.SLIDE_LEFT -> {
                binding.ivStagePhotoNext.translationX = stageWidth
                binding.ivStagePhotoNext.alpha = 1f
                binding.ivStagePhoto.animate()
                    .translationX(-stageWidth)
                    .setDuration(450)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
                binding.ivStagePhotoNext.animate()
                    .translationX(0f)
                    .setDuration(450)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        binding.ivStagePhoto.load(nextPhotoUri)
                        resetPhotoTransformations()
                    }
                    .start()
            }
            TransitionType.ZOOM_IN -> {
                binding.ivStagePhotoNext.alpha = 0f
                binding.ivStagePhotoNext.scaleX = 0.85f
                binding.ivStagePhotoNext.scaleY = 0.85f

                binding.ivStagePhoto.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .alpha(0f)
                    .setDuration(450)
                    .start()

                binding.ivStagePhotoNext.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(450)
                    .withEndAction {
                        binding.ivStagePhoto.load(nextPhotoUri)
                        resetPhotoTransformations()
                    }
                    .start()
            }
            TransitionType.WIPE_DOWN, TransitionType.FLASH_PULSE -> {
                binding.ivStagePhoto.animate()
                    .alpha(0.15f)
                    .setDuration(220)
                    .withEndAction {
                        binding.ivStagePhoto.load(nextPhotoUri)
                        binding.ivStagePhoto.animate().alpha(1f).setDuration(220).start()
                    }
                    .start()
            }
        }
    }

    private fun startPlayback() {
        if (photoUris.isEmpty() || isExporting) return
        isPlaying = true
        updatePlayStateUi(true)

        val remainingTime = videoDurationSec - currentTimeSec
        val remainingDurationMs = (remainingTime * 1000L).toLong()

        playbackAnimator?.cancel()
        playbackAnimator = ValueAnimator.ofFloat(currentTimeSec, videoDurationSec).apply {
            duration = remainingDurationMs
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val time = animator.animatedValue as Float
                currentTimeSec = time
                updateTimeUi(time)

                val targetIndex = (time / secondsPerPhoto).toInt().coerceIn(0, photoUris.lastIndex)
                if (targetIndex != currentPhotoIndex) {
                    displayPhoto(targetIndex)
                }
            }
            doOnEnd {
                if (currentTimeSec >= videoDurationSec) {
                    currentTimeSec = 0f
                    isPlaying = false
                    updatePlayStateUi(false)
                    updateTimeUi(0f)
                    displayPhoto(0)
                    pauseAudioPreview()
                }
            }
            start()
        }
        startAudioPreview()
    }

    private var isAudioPrepared = false

    private fun startAudioPreview() {
        val audioUrl = musicAudioUrl
        if (audioUrl.isNullOrBlank()) return

        try {
            if (previewMediaPlayer == null) {
                isAudioPrepared = false
                previewMediaPlayer = android.media.MediaPlayer().apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(audioUrl)
                    isLooping = true
                    setOnPreparedListener { mp ->
                        isAudioPrepared = true
                        if (isPlaying) {
                            try {
                                mp.seekTo((currentTimeSec * 1000).toInt())
                                mp.start()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    setOnErrorListener { mp, what, extra ->
                        isAudioPrepared = false
                        try {
                            mp.reset()
                            mp.release()
                        } catch (_: Exception) {}
                        previewMediaPlayer = null
                        true
                    }
                    prepareAsync()
                }
            } else {
                previewMediaPlayer?.let { mp ->
                    if (isAudioPrepared) {
                        try {
                            mp.seekTo((currentTimeSec * 1000).toInt())
                            if (!mp.isPlaying) mp.start()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pauseAudioPreview() {
        try {
            if (isAudioPrepared && previewMediaPlayer?.isPlaying == true) {
                previewMediaPlayer?.pause()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAudioPreview() {
        try {
            isAudioPrepared = false
            previewMediaPlayer?.stop()
            previewMediaPlayer?.release()
            previewMediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pausePlayback() {
        isPlaying = false
        updatePlayStateUi(false)
        playbackAnimator?.cancel()
        playbackAnimator = null
        pauseAudioPreview()
    }

    private fun togglePlayback() {
        if (isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
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

    private fun updateTimeUi(timeSec: Float) {
        val b = bindingOrNull ?: return
        b.tvVideoTime.text = "${formatTime(timeSec)} / ${formatTime(videoDurationSec)}"
        val progress = ((timeSec / videoDurationSec) * 1000).toInt()
        b.videoProgressBar.progress = progress
    }

    private fun formatTime(seconds: Float): String {
        val totalSecs = seconds.toInt()
        val m = totalSecs / 60
        val s = totalSecs % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    private fun showExportLoading(show: Boolean, status: String = "Đang xuất video...", percent: Int = 0) {
        binding.flExportLoadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvExportStatus.text = status
        binding.pbExportHorizontal.progress = percent
        binding.tvExportPercent.text = "$percent%"
    }

    /**
     * Render video MP4 với các hiệu ứng chuyển cảnh random và ghép nhạc
     */
    private suspend fun renderBaseVideoFile(statusText: String): File? {
        showExportLoading(true, statusText, 0)
        isExporting = true
        pausePlayback()

        return try {
            VideoGenerator.generateMp4FromPhotos(
                context = requireContext(),
                photoUris = photoUris,
                durationSec = videoDurationSec,
                audioUrl = musicAudioUrl,
                transitions = randomTransitions,
                onProgress = { progress, statusMessage ->
                    binding.pbExportHorizontal.progress = progress
                    binding.tvExportStatus.text = statusMessage
                    binding.tvExportPercent.text = "$progress%"
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Tạo video thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        } finally {
            isExporting = false
            showExportLoading(false)
        }
    }

    /**
     * Bấm LƯU: Lưu cả vào App Documents riêng và tạo bản sao sang Thư viện máy (Gallery).
     */
    private fun handleSave() {
        if (photoUris.isEmpty() || isExporting) return

        viewLifecycleOwner.lifecycleScope.launch {
            val baseFile = appDocumentVideoFile ?: renderBaseVideoFile("Đang xuất video vào thư viện...")
            if (baseFile != null && baseFile.exists()) {
                val videoTitle = if (!musicTitle.isNullOrBlank()) {
                    "Video tạo từ ${photoUris.size} ảnh AI • Nhạc: $musicTitle"
                } else {
                    "Video tạo từ ${photoUris.size} ảnh AI"
                }

                // 1. Lưu bản gốc vào App Documents riêng (được bảo tồn, chỉ xóa khi xóa ở Lịch sử)
                val docFile = if (appDocumentVideoFile == null) {
                    val savedDoc = VideoGenerator.saveVideoToAppDocuments(
                        context = requireContext(),
                        sourceFile = baseFile,
                        title = "Memories_${photoUris.size}_photos"
                    )
                    appDocumentVideoFile = savedDoc
                    savedDoc
                } else appDocumentVideoFile!!

                // 2. Tạo bản sao ra MediaStore Gallery (Album thiết bị)
                val galleryUri = VideoGenerator.copyVideoToGallery(
                    context = requireContext(),
                    videoFile = docFile,
                    title = "Memories_${photoUris.size}_photos"
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

                Toast.makeText(requireContext(), "Đã lưu video thành công vào ứng dụng và thư viện ảnh!", Toast.LENGTH_LONG).show()

                binding.root.postDelayed({
                    findNavController().popBackStack()
                }, 1000)
            }
        }
    }

    /**
     * Bấm SHARE: Sử dụng file trong App Documents (nếu đã lưu), hoặc file tạm trong cache.
     * Xóa ngay file tạm trong cache sau khi hoàn tất chia sẻ.
     */
    private fun handleShare() {
        if (photoUris.isEmpty() || isExporting) return

        viewLifecycleOwner.lifecycleScope.launch {
            // Nếu đã lưu thì dùng luôn file App Documents, nếu chưa thì tạo file tạm trong cache
            val videoToShare = appDocumentVideoFile ?: run {
                val temp = renderBaseVideoFile("Đang chuẩn bị video để chia sẻ...")
                tempShareCacheFile = temp
                temp
            }

            if (videoToShare != null && videoToShare.exists()) {
                try {
                    val shareableUri = VideoGenerator.getShareableUri(requireContext(), videoToShare)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, shareableUri)
                        putExtra(Intent.EXTRA_SUBJECT, "Kỷ niệm của chúng ta")
                        putExtra(Intent.EXTRA_TEXT, "Video kỷ niệm tạo bởi Photo AI")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Chia sẻ video"))
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Thiết bị chưa hỗ trợ chia sẻ: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Dọn sạch file tạm trong cache sau khi người dùng chia sẻ xong và quay lại app
        cleanTempShareCache()
    }

    private fun cleanTempShareCache() {
        tempShareCacheFile?.let { temp ->
            if (temp.exists() && temp != appDocumentVideoFile) {
                temp.delete()
            }
        }
        tempShareCacheFile = null
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
            handleSave()
        }

        binding.btnShare.setOnClickListener {
            handleShare()
        }

        binding.cardVideoInfo.setOnClickListener {
            pausePlayback()
            findNavController().navigate(R.id.action_videoResultFragment_to_musicFragment)
        }
    }

    override fun onPause() {
        super.onPause()
        pausePlayback()
    }

    override fun onDestroyView() {
        playbackAnimator?.cancel()
        playbackAnimator = null
        stopAudioPreview()
        stopNoteMusicAnimation()
        cleanTempShareCache()
        super.onDestroyView()
    }

    companion object {
        const val ARG_PHOTOS = "arg_photos"
        const val ARG_TRACK = "arg_track"
        const val ARG_AUDIO_URL = "arg_audio_url"
        const val ARG_IMAGE_URL = "arg_image_url"

        fun createBundle(photos: List<String>, track: String?, audioUrl: String? = null, imageUrl: String? = null): Bundle {
            return Bundle().apply {
                putStringArrayList(ARG_PHOTOS, ArrayList(photos))
                putString(ARG_TRACK, track)
                putString(ARG_AUDIO_URL, audioUrl)
                putString(ARG_IMAGE_URL, imageUrl)
            }
        }
    }
}
