package com.aiart.photo.beauti.editor.ui.screen.feature.createtemplate

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.core.base.BaseFragment
import com.aiart.photo.beauti.editor.core.dialog.AppLoadingDialog
import com.aiart.photo.beauti.editor.core.utils.AudioCacheManager
import com.aiart.photo.beauti.editor.core.utils.ClickDebouncer
import com.aiart.photo.beauti.editor.core.utils.ErrorFormatter
import com.aiart.photo.beauti.editor.core.utils.ImageCompressor
import com.aiart.photo.beauti.editor.core.utils.VideoGenerator
import com.aiart.photo.beauti.editor.core.utils.navigateSafe
import com.aiart.photo.beauti.editor.core.utils.safePopBackStack
import com.aiart.photo.beauti.editor.core.utils.setOnSingleClickListener
import com.aiart.photo.beauti.editor.data.model.CategoryModel
import com.aiart.photo.beauti.editor.data.model.HistoryItemModel
import com.aiart.photo.beauti.editor.data.model.TemplateModel
import com.aiart.photo.beauti.editor.data.repository.HistoryRepository
import com.aiart.photo.beauti.editor.data.repository.TemplateRepository
import com.aiart.photo.beauti.editor.databinding.FragmentCreateTemplateVideoBinding
import com.aiart.photo.beauti.editor.ui.screen.template.CategoryAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CreateTemplateVideoFragment : BaseFragment<FragmentCreateTemplateVideoBinding>(
    FragmentCreateTemplateVideoBinding::inflate
) {

    private val templateRepository by lazy { TemplateRepository() }
    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    // Selected photo
    private var selectedPhotoUri: Uri? = null

    // Selected template
    private var templateCode: String = ""
    private var templateTitle: String = ""
    private var templateCategory: String = ""
    private var templateThumbnailUrl: String = ""
    private var templateType: String = ""
    private var userChangedMusic: Boolean = false
    private var isInitialArgumentsConsumed: Boolean = false

    // Selected music
    private var selectedMusicTrackId: String? = null
    private var selectedMusicTitle: String? = null
    private var selectedMusicAudioUrl: String? = null
    private var selectedMusicAudioFilePath: String? = null
    private var selectedMusicImageUrl: String? = null

    // Adapters
    private lateinit var templateCategoryAdapter: CategoryAdapter
    private lateinit var danceTemplateAdapter: DanceTemplatePickerAdapter
    private lateinit var musicCategoryAdapter: CategoryAdapter
    private lateinit var danceMusicAdapter: DanceMusicPickerAdapter

    // Cached data
    private var cachedDancingTemplates: List<TemplateModel> = emptyList()
    private var cachedMusicTracks: List<TemplateModel> = emptyList()
    private var selectedTemplateCatCode: String = "ALL"
    private var selectedMusicCatCode: String = "ALL"

    // Audio preview player
    private var mediaPlayer: MediaPlayer? = null
    private var activePlayingTrackId: String? = null
    private var previewJob: Job? = null

    // Video generation state
    private var loadingDialog: AppLoadingDialog? = null
    private var isGeneratingVideo = false
    private var generateVideoJob: Job? = null
    private var pendingResultBundle: Bundle? = null
    private var pendingErrorMsg: String? = null

    // Native Photo Picker launcher
    private val pickSingleVisualMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = copyUriToAppCache(uri)
            updateSelectedPhotoUi()
        }
    }

    private val fallbackSingleImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = copyUriToAppCache(uri)
            updateSelectedPhotoUi()
        }
    }

    private var lastPickerActionTime: Long = 0L
    private fun launchSinglePhotoPicker() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPickerActionTime < 500L) return
        lastPickerActionTime = now
        try {
            pickSingleVisualMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (_: Exception) {
            try {
                fallbackSingleImageLauncher.launch("image/*")
            } catch (_: Exception) {
                Toast.makeText(requireContext(), getString(R.string.toast_cannot_open_gallery), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyUriToAppCache(uri: Uri): Uri {
        return try {
            val scheme = uri.scheme
            if (scheme == "file") {
                val f = File(uri.path ?: "")
                if (f.exists() && f.length() > 0) return uri
            }
            val cacheDir = File(requireContext().cacheDir, "dance_photos").apply { mkdirs() }
            val fileName = "photo_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
            val targetFile = File(cacheDir, fileName)

            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0) {
                Uri.fromFile(targetFile)
            } else {
                uri
            }
        } catch (e: Exception) {
            e.printStackTrace()
            uri
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString("saved_photo_uri")?.let {
            selectedPhotoUri = Uri.parse(it)
        }
        savedInstanceState?.getString("saved_template_code")?.let {
            if (it.isNotBlank()) templateCode = it
        }
        savedInstanceState?.getString("saved_template_title")?.let {
            if (it.isNotBlank()) templateTitle = it
        }
        savedInstanceState?.getString("saved_template_category")?.let {
            if (it.isNotBlank()) {
                templateCategory = it
                selectedTemplateCatCode = it
            }
        }
        savedInstanceState?.getString("saved_template_thumb")?.let {
            if (it.isNotBlank()) templateThumbnailUrl = it
        }
        savedInstanceState?.getString("saved_template_type")?.let {
            if (it.isNotBlank()) templateType = it
        }
        savedInstanceState?.getString("saved_music_id")?.let {
            if (it.isNotBlank()) selectedMusicTrackId = it
        }
        isInitialArgumentsConsumed = savedInstanceState?.getBoolean("saved_args_consumed", false) ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedPhotoUri?.let { outState.putString("saved_photo_uri", it.toString()) }
        if (templateCode.isNotBlank()) outState.putString("saved_template_code", templateCode)
        if (templateTitle.isNotBlank()) outState.putString("saved_template_title", templateTitle)
        if (templateCategory.isNotBlank()) outState.putString("saved_template_category", templateCategory)
        if (templateThumbnailUrl.isNotBlank()) outState.putString("saved_template_thumb", templateThumbnailUrl)
        if (templateType.isNotBlank()) outState.putString("saved_template_type", templateType)
        selectedMusicTrackId?.let { outState.putString("saved_music_id", it) }
        outState.putBoolean("saved_args_consumed", isInitialArgumentsConsumed)
    }

    override fun onResume() {
        super.onResume()
        pendingResultBundle?.let { bundle ->
            pendingResultBundle = null
            hideLoadingDialog()
            bindingOrNull?.btnCreateDanceVideo?.isEnabled = true
            findNavController().navigateSafe(R.id.action_createTemplateVideoFragment_to_templateVideoResultFragment, bundle)
            return
        }

        pendingErrorMsg?.let { errorMsg ->
            pendingErrorMsg = null
            hideLoadingDialog()
            bindingOrNull?.btnCreateDanceVideo?.isEnabled = true
            context?.let { ctx ->
                val friendly = ErrorFormatter.formatVideoErrorMessage(ctx, errorMsg)
                Toast.makeText(ctx, getString(R.string.toast_create_video_failed, friendly), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val density = resources.displayMetrics.density
            binding.btnCreateDanceVideo.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navBarInset + (20 * density).toInt()
                leftMargin = (20 * density).toInt()
                rightMargin = (20 * density).toInt()
            }
            insets
        }

        // 1. Check if returning from Result screen with savedStateHandle
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        val returnedTemplateCode = savedStateHandle?.remove<String>(ARG_TEMPLATE_CODE)
        val returnedTemplateCategory = savedStateHandle?.remove<String>(ARG_TEMPLATE_CATEGORY)
        val returnedTemplateTitle = savedStateHandle?.remove<String>(ARG_TEMPLATE_TITLE)
        val returnedTemplateThumb = savedStateHandle?.remove<String>(ARG_TEMPLATE_THUMB)

        if (!returnedTemplateCode.isNullOrBlank()) {
            templateCode = returnedTemplateCode
        }
        if (!returnedTemplateCategory.isNullOrBlank()) {
            templateCategory = returnedTemplateCategory
            selectedTemplateCatCode = returnedTemplateCategory
        }
        if (!returnedTemplateTitle.isNullOrBlank()) {
            templateTitle = returnedTemplateTitle
        }
        if (!returnedTemplateThumb.isNullOrBlank()) {
            templateThumbnailUrl = returnedTemplateThumb
        }

        // 2. Initial arguments parsed only on first launch (not when returning or recreating)
        if (!isInitialArgumentsConsumed) {
            val args = arguments
            val initialTemplateCode = args?.getString(ARG_TEMPLATE_CODE)?.takeIf { it.isNotBlank() }
                ?: args?.getString("template_code").orEmpty()
            if (initialTemplateCode.isNotBlank()) templateCode = initialTemplateCode

            val initialTemplateTitle = args?.getString(ARG_TEMPLATE_TITLE)?.takeIf { it.isNotBlank() }
                ?: args?.getString("template_title").orEmpty()
            if (initialTemplateTitle.isNotBlank()) templateTitle = initialTemplateTitle

            val initialTemplateCat = args?.getString(ARG_TEMPLATE_CATEGORY)?.takeIf { it.isNotBlank() }
                ?: args?.getString("template_category").orEmpty()
            if (initialTemplateCat.isNotBlank()) {
                templateCategory = initialTemplateCat
                selectedTemplateCatCode = initialTemplateCat
            }

            val initialTemplateThumb = args?.getString(ARG_TEMPLATE_THUMB)?.takeIf { it.isNotBlank() }
                ?: args?.getString("template_thumb").orEmpty()
            if (initialTemplateThumb.isNotBlank()) templateThumbnailUrl = initialTemplateThumb

            val initialTemplateType = args?.getString(ARG_TEMPLATE_TYPE)?.takeIf { it.isNotBlank() }
                ?: args?.getString("template_type").orEmpty()
            if (initialTemplateType.isNotBlank()) templateType = initialTemplateType

            // Initial Music from arguments
            val initialMusicId = args?.getString("selected_track_id")?.takeIf { it.isNotBlank() }
            if (!initialMusicId.isNullOrBlank()) selectedMusicTrackId = initialMusicId

            val initialMusicTitle = args?.getString("selected_music_title")?.takeIf { it.isNotBlank() }
            if (!initialMusicTitle.isNullOrBlank()) selectedMusicTitle = initialMusicTitle

            val initialMusicAudio = args?.getString("selected_music_audio_url")
                ?: args?.getString("template_audio_url")
            if (!initialMusicAudio.isNullOrBlank()) selectedMusicAudioUrl = initialMusicAudio

            val initialMusicThumb = args?.getString("selected_music_image_url")
            val initialImage = args?.getString(ARG_INITIAL_IMAGE)
                ?: args?.getString("extra_initial_image_1")
                ?: args?.getString("initial_image_uri")
                ?: args?.getString("selected_photo_uri")
                ?: args?.getString("photo_uri")
                ?: args?.getString("image_uri")
            if (!initialImage.isNullOrBlank() && selectedPhotoUri == null) {
                val uri = if (initialImage.startsWith("content://") || initialImage.startsWith("file://")) {
                    Uri.parse(initialImage)
                } else {
                    Uri.fromFile(File(initialImage))
                }
                selectedPhotoUri = copyUriToAppCache(uri)
            }

            isInitialArgumentsConsumed = true
        }

        // Keep arguments bundle updated in sync
        arguments?.let {
            if (templateCode.isNotBlank()) it.putString(ARG_TEMPLATE_CODE, templateCode)
            if (templateCategory.isNotBlank()) it.putString(ARG_TEMPLATE_CATEGORY, templateCategory)
            if (templateTitle.isNotBlank()) it.putString(ARG_TEMPLATE_TITLE, templateTitle)
            if (templateThumbnailUrl.isNotBlank()) it.putString(ARG_TEMPLATE_THUMB, templateThumbnailUrl)
            if (templateType.isNotBlank()) it.putString(ARG_TEMPLATE_TYPE, templateType)
        }

        setupHeader()
        setupPhotoSection()
        setupTemplatesSection()
        setupMusicSection()
        updateSelectedPhotoUi()

        loadAllData()
    }

    private fun setupHeader() {
        binding.header.applyTitleGradient()
        binding.header.setOnBackClickListener {
            stopAudio()
            findNavController().safePopBackStack()
        }
    }

    private fun setupPhotoSection() {
        binding.layoutSinglePhoto.setOnClickListener {
            launchSinglePhotoPicker()
        }
        binding.cardSinglePhoto.setOnClickListener {
            launchSinglePhotoPicker()
        }
    }

    private fun showSinglePhoto(uri: Uri) {
        binding.layoutSingleEmpty.visibility = View.GONE
        binding.cardSinglePhoto.visibility = View.VISIBLE
        binding.layoutSinglePhoto.background = null
        binding.layoutSinglePhoto.minimumHeight = 0
        binding.ivSingleSelectedPhoto.load(uri) {
            crossfade(true)
            listener(
                onError = { _, result ->
                    result.throwable.printStackTrace()
                }
            )
        }
    }

    private fun clearSinglePhoto() {
        binding.layoutSingleEmpty.visibility = View.VISIBLE
        binding.cardSinglePhoto.visibility = View.GONE
        binding.layoutSinglePhoto.setBackgroundResource(R.drawable.bg_upload_photo_box)
        binding.ivSingleSelectedPhoto.setImageDrawable(null)
    }

    private fun updateSelectedPhotoUi() {
        val uri = selectedPhotoUri
        if (uri != null) {
            showSinglePhoto(uri)
        } else {
            clearSinglePhoto()
        }
        val hasPhoto = uri != null
        binding.btnCreateDanceVideo.alpha = if (hasPhoto) 1.0f else 0.5f
    }

    private fun setupTemplatesSection() {
        templateCategoryAdapter = CategoryAdapter(
            categories = listOf(CategoryModel.ALL),
            selectedCategoryCode = selectedTemplateCatCode
        ) { category ->
            selectedTemplateCatCode = category.code
            arguments?.putString(ARG_TEMPLATE_CATEGORY, category.code)
            filterTemplatesByCategory(category)
        }
        binding.rvTemplateCategories.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvTemplateCategories.adapter = templateCategoryAdapter

        danceTemplateAdapter = DanceTemplatePickerAdapter(
            items = emptyList(),
            selectedTemplateId = templateCode.ifBlank { null }
        ) { template ->
            templateCode = template.id
            templateTitle = template.title
            templateCategory = template.category
            templateThumbnailUrl = template.safeImageUrl.orEmpty()
            templateType = template.templateType.orEmpty()

            arguments?.let {
                it.putString(ARG_TEMPLATE_CODE, templateCode)
                it.putString(ARG_TEMPLATE_CATEGORY, templateCategory)
                it.putString(ARG_TEMPLATE_TITLE, templateTitle)
                it.putString(ARG_TEMPLATE_THUMB, templateThumbnailUrl)
                it.putString(ARG_TEMPLATE_TYPE, templateType)
            }

            // If music hasn't been explicitly changed, sync template audio as well
            if (selectedMusicTrackId == null && !template.safeAudioUrl.isNullOrBlank()) {
                selectedMusicAudioUrl = template.safeAudioUrl
                selectedMusicTitle = template.title
                selectedMusicImageUrl = template.safeImageUrl
            }
        }
        binding.rvTemplates.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvTemplates.adapter = danceTemplateAdapter
    }

    private fun setupMusicSection() {
        musicCategoryAdapter = CategoryAdapter(
            categories = listOf(CategoryModel.ALL),
            selectedCategoryCode = selectedMusicCatCode
        ) { category ->
            selectedMusicCatCode = category.code
            filterMusicByCategory(category)
        }
        binding.rvMusicCategories.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvMusicCategories.adapter = musicCategoryAdapter

        danceMusicAdapter = DanceMusicPickerAdapter(
            tracks = emptyList(),
            selectedTrackId = selectedMusicTrackId,
            onTrackSelected = { track ->
                userChangedMusic = true
                if (track != null) {
                    selectedMusicTrackId = track.id
                    selectedMusicTitle = track.title
                    selectedMusicAudioUrl = track.safeAudioUrl
                    selectedMusicImageUrl = track.safeImageUrl
                    selectedMusicAudioFilePath = null
                } else {
                    // Deselect music -> Revert to template original audio
                    selectedMusicTrackId = null
                    selectedMusicTitle = null
                    selectedMusicAudioUrl = null
                    selectedMusicImageUrl = null
                    selectedMusicAudioFilePath = null
                    stopAudio()
                }
            },
            onPreviewClick = { track ->
                toggleAudioPreview(track)
            }
        )
        binding.rvMusicTracks.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.rvMusicTracks.adapter = danceMusicAdapter
    }

    override fun initListener() {
        binding.btnCreateDanceVideo.setOnSingleClickListener(700L) {
            if (selectedPhotoUri == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_please_select_photo),
                    Toast.LENGTH_SHORT
                ).show()
                launchSinglePhotoPicker()
                return@setOnSingleClickListener
            }

            stopAudio()
            startDanceVideoGeneration()
        }
    }

    private fun loadAllData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1. Fetch dancing templates first
                val dancingTemplates = templateRepository.getDancingTemplates()
                val allTemplates = if (templateCode.isNotBlank() && dancingTemplates.none { it.id == templateCode }) {
                    val passedTemplate = TemplateModel(
                        id = templateCode,
                        title = templateTitle,
                        category = templateCategory,
                        imageUrl = templateThumbnailUrl,
                        templateType = templateType,
                        audioUrl = selectedMusicAudioUrl
                    )
                    listOf(passedTemplate) + dancingTemplates
                } else {
                    dancingTemplates
                }
                cachedDancingTemplates = allTemplates

                // 2. Extract categories dynamically from the template list (lấy chuẩn từ danh sách, không fix cứng)
                val dynamicCategories = mutableListOf(CategoryModel.ALL)
                val seenCategoryCodes = mutableSetOf("all", "tất cả")

                allTemplates.forEach { template ->
                    val rawCat = template.category.trim()
                    if (rawCat.isNotBlank() && !rawCat.startsWith("MUSIC_", ignoreCase = true)) {
                        val key = rawCat.lowercase()
                        if (seenCategoryCodes.add(key)) {
                            val displayName = template.categoryDisplayName.ifBlank {
                                rawCat.replace("_", " ").split(" ")
                                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                            }
                            dynamicCategories.add(
                                CategoryModel(
                                    code = rawCat,
                                    name = displayName,
                                    active = true
                                )
                            )
                        }
                    }
                }

                val finalCategories = if (dynamicCategories.size > 1) {
                    dynamicCategories
                } else {
                    templateRepository.getDancingCategories()
                }
                templateCategoryAdapter.submitList(finalCategories)

                if (allTemplates.isNotEmpty()) {
                    val matchingTemplate = allTemplates.firstOrNull { it.id == templateCode } ?: allTemplates.first()
                    if (templateCode.isBlank()) {
                        templateCode = matchingTemplate.id
                        templateTitle = matchingTemplate.title
                        templateCategory = matchingTemplate.category
                        templateThumbnailUrl = matchingTemplate.safeImageUrl.orEmpty()
                        templateType = matchingTemplate.templateType.orEmpty()
                    } else {
                        if (templateType.isBlank()) {
                            templateType = matchingTemplate.templateType.orEmpty()
                        }
                        if (templateCategory.isBlank()) {
                            templateCategory = matchingTemplate.category
                        }
                        if (templateTitle.isBlank()) {
                            templateTitle = matchingTemplate.title
                        }
                        if (templateThumbnailUrl.isBlank()) {
                            templateThumbnailUrl = matchingTemplate.safeImageUrl.orEmpty()
                        }
                    }

                    // Find matching category in finalCategories for the selected template
                    val targetCategoryStr = if (selectedTemplateCatCode.isNotBlank() && !selectedTemplateCatCode.equals("ALL", ignoreCase = true)) {
                        selectedTemplateCatCode
                    } else if (templateCategory.isNotBlank()) {
                        templateCategory
                    } else {
                        matchingTemplate.category
                    }
                    val matchingCat = finalCategories.firstOrNull { cat ->
                        !cat.code.equals("ALL", ignoreCase = true) && (
                            cat.code.equals(targetCategoryStr, ignoreCase = true) ||
                            cat.name.equals(targetCategoryStr, ignoreCase = true) ||
                            cat.displayName.equals(targetCategoryStr, ignoreCase = true) ||
                            cat.code.replace("_", "").equals(targetCategoryStr.replace("_", ""), ignoreCase = true)
                        )
                    } ?: finalCategories.firstOrNull { cat ->
                        !cat.code.equals("ALL", ignoreCase = true) && targetCategoryStr.isNotBlank() && (
                            cat.code.contains(targetCategoryStr, ignoreCase = true) ||
                            targetCategoryStr.contains(cat.code, ignoreCase = true)
                        )
                    } ?: finalCategories.firstOrNull()

                    val activeCat = if (selectedTemplateCatCode.equals("ALL", ignoreCase = true) && matchingCat == null) {
                        CategoryModel.ALL
                    } else {
                        matchingCat ?: CategoryModel.ALL
                    }
                    selectedTemplateCatCode = activeCat.code
                    templateCategoryAdapter.setSelected(activeCat.code)

                    // Scroll category chip into view
                    val catIndex = finalCategories.indexOfFirst { it.code.equals(activeCat.code, ignoreCase = true) }
                    if (catIndex != -1) {
                        scrollCategoryToPosition(catIndex)
                    }

                    // Filter template list by active category
                    val isAllCat = activeCat.code.isBlank() ||
                            activeCat.code.equals("ALL", ignoreCase = true) ||
                            activeCat.code.equals("Tất cả", ignoreCase = true)

                    val listToSubmit = if (isAllCat) {
                        allTemplates
                    } else {
                        val target = activeCat.code.trim().lowercase()
                        val targetName = activeCat.name.trim().lowercase()
                        val filtered = allTemplates.filter { item ->
                            val itemCat = item.category.trim().lowercase()
                            itemCat == target || itemCat == targetName || itemCat.contains(target) || itemCat.contains(targetName)
                        }
                        if (filtered.any { it.id == templateCode }) filtered else allTemplates
                    }

                    danceTemplateAdapter.submitList(listToSubmit)
                    danceTemplateAdapter.setSelectedId(templateCode)

                    // Scroll template item into view
                    val templateIndex = listToSubmit.indexOfFirst { it.id == templateCode }
                    if (templateIndex != -1) {
                        scrollTemplateToPosition(templateIndex)
                    }
                }

                // 3. Fetch music tracks & extract music categories dynamically from tracks or API
                val musicTracks = templateRepository.getMusicTracks()
                cachedMusicTracks = musicTracks

                val dynamicMusicCats = mutableListOf(
                    CategoryModel(
                        code = "ALL",
                        name = "Tất cả",
                        groupCode = "MUSIC",
                        active = true
                    )
                )
                val seenMusicCats = mutableSetOf("all", "tất cả")
                musicTracks.forEach { track ->
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
                    musicCategoryAdapter.submitList(dynamicMusicCats)
                } else {
                    val musicCats = templateRepository.getMusicCategories()
                    musicCategoryAdapter.submitList(musicCats)
                }

                if (musicTracks.isNotEmpty()) {
                    danceMusicAdapter.submitList(musicTracks)
                    if (!selectedMusicTrackId.isNullOrBlank()) {
                        danceMusicAdapter.setSelectedTrackId(selectedMusicTrackId)
                    } else {
                        danceMusicAdapter.setSelectedTrackId(null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading dance screen data: ${e.message}", e)
            }
        }
    }

    private fun filterTemplatesByCategory(category: CategoryModel) {
        val pool = cachedDancingTemplates
        if (pool.isEmpty()) return

        val isAll = category.code.isBlank() ||
                category.code.equals("ALL", ignoreCase = true) ||
                category.code.equals("Tất cả", ignoreCase = true)

        val listToSubmit = if (isAll) {
            pool
        } else {
            val target = category.code.trim().lowercase()
            val targetName = category.name.trim().lowercase()
            val filtered = pool.filter { item ->
                val itemCat = item.category.trim().lowercase()
                itemCat == target || itemCat == targetName || itemCat.contains(target) || itemCat.contains(targetName)
            }
            if (filtered.isNotEmpty()) filtered else pool
        }

        danceTemplateAdapter.submitList(listToSubmit)

        val existingIndex = listToSubmit.indexOfFirst { it.id == templateCode }
        if (existingIndex != -1) {
            danceTemplateAdapter.setSelectedId(templateCode)
            scrollTemplateToPosition(existingIndex)
        } else if (listToSubmit.isNotEmpty()) {
            val first = listToSubmit[0]
            templateCode = first.id
            templateTitle = first.title
            templateCategory = first.category
            templateThumbnailUrl = first.safeImageUrl.orEmpty()
            templateType = first.templateType.orEmpty()
            danceTemplateAdapter.setSelectedId(first.id)
            if (selectedMusicTrackId == null && !first.safeAudioUrl.isNullOrBlank()) {
                selectedMusicAudioUrl = first.safeAudioUrl
                selectedMusicTitle = first.title
                selectedMusicImageUrl = first.safeImageUrl
            }
            scrollTemplateToPosition(0)
        }
    }

    private fun scrollCategoryToPosition(position: Int) {
        if (position < 0) return
        val b = bindingOrNull ?: return
        val lm = b.rvTemplateCategories.layoutManager as? LinearLayoutManager ?: return
        lm.scrollToPositionWithOffset(position, 0)
        b.rvTemplateCategories.post {
            (bindingOrNull?.rvTemplateCategories?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
        }
        b.rvTemplateCategories.postDelayed({
            (bindingOrNull?.rvTemplateCategories?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
        }, 100L)
    }

    private fun scrollTemplateToPosition(position: Int) {
        if (position < 0) return
        val b = bindingOrNull ?: return
        val lm = b.rvTemplates.layoutManager as? LinearLayoutManager ?: return
        lm.scrollToPositionWithOffset(position, 0)
        b.rvTemplates.post {
            (bindingOrNull?.rvTemplates?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
        }
        b.rvTemplates.postDelayed({
            (bindingOrNull?.rvTemplates?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
        }, 100L)
    }

    private fun filterMusicByCategory(category: CategoryModel) {
        stopAudio()
        val pool = cachedMusicTracks
        if (pool.isEmpty()) return

        val isAll = category.code.isBlank() ||
                category.code.equals("ALL", ignoreCase = true) ||
                category.code.equals("Tất cả", ignoreCase = true)

        if (isAll) {
            danceMusicAdapter.submitList(pool)
        } else {
            val target = category.code.trim().lowercase()
            val cleanTarget = target.removePrefix("music_")
            val filtered = pool.filter { item ->
                val itemCat = item.category.trim().lowercase()
                itemCat == target || itemCat.removePrefix("music_") == cleanTarget || itemCat.contains(cleanTarget)
            }
            danceMusicAdapter.submitList(filtered)
        }

        (binding.rvMusicTracks.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
        binding.rvMusicTracks.post {
            (binding.rvMusicTracks.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
        }
    }

    // Audio Preview Logic
    private fun toggleAudioPreview(track: TemplateModel) {
        if (activePlayingTrackId == track.id) {
            stopAudio()
            return
        }

        stopAudio()

        val audioUrl = track.safeAudioUrl
        if (audioUrl.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.toast_music_url_empty), Toast.LENGTH_SHORT).show()
            return
        }

        danceMusicAdapter.setLoadingState(track.id)

        previewJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val cachedFile = AudioCacheManager.getOrDownloadAudio(requireContext(), audioUrl)
                if (!isAdded || view == null) return@launch

                if (cachedFile == null || !cachedFile.exists() || cachedFile.length() == 0L) {
                    danceMusicAdapter.setPlayingState(track.id, false)
                    return@launch
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(cachedFile.absolutePath)
                    setOnPreparedListener { mp ->
                        mp.start()
                        activePlayingTrackId = track.id
                        danceMusicAdapter.setPlayingState(track.id, true)
                    }
                    setOnCompletionListener {
                        stopAudio()
                    }
                    setOnErrorListener { _, _, _ ->
                        stopAudio()
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                stopAudio()
            }
        }
    }

    private fun stopAudio() {
        previewJob?.cancel()
        previewJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        val oldId = activePlayingTrackId
        activePlayingTrackId = null
        if (!oldId.isNullOrEmpty()) {
            danceMusicAdapter.setPlayingState(oldId, false)
        }
    }

    // Video Generation
    private fun startDanceVideoGeneration() {
        val photoUri = selectedPhotoUri ?: return
        if (isGeneratingVideo) return
        isGeneratingVideo = true
        binding.btnCreateDanceVideo.isEnabled = false

        val chosenTemplate = danceTemplateAdapter.getSelectedTemplate()
        if (chosenTemplate != null) {
            templateCode = chosenTemplate.id
            templateTitle = chosenTemplate.title
            templateCategory = chosenTemplate.category
            selectedTemplateCatCode = chosenTemplate.category
            templateThumbnailUrl = chosenTemplate.safeImageUrl.orEmpty()
            arguments?.let {
                it.putString(ARG_TEMPLATE_CODE, templateCode)
                it.putString(ARG_TEMPLATE_CATEGORY, templateCategory)
                it.putString(ARG_TEMPLATE_TITLE, templateTitle)
                it.putString(ARG_TEMPLATE_THUMB, templateThumbnailUrl)
            }
        }

        val chosenMusic = danceMusicAdapter.getSelectedTrack()
        if (chosenMusic != null) {
            // Người dùng chọn nhạc từ danh sách -> Tắt nhạc của template, dùng bài nhạc đã chọn
            selectedMusicTrackId = chosenMusic.id
            selectedMusicTitle = chosenMusic.title
            selectedMusicAudioUrl = chosenMusic.safeAudioUrl
            selectedMusicImageUrl = chosenMusic.safeImageUrl
        } else {
            // Không chọn nhạc ngoài -> Giữ nguyên âm thanh gốc của template
            selectedMusicTrackId = null
            selectedMusicTitle = null
            selectedMusicAudioUrl = null
            selectedMusicImageUrl = null
        }

        showLoadingDialog(
            title = getString(R.string.loading_generating_video_ai),
            subtitle = getString(R.string.loading_preparing_image_data),
            cancelable = true,
            onCancel = { cancelVideoGeneration() }
        )

        generateVideoJob = viewLifecycleOwner.lifecycleScope.launch {
            val overallStart = System.currentTimeMillis()
            try {
                // 1. Tải trước file audio nếu có URL
                var audioPath: String? = selectedMusicAudioFilePath
                val audioUrl = selectedMusicAudioUrl

                if (audioPath.isNullOrBlank() && !audioUrl.isNullOrBlank()) {
                    if (isAdded) {
                        loadingDialog?.updateMessage(getString(R.string.loading_preparing_bg_music), getString(R.string.loading_please_wait))
                    }
                    val downloaded = withContext(Dispatchers.IO) {
                        AudioCacheManager.getOrDownloadAudio(requireContext(), audioUrl)
                    }
                    if (downloaded != null && downloaded.exists() && downloaded.length() > 0) {
                        audioPath = downloaded.absolutePath
                        selectedMusicAudioFilePath = audioPath
                    }
                }

                // 2. Nén ảnh qua ImageCompressor
                if (isAdded) {
                    loadingDialog?.updateMessage(getString(R.string.loading_processing_input_image), getString(R.string.loading_compressing_optimizing))
                }
                val rawFile = withContext(Dispatchers.IO) { copyUriToFile(photoUri) }
                if (rawFile == null || !rawFile.exists()) {
                    isGeneratingVideo = false
                    if (isResumed && isAdded) {
                        hideLoadingDialog()
                        bindingOrNull?.btnCreateDanceVideo?.isEnabled = true
                        Toast.makeText(requireContext(), getString(R.string.toast_cannot_read_image), Toast.LENGTH_SHORT).show()
                    } else {
                        pendingErrorMsg = getString(R.string.toast_cannot_read_image)
                    }
                    return@launch
                }

                val compressedFile = withContext(Dispatchers.IO) {
                    ImageCompressor.compressIfNeeded(requireContext(), rawFile)
                }

                // 3. Gọi backend processImageEditing hoặc generateStaticVideo
                val codeToUse = templateCode.ifBlank { "ai_dance_default" }
                if (isAdded) {
                    loadingDialog?.updateMessage(getString(R.string.loading_sending_video_request), getString(R.string.loading_backend_ai_processing))
                }

                val isStatic = templateType.equals("GENERATE_STATIC_VIDEO", ignoreCase = true) ||
                        templateType.equals("GEN_VIDEO_STATIC", ignoreCase = true)

                val processResult = if (isStatic) {
                    templateRepository.generateStaticVideo(
                        code = codeToUse,
                        file = compressedFile,
                        options = "{}",
                        prompt = null
                    )
                } else {
                    templateRepository.processImageEditing(
                        file = compressedFile,
                        code = codeToUse,
                        options = "{}"
                    )
                }

                var requestId: String? = null
                var videoUrl: String? = null

                if (processResult.isFailure) {
                    val errorMsg = processResult.exceptionOrNull()?.message ?: ""
                    val extractedUuid = templateRepository.extractRequestIdFromError(errorMsg)
                    if (!extractedUuid.isNullOrBlank()) {
                        requestId = extractedUuid
                    } else {
                        isGeneratingVideo = false
                        val friendlyError = context?.let { ErrorFormatter.formatVideoErrorMessage(it, errorMsg) } ?: errorMsg
                        if (isResumed && isAdded) {
                            hideLoadingDialog()
                            bindingOrNull?.btnCreateDanceVideo?.isEnabled = true
                            Toast.makeText(requireContext(), getString(R.string.toast_create_video_failed, friendlyError), Toast.LENGTH_LONG).show()
                        } else {
                            pendingErrorMsg = friendlyError
                        }
                        return@launch
                    }
                } else {
                    val jsonResponse = processResult.getOrNull()
                    videoUrl = templateRepository.extractVideoUrl(jsonResponse)
                    requestId = templateRepository.extractRequestId(jsonResponse)
                }

                if (videoUrl.isNullOrBlank() && requestId.isNullOrBlank()) {
                    isGeneratingVideo = false
                    val errorMsg = ErrorFormatter.formatVideoErrorMessage(requireContext(), processResult.getOrNull()?.toString())
                    if (isResumed && isAdded) {
                        hideLoadingDialog()
                        bindingOrNull?.btnCreateDanceVideo?.isEnabled = true
                        Toast.makeText(requireContext(), getString(R.string.toast_create_video_failed, errorMsg), Toast.LENGTH_LONG).show()
                    } else {
                        pendingErrorMsg = errorMsg
                    }
                    return@launch
                }

                // 4. Poll kết quả nếu cần
                if (videoUrl.isNullOrBlank() && !requestId.isNullOrBlank()) {
                    val currentRequestId = requestId
                    if (isAdded) {
                        loadingDialog?.updateMessage(getString(R.string.loading_generating_video_ai), getString(R.string.loading_processing_progress, 30))
                    }
                    val pollResult = templateRepository.pollImageEditingResult(
                        requestId = currentRequestId,
                        intervalMillis = 5_000L,
                        timeoutMillis = 180_000L,
                        onProgress = { progress ->
                            if (isAdded) {
                                loadingDialog?.updateMessage(
                                    getString(R.string.loading_generating_video_ai),
                                    getString(R.string.loading_processing_progress, progress)
                                )
                            }
                        }
                    )

                    if (pollResult.isSuccess) {
                        videoUrl = templateRepository.extractVideoUrl(pollResult.getOrNull())
                    } else {
                        val rawPollError = pollResult.exceptionOrNull()?.message
                        val pollError = ErrorFormatter.formatVideoErrorMessage(requireContext(), rawPollError)
                        isGeneratingVideo = false
                        if (isResumed && isAdded) {
                            hideLoadingDialog()
                            bindingOrNull?.btnCreateDanceVideo?.isEnabled = true
                            Toast.makeText(requireContext(), getString(R.string.toast_create_video_failed, pollError), Toast.LENGTH_LONG).show()
                        } else {
                            pendingErrorMsg = pollError
                        }
                        return@launch
                    }
                }

                // 5. Lưu video & ghép nhạc
                var finalLocalVideoPath: String? = null

                if (!videoUrl.isNullOrBlank()) {
                    if (isAdded) {
                        loadingDialog?.updateMessage(getString(R.string.loading_preparing_video), getString(R.string.loading_storing_local))
                    }
                    val rawVideoFile = if (File(videoUrl).exists()) {
                        File(videoUrl)
                    } else {
                        val downloadedRawVideo = File(requireContext().cacheDir, "raw_dance_${System.currentTimeMillis()}.mp4")
                        val downloadSuccess = VideoGenerator.downloadVideoFile(videoUrl, downloadedRawVideo)
                        if (downloadSuccess && downloadedRawVideo.exists()) downloadedRawVideo else null
                    }

                    if (rawVideoFile != null && rawVideoFile.exists()) {
                        val shouldMergeAudio = (!isStatic || userChangedMusic) &&
                                chosenMusic != null && (!audioPath.isNullOrBlank() || !audioUrl.isNullOrBlank())
                        val mergedFile = if (shouldMergeAudio) {
                            if (isAdded) {
                                loadingDialog?.updateMessage(getString(R.string.loading_merging_music), getString(R.string.loading_please_wait))
                            }
                            val outMerged = File(requireContext().cacheDir, "merged_dance_${System.currentTimeMillis()}.mp4")
                            val audioSource = audioPath?.takeIf { File(it).exists() } ?: audioUrl.orEmpty()
                            val muxSuccess = VideoGenerator.mergeAudioIntoVideo(
                                context = requireContext(),
                                videoInputFile = rawVideoFile,
                                audioSource = audioSource,
                                outputFile = outMerged
                            )
                            if (muxSuccess && outMerged.exists()) outMerged else rawVideoFile
                        } else {
                            rawVideoFile
                        }

                        val permanentDoc = VideoGenerator.saveVideoToAppDocuments(
                            context = requireContext(),
                            sourceFile = mergedFile,
                            title = "Dance_Video_${System.currentTimeMillis()}"
                        )
                        finalLocalVideoPath = permanentDoc.absolutePath
                    } else {
                        finalLocalVideoPath = videoUrl
                    }
                }

                isGeneratingVideo = false
                val totalElapsed = System.currentTimeMillis() - overallStart
                Log.i(TAG, "Dance video generation completed in ${totalElapsed}ms, path: $finalLocalVideoPath")

                // 6. Điều hướng kết quả và lưu lịch sử
                if (!finalLocalVideoPath.isNullOrBlank()) {
                    saveToHistory(finalLocalVideoPath, photoUri.toString())
                    navigateToResult(finalLocalVideoPath, photoUri.toString(), audioPath)
                } else {
                    navigateToResult(null, photoUri.toString(), audioPath)
                }

            } catch (_: kotlinx.coroutines.CancellationException) {
                isGeneratingVideo = false
                hideLoadingDialog()
                bindingOrNull?.btnCreateDanceVideo?.isEnabled = true
            } catch (e: Exception) {
                if (!isGeneratingVideo) return@launch
                isGeneratingVideo = false
                val friendlyError = context?.let { ErrorFormatter.formatVideoErrorMessage(it, e.message) }
                    ?: (e.message ?: "")
                if (isResumed && isAdded) {
                    hideLoadingDialog()
                    bindingOrNull?.btnCreateDanceVideo?.isEnabled = true
                    Toast.makeText(requireContext(), getString(R.string.toast_create_video_failed, friendlyError), Toast.LENGTH_LONG).show()
                } else {
                    pendingErrorMsg = friendlyError
                }
            } finally {
                generateVideoJob = null
            }
        }
    }

    private fun cancelVideoGeneration() {
        if (isGeneratingVideo) {
            isGeneratingVideo = false
            generateVideoJob?.cancel()
            generateVideoJob = null
            pendingResultBundle = null
            pendingErrorMsg = null
            hideLoadingDialog()
            bindingOrNull?.btnCreateDanceVideo?.isEnabled = true
            context?.let { ctx ->
                Toast.makeText(ctx, getString(R.string.toast_video_cancelled), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToResult(videoUrl: String?, inputPhotoUri: String, audioPath: String?) {
        val bundle = Bundle().apply {
            putString(TemplateVideoResultFragment.ARG_VIDEO_URL, videoUrl)
            putString(TemplateVideoResultFragment.ARG_TEMPLATE_TITLE, templateTitle)
            putString(TemplateVideoResultFragment.ARG_TEMPLATE_THUMB, templateThumbnailUrl)
            putString(TemplateVideoResultFragment.ARG_TEMPLATE_CODE, templateCode)
            putString(
                TemplateVideoResultFragment.ARG_TEMPLATE_CATEGORY,
                if (selectedTemplateCatCode.isNotBlank() && !selectedTemplateCatCode.equals("ALL", ignoreCase = true)) {
                    selectedTemplateCatCode
                } else {
                    templateCategory
                }
            )
            if (!selectedMusicTrackId.isNullOrBlank()) {
                putString(TemplateVideoResultFragment.ARG_MUSIC_TRACK_ID, selectedMusicTrackId)
                putString(TemplateVideoResultFragment.ARG_MUSIC_TITLE, selectedMusicTitle.orEmpty())
                putString(TemplateVideoResultFragment.ARG_MUSIC_AUDIO_URL, selectedMusicAudioUrl)
                putString(TemplateVideoResultFragment.ARG_MUSIC_FILE_PATH, audioPath)
                putString(TemplateVideoResultFragment.ARG_MUSIC_IMAGE_URL, selectedMusicImageUrl)
            } else {
                putString(TemplateVideoResultFragment.ARG_MUSIC_TRACK_ID, null)
                putString(TemplateVideoResultFragment.ARG_MUSIC_TITLE, null)
                putString(TemplateVideoResultFragment.ARG_MUSIC_AUDIO_URL, null)
                putString(TemplateVideoResultFragment.ARG_MUSIC_FILE_PATH, null)
                putString(TemplateVideoResultFragment.ARG_MUSIC_IMAGE_URL, null)
            }
        }
        if (isResumed && isAdded) {
            hideLoadingDialog()
            bindingOrNull?.btnCreateDanceVideo?.isEnabled = true
            findNavController().navigateSafe(R.id.action_createTemplateVideoFragment_to_templateVideoResultFragment, bundle)
        } else {
            pendingResultBundle = bundle
        }
    }

    private fun saveToHistory(localVideoPath: String, photoUri: String) {
        try {
            val currentTime = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
            val dateStr = dateFormat.format(Date(currentTime))
            val title = if (!selectedMusicTitle.isNullOrBlank()) {
                "$templateTitle • ${getString(R.string.detail_music_label, selectedMusicTitle)}"
            } else {
                templateTitle.ifBlank { getString(R.string.dance_video_title) }
            }

            var durationStr: String? = null
            try {
                val cleanPath = if (localVideoPath.startsWith("file://")) localVideoPath.removePrefix("file://") else localVideoPath
                val file = File(cleanPath)
                if (file.exists()) {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val timeMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    retriever.release()
                    if (timeMs > 0) {
                        val totalSec = (timeMs / 1000).toInt()
                        val m = totalSec / 60
                        val s = totalSec % 60
                        durationStr = String.format(Locale.US, "%02d:%02d", m, s)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to retrieve duration from $localVideoPath: ${e.message}")
            }

            if (durationStr.isNullOrBlank()) {
                durationStr = danceTemplateAdapter.getSelectedTemplate()?.displayDuration
            }

            val effectiveCategory = if (templateCategory.isNotBlank()) {
                templateCategory
            } else {
                danceTemplateAdapter.getSelectedTemplate()?.category.orEmpty()
            }

            val item = HistoryItemModel(
                title = title,
                date = dateStr,
                type = "Dance",
                imageUrl = templateThumbnailUrl.ifBlank { photoUri },
                imageUri = localVideoPath,
                duration = durationStr,
                styleName = effectiveCategory,
                timestamp = currentTime
            )
            historyRepository.addHistoryItem(item)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyUriToFile(uri: Uri): File? {
        return try {
            val context = requireContext()
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "input_dance_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                inputStream.copyTo(out)
            }
            tempFile
        } catch (_: Exception) {
            null
        }
    }

    private fun showLoadingDialog(title: String, subtitle: String, cancelable: Boolean = false, onCancel: (() -> Unit)? = null) {
        if (loadingDialog == null) {
            loadingDialog = AppLoadingDialog.show(requireContext(), title, subtitle, cancelable = cancelable, onCancel = onCancel)
        } else {
            loadingDialog?.updateMessage(title, subtitle)
            if (cancelable || onCancel != null) {
                loadingDialog?.setCancelAction(cancelable, onCancel)
            }
            if (!loadingDialog!!.isShowing) {
                loadingDialog?.show()
            }
        }
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    override fun onDestroyView() {
        stopAudio()
        if (isGeneratingVideo) {
            generateVideoJob?.cancel()
            generateVideoJob = null
            isGeneratingVideo = false
        }
        hideLoadingDialog()
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "PhotoArt_VideoGen"
        const val ARG_TEMPLATE_CODE = "arg_template_code"
        const val ARG_TEMPLATE_TITLE = "arg_template_title"
        const val ARG_TEMPLATE_CATEGORY = "arg_template_category"
        const val ARG_TEMPLATE_THUMB = "arg_template_thumb"
        const val ARG_TEMPLATE_TYPE = "arg_template_type"
        const val ARG_INITIAL_IMAGE = "extra_initial_image_1"
    }
}
