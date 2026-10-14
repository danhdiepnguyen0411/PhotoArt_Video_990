package com.example.phortart_video_990.ui.screen.feature.memories

import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import android.content.Context
import android.util.Log
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MemoriesFragment : BaseFragment<FragmentMemoriesBinding>(FragmentMemoriesBinding::inflate) {

    companion object {
        private const val TAG = "PhotoArt_VideoGen"
        private const val SAVED_PHOTOS_KEY = "saved_selected_photos"
    }

    private val selectedPhotos = mutableListOf<Uri>()
    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private var selectedAspectRatio: String = "9:16"
    private var maxPhotoLimit: Int = 10
    private var templateDurationSec: Float? = null

    // Native Photo Picker launcher (Media Picker)
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
    private var generateVideoJob: kotlinx.coroutines.Job? = null
    private var pendingVideoResultBundle: Bundle? = null
    private var pendingVideoErrorMsg: String? = null

    private fun cancelVideoGeneration() {
        if (isGeneratingVideo) {
            isGeneratingVideo = false
            generateVideoJob?.cancel()
            generateVideoJob = null
            pendingVideoResultBundle = null
            pendingVideoErrorMsg = null
            hideLoading()
            bindingOrNull?.btnCreateVideo?.isEnabled = true
            context?.let { ctx ->
                Toast.makeText(ctx, getString(R.string.toast_video_cancelled), Toast.LENGTH_SHORT).show()
            }
        }
    }

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

    private fun copyUriToAppCache(context: Context, sourceUri: Uri): Uri {
        return try {
            val scheme = sourceUri.scheme
            if (scheme == "file") {
                val f = File(sourceUri.path ?: "")
                if (f.exists() && f.length() > 0) return sourceUri
            }
            val cacheDir = File(context.cacheDir, "memories_photos").apply { mkdirs() }
            val fileName = "photo_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
            val targetFile = File(cacheDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0) {
                Uri.fromFile(targetFile)
            } else {
                sourceUri
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sourceUri
        }
    }

    private fun handleSelectedPhotos(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            val context = requireContext()
            // Sao chép ngay ảnh về bộ nhớ cache cục bộ để giữ quyền truy cập vĩnh viễn
            val copiedUris = uris.map { copyUriToAppCache(context, it) }
            val newUris = copiedUris.filter { it !in selectedPhotos }
            val remainingSlots = maxPhotoLimit - selectedPhotos.size
            if (remainingSlots <= 0) {
                Toast.makeText(
                    context,
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
                    context,
                    getString(R.string.toast_max_photos_limit, maxPhotoLimit),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun initView() {
        binding.header.setTitleRes(R.string.memories_title)

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

        try {
            pickVisualMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (e: Exception) {
            try {
                fallbackGetMultipleContentsLauncher.launch("image/*")
            } catch (ex: Exception) {
                Toast.makeText(requireContext(), getString(R.string.toast_cannot_open_gallery), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun initListener() {
        binding.header.setOnBackClickListener {
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
                putString("selected_music_audio_url", selectedMusicAudioUrl)
            }
            findNavController().navigateSafe(R.id.action_memoriesFragment_to_musicFragment, bundle)
        }

        binding.btnChangeMusic.setOnClickListener {
            val bundle = android.os.Bundle().apply {
                putString("selected_track_id", selectedMusicTrackId)
                putString("selected_music_title", selectedMusicTitle)
                putString("selected_music_audio_url", selectedMusicAudioUrl)
            }
            findNavController().navigateSafe(R.id.action_memoriesFragment_to_musicFragment, bundle)
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isGeneratingVideo) {
                    cancelVideoGeneration()
                } else {
                    isEnabled = false
                    findNavController().safePopBackStack()
                }
            }
        })

        binding.btnCreateVideo.setOnClickListener {
            Log.i(TAG, "==========================================================")
            Log.i(TAG, ">>> [MEMORIES] User clicked Create Video button. Selected photos: ${selectedPhotos.size}")
            Log.i(TAG, "==========================================================")
            if (selectedPhotos.isEmpty()) {
                Log.w(TAG, "[MEMORIES] No photos selected!")
                Toast.makeText(requireContext(), getString(R.string.toast_select_at_least_1_photo), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val stateHandle = findNavController().currentBackStackEntry?.savedStateHandle
            val finalTrack = selectedMusicTitle ?: stateHandle?.get<String>("selected_music_title")
            val finalAudioUrl = selectedMusicAudioUrl ?: stateHandle?.get<String?>("selected_music_audio_url")
            val finalAudioPath = selectedMusicAudioFilePath ?: stateHandle?.get<String?>("selected_music_file_path")
            val finalImageUrl = selectedMusicImageUrl ?: stateHandle?.get<String?>("selected_music_image_url")

            Log.i(TAG, "[MEMORIES] Music params: track=$finalTrack, url=$finalAudioUrl, path=$finalAudioPath")

            isGeneratingVideo = true
            binding.btnCreateVideo.isEnabled = false
            showLoading(
                title = getString(R.string.loading_creating_video),
                subtitle = getString(R.string.loading_preparing_images),
                cancelable = true,
                onCancel = { cancelVideoGeneration() }
            )

            generateVideoJob?.cancel()
            generateVideoJob = lifecycleScope.launch {
                try {
                    // 1. Tải nhạc nền nếu cần
                    var resolvedAudioPath = finalAudioPath
                    val hasValidAudioFile = !resolvedAudioPath.isNullOrBlank() && File(resolvedAudioPath).let { it.exists() && it.length() > 0 }
                    if (!hasValidAudioFile && !finalAudioUrl.isNullOrBlank()) {
                        Log.i(TAG, "[MEMORIES] Preparing audio track: checking/downloading $finalAudioUrl")
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
                            Log.i(TAG, "[MEMORIES] Audio ready at: $resolvedAudioPath (${downloaded.length() / 1024} KB)")
                        } else {
                            Log.w(TAG, "[MEMORIES] Audio download failed or empty, will proceed without custom audio or stream directly")
                        }
                    }

                    kotlinx.coroutines.currentCoroutineContext().ensureActive()

                    // 2. Tính toán thời lượng và sinh hiệu ứng chuyển cảnh
                    val photoCount = selectedPhotos.size
                    val videoDurationSec = templateDurationSec ?: VideoGenerator.calculateVideoDuration(photoCount)
                    val transitions = VideoGenerator.generateRandomTransitions(photoCount)
                    val photoUriStrings = selectedPhotos.map { it.toString() }

                    // Nguồn âm thanh (ưu tiên file nội bộ đã tải)
                    val audioSource = resolvedAudioPath?.takeIf { File(it).exists() } ?: finalAudioUrl

                    Log.i(TAG, "MemoriesFragment: Invoking VideoGenerator for $photoCount photos, duration=${videoDurationSec}s, audio=$audioSource")

                    // 3. Render video MP4 hoàn chỉnh (ảnh + chuyển cảnh + ghép nhạc + tỷ lệ khung hình)
                    val generatedVideoFile = VideoGenerator.generateMp4FromPhotos(
                        context = requireContext(),
                        photoUris = photoUriStrings,
                        durationSec = videoDurationSec,
                        audioUrl = audioSource,
                        transitions = transitions,
                        aspectRatio = selectedAspectRatio,
                        onProgress = { progress, _ ->
                            if (isAdded && isGeneratingVideo) {
                                showLoading(
                                    title = getString(R.string.loading_creating_video),
                                    subtitle = getString(R.string.loading_processing_progress, progress)
                                )
                            }
                        }
                    )

                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (!isGeneratingVideo) return@launch

                    isGeneratingVideo = false

                    if (generatedVideoFile.exists() && generatedVideoFile.length() > 0) {
                        Log.i(TAG, "MemoriesFragment: Video generated successfully at: ${generatedVideoFile.absolutePath} (${generatedVideoFile.length() / 1024} KB)")
                        val bundle = VideoResultFragment.createBundle(
                            videoPath = generatedVideoFile.absolutePath,
                            photos = selectedPhotos.map { it.toString() },
                            track = finalTrack,
                            audioUrl = finalAudioUrl,
                            audioPath = resolvedAudioPath,
                            imageUrl = finalImageUrl,
                            trackId = selectedMusicTrackId
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
                        Log.e(TAG, "MemoriesFragment: Video file is empty or missing: ${generatedVideoFile.absolutePath}")
                        if (isResumed && isAdded) {
                            hideLoading()
                            bindingOrNull?.btnCreateVideo?.isEnabled = true
                            Toast.makeText(requireContext(), getString(R.string.toast_cannot_create_video_file), Toast.LENGTH_SHORT).show()
                        } else {
                            pendingVideoErrorMsg = getString(R.string.toast_cannot_create_video_file)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Người dùng đã nhấn hủy/thoát: không hiển thị lỗi và không chuyển màn hình
                    Log.i(TAG, "MemoriesFragment: Video generation cancelled by user.")
                    isGeneratingVideo = false
                    hideLoading()
                    bindingOrNull?.btnCreateVideo?.isEnabled = true
                } catch (e: Exception) {
                    Log.e(TAG, "MemoriesFragment: Video generation failed with exception: ${e.message}", e)
                    if (!isGeneratingVideo) return@launch
                    isGeneratingVideo = false
                    if (isResumed && isAdded) {
                        hideLoading()
                        bindingOrNull?.btnCreateVideo?.isEnabled = true
                        Toast.makeText(requireContext(), getString(R.string.toast_create_video_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                    } else {
                        pendingVideoErrorMsg = e.message ?: ""
                    }
                } finally {
                    generateVideoJob = null
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

    override fun onDestroyView() {
        if (isGeneratingVideo) {
            generateVideoJob?.cancel()
            generateVideoJob = null
            isGeneratingVideo = false
        }
        hideLoading()
        super.onDestroyView()
    }
}
