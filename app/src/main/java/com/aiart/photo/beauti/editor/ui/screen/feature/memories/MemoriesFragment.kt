package com.aiart.photo.beauti.editor.ui.screen.feature.memories

import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.core.base.BaseFragment
import com.aiart.photo.beauti.editor.core.utils.AudioCacheManager
import com.aiart.photo.beauti.editor.core.utils.ClickDebouncer
import com.aiart.photo.beauti.editor.core.utils.ErrorFormatter
import com.aiart.photo.beauti.editor.core.utils.VideoGenerator
import com.aiart.photo.beauti.editor.core.utils.navigateSafe
import com.aiart.photo.beauti.editor.core.utils.safePopBackStack
import com.aiart.photo.beauti.editor.core.utils.setOnSingleClickListener
import com.aiart.photo.beauti.editor.data.local.PreferencesManager
import com.aiart.photo.beauti.editor.data.model.CategoryModel
import com.aiart.photo.beauti.editor.data.model.TemplateModel
import com.aiart.photo.beauti.editor.data.repository.HistoryRepository
import com.aiart.photo.beauti.editor.data.repository.TemplateRepository
import com.aiart.photo.beauti.editor.databinding.FragmentMemoriesBinding
import com.aiart.photo.beauti.editor.ui.screen.feature.music.MusicCategoryAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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
    private val templateRepository by lazy { TemplateRepository() }

    private var selectedAspectRatio: String = "9:16"
    private var maxPhotoLimit: Int = 10
    private var templateDurationSec: Float? = null
    private var activeBottomSheetDialog: SelectedPhotosBottomSheetDialog? = null

    // Native Photo Picker launcher
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

    // Music data & adapters
    private lateinit var musicCategoryAdapter: MusicCategoryAdapter
    private lateinit var memoriesMusicAdapter: MemoriesMusicAdapter
    private var musicCategories: List<CategoryModel> = listOf(CategoryModel.ALL)
    private var allMusicTracks: List<TemplateModel> = emptyList()
    private var selectedCategoryCode: String = "ALL"

    private var selectedMusicTrackId: String? = null
    private var selectedMusicTitle: String? = null
    private var selectedMusicAudioUrl: String? = null
    private var selectedMusicAudioFilePath: String? = null
    private var selectedMusicImageUrl: String? = null

    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingTrackId: String? = null
    private var audioActionJob: Job? = null

    private var isGeneratingVideo = false
    private var generateVideoJob: Job? = null
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
        pendingVideoResultBundle?.let { bundle ->
            pendingVideoResultBundle = null
            hideLoading()
            bindingOrNull?.btnCreateVideo?.isEnabled = true
            findNavController().navigateSafe(R.id.action_memoriesFragment_to_videoResultFragment, bundle)
            return
        }

        pendingVideoErrorMsg?.let { errorMsg ->
            pendingVideoErrorMsg = null
            hideLoading()
            bindingOrNull?.btnCreateVideo?.isEnabled = true
            context?.let { ctx ->
                val friendly = ErrorFormatter.formatVideoErrorMessage(ctx, errorMsg)
                Toast.makeText(ctx, getString(R.string.toast_create_video_failed, friendly), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopPreviewAudio()
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
        setupMusicAdapters()
        loadMusicData()

        val args = arguments
        val initialTrackId = args?.getString("selected_track_id")?.takeIf { it.isNotBlank() }
            ?: stateHandle?.get<String>("selected_track_id")?.takeIf { it.isNotBlank() }
        if (!initialTrackId.isNullOrBlank()) {
            selectedMusicTrackId = initialTrackId
        }

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
    }

    private fun setupMusicAdapters() {
        musicCategoryAdapter = MusicCategoryAdapter(
            categories = musicCategories,
            selectedCategoryCode = selectedCategoryCode
        ) { category ->
            selectedCategoryCode = category.code
            applyCategoryFilter()
        }
        binding.rvMusicCategories.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvMusicCategories.adapter = musicCategoryAdapter

        memoriesMusicAdapter = MemoriesMusicAdapter(
            tracks = emptyList(),
            selectedTrackId = selectedMusicTrackId,
            onTrackSelected = { track ->
                selectTrack(track)
            },
            onPlayToggle = { track ->
                toggleTrackPlayback(track)
            }
        )
        binding.rvMusicTracks.layoutManager = object : LinearLayoutManager(requireContext(), VERTICAL, false) {
            override fun canScrollVertically(): Boolean = false
        }
        binding.rvMusicTracks.isNestedScrollingEnabled = false
        binding.rvMusicTracks.setHasFixedSize(false)
        binding.rvMusicTracks.adapter = memoriesMusicAdapter
    }

    private fun loadMusicData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val tracks = templateRepository.getMusicTracks()
                val validTracks = if (tracks.isNotEmpty()) {
                    tracks
                } else {
                    templateRepository.getDefaultMusicTracks()
                }
                allMusicTracks = validTracks

                // Extract dynamic music categories with "ALL" first, matching the Video section
                val dynamicMusicCats = mutableListOf(
                    CategoryModel(
                        code = "ALL",
                        name = "Tất cả",
                        groupCode = "MUSIC",
                        active = true
                    )
                )
                val seenMusicCats = mutableSetOf("all", "tất cả")
                validTracks.forEach { track ->
                    val rawCat = track.category.trim()
                    if (rawCat.isNotBlank()) {
                        val key = rawCat.lowercase()
                        if (seenMusicCats.add(key)) {
                            val displayName = track.categoryDisplayName.ifBlank {
                                val clean = if (rawCat.startsWith("MUSIC_", ignoreCase = true)) rawCat.substring(6) else rawCat
                                clean.replace("_", " ").split(" ")
                                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                            }
                            dynamicMusicCats.add(
                                CategoryModel(
                                    code = rawCat,
                                    name = displayName,
                                    groupCode = "MUSIC",
                                    active = true
                                )
                            )
                        }
                    }
                }

                if (dynamicMusicCats.size > 1) {
                    musicCategories = dynamicMusicCats
                    musicCategoryAdapter.submitList(dynamicMusicCats)
                } else {
                    val fallbackCats = templateRepository.getMusicCategories()
                    musicCategories = fallbackCats
                    musicCategoryAdapter.submitList(fallbackCats)
                }

                // Restore or select default track
                val ctx = context
                val savedTrackId = selectedMusicTrackId
                    ?: (if (ctx != null) PreferencesManager.getInstance(ctx).lastSelectedMusicTrackId else null)
                val trackToSelect = allMusicTracks.find { it.id == savedTrackId }
                    ?: allMusicTracks.firstOrNull()

                if (trackToSelect != null) {
                    selectTrack(trackToSelect, rememberPref = false)
                }

                applyCategoryFilter()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading music in MemoriesFragment: ${e.message}", e)
                val fallbackTracks = templateRepository.getDefaultMusicTracks()
                allMusicTracks = fallbackTracks
                if (fallbackTracks.isNotEmpty()) {
                    selectTrack(fallbackTracks.first(), rememberPref = false)
                }
                applyCategoryFilter()
            }
        }
    }

    private fun applyCategoryFilter() {
        val isAll = selectedCategoryCode.isBlank() ||
                selectedCategoryCode.equals("ALL", ignoreCase = true) ||
                selectedCategoryCode.equals("Tất cả", ignoreCase = true)

        val filtered = if (isAll) {
            allMusicTracks
        } else {
            val target = selectedCategoryCode.trim().lowercase()
            val cleanTarget = target.removePrefix("music_")
            allMusicTracks.filter { item ->
                val itemCat = item.category.trim().lowercase()
                val itemType = item.templateType?.trim()?.lowercase().orEmpty()
                itemCat == target ||
                        itemCat.removePrefix("music_") == cleanTarget ||
                        itemCat.contains(cleanTarget) ||
                        itemType == target ||
                        itemType.contains(cleanTarget)
            }
        }
        memoriesMusicAdapter.submitList(filtered)
        binding.rvMusicTracks.post {
            binding.rvMusicTracks.requestLayout()
            binding.nestedScrollView.requestLayout()
        }
    }

    private fun selectTrack(track: TemplateModel, rememberPref: Boolean = true) {
        selectedMusicTrackId = track.id
        selectedMusicTitle = track.title
        selectedMusicAudioUrl = track.safeAudioUrl
        selectedMusicImageUrl = track.safeImageUrl
        selectedMusicAudioFilePath = null

        memoriesMusicAdapter.setSelectedTrackId(track.id)

        if (rememberPref) {
            context?.let { ctx ->
                PreferencesManager.getInstance(ctx).lastSelectedMusicTrackId = track.id
            }
        }
    }

    private fun toggleTrackPlayback(track: TemplateModel) {
        if (currentlyPlayingTrackId == track.id) {
            stopPreviewAudio()
        } else {
            selectTrack(track)
            playPreviewAudio(track)
        }
    }

    private fun playPreviewAudio(track: TemplateModel) {
        val audioUrl = track.safeAudioUrl
        if (audioUrl.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.toast_music_preview_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        stopPreviewAudio()

        currentlyPlayingTrackId = track.id
        memoriesMusicAdapter.setLoadingState(track.id)

        audioActionJob = viewLifecycleOwner.lifecycleScope.launch {
            val ctx = context ?: return@launch
            val cachedFile = AudioCacheManager.getOrDownloadAudio(ctx, audioUrl)

            if (!isAdded || view == null || bindingOrNull == null) return@launch

            if (cachedFile == null || !cachedFile.exists() || cachedFile.length() == 0L) {
                memoriesMusicAdapter.setLoadingState(null)
                currentlyPlayingTrackId = null
                Toast.makeText(ctx, getString(R.string.toast_cannot_download_preview), Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(cachedFile.absolutePath)
                    isLooping = true
                    prepare()
                    start()
                }
                memoriesMusicAdapter.setPlayingState(track.id, true)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing preview audio: ${e.message}", e)
                memoriesMusicAdapter.setPlayingState(null, false)
                currentlyPlayingTrackId = null
            }
        }
    }

    private fun stopPreviewAudio() {
        audioActionJob?.cancel()
        audioActionJob = null
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
        currentlyPlayingTrackId = null
        if (::memoriesMusicAdapter.isInitialized) {
            memoriesMusicAdapter.setPlayingState(null, false)
        }
    }

    private fun updateEmptyPhotoDescription() {
        val b = bindingOrNull ?: return
        b.tvEmptyPhotoDescription.text = getString(R.string.dance_upload_card_desc)
    }

    private fun updatePhotosUi() {
        val count = selectedPhotos.size
        val density = resources.displayMetrics.density

        val hasPhotos = count > 0
        binding.btnCreateVideo.alpha = if (hasPhotos) 1.0f else 0.5f

        if (count == 0) {
            binding.tvPhotosSectionTitle.text = getString(R.string.memories_upload_title)
            binding.tvSelectedCount.visibility = View.GONE
            binding.btnAddPhotos.visibility = View.GONE
            binding.cardPhotoStackContainer.setBackgroundResource(R.drawable.bg_photo_box_empty)

            binding.llEmptyPhotosPrompt.visibility = View.VISIBLE
            binding.flPolaroidStack.visibility = View.GONE
            updateEmptyPhotoDescription()
        } else {
            binding.tvPhotosSectionTitle.text = getString(R.string.memories_upload_title)
            binding.tvSelectedCount.visibility = View.GONE
            binding.btnAddPhotos.visibility = View.GONE
            binding.cardPhotoStackContainer.setBackgroundResource(R.drawable.bg_dashed_photo_box)

            binding.llEmptyPhotosPrompt.visibility = View.GONE
            binding.flPolaroidStack.visibility = View.VISIBLE

            when (count) {
                1 -> {
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

    private var lastPickerActionTime = 0L
    private fun canTriggerPickerAction(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPickerActionTime < 500L) return false
        lastPickerActionTime = now
        return true
    }

    private fun onUploadAreaClicked() {
        if (!canTriggerPickerAction()) return
        if (selectedPhotos.isEmpty()) {
            launchImagePicker()
        } else {
            showSelectedPhotosBottomSheet()
        }
    }

    private fun showSelectedPhotosBottomSheet() {
        if (!isAdded || isStateSaved) return
        if (activeBottomSheetDialog?.isShowing == true) return

        val dialog = SelectedPhotosBottomSheetDialog(
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
        )
        activeBottomSheetDialog = dialog
        dialog.setOnDismissListener {
            if (activeBottomSheetDialog == dialog) {
                activeBottomSheetDialog = null
            }
        }
        dialog.show()
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
            if (!canTriggerPickerAction()) return@setOnClickListener
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
            Log.i(TAG, ">>> [MEMORIES] User clicked Preview / Create Video button. Selected photos: ${selectedPhotos.size}")
            Log.i(TAG, "==========================================================")
            if (selectedPhotos.isEmpty()) {
                Log.w(TAG, "[MEMORIES] No photos selected!")
                Toast.makeText(requireContext(), getString(R.string.toast_select_at_least_1_photo), Toast.LENGTH_SHORT).show()
                launchImagePicker()
                return@setOnClickListener
            }

            stopPreviewAudio()

            val finalTrack = selectedMusicTitle
            val finalAudioUrl = selectedMusicAudioUrl
            val finalAudioPath = selectedMusicAudioFilePath
            val finalImageUrl = selectedMusicImageUrl

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
                        val downloaded = AudioCacheManager.getOrDownloadAudio(
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

                    val audioSource = resolvedAudioPath?.takeIf { File(it).exists() } ?: finalAudioUrl

                    Log.i(TAG, "MemoriesFragment: Invoking VideoGenerator for $photoCount photos, duration=${videoDurationSec}s, audio=$audioSource, ratio=$selectedAspectRatio")

                    // 3. Render video MP4 hoàn chỉnh (fitCenter + blurred background + audio)
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
                        val friendly = ErrorFormatter.formatVideoErrorMessage(requireContext(), e.message)
                        Toast.makeText(requireContext(), getString(R.string.toast_create_video_failed, friendly), Toast.LENGTH_SHORT).show()
                    } else {
                        pendingVideoErrorMsg = e.message ?: ""
                    }
                } finally {
                    generateVideoJob = null
                }
            }
        }
    }

    override fun onDestroyView() {
        activeBottomSheetDialog?.dismiss()
        activeBottomSheetDialog = null
        stopPreviewAudio()
        if (isGeneratingVideo) {
            generateVideoJob?.cancel()
            generateVideoJob = null
            isGeneratingVideo = false
        }
        hideLoading()
        super.onDestroyView()
    }
}
