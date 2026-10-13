package com.example.phortart_video_990.ui.screen.feature.memories

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.core.utils.safePopBackStack
import com.example.phortart_video_990.core.utils.VideoGenerator
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.databinding.FragmentMemoriesBinding
import kotlinx.coroutines.launch
import java.io.File

class MemoriesFragment : BaseFragment<FragmentMemoriesBinding>(FragmentMemoriesBinding::inflate) {

    companion object {
        private const val SAVED_PHOTOS_KEY = "saved_selected_photos"
    }

    private val selectedPhotos = mutableListOf<Uri>()
    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private var selectedAspectRatio: String = "9:16"
    private var maxPhotoLimit: Int = 10
    private var templateDurationSec: Float? = null

    // Permission launcher for accessing device gallery
    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_IMAGES] == true ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    permissions[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true)
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }

        if (granted) {
            showImagePickerBottomSheet()
        } else {
            // Fallback to system photo picker if permission is denied
            try {
                pickVisualMediaLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } catch (e: Exception) {
                fallbackGetMultipleContentsLauncher.launch("image/*")
            }
        }
    }

    // Native Photo Picker bottom sheet launcher (fallback)
    private val pickVisualMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        handleSelectedPhotos(uris)
    }

    // Fallback launcher for devices without Photo Picker support
    private val fallbackGetMultipleContentsLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        handleSelectedPhotos(uris)
    }

    private var selectedMusicTrackId: String? = null
    private var selectedMusicTitle: String? = null
    private var selectedMusicAudioUrl: String? = null
    private var selectedMusicAudioFilePath: String? = null
    private var selectedMusicImageUrl: String? = null

    private var isGeneratingVideo = false
    private var pendingVideoResultBundle: Bundle? = null
    private var pendingVideoErrorMsg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedUris = savedInstanceState?.getStringArrayList(SAVED_PHOTOS_KEY)
        if (selectedPhotos.isEmpty() && !savedUris.isNullOrEmpty()) {
            selectedPhotos.addAll(savedUris.map { Uri.parse(it) })
        }
        savedInstanceState?.getString("selected_aspect_ratio")?.let {
            selectedAspectRatio = it
        }
        val savedLimit = savedInstanceState?.getInt("max_photo_limit", 0) ?: 0
        if (savedLimit > 0) {
            maxPhotoLimit = savedLimit
        }
        savedInstanceState?.getString("selected_music_track_id")?.let {
            selectedMusicTrackId = it
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(SAVED_PHOTOS_KEY, ArrayList(selectedPhotos.map { it.toString() }))
        outState.putString("selected_aspect_ratio", selectedAspectRatio)
        outState.putInt("max_photo_limit", maxPhotoLimit)
        outState.putString("selected_music_track_id", selectedMusicTrackId)
    }

    override fun onResume() {
        super.onResume()
        // If video finished generating while the app was in the background, navigate now safely!
        pendingVideoResultBundle?.let { bundle ->
            pendingVideoResultBundle = null
            hideLoading()
            bindingOrNull?.btnCreateVideo?.isEnabled = true
            findNavController().navigateSafe(R.id.action_memoriesFragment_to_videoResultFragment, bundle)
            return
        }

        // If an error occurred while the app was in the background, show error toast now!
        pendingVideoErrorMsg?.let { errorMsg ->
            pendingVideoErrorMsg = null
            hideLoading()
            bindingOrNull?.btnCreateVideo?.isEnabled = true
            context?.let { ctx ->
                Toast.makeText(ctx, getString(R.string.toast_create_video_failed, errorMsg), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun savePhotosState() {
        val uriStrings = ArrayList(selectedPhotos.map { it.toString() })
        findNavController().currentBackStackEntry?.savedStateHandle?.set(SAVED_PHOTOS_KEY, uriStrings)
    }

    private fun handleSelectedPhotos(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            val newUris = uris.filter { it !in selectedPhotos }
            val remainingSlots = maxPhotoLimit - selectedPhotos.size
            if (remainingSlots <= 0) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_max_photos_limit, maxPhotoLimit),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            val toAdd = newUris.take(remainingSlots)
            if (toAdd.isNotEmpty()) {
                selectedPhotos.addAll(toAdd)
                savePhotosState()
                updatePhotosUi()
            }
            if (newUris.size > remainingSlots) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_max_photos_limit, maxPhotoLimit),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun initView() {
        binding.tvTopTitle.text = getString(R.string.memories_title)

        // Restore uploaded photos if previously saved
        val stateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        val restoredUris = stateHandle?.get<ArrayList<String>>(SAVED_PHOTOS_KEY)
            ?: arguments?.getStringArrayList(SAVED_PHOTOS_KEY)
        if (selectedPhotos.isEmpty() && !restoredUris.isNullOrEmpty()) {
            selectedPhotos.addAll(restoredUris.map { Uri.parse(it) })
        }

        updatePhotosUi()
        updateRatioUi()

        // Read music selection from arguments or savedStateHandle
        val args = arguments

        val initialTrackId = args?.getString("selected_track_id")?.takeIf { it.isNotBlank() }
            ?: stateHandle?.get<String>("selected_track_id")?.takeIf { it.isNotBlank() }
        if (!initialTrackId.isNullOrBlank()) {
            selectedMusicTrackId = initialTrackId
        }

        val initialTitle = args?.getString("selected_music_title")?.takeIf { it.isNotBlank() }
            ?: stateHandle?.get<String>("selected_music_title")?.takeIf { it.isNotBlank() }
        val initialSubtitle = args?.getString("selected_music_subtitle")?.takeIf { it.isNotBlank() }
            ?: stateHandle?.get<String>("selected_music_subtitle")?.takeIf { it.isNotBlank() }
        if (!initialTitle.isNullOrBlank()) {
            selectedMusicTitle = initialTitle
            binding.tvSelectedMusicTitle.text = initialTitle
            binding.tvSelectedMusicSubtitle.text = initialSubtitle ?: "04:17"
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
        updateEmptyPhotoDescription()

        // Observe returned music selection from MusicFragment
        stateHandle?.getLiveData<String>("selected_track_id")
            ?.observe(viewLifecycleOwner) { trackId ->
                if (!trackId.isNullOrBlank()) {
                    selectedMusicTrackId = trackId
                }
            }

        stateHandle?.getLiveData<String>("selected_music_title")
            ?.observe(viewLifecycleOwner) { musicTitle ->
                if (!musicTitle.isNullOrBlank()) {
                    selectedMusicTitle = musicTitle
                    binding.tvSelectedMusicTitle.text = musicTitle
                }
            }

        stateHandle?.getLiveData<String>("selected_music_subtitle")
            ?.observe(viewLifecycleOwner) { subtitle ->
                if (!subtitle.isNullOrBlank()) {
                    binding.tvSelectedMusicSubtitle.text = subtitle
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
                        savePhotosState()
                        updatePhotosUi()
                    }
                    updateEmptyPhotoDescription()
                }
            }
    }

    private fun updateEmptyPhotoDescription() {
        val b = bindingOrNull ?: return
        b.tvEmptyPhotoDescription.text = getString(R.string.select_max_photos_format, maxPhotoLimit)
    }

    private fun updateMusicCoverUi(imageUrl: String?) {
        selectedMusicImageUrl = imageUrl
        val b = bindingOrNull ?: return
        if (!imageUrl.isNullOrBlank()) {
            b.ivMusicCover.visibility = View.VISIBLE
            b.ivMusicIcon.visibility = View.GONE
            b.ivMusicCover.load(imageUrl) {
                crossfade(true)
                error(R.drawable.ic_music_note)
            }
        } else {
            b.ivMusicCover.visibility = View.GONE
            b.ivMusicIcon.visibility = View.VISIBLE
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
            updateEmptyPhotoDescription()
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
        val activeColor = Color.parseColor("#8489F8")
        val inactiveColor = Color.parseColor("#64748B")

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

    private fun onUploadAreaClicked() {
        if (selectedPhotos.isEmpty()) {
            launchImagePicker()
        } else {
            showSelectedPhotosBottomSheet()
        }
    }

    private fun showSelectedPhotosBottomSheet() {
        SelectedPhotosBottomSheetDialog(
            context = requireContext(),
            photos = selectedPhotos,
            maxLimit = maxPhotoLimit,
            onPhotosChanged = {
                savePhotosState()
                updatePhotosUi()
            },
            onAddMoreRequested = {
                launchImagePicker()
            }
        ).show()
    }

    private fun launchImagePicker() {
        val remainingSlots = maxPhotoLimit - selectedPhotos.size
        if (remainingSlots <= 0) {
            Toast.makeText(
                requireContext(),
                getString(R.string.toast_max_photos_limit, maxPhotoLimit),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val context = requireContext()
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            showImagePickerBottomSheet()
        } else {
            val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            requestGalleryPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun showImagePickerBottomSheet() {
        ImagePickerBottomSheetDialog(
            context = requireContext(),
            initialSelectedPhotos = selectedPhotos,
            maxLimit = maxPhotoLimit,
            onPhotosSelected = { newlySelected ->
                selectedPhotos.clear()
                selectedPhotos.addAll(newlySelected)
                savePhotosState()
                updatePhotosUi()
            }
        ).show()
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().safePopBackStack()
        }

        binding.btnAddPhotos.setOnClickListener {
            if (selectedPhotos.size >= maxPhotoLimit) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_max_photos_limit, maxPhotoLimit),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                launchImagePicker()
            }
        }

        binding.cardPhotoStackContainer.setOnClickListener {
            onUploadAreaClicked()
        }

        binding.cardPolaroidLeft.setOnClickListener {
            onUploadAreaClicked()
        }

        binding.cardPolaroidCenter.setOnClickListener {
            onUploadAreaClicked()
        }

        binding.cardPolaroidRight.setOnClickListener {
            onUploadAreaClicked()
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
            val bundle = android.os.Bundle().apply {
                putString("selected_track_id", selectedMusicTrackId)
                putString("selected_music_title", selectedMusicTitle)
            }
            findNavController().navigateSafe(R.id.action_memoriesFragment_to_musicFragment, bundle)
        }

        binding.btnChangeMusic.setOnClickListener {
            val bundle = android.os.Bundle().apply {
                putString("selected_track_id", selectedMusicTrackId)
                putString("selected_music_title", selectedMusicTitle)
            }
            findNavController().navigateSafe(R.id.action_memoriesFragment_to_musicFragment, bundle)
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

            isGeneratingVideo = true
            binding.btnCreateVideo.isEnabled = false
            showLoading(
                title = getString(R.string.loading_creating_video),
                subtitle = getString(R.string.loading_preparing_images)
            )

            lifecycleScope.launch {
                try {
                    // 1. Tải nhạc nền nếu cần
                    var resolvedAudioPath = finalAudioPath
                    val hasValidAudioFile = !resolvedAudioPath.isNullOrBlank() && File(resolvedAudioPath).let { it.exists() && it.length() > 0 }
                    if (!hasValidAudioFile && !finalAudioUrl.isNullOrBlank()) {
                        if (isAdded) {
                            showLoading(
                                title = getString(R.string.loading_downloading_music),
                                subtitle = getString(R.string.loading_please_wait)
                            )
                        }
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
                            if (isAdded) {
                                showLoading(
                                    title = getString(R.string.loading_creating_video),
                                    subtitle = getString(R.string.loading_processing_progress, progress)
                                )
                            }
                        }
                    )

                    isGeneratingVideo = false

                    if (generatedVideoFile.exists() && generatedVideoFile.length() > 0) {
                        val bundle = VideoResultFragment.createBundle(
                            videoPath = generatedVideoFile.absolutePath,
                            photos = selectedPhotos.map { it.toString() },
                            track = finalTrack,
                            audioUrl = finalAudioUrl,
                            audioPath = resolvedAudioPath,
                            imageUrl = finalImageUrl
                        )

                        if (isResumed && isAdded) {
                            hideLoading()
                            bindingOrNull?.btnCreateVideo?.isEnabled = true
                            findNavController().navigateSafe(R.id.action_memoriesFragment_to_videoResultFragment, bundle)
                        } else {
                            // Defer navigation until app comes back to foreground
                            pendingVideoResultBundle = bundle
                        }
                    } else {
                        if (isResumed && isAdded) {
                            hideLoading()
                            bindingOrNull?.btnCreateVideo?.isEnabled = true
                            Toast.makeText(requireContext(), getString(R.string.toast_cannot_create_video_file), Toast.LENGTH_SHORT).show()
                        } else {
                            pendingVideoErrorMsg = getString(R.string.toast_cannot_create_video_file)
                        }
                    }
                } catch (e: Exception) {
                    isGeneratingVideo = false
                    if (isResumed && isAdded) {
                        hideLoading()
                        bindingOrNull?.btnCreateVideo?.isEnabled = true
                        Toast.makeText(requireContext(), getString(R.string.toast_create_video_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                    } else {
                        pendingVideoErrorMsg = e.message ?: ""
                    }
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
        if (isResumed && isAdded) {
            hideLoading()
            bindingOrNull?.btnCreateVideo?.isEnabled = true
            findNavController().navigateSafe(R.id.action_memoriesFragment_to_videoResultFragment, bundle)
        } else {
            pendingVideoResultBundle = bundle
        }
    }
}
