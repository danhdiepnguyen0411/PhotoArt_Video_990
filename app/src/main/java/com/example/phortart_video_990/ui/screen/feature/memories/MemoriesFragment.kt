package com.example.phortart_video_990.ui.screen.feature.memories

import android.graphics.Color
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
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.databinding.FragmentMemoriesBinding
import kotlinx.coroutines.launch
import java.io.File

class MemoriesFragment : BaseFragment<FragmentMemoriesBinding>(FragmentMemoriesBinding::inflate) {

    private val selectedPhotos = mutableListOf<Uri>()
    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private var selectedAspectRatio: String = "9:16"
    private var maxPhotoLimit: Int = 10
    private var templateDurationSec: Float? = null

    private val pickMultipleImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val newUris = uris.filter { it !in selectedPhotos }
            val remainingSlots = maxPhotoLimit - selectedPhotos.size
            if (remainingSlots <= 0) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_max_photos_limit, maxPhotoLimit),
                    Toast.LENGTH_SHORT
                ).show()
                return@registerForActivityResult
            }
            val toAdd = newUris.take(remainingSlots)
            if (toAdd.isNotEmpty()) {
                selectedPhotos.addAll(toAdd)
                if (newUris.size > remainingSlots) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_max_photos_limit, maxPhotoLimit),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                updatePhotosUi()
            }
        }
    }

    private var selectedMusicTitle: String? = null
    private var selectedMusicAudioUrl: String? = null
    private var selectedMusicAudioFilePath: String? = null
    private var selectedMusicImageUrl: String? = null

    override fun initView() {
        binding.tvTopTitle.text = getString(R.string.memories_title)

        updatePhotosUi()
        updateRatioUi()

        // Read music selection from arguments or savedStateHandle
        val args = arguments
        val stateHandle = findNavController().currentBackStackEntry?.savedStateHandle

        val initialTitle = args?.getString("selected_music_title")?.takeIf { it.isNotBlank() }
            ?: stateHandle?.get<String>("selected_music_title")?.takeIf { it.isNotBlank() }
        if (!initialTitle.isNullOrBlank()) {
            selectedMusicTitle = initialTitle
            binding.tvSelectedMusicTitle.text = initialTitle
            binding.tvSelectedMusicSubtitle.text = getString(R.string.music_chosen_as_bg)
        }

        val initialAudioUrl = args?.getString("selected_music_audio_url")
            ?: stateHandle?.get<String?>("selected_music_audio_url")
        if (!initialAudioUrl.isNullOrBlank()) {
            selectedMusicAudioUrl = initialAudioUrl
        }

        val initialFilePath = args?.getString("selected_music_file_path")
            ?: stateHandle?.get<String?>("selected_music_file_path")
        if (!initialFilePath.isNullOrBlank()) {
            selectedMusicAudioFilePath = initialFilePath
        }

        val initialImageUrl = args?.getString("selected_music_image_url")
            ?: stateHandle?.get<String?>("selected_music_image_url")
        updateMusicCoverUi(initialImageUrl)

        val initialDuration = args?.getFloat("selected_template_duration_sec", 0f)?.takeIf { it > 0f }
            ?: stateHandle?.get<Float>("selected_template_duration_sec")?.takeIf { it > 0f }
        if (initialDuration != null) {
            templateDurationSec = initialDuration
        }

        val initialPhotoLimit = args?.getInt("selected_template_photo_count", 0)?.takeIf { it > 0 }
            ?: stateHandle?.get<Int>("selected_template_photo_count")?.takeIf { it > 0 }
        if (initialPhotoLimit != null) {
            maxPhotoLimit = initialPhotoLimit
        }

        // Observe returned music selection from MusicFragment
        stateHandle?.getLiveData<String>("selected_music_title")
            ?.observe(viewLifecycleOwner) { musicTitle ->
                if (!musicTitle.isNullOrBlank()) {
                    selectedMusicTitle = musicTitle
                    binding.tvSelectedMusicTitle.text = musicTitle
                    binding.tvSelectedMusicSubtitle.text = getString(R.string.music_chosen_as_bg)
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
                updateMusicCoverUi(imageUrl)
            }

        stateHandle?.getLiveData<Float>("selected_template_duration_sec")
            ?.observe(viewLifecycleOwner) { dur ->
                if (dur != null && dur > 0f) {
                    templateDurationSec = dur
                }
            }

        stateHandle?.getLiveData<Int>("selected_template_photo_count")
            ?.observe(viewLifecycleOwner) { limit ->
                if (limit != null && limit > 0) {
                    maxPhotoLimit = limit
                    if (selectedPhotos.size > limit) {
                        val toRemove = selectedPhotos.drop(limit)
                        selectedPhotos.removeAll(toRemove)
                        updatePhotosUi()
                    }
                }
            }
    }

    private fun updateMusicCoverUi(imageUrl: String?) {
        selectedMusicImageUrl = imageUrl
        if (!imageUrl.isNullOrBlank()) {
            binding.ivMusicCover.visibility = View.VISIBLE
            binding.ivMusicIcon.visibility = View.GONE
            binding.ivMusicCover.load(imageUrl) {
                crossfade(true)
                error(R.drawable.ic_music_note)
            }
        } else {
            binding.ivMusicCover.visibility = View.GONE
            binding.ivMusicIcon.visibility = View.VISIBLE
        }
    }

    private fun updatePhotosUi() {
        val count = selectedPhotos.size
        val density = resources.displayMetrics.density

        if (count == 0) {
            binding.tvPhotosSectionTitle.text = getString(R.string.memories_upload_title)
            binding.tvSelectedCount.visibility = View.GONE
            binding.btnAddPhotos.visibility = View.GONE
            binding.cardPhotoStackContainer.setBackgroundResource(R.drawable.bg_photo_box_empty)

            binding.llEmptyPhotosPrompt.visibility = View.VISIBLE
            binding.flPolaroidStack.visibility = View.GONE
        } else {
            binding.tvPhotosSectionTitle.text = getString(R.string.your_photos)
            binding.tvSelectedCount.visibility = View.VISIBLE
            binding.btnAddPhotos.visibility = View.VISIBLE
            binding.cardPhotoStackContainer.setBackgroundResource(R.drawable.bg_dashed_photo_box)

            binding.llEmptyPhotosPrompt.visibility = View.GONE
            binding.flPolaroidStack.visibility = View.VISIBLE
            binding.tvSelectedCount.text = getString(R.string.selected_x_photos, count)

            when (count) {
                1 -> {
                    // Only 1 photo selected: show 1 upright card centered
                    binding.cardPolaroidCenter.visibility = View.VISIBLE
                    binding.cardPolaroidCenter.translationX = 0f
                    binding.cardPolaroidCenter.rotation = 0f
                    binding.cardPolaroidCenter.cardElevation = 6 * density
                    binding.ivPolaroidCenter.load(selectedPhotos[0]) {
                        crossfade(true)
                    }

                    binding.cardPolaroidLeft.visibility = View.GONE
                    binding.cardPolaroidRight.visibility = View.GONE
                }
                2 -> {
                    // 2 photos selected: show 2 cards slightly fanned
                    binding.cardPolaroidLeft.visibility = View.VISIBLE
                    binding.cardPolaroidLeft.translationX = -32 * density
                    binding.cardPolaroidLeft.rotation = -6f
                    binding.cardPolaroidLeft.cardElevation = 3 * density
                    binding.ivPolaroidLeft.load(selectedPhotos[0]) {
                        crossfade(true)
                    }

                    binding.cardPolaroidCenter.visibility = View.VISIBLE
                    binding.cardPolaroidCenter.translationX = 32 * density
                    binding.cardPolaroidCenter.rotation = 6f
                    binding.cardPolaroidCenter.cardElevation = 6 * density
                    binding.ivPolaroidCenter.load(selectedPhotos[1]) {
                        crossfade(true)
                    }

                    binding.cardPolaroidRight.visibility = View.GONE
                }
                else -> {
                    // 3 or more photos: show 3 fanned cards (Image 1 style)
                    binding.cardPolaroidLeft.visibility = View.VISIBLE
                    binding.cardPolaroidLeft.translationX = -42 * density
                    binding.cardPolaroidLeft.rotation = -11f
                    binding.cardPolaroidLeft.cardElevation = 3 * density
                    binding.ivPolaroidLeft.load(selectedPhotos[1]) {
                        crossfade(true)
                    }

                    binding.cardPolaroidRight.visibility = View.VISIBLE
                    binding.cardPolaroidRight.translationX = 42 * density
                    binding.cardPolaroidRight.rotation = 11f
                    binding.cardPolaroidRight.cardElevation = 3 * density
                    binding.ivPolaroidRight.load(selectedPhotos[2]) {
                        crossfade(true)
                    }

                    binding.cardPolaroidCenter.visibility = View.VISIBLE
                    binding.cardPolaroidCenter.translationX = 0f
                    binding.cardPolaroidCenter.rotation = 0f
                    binding.cardPolaroidCenter.cardElevation = 8 * density
                    binding.ivPolaroidCenter.load(selectedPhotos[0]) {
                        crossfade(true)
                    }
                }
            }
        }
    }

    private fun updateRatioUi() {
        val activeColor = Color.parseColor("#4A6BF8")
        val inactiveColor = Color.parseColor("#506584")

        when (selectedAspectRatio) {
            "9:16" -> {
                binding.btnRatio916.setBackgroundResource(R.drawable.bg_aspect_ratio_selected)
                binding.ivRatio916.setColorFilter(activeColor)
                binding.tvRatio916.setTextColor(activeColor)

                binding.btnRatio11.setBackgroundResource(R.drawable.bg_aspect_ratio_unselected)
                binding.ivRatio11.setColorFilter(inactiveColor)
                binding.tvRatio11.setTextColor(inactiveColor)

                binding.btnRatio169.setBackgroundResource(R.drawable.bg_aspect_ratio_unselected)
                binding.ivRatio169.setColorFilter(inactiveColor)
                binding.tvRatio169.setTextColor(inactiveColor)
            }
            "1:1" -> {
                binding.btnRatio916.setBackgroundResource(R.drawable.bg_aspect_ratio_unselected)
                binding.ivRatio916.setColorFilter(inactiveColor)
                binding.tvRatio916.setTextColor(inactiveColor)

                binding.btnRatio11.setBackgroundResource(R.drawable.bg_aspect_ratio_selected)
                binding.ivRatio11.setColorFilter(activeColor)
                binding.tvRatio11.setTextColor(activeColor)

                binding.btnRatio169.setBackgroundResource(R.drawable.bg_aspect_ratio_unselected)
                binding.ivRatio169.setColorFilter(inactiveColor)
                binding.tvRatio169.setTextColor(inactiveColor)
            }
            "16:9" -> {
                binding.btnRatio916.setBackgroundResource(R.drawable.bg_aspect_ratio_unselected)
                binding.ivRatio916.setColorFilter(inactiveColor)
                binding.tvRatio916.setTextColor(inactiveColor)

                binding.btnRatio11.setBackgroundResource(R.drawable.bg_aspect_ratio_unselected)
                binding.ivRatio11.setColorFilter(inactiveColor)
                binding.tvRatio11.setTextColor(inactiveColor)

                binding.btnRatio169.setBackgroundResource(R.drawable.bg_aspect_ratio_selected)
                binding.ivRatio169.setColorFilter(activeColor)
                binding.tvRatio169.setTextColor(activeColor)
            }
        }
    }

    private fun openPhotoViewer(initialPosition: Int = 0) {
        if (selectedPhotos.isEmpty()) {
            pickMultipleImagesLauncher.launch("image/*")
            return
        }
        PhotoViewerDialog(
            context = requireContext(),
            photos = selectedPhotos,
            initialPosition = initialPosition,
            onPhotosChanged = {
                updatePhotosUi()
            },
            onAddMoreRequested = {
                if (selectedPhotos.size >= maxPhotoLimit) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_max_photos_limit, maxPhotoLimit),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    pickMultipleImagesLauncher.launch("image/*")
                }
            }
        ).show()
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnAddPhotos.setOnClickListener {
            if (selectedPhotos.size >= maxPhotoLimit) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_max_photos_limit, maxPhotoLimit),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                pickMultipleImagesLauncher.launch("image/*")
            }
        }

        binding.cardPhotoStackContainer.setOnClickListener {
            openPhotoViewer(0)
        }

        binding.cardPolaroidLeft.setOnClickListener {
            if (selectedPhotos.size == 2) {
                openPhotoViewer(0)
            } else if (selectedPhotos.size >= 3) {
                openPhotoViewer(1)
            }
        }

        binding.cardPolaroidCenter.setOnClickListener {
            if (selectedPhotos.size == 2) {
                openPhotoViewer(1)
            } else {
                openPhotoViewer(0)
            }
        }

        binding.cardPolaroidRight.setOnClickListener {
            if (selectedPhotos.size >= 3) {
                openPhotoViewer(2)
            }
        }

        binding.btnRatio916.setOnClickListener {
            selectedAspectRatio = "9:16"
            updateRatioUi()
        }

        binding.btnRatio11.setOnClickListener {
            selectedAspectRatio = "1:1"
            updateRatioUi()
        }

        binding.btnRatio169.setOnClickListener {
            selectedAspectRatio = "16:9"
            updateRatioUi()
        }

        binding.cardSelectMusic.setOnClickListener {
            findNavController().navigate(R.id.action_memoriesFragment_to_musicFragment)
        }

        binding.btnChangeMusic.setOnClickListener {
            findNavController().navigate(R.id.action_memoriesFragment_to_musicFragment)
        }

        binding.btnCreateVideo.setOnClickListener {
            if (selectedPhotos.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.toast_select_at_least_1_photo), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val stateHandle = findNavController().currentBackStackEntry?.savedStateHandle
            val finalTrack = selectedMusicTitle ?: stateHandle?.get<String>("selected_music_title")
            val finalAudioUrl = selectedMusicAudioUrl ?: stateHandle?.get<String?>("selected_music_audio_url")
            val finalAudioPath = selectedMusicAudioFilePath ?: stateHandle?.get<String?>("selected_music_file_path")
            val finalImageUrl = selectedMusicImageUrl ?: stateHandle?.get<String?>("selected_music_image_url")

            binding.btnCreateVideo.isEnabled = false
            showLoading(
                title = getString(R.string.loading_creating_video),
                subtitle = getString(R.string.loading_preparing_images)
            )

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // 1. Tải nhạc nền nếu cần
                    var resolvedAudioPath = finalAudioPath
                    val hasValidAudioFile = !resolvedAudioPath.isNullOrBlank() && File(resolvedAudioPath).let { it.exists() && it.length() > 0 }
                    if (!hasValidAudioFile && !finalAudioUrl.isNullOrBlank()) {
                        showLoading(
                            title = getString(R.string.loading_downloading_music),
                            subtitle = getString(R.string.loading_please_wait)
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
                    val videoDurationSec = templateDurationSec ?: VideoGenerator.calculateVideoDuration(photoCount)
                    val transitions = VideoGenerator.generateRandomTransitions(photoCount)
                    val photoUriStrings = selectedPhotos.map { it.toString() }

                    // Nguồn âm thanh (ưu tiên file nội bộ đã tải)
                    val audioSource = resolvedAudioPath?.takeIf { File(it).exists() } ?: finalAudioUrl

                    // 3. Render video MP4 hoàn chỉnh (ảnh + chuyển cảnh + ghép nhạc + tỷ lệ khung hình)
                    val generatedVideoFile = VideoGenerator.generateMp4FromPhotos(
                        context = requireContext(),
                        photoUris = photoUriStrings,
                        durationSec = videoDurationSec,
                        audioUrl = audioSource,
                        transitions = transitions,
                        aspectRatio = selectedAspectRatio,
                        onProgress = { progress, _ ->
                            showLoading(
                                title = getString(R.string.loading_creating_video),
                                subtitle = getString(R.string.loading_processing_progress, progress)
                            )
                        }
                    )

                    if (!isAdded || view == null) return@launch
                    hideLoading()
                    binding.btnCreateVideo.isEnabled = true

                    if (generatedVideoFile.exists() && generatedVideoFile.length() > 0) {
                        navigateToVideoResult(
                            videoPath = generatedVideoFile.absolutePath,
                            finalTrack = finalTrack,
                            finalAudioUrl = finalAudioUrl,
                            finalAudioPath = resolvedAudioPath,
                            finalImageUrl = finalImageUrl
                        )
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.toast_cannot_create_video_file), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    if (!isAdded || view == null) return@launch
                    hideLoading()
                    binding.btnCreateVideo.isEnabled = true
                    Toast.makeText(requireContext(), getString(R.string.toast_create_video_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
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
