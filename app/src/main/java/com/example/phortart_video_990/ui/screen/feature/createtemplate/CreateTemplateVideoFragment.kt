package com.example.phortart_video_990.ui.screen.feature.createtemplate

import android.net.Uri
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
import com.example.phortart_video_990.core.dialog.AppLoadingDialog
import com.example.phortart_video_990.core.utils.AudioCacheManager
import com.example.phortart_video_990.core.utils.ImageCompressor
import com.example.phortart_video_990.core.utils.ShimmerDrawable
import com.example.phortart_video_990.core.utils.VideoGenerator
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.core.utils.safePopBackStack
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.data.repository.TemplateRepository
import com.example.phortart_video_990.databinding.FragmentCreateTemplateVideoBinding
import com.example.phortart_video_990.ui.screen.feature.memories.VideoResultFragment
import android.util.Log
import kotlinx.coroutines.Dispatchers
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

    private var templateCode: String = ""
    private var templateTitle: String = ""
    private var templateCategory: String = ""
    private var templateThumbnailUrl: String = ""

    private var selectedPhotoUri: Uri? = null

    // Optional music
    private var selectedMusicTrackId: String? = null
    private var selectedMusicTitle: String? = null
    private var selectedMusicAudioUrl: String? = null
    private var selectedMusicAudioFilePath: String? = null
    private var selectedMusicImageUrl: String? = null

    private var loadingDialog: AppLoadingDialog? = null

    private var isGeneratingVideo = false
    private var generateVideoJob: kotlinx.coroutines.Job? = null
    private var pendingResultBundle: Bundle? = null
    private var pendingErrorMsg: String? = null

    private fun cancelVideoGeneration() {
        if (isGeneratingVideo) {
            isGeneratingVideo = false
            generateVideoJob?.cancel()
            generateVideoJob = null
            pendingResultBundle = null
            pendingErrorMsg = null
            hideLoadingDialog()
            bindingOrNull?.btnCreateVideo?.isEnabled = true
            context?.let { ctx ->
                Toast.makeText(ctx, getString(R.string.toast_video_cancelled), Toast.LENGTH_SHORT).show()
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
            val cacheDir = File(requireContext().cacheDir, "template_photos").apply { mkdirs() }
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

    // Native Photo Picker launcher (Media Picker)
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

    private fun launchSinglePhotoPicker() {
        try {
            pickSingleVisualMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (e: Exception) {
            try {
                fallbackSingleImageLauncher.launch("image/*")
            } catch (ex: Exception) {
                Toast.makeText(requireContext(), getString(R.string.toast_cannot_open_gallery), Toast.LENGTH_SHORT).show()
            }
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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedPhotoUri?.let { outState.putString("saved_photo_uri", it.toString()) }
        if (templateCode.isNotBlank()) outState.putString("saved_template_code", templateCode)
    }

    override fun onResume() {
        super.onResume()
        // If video generation finished while in background, navigate now safely!
        pendingResultBundle?.let { bundle ->
            pendingResultBundle = null
            hideLoadingDialog()
            bindingOrNull?.btnCreateVideo?.isEnabled = true
            findNavController().navigateSafe(R.id.action_createTemplateVideoFragment_to_templateVideoResultFragment, bundle)
            return
        }

        // If error occurred while in background, show error toast now!
        pendingErrorMsg?.let { errorMsg ->
            pendingErrorMsg = null
            hideLoadingDialog()
            bindingOrNull?.btnCreateVideo?.isEnabled = true
            context?.let { ctx ->
                Toast.makeText(ctx, getString(R.string.toast_error_occurred, errorMsg), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun initView() {
        val args = arguments
        templateCode = args?.getString(ARG_TEMPLATE_CODE).orEmpty()
        templateTitle = args?.getString(ARG_TEMPLATE_TITLE).orEmpty().ifBlank { "Template AI" }
        templateCategory = args?.getString(ARG_TEMPLATE_CATEGORY).orEmpty().ifBlank { "AI Video" }
        templateThumbnailUrl = args?.getString(ARG_TEMPLATE_THUMB).orEmpty()

        binding.tvTemplateTitle.text = templateTitle
        binding.tvTemplateCategory.text = getString(R.string.template_requirement_one_photo, templateCategory)

        if (templateThumbnailUrl.isNotBlank()) {
            val shimmerPlaceholder = ShimmerDrawable(cornerRadius = 14f)
            binding.ivTemplateThumb.load(templateThumbnailUrl) {
                crossfade(true)
                placeholder(shimmerPlaceholder)
                error(shimmerPlaceholder)
            }
        } else {
            binding.ivTemplateThumb.setImageDrawable(ShimmerDrawable(cornerRadius = 14f))
        }

        updateSelectedPhotoUi()

        // Read music selection returned or preloaded
        val stateHandle = findNavController().currentBackStackEntry?.savedStateHandle

        val initialTrackId = args?.getString("selected_track_id")?.takeIf { it.isNotBlank() }
            ?: args?.getString(ARG_TEMPLATE_CODE)?.takeIf { it.isNotBlank() }
            ?: stateHandle?.get<String>("selected_track_id")?.takeIf { it.isNotBlank() }
        if (!initialTrackId.isNullOrBlank()) {
            selectedMusicTrackId = initialTrackId
        }

        val initialMusicTitle = args?.getString("selected_music_title")?.takeIf { it.isNotBlank() }
            ?: args?.getString(ARG_TEMPLATE_TITLE)?.takeIf { it.isNotBlank() }
            ?: stateHandle?.get<String>("selected_music_title")?.takeIf { it.isNotBlank() }
        if (!initialMusicTitle.isNullOrBlank()) {
            selectedMusicTitle = initialMusicTitle
            binding.tvSelectedMusicTitle.text = initialMusicTitle
            binding.tvSelectedMusicSubtitle.text = getString(R.string.music_chosen_as_bg)
            binding.tvMusicAction.text = getString(R.string.music_change_action)
        }

        val initialAudioUrl = args?.getString("selected_music_audio_url")
            ?: args?.getString("template_audio_url")
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
            ?: args?.getString(ARG_TEMPLATE_THUMB)
            ?: stateHandle?.get<String?>("selected_music_image_url")
        if (!initialImageUrl.isNullOrBlank()) {
            selectedMusicImageUrl = initialImageUrl
            binding.ivMusicIcon.load(initialImageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_func_music)
                error(R.drawable.ic_func_music)
            }
        }

        stateHandle?.getLiveData<String>("selected_track_id")?.observe(viewLifecycleOwner) { trackId ->
            if (!trackId.isNullOrBlank()) {
                selectedMusicTrackId = trackId
            }
        }
        stateHandle?.get<String?>("selected_music_image_url")?.let { imgUrl ->
            selectedMusicImageUrl = imgUrl
            if (!imgUrl.isNullOrBlank()) {
                binding.ivMusicIcon.load(imgUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_func_music)
                    error(R.drawable.ic_func_music)
                }
            }
        }

        stateHandle?.getLiveData<String>("selected_music_title")?.observe(viewLifecycleOwner) { title ->
            if (!title.isNullOrBlank()) {
                selectedMusicTitle = title
                binding.tvSelectedMusicTitle.text = title
                binding.tvSelectedMusicSubtitle.text = getString(R.string.music_chosen_as_bg)
                binding.tvMusicAction.text = getString(R.string.music_change_action)
            }
        }
        stateHandle?.getLiveData<String?>("selected_music_audio_url")?.observe(viewLifecycleOwner) {
            selectedMusicAudioUrl = it
        }
        stateHandle?.getLiveData<String?>("selected_music_file_path")?.observe(viewLifecycleOwner) {
            selectedMusicAudioFilePath = it
        }
        stateHandle?.getLiveData<String?>("selected_music_image_url")?.observe(viewLifecycleOwner) { imgUrl ->
            selectedMusicImageUrl = imgUrl
            if (!imgUrl.isNullOrBlank()) {
                binding.ivMusicIcon.load(imgUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_func_music)
                    error(R.drawable.ic_func_music)
                }
            }
        }
    }

    private fun updateSelectedPhotoUi() {
        val uri = selectedPhotoUri
        if (uri != null) {
            binding.cardUploadPhoto.visibility = View.GONE
            binding.cardSelectedPhoto.visibility = View.VISIBLE
            binding.ivSelectedPhoto.load(uri) {
                crossfade(true)
            }
        } else {
            binding.cardUploadPhoto.visibility = View.VISIBLE
            binding.cardSelectedPhoto.visibility = View.GONE
        }
    }

    override fun initListener() {
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

        binding.header.setOnBackClickListener {
            findNavController().safePopBackStack()
        }

        binding.cardUploadPhoto.setOnClickListener {
            launchSinglePhotoPicker()
        }

        binding.btnChangePhoto.setOnClickListener {
            launchSinglePhotoPicker()
        }

        binding.cardSelectMusic.setOnClickListener {
            val bundle = Bundle().apply {
                putString("selected_track_id", selectedMusicTrackId)
                putString("selected_music_title", selectedMusicTitle)
                putString("selected_music_audio_url", selectedMusicAudioUrl)
            }
            findNavController().navigateSafe(R.id.action_createTemplateVideoFragment_to_musicFragment, bundle)
        }

        binding.btnCreateVideo.setOnClickListener {
            Log.i(TAG, "==========================================================")
            Log.i(TAG, ">>> [TEMPLATE AI VIDEO] User clicked Create Video button.")
            Log.i(TAG, "==========================================================")
            val photoUri = selectedPhotoUri
            if (photoUri == null) {
                Log.w(TAG, "[TEMPLATE AI VIDEO] No photo selected!")
                Toast.makeText(requireContext(), getString(R.string.toast_select_portrait_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            processAndGenerateVideo(photoUri)
        }
    }

    private fun processAndGenerateVideo(photoUri: Uri) {
        val overallStart = System.currentTimeMillis()
        isGeneratingVideo = true
        binding.btnCreateVideo.isEnabled = false
        Log.i(TAG, ">>> processAndGenerateVideo started: templateCode='$templateCode', templateTitle='$templateTitle', photoUri=$photoUri")
        Log.i(TAG, "Music configuration: audioUrl=$selectedMusicAudioUrl, localPath=$selectedMusicAudioFilePath")

        showLoadingDialog(
            title = getString(R.string.loading_preparing_image),
            subtitle = getString(R.string.loading_please_wait),
            cancelable = true,
            onCancel = { cancelVideoGeneration() }
        )

        generateVideoJob?.cancel()
        generateVideoJob = lifecycleScope.launch {
            try {
                // 1. Tải nhạc nền nếu người dùng có chọn nhạc (Optional)
                val audioUrl = selectedMusicAudioUrl
                var audioPath = selectedMusicAudioFilePath
                if (!audioUrl.isNullOrBlank() && (audioPath.isNullOrBlank() || !File(audioPath).exists())) {
                    Log.i(TAG, "[Template Phase 1/5] Downloading background music: $audioUrl")
                    if (isAdded) {
                        loadingDialog?.updateMessage(getString(R.string.loading_downloading_music), getString(R.string.loading_please_wait))
                    }
                    val downloaded = AudioCacheManager.getOrDownloadAudio(requireContext(), audioUrl)
                    if (downloaded != null && downloaded.exists()) {
                        audioPath = downloaded.absolutePath
                        selectedMusicAudioFilePath = audioPath
                        Log.i(TAG, "[Template Phase 1/5] Music ready at: $audioPath (${downloaded.length() / 1024} KB)")
                    } else {
                        Log.w(TAG, "[Template Phase 1/5] Music download failed, continuing without custom music")
                    }
                }

                // 2. Chuyển Uri thành File và nén qua ImageCompressor
                Log.i(TAG, "[Template Phase 2/5] Preparing and compressing input photo: $photoUri")
                if (isAdded) {
                    loadingDialog?.updateMessage(getString(R.string.loading_processing_input_image), getString(R.string.loading_compressing_optimizing))
                }
                val rawFile = withContext(Dispatchers.IO) { copyUriToFile(photoUri) }
                if (rawFile == null || !rawFile.exists()) {
                    Log.e(TAG, "[Template Phase 2/5] FATAL: Cannot copy photo Uri to local file!")
                    isGeneratingVideo = false
                    if (isResumed && isAdded) {
                        hideLoadingDialog()
                        bindingOrNull?.btnCreateVideo?.isEnabled = true
                        Toast.makeText(requireContext(), getString(R.string.toast_cannot_read_image), Toast.LENGTH_SHORT).show()
                    } else {
                        pendingErrorMsg = getString(R.string.toast_cannot_read_image)
                    }
                    return@launch
                }

                val compressedFile = withContext(Dispatchers.IO) {
                    ImageCompressor.compressIfNeeded(requireContext(), rawFile)
                }
                Log.i(TAG, "[Template Phase 2/5] Photo prepared: ${compressedFile.name} (${compressedFile.length() / 1024} KB)")

                // 3. Gọi backend processImageEditing
                val codeToUse = templateCode.ifBlank { "ai_effect_default" }
                Log.i(TAG, "[Template Phase 3/5] Requesting Backend AI video generation: code='$codeToUse'...")
                if (isAdded) {
                    loadingDialog?.updateMessage(getString(R.string.loading_sending_video_request), getString(R.string.loading_backend_ai_processing))
                }

                val processResult = templateRepository.processImageEditing(
                    file = compressedFile,
                    code = codeToUse,
                    options = "{}"
                )

                if (processResult.isFailure) {
                    val errorMsg = processResult.exceptionOrNull()?.message ?: "AI Error"
                    Log.e(TAG, "[Template Phase 3/5] processImageEditing failed: $errorMsg")
                    // Nếu lỗi mạng / timeout thử lấy requestId từ error nếu có
                    val reqId = templateRepository.extractRequestId(null)
                    if (reqId.isNullOrBlank()) {
                        isGeneratingVideo = false
                        if (isResumed && isAdded) {
                            hideLoadingDialog()
                            bindingOrNull?.btnCreateVideo?.isEnabled = true
                            Toast.makeText(requireContext(), getString(R.string.toast_create_video_failed, errorMsg), Toast.LENGTH_LONG).show()
                        } else {
                            pendingErrorMsg = errorMsg
                        }
                        return@launch
                    }
                }

                val jsonResponse = processResult.getOrNull()
                Log.d(TAG, "[Template Phase 3/5] Backend response: $jsonResponse")
                var videoUrl = templateRepository.extractVideoUrl(jsonResponse)
                val requestId = templateRepository.extractRequestId(jsonResponse)
                Log.i(TAG, "[Template Phase 3/5] Extracted direct videoUrl='$videoUrl', requestId='$requestId'")

                // 4. Nếu chưa có video trực tiếp mà có requestId -> Poll kết quả
                if (videoUrl.isNullOrBlank() && !requestId.isNullOrBlank()) {
                    Log.i(TAG, "[Template Phase 4/5] Polling AI server for requestId='$requestId' (timeout=180s)...")
                    if (isAdded) {
                        loadingDialog?.updateMessage(getString(R.string.loading_generating_video_ai), getString(R.string.loading_processing_progress, 30))
                    }
                    val pollResult = templateRepository.pollImageEditingResult(
                        requestId = requestId,
                        intervalMillis = 5_000L,
                        timeoutMillis = 180_000L,
                        onProgress = { progress ->
                            Log.d(TAG, "  Polling progress: $progress% for requestId=$requestId")
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
                        Log.i(TAG, "[Template Phase 4/5] Polling SUCCEEDED! videoUrl='$videoUrl'")
                    } else {
                        val pollError = pollResult.exceptionOrNull()?.message ?: getString(R.string.loading_timeout_error)
                        Log.e(TAG, "[Template Phase 4/5] Polling FAILED: $pollError")
                        isGeneratingVideo = false
                        if (isResumed && isAdded) {
                            hideLoadingDialog()
                            bindingOrNull?.btnCreateVideo?.isEnabled = true
                            Toast.makeText(requireContext(), getString(R.string.toast_process_video_failed, pollError), Toast.LENGTH_LONG).show()
                        } else {
                            pendingErrorMsg = pollError
                        }
                        return@launch
                    }
                }

                // 5. Nếu video từ URL hoặc có chọn nhạc: tải về/ghép nhạc và lưu vào local App Documents
                var finalLocalVideoPath: String? = null

                if (!videoUrl.isNullOrBlank()) {
                    Log.i(TAG, "[Template Phase 5/5] Downloading and saving video: $videoUrl")
                    if (isAdded) {
                        loadingDialog?.updateMessage(getString(R.string.loading_preparing_video), getString(R.string.loading_storing_local))
                    }
                    val rawVideoFile = if (File(videoUrl).exists()) {
                        File(videoUrl)
                    } else {
                        val downloadedRawVideo = File(requireContext().cacheDir, "raw_ai_${System.currentTimeMillis()}.mp4")
                        val downloadSuccess = VideoGenerator.downloadVideoFile(videoUrl, downloadedRawVideo)
                        if (downloadSuccess && downloadedRawVideo.exists()) downloadedRawVideo else null
                    }

                    if (rawVideoFile != null && rawVideoFile.exists()) {
                        Log.i(TAG, "[Template Phase 5/5] Raw AI video downloaded: ${rawVideoFile.absolutePath} (${rawVideoFile.length() / 1024} KB)")
                        val mergedFile = if (!audioPath.isNullOrBlank() || !audioUrl.isNullOrBlank()) {
                            if (isAdded) {
                                loadingDialog?.updateMessage(getString(R.string.loading_merging_music), getString(R.string.loading_please_wait))
                            }
                            val outMerged = File(requireContext().cacheDir, "merged_ai_${System.currentTimeMillis()}.mp4")
                            val audioSource = audioPath?.takeIf { File(it).exists() } ?: audioUrl.orEmpty()
                            Log.i(TAG, "[Template Phase 5/5] Merging audio track into AI video: audioSource=$audioSource")
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

                        // Lưu bản cố định vào App Documents để lưu vào lịch sử xem offline vĩnh viễn
                        val permanentDoc = VideoGenerator.saveVideoToAppDocuments(
                            context = requireContext(),
                            sourceFile = mergedFile,
                            title = "Template_AI_${System.currentTimeMillis()}"
                        )
                        finalLocalVideoPath = permanentDoc.absolutePath
                        Log.i(TAG, "[Template Phase 5/5] Permanent video document saved: $finalLocalVideoPath (${permanentDoc.length() / 1024} KB)")
                    } else {
                        finalLocalVideoPath = videoUrl
                    }
                }

                isGeneratingVideo = false
                val totalElapsed = System.currentTimeMillis() - overallStart
                Log.i(TAG, "==========================================================")
                Log.i(TAG, ">>> [TEMPLATE AI VIDEO] COMPLETED in ${totalElapsed}ms (${totalElapsed / 1000}s)!")
                Log.i(TAG, "Result video path: $finalLocalVideoPath")
                Log.i(TAG, "==========================================================")

                // 6. Điều hướng sang màn xem video kết quả và tự động lưu vào Lịch sử
                if (!finalLocalVideoPath.isNullOrBlank()) {
                    saveToHistory(finalLocalVideoPath, photoUri.toString())
                    navigateToResult(finalLocalVideoPath, photoUri.toString(), audioPath)
                } else {
                    navigateToResult(null, photoUri.toString(), audioPath)
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.w(TAG, "[TEMPLATE AI VIDEO] Video generation cancelled by user.")
                isGeneratingVideo = false
                hideLoadingDialog()
                bindingOrNull?.btnCreateVideo?.isEnabled = true
            } catch (e: Exception) {
                Log.e(TAG, "[TEMPLATE AI VIDEO] Video generation failed with exception: ${e.message}", e)
                if (!isGeneratingVideo) return@launch
                isGeneratingVideo = false
                if (isResumed && isAdded) {
                    hideLoadingDialog()
                    bindingOrNull?.btnCreateVideo?.isEnabled = true
                    Toast.makeText(requireContext(), getString(R.string.toast_error_occurred, e.message ?: ""), Toast.LENGTH_SHORT).show()
                } else {
                    pendingErrorMsg = e.message ?: ""
                }
            } finally {
                generateVideoJob = null
            }
        }
    }

    private fun navigateToResult(videoUrl: String?, inputPhotoUri: String, audioPath: String?) {
        val bundle = Bundle().apply {
            putString(TemplateVideoResultFragment.ARG_VIDEO_URL, videoUrl)
            putString(TemplateVideoResultFragment.ARG_MUSIC_TRACK_ID, selectedMusicTrackId)
            putString(TemplateVideoResultFragment.ARG_MUSIC_TITLE, selectedMusicTitle.orEmpty())
            putString(TemplateVideoResultFragment.ARG_MUSIC_AUDIO_URL, selectedMusicAudioUrl)
            putString(TemplateVideoResultFragment.ARG_MUSIC_FILE_PATH, audioPath)
            putString(TemplateVideoResultFragment.ARG_MUSIC_IMAGE_URL, selectedMusicImageUrl)
        }
        if (isResumed && isAdded) {
            hideLoadingDialog()
            bindingOrNull?.btnCreateVideo?.isEnabled = true
            findNavController().navigateSafe(R.id.action_createTemplateVideoFragment_to_templateVideoResultFragment, bundle)
        } else {
            pendingResultBundle = bundle
        }
    }

    private fun saveToHistory(localVideoPath: String, photoUri: String) {
        try {
            val dateFormat = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
            val dateStr = dateFormat.format(Date())
            val title = if (!selectedMusicTitle.isNullOrBlank()) {
                "$templateTitle • ${getString(R.string.detail_music_label, selectedMusicTitle)}"
            } else {
                templateTitle
            }
            val item = HistoryItemModel(
                title = title,
                date = dateStr,
                type = "Template",
                imageUrl = templateThumbnailUrl.ifBlank { photoUri },
                imageUri = localVideoPath
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
            val tempFile = File(context.cacheDir, "input_template_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                inputStream.copyTo(out)
            }
            tempFile
        } catch (e: Exception) {
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
    }
}
