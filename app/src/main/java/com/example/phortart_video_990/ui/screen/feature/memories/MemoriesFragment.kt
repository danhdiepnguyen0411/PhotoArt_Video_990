package com.example.phortart_video_990.ui.screen.feature.memories

import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.VideoGenerator
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.databinding.FragmentMemoriesBinding
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoriesFragment : BaseFragment<FragmentMemoriesBinding>(FragmentMemoriesBinding::inflate) {

    private val selectedPhotos = mutableListOf<Uri>()
    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private val pickMultipleImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val newUris = uris.filter { it !in selectedPhotos }
            if (newUris.isNotEmpty()) {
                selectedPhotos.addAll(newUris)
                updatePhotosUi()
            }
        }
    }

    private var selectedMusicTitle: String? = null
    private var selectedMusicAudioUrl: String? = null
    private var selectedMusicAudioFilePath: String? = null
    private var selectedMusicImageUrl: String? = null

    override fun initView() {
        binding.tvTopTitle.text = getString(R.string.feature_video_title).replace("\n", " ")

        updatePhotosUi()

        // Read any already set music selection immediately from savedStateHandle (for first-time navigation)
        val stateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        stateHandle?.get<String>("selected_music_title")?.let { musicTitle ->
            if (musicTitle.isNotBlank()) {
                selectedMusicTitle = musicTitle
                binding.tvSelectedMusicTitle.text = musicTitle
                binding.tvSelectedMusicSubtitle.text = "Đã chọn nhạc nền cho video"
            }
        }
        stateHandle?.get<String?>("selected_music_audio_url")?.let { audioUrl ->
            selectedMusicAudioUrl = audioUrl
        }
        stateHandle?.get<String?>("selected_music_file_path")?.let { filePath ->
            selectedMusicAudioFilePath = filePath
        }
        stateHandle?.get<String?>("selected_music_image_url")?.let { imageUrl ->
            selectedMusicImageUrl = imageUrl
            if (!imageUrl.isNullOrBlank()) {
                binding.ivMusicIcon.load(imageUrl) {
                    crossfade(true)
                    error(R.drawable.ic_func_music)
                    placeholder(R.drawable.ic_func_music)
                }
            } else {
                binding.ivMusicIcon.setImageResource(R.drawable.ic_func_music)
            }
        }

        // Observe returned music selection from MusicFragment
        stateHandle?.getLiveData<String>("selected_music_title")
            ?.observe(viewLifecycleOwner) { musicTitle ->
                if (!musicTitle.isNullOrBlank()) {
                    selectedMusicTitle = musicTitle
                    binding.tvSelectedMusicTitle.text = musicTitle
                    binding.tvSelectedMusicSubtitle.text = "Đã chọn nhạc nền cho video"
                }
            }

        stateHandle?.getLiveData<String?>("selected_music_audio_url")
            ?.observe(viewLifecycleOwner) { audioUrl ->
                selectedMusicAudioUrl = audioUrl
            }

        stateHandle?.getLiveData<String?>("selected_music_file_path")
            ?.observe(viewLifecycleOwner) { filePath ->
                selectedMusicAudioFilePath = filePath
            }

        stateHandle?.getLiveData<String?>("selected_music_image_url")
            ?.observe(viewLifecycleOwner) { imageUrl ->
                selectedMusicImageUrl = imageUrl
                if (!imageUrl.isNullOrBlank()) {
                    binding.ivMusicIcon.load(imageUrl) {
                        crossfade(true)
                        error(R.drawable.ic_func_music)
                        placeholder(R.drawable.ic_func_music)
                    }
                } else {
                    binding.ivMusicIcon.setImageResource(R.drawable.ic_func_music)
                }
            }
    }

    private fun updatePhotosUi() {
        val count = selectedPhotos.size
        if (count > 0) {
            binding.cardUploadPhoto.visibility = View.GONE
            binding.llSelectedPhotosContainer.visibility = View.VISIBLE
            binding.tvSelectedCount.visibility = View.VISIBLE
            binding.tvSelectedCount.text = "$count ảnh đã chọn"

            // Layer 1 (Top image)
            binding.ivStackImage1.load(selectedPhotos[0]) {
                crossfade(true)
            }

            // Layer 2 (Middle image)
            if (count > 1) {
                binding.cardStack2.visibility = View.VISIBLE
                binding.ivStackImage2.load(selectedPhotos[1]) {
                    crossfade(true)
                }
            } else {
                binding.cardStack2.visibility = View.GONE
            }

            // Layer 3 (Bottom image)
            if (count > 2) {
                binding.cardStack3.visibility = View.VISIBLE
                binding.ivStackImage3.load(selectedPhotos[2]) {
                    crossfade(true)
                }
            } else {
                binding.cardStack3.visibility = View.GONE
            }

            // Badge overlay text on top image (+N or count)
            if (count > 1) {
                binding.tvStackCountBadge.visibility = View.VISIBLE
                binding.tvStackCountBadge.text = "+${count - 1}"
            } else {
                binding.tvStackCountBadge.visibility = View.GONE
            }
        } else {
            binding.cardUploadPhoto.visibility = View.VISIBLE
            binding.llSelectedPhotosContainer.visibility = View.GONE
            binding.tvSelectedCount.visibility = View.GONE
        }
    }

    private fun openPhotoViewer(initialPosition: Int = 0) {
        if (selectedPhotos.isEmpty()) return
        PhotoViewerDialog(
            context = requireContext(),
            photos = selectedPhotos,
            initialPosition = initialPosition,
            onPhotosChanged = {
                updatePhotosUi()
            },
            onAddMoreRequested = {
                pickMultipleImagesLauncher.launch("image/*")
            }
        ).show()
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.cardUploadPhoto.setOnClickListener {
            pickMultipleImagesLauncher.launch("image/*")
        }

        binding.btnAddMorePhotos.setOnClickListener {
            pickMultipleImagesLauncher.launch("image/*")
        }

        binding.cardPhotoStackContainer.setOnClickListener {
            openPhotoViewer(0)
        }

        binding.btnViewAllPhotos.setOnClickListener {
            openPhotoViewer(0)
        }

        binding.cardSelectMusic.setOnClickListener {
            findNavController().navigate(R.id.action_memoriesFragment_to_musicFragment)
        }

        binding.btnCreateVideo.setOnClickListener {
            if (selectedPhotos.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng chọn ít nhất 1 ảnh để tạo video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val stateHandle = findNavController().currentBackStackEntry?.savedStateHandle
            val finalTrack = selectedMusicTitle ?: stateHandle?.get<String>("selected_music_title")
            val finalAudioUrl = selectedMusicAudioUrl ?: stateHandle?.get<String?>("selected_music_audio_url")
            val finalAudioPath = selectedMusicAudioFilePath ?: stateHandle?.get<String?>("selected_music_file_path")
            val finalImageUrl = selectedMusicImageUrl ?: stateHandle?.get<String?>("selected_music_image_url")

            binding.btnCreateVideo.isEnabled = false
            showLoading(
                title = "Đang tạo video...",
                subtitle = "Đang chuẩn bị hình ảnh và hiệu ứng..."
            )

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // 1. Tải nhạc nền nếu cần
                    var resolvedAudioPath = finalAudioPath
                    val hasValidAudioFile = !resolvedAudioPath.isNullOrBlank() && File(resolvedAudioPath).let { it.exists() && it.length() > 0 }
                    if (!hasValidAudioFile && !finalAudioUrl.isNullOrBlank()) {
                        showLoading(
                            title = "Đang tải nhạc nền...",
                            subtitle = "Vui lòng chờ trong giây lát"
                        )
                        val downloaded = com.example.phortart_video_990.core.utils.AudioCacheManager.getOrDownloadAudio(
                            requireContext(),
                            finalAudioUrl
                        )
                        if (downloaded != null && downloaded.exists() && downloaded.length() > 0) {
                            resolvedAudioPath = downloaded.absolutePath
                            selectedMusicAudioFilePath = resolvedAudioPath
                        }
                    }

                    // 2. Tính toán thời lượng và sinh hiệu ứng chuyển cảnh
                    val photoCount = selectedPhotos.size
                    val videoDurationSec = VideoGenerator.calculateVideoDuration(photoCount)
                    val transitions = VideoGenerator.generateRandomTransitions(photoCount)
                    val photoUriStrings = selectedPhotos.map { it.toString() }

                    // Nguồn âm thanh (ưu tiên file nội bộ đã tải)
                    val audioSource = resolvedAudioPath?.takeIf { File(it).exists() } ?: finalAudioUrl

                    // 3. Render video MP4 hoàn chỉnh (ảnh + chuyển cảnh + ghép nhạc)
                    val generatedVideoFile = VideoGenerator.generateMp4FromPhotos(
                        context = requireContext(),
                        photoUris = photoUriStrings,
                        durationSec = videoDurationSec,
                        audioUrl = audioSource,
                        transitions = transitions,
                        onProgress = { progress, status ->
                            showLoading(
                                title = "Đang tạo video...",
                                subtitle = "$status ($progress%)"
                            )
                        }
                    )

                    if (!isAdded || view == null) return@launch
                    hideLoading()
                    binding.btnCreateVideo.isEnabled = true

                    if (generatedVideoFile.exists() && generatedVideoFile.length() > 0) {
                        // Lưu bản cố định vào App Documents & ghi nhận vào History ngay lập tức
                        val savedDoc = VideoGenerator.saveVideoToAppDocuments(
                            context = requireContext(),
                            sourceFile = generatedVideoFile,
                            title = "PhotoArt_Memories_${System.currentTimeMillis()}"
                        )

                        val dateFormat = SimpleDateFormat("d 'thg' M, yyyy • HH:mm", Locale.getDefault())
                        val videoTitle = if (!finalTrack.isNullOrBlank()) "Gom ảnh • Nhạc: $finalTrack" else "Video gom ảnh"
                        val historyItem = HistoryItemModel(
                            type = "Gom ảnh",
                            title = videoTitle,
                            date = dateFormat.format(Date()),
                            imageUri = savedDoc.absolutePath
                        )
                        historyRepository.addHistoryItem(historyItem)

                        navigateToVideoResult(
                            videoPath = savedDoc.absolutePath,
                            finalTrack = finalTrack,
                            finalAudioUrl = finalAudioUrl,
                            finalAudioPath = resolvedAudioPath,
                            finalImageUrl = finalImageUrl
                        )
                    } else {
                        Toast.makeText(requireContext(), "Không thể tạo file video. Vui lòng thử lại!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    if (!isAdded || view == null) return@launch
                    hideLoading()
                    binding.btnCreateVideo.isEnabled = true
                    Toast.makeText(requireContext(), "Lỗi khi tạo video: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateToVideoResult(
        videoPath: String,
        finalTrack: String?,
        finalAudioUrl: String?,
        finalAudioPath: String?,
        finalImageUrl: String?
    ) {
        val bundle = VideoResultFragment.createBundle(
            videoPath = videoPath,
            photos = selectedPhotos.map { it.toString() },
            track = finalTrack,
            audioUrl = finalAudioUrl,
            audioPath = finalAudioPath,
            imageUrl = finalImageUrl
        )
        findNavController().navigate(R.id.action_memoriesFragment_to_videoResultFragment, bundle)
    }
}
