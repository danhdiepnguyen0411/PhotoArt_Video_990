package com.example.phortart_video_990.ui.screen.feature.memories

import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.animation.doOnEnd
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.VideoGenerator
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

    private val photoUris = mutableListOf<String>()
    private var musicTitle: String? = null

    // Video playback constants & state
    private val videoDurationSec = 24f
    private var isPlaying = false
    private var currentTimeSec = 0f
    private var currentPhotoIndex = -1

    private var playbackAnimator: ValueAnimator? = null
    private var generatedVideoFile: File? = null
    private var isExporting = false

    private lateinit var photoStripAdapter: ResultPhotoStripAdapter

    private val pickMoreImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val newUris = uris.map { it.toString() }.filter { it !in photoUris }
            if (newUris.isNotEmpty()) {
                photoUris.addAll(newUris)
                generatedVideoFile = null // Invalidate cached video file
                updatePhotosStripUi()
            }
        }
    }

    override fun initView() {
        val args = arguments
        val initialPhotos = args?.getStringArrayList(ARG_PHOTOS) ?: emptyList()
        photoUris.clear()
        photoUris.addAll(initialPhotos)
        musicTitle = args?.getString(ARG_TRACK)

        setupMusicUi()
        setupPhotosStrip()

        // Observe returned music selection if user changes music
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<String>("selected_music_title")
            ?.observe(viewLifecycleOwner) { newMusic ->
                if (!newMusic.isNullOrBlank()) {
                    musicTitle = newMusic
                    setupMusicUi()
                    generatedVideoFile = null
                }
            }

        updateTimeUi(0f)
        updatePlayStateUi(false)
    }

    private fun setupMusicUi() {
        if (!musicTitle.isNullOrBlank()) {
            binding.tvTrackTitle.text = musicTitle
            binding.tvTrackSubtitle.text = "Nhạc nền đã chọn • 00:24"
        } else {
            binding.tvTrackTitle.text = "Memories – Piano version"
            binding.tvTrackSubtitle.text = "Soft Piano • 00:24"
        }
    }

    private fun setupPhotosStrip() {
        photoStripAdapter = ResultPhotoStripAdapter(
            photos = photoUris,
            selectedIndex = 0,
            onPhotoClick = { index ->
                pausePlayback()
                val secondsPerPhoto = videoDurationSec / photoUris.size.coerceAtLeast(1)
                currentTimeSec = (index * secondsPerPhoto).coerceIn(0f, videoDurationSec)
                updateTimeUi(currentTimeSec)
                displayPhoto(index)
            }
        )
        binding.rvResultPhotos.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvResultPhotos.adapter = photoStripAdapter

        updatePhotosStripUi()
    }

    private fun updatePhotosStripUi() {
        binding.tvPhotosBadgeCount.text = "${photoUris.size} ảnh"
        photoStripAdapter.updateData(photoUris, currentPhotoIndex.coerceAtLeast(0))

        if (photoUris.isNotEmpty() && currentPhotoIndex == -1) {
            displayPhoto(0)
        }
    }

    private fun displayPhoto(index: Int) {
        if (index !in photoUris.indices) return
        currentPhotoIndex = index

        binding.ivStagePhoto.load(Uri.parse(photoUris[index])) {
            crossfade(true)
            crossfade(300)
        }
        photoStripAdapter.setSelectedIndex(index)
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

                val secondsPerPhoto = videoDurationSec / photoUris.size.coerceAtLeast(1)
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
                }
            }
            start()
        }
    }

    private fun pausePlayback() {
        isPlaying = false
        updatePlayStateUi(false)
        playbackAnimator?.cancel()
        playbackAnimator = null
    }

    private fun togglePlayback() {
        if (isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    private fun updatePlayStateUi(playing: Boolean) {
        if (playing) {
            binding.btnPlayPause.alpha = 0f
            binding.btnPlayPause.isClickable = false
            binding.ivPlayPauseIcon.setImageResource(R.drawable.ic_pause_small)
            binding.ivMusicPlayIcon.setImageResource(R.drawable.ic_pause_small)
        } else {
            binding.btnPlayPause.alpha = 1f
            binding.btnPlayPause.isClickable = true
            binding.ivPlayPauseIcon.setImageResource(R.drawable.ic_play_small)
            binding.ivMusicPlayIcon.setImageResource(R.drawable.ic_play_small)
        }
    }

    private fun updateTimeUi(timeSec: Float) {
        binding.tvVideoTime.text = "${formatTime(timeSec)} / ${formatTime(videoDurationSec)}"
        val progress = ((timeSec / videoDurationSec) * 1000).toInt()
        binding.videoProgressBar.progress = progress
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
        binding.tvExportPercent.text = "$percent%"
    }

    private suspend fun obtainVideoFile(statusText: String): File? {
        val existing = generatedVideoFile
        if (existing != null && existing.exists()) {
            return existing
        }

        showExportLoading(true, statusText, 0)
        isExporting = true
        pausePlayback()

        return try {
            val file = VideoGenerator.generateMp4FromPhotos(
                context = requireContext(),
                photoUris = photoUris,
                durationSec = videoDurationSec,
                onProgress = { progress ->
                    binding.tvExportPercent.text = "$progress%"
                }
            )
            generatedVideoFile = file
            file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Xuất video thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        } finally {
            isExporting = false
            showExportLoading(false)
        }
    }

    private fun handleShare() {
        if (photoUris.isEmpty() || isExporting) return

        viewLifecycleOwner.lifecycleScope.launch {
            val videoFile = obtainVideoFile("Đang chuẩn bị video để chia sẻ...")
            if (videoFile != null) {
                try {
                    val shareableUri = VideoGenerator.getShareableUri(requireContext(), videoFile)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, shareableUri)
                        putExtra(Intent.EXTRA_SUBJECT, "Tạo video kỷ niệm")
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

    private fun handleSave(onSuccessPop: Boolean = true) {
        if (photoUris.isEmpty() || isExporting) return

        viewLifecycleOwner.lifecycleScope.launch {
            val videoFile = obtainVideoFile("Đang xuất video vào thư viện...")
            if (videoFile != null) {
                val videoTitle = if (!musicTitle.isNullOrBlank()) {
                    "Video tạo từ ${photoUris.size} ảnh AI • Nhạc: $musicTitle"
                } else {
                    "Video tạo từ ${photoUris.size} ảnh AI"
                }

                val savedUri = VideoGenerator.saveVideoToGallery(
                    context = requireContext(),
                    videoFile = videoFile,
                    title = "Memories_${photoUris.size}_photos"
                )

                val dateFormat = SimpleDateFormat("d 'thg' M, yyyy • HH:mm", Locale.getDefault())
                val currentDate = dateFormat.format(Date())

                val historyItem = HistoryItemModel(
                    type = "Gom ảnh",
                    title = videoTitle,
                    date = currentDate,
                    imageUri = savedUri?.toString() ?: photoUris.firstOrNull() ?: ""
                )
                historyRepository.addHistoryItem(historyItem)

                Toast.makeText(requireContext(), "Đã lưu video thành công vào thư viện!", Toast.LENGTH_LONG).show()

                if (onSuccessPop) {
                    binding.root.postDelayed({
                        findNavController().popBackStack()
                    }, 1200)
                }
            }
        }
    }

    private fun showSortDialog() {
        val sortOptions = arrayOf("Thứ tự ban đầu", "Đảo ngược thứ tự", "Xáo trộn ngẫu nhiên")
        AlertDialog.Builder(requireContext())
            .setTitle("Sắp xếp ảnh")
            .setItems(sortOptions) { _, which ->
                when (which) {
                    1 -> photoUris.reverse()
                    2 -> photoUris.shuffle()
                    else -> {}
                }
                generatedVideoFile = null
                currentPhotoIndex = 0
                photoStripAdapter.updateData(photoUris, 0)
                displayPhoto(0)
            }
            .show()
    }

    private fun showTransitionEffectDialog() {
        val effects = arrayOf("Mờ dần", "Trượt ngang", "Thu phóng nhẹ", "Hòa tan")
        AlertDialog.Builder(requireContext())
            .setTitle("Hiệu ứng chuyển ảnh")
            .setItems(effects) { _, which ->
                binding.tvTransitionEffectName.text = effects[which]
                Toast.makeText(requireContext(), "Đã chọn: ${effects[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.cardInnerVideoFrame.setOnClickListener {
            togglePlayback()
        }

        binding.btnPlayPause.setOnClickListener {
            togglePlayback()
        }

        binding.btnMusicPlayPreview.setOnClickListener {
            togglePlayback()
        }

        binding.btnSave.setOnClickListener {
            handleSave(onSuccessPop = true)
        }

        binding.btnShare.setOnClickListener {
            handleShare()
        }

        binding.btnCreateVideoBottom.setOnClickListener {
            handleSave(onSuccessPop = false)
        }

        binding.btnAddMorePhotosInResult.setOnClickListener {
            pickMoreImagesLauncher.launch("image/*")
        }

        binding.btnSortPhotos.setOnClickListener {
            showSortDialog()
        }

        binding.btnChangeMusic.setOnClickListener {
            findNavController().navigate(R.id.action_videoResultFragment_to_musicFragment)
        }

        binding.btnSelectTransitionEffect.setOnClickListener {
            showTransitionEffectDialog()
        }

        binding.btnFullscreen.setOnClickListener {
            if (photoUris.isEmpty()) return@setOnClickListener
            pausePlayback()
            FullscreenVideoDialog(
                context = requireContext(),
                photos = photoUris,
                videoDurationSec = videoDurationSec,
                initialTimeSec = currentTimeSec,
                onDismissCallback = { newTime, wasPlaying ->
                    currentTimeSec = newTime
                    updateTimeUi(newTime)
                    val secondsPerPhoto = videoDurationSec / photoUris.size.coerceAtLeast(1)
                    val targetIndex = (newTime / secondsPerPhoto).toInt().coerceIn(0, photoUris.lastIndex)
                    displayPhoto(targetIndex)
                    if (wasPlaying) {
                        startPlayback()
                    } else {
                        pausePlayback()
                    }
                }
            ).show()
        }
    }

    override fun onPause() {
        super.onPause()
        pausePlayback()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        playbackAnimator?.cancel()
        playbackAnimator = null
    }

    companion object {
        const val ARG_PHOTOS = "arg_photos"
        const val ARG_TRACK = "arg_track"

        fun createBundle(photos: List<String>, track: String?): Bundle {
            return Bundle().apply {
                putStringArrayList(ARG_PHOTOS, ArrayList(photos))
                putString(ARG_TRACK, track)
            }
        }
    }
}
