package com.example.phortart_video_990.ui.screen.feature.createtemplate

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.dialog.AppLoadingDialog
import com.example.phortart_video_990.core.utils.AudioCacheManager
import com.example.phortart_video_990.core.utils.ImageCompressor
import com.example.phortart_video_990.core.utils.VideoGenerator
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.data.repository.TemplateRepository
import com.example.phortart_video_990.databinding.FragmentCreateTemplateVideoBinding
import com.example.phortart_video_990.ui.screen.feature.memories.VideoResultFragment
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
    private var selectedMusicTitle: String? = null
    private var selectedMusicAudioUrl: String? = null
    private var selectedMusicAudioFilePath: String? = null
    private var selectedMusicImageUrl: String? = null

    private var loadingDialog: AppLoadingDialog? = null

    // Photo picker launcher (Only 1 image)
    private val pickSingleImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri
            updateSelectedPhotoUi()
        }
    }

    override fun initView() {
        val args = arguments
        templateCode = args?.getString(ARG_TEMPLATE_CODE).orEmpty()
        templateTitle = args?.getString(ARG_TEMPLATE_TITLE).orEmpty().ifBlank { "Template AI" }
        templateCategory = args?.getString(ARG_TEMPLATE_CATEGORY).orEmpty().ifBlank { "AI Video" }
        templateThumbnailUrl = args?.getString(ARG_TEMPLATE_THUMB).orEmpty()

        binding.tvTemplateTitle.text = templateTitle
        binding.tvTemplateCategory.text = "Mẫu: $templateCategory • Yêu cầu 1 ảnh"

        if (templateThumbnailUrl.isNotBlank()) {
            binding.ivTemplateThumb.load(templateThumbnailUrl) {
                crossfade(true)
                placeholder(R.drawable.template_trendy)
                error(R.drawable.template_trendy)
            }
        } else {
            binding.ivTemplateThumb.setImageResource(R.drawable.template_trendy)
        }

        updateSelectedPhotoUi()

        // Read music selection returned or preloaded
        val stateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        stateHandle?.get<String>("selected_music_title")?.let { title ->
            if (title.isNotBlank()) {
                selectedMusicTitle = title
                binding.tvSelectedMusicTitle.text = title
                binding.tvSelectedMusicSubtitle.text = "Đã chọn làm nhạc nền"
                binding.tvMusicAction.text = "Đổi nhạc"
            }
        }
        stateHandle?.get<String?>("selected_music_audio_url")?.let { selectedMusicAudioUrl = it }
        stateHandle?.get<String?>("selected_music_file_path")?.let { selectedMusicAudioFilePath = it }
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
                binding.tvSelectedMusicSubtitle.text = "Đã chọn làm nhạc nền"
                binding.tvMusicAction.text = "Đổi nhạc"
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
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.cardUploadPhoto.setOnClickListener {
            pickSingleImageLauncher.launch("image/*")
        }

        binding.btnChangePhoto.setOnClickListener {
            pickSingleImageLauncher.launch("image/*")
        }

        binding.cardSelectMusic.setOnClickListener {
            findNavController().navigate(R.id.action_createTemplateVideoFragment_to_musicFragment)
        }

        binding.btnCreateVideo.setOnClickListener {
            val photoUri = selectedPhotoUri
            if (photoUri == null) {
                Toast.makeText(requireContext(), "Vui lòng chọn 1 ảnh chân dung trước khi tạo video!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            processAndGenerateVideo(photoUri)
        }
    }

    private fun processAndGenerateVideo(photoUri: Uri) {
        binding.btnCreateVideo.isEnabled = false
        showLoadingDialog("Đang chuẩn bị ảnh...", "Vui lòng chờ giây lát")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1. Tải nhạc nền nếu người dùng có chọn nhạc (Optional)
                val audioUrl = selectedMusicAudioUrl
                var audioPath = selectedMusicAudioFilePath
                if (!audioUrl.isNullOrBlank() && (audioPath.isNullOrBlank() || !File(audioPath).exists())) {
                    loadingDialog?.updateMessage("Đang tải nhạc nền...", "Vui lòng chờ giây lát")
                    val downloaded = AudioCacheManager.getOrDownloadAudio(requireContext(), audioUrl)
                    if (downloaded != null && downloaded.exists()) {
                        audioPath = downloaded.absolutePath
                        selectedMusicAudioFilePath = audioPath
                    }
                }

                // 2. Chuyển Uri thành File và nén qua ImageCompressor
                loadingDialog?.updateMessage("Đang xử lý ảnh đầu vào...", "Đang nén và tối ưu độ phân giải")
                val rawFile = withContext(Dispatchers.IO) { copyUriToFile(photoUri) }
                if (rawFile == null || !rawFile.exists()) {
                    hideLoadingDialog()
                    binding.btnCreateVideo.isEnabled = true
                    Toast.makeText(requireContext(), "Không thể đọc file ảnh đã chọn!", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val compressedFile = withContext(Dispatchers.IO) {
                    ImageCompressor.compressIfNeeded(requireContext(), rawFile)
                }

                // 3. Gọi backend processImageEditing
                loadingDialog?.updateMessage("Đang gửi yêu cầu tạo video...", "Backend AI đang xử lý (10%)")
                val codeToUse = templateCode.ifBlank { "ai_effect_default" }

                val processResult = templateRepository.processImageEditing(
                    file = compressedFile,
                    code = codeToUse,
                    options = "{}"
                )

                if (processResult.isFailure) {
                    val errorMsg = processResult.exceptionOrNull()?.message ?: "Lỗi gọi AI"
                    // Nếu lỗi mạng / timeout thử lấy requestId từ error nếu có
                    val reqId = templateRepository.extractRequestId(null)
                    if (reqId.isNullOrBlank()) {
                        hideLoadingDialog()
                        binding.btnCreateVideo.isEnabled = true
                        Toast.makeText(requireContext(), "Tạo video thất bại: $errorMsg", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                }

                val jsonResponse = processResult.getOrNull()
                android.util.Log.d("CreateTemplateVideo", "Backend response: $jsonResponse")
                var videoUrl = templateRepository.extractVideoUrl(jsonResponse)
                var requestId = templateRepository.extractRequestId(jsonResponse)
                android.util.Log.d("CreateTemplateVideo", "Extracted videoUrl=$videoUrl, requestId=$requestId")

                // 4. Nếu chưa có video trực tiếp mà có requestId -> Poll kết quả
                if (videoUrl.isNullOrBlank() && !requestId.isNullOrBlank()) {
                    loadingDialog?.updateMessage("Đang tạo video bằng AI...", "Đang xử lý (30%)...")
                    val pollResult = templateRepository.pollImageEditingResult(
                        requestId = requestId,
                        intervalMillis = 5_000L,
                        timeoutMillis = 180_000L,
                        onProgress = { progress ->
                            loadingDialog?.updateMessage(
                                "Đang tạo video bằng AI...",
                                "Đang xử lý ($progress%)..."
                            )
                        }
                    )

                    if (pollResult.isSuccess) {
                        videoUrl = templateRepository.extractVideoUrl(pollResult.getOrNull())
                    } else {
                        val pollError = pollResult.exceptionOrNull()?.message ?: "Quá thời gian xử lý"
                        hideLoadingDialog()
                        binding.btnCreateVideo.isEnabled = true
                        Toast.makeText(requireContext(), "Xử lý video thất bại: $pollError", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                }

                // 5. Nếu video từ URL hoặc có chọn nhạc: tải về/ghép nhạc và lưu vào local App Documents
                var finalLocalVideoPath: String? = null

                if (!videoUrl.isNullOrBlank()) {
                    loadingDialog?.updateMessage("Đang chuẩn bị video...", "Đang lưu trữ cục bộ...")
                    val rawVideoFile = if (File(videoUrl).exists()) {
                        File(videoUrl)
                    } else {
                        val downloadedRawVideo = File(requireContext().cacheDir, "raw_ai_${System.currentTimeMillis()}.mp4")
                        val downloadSuccess = VideoGenerator.downloadVideoFile(videoUrl, downloadedRawVideo)
                        if (downloadSuccess && downloadedRawVideo.exists()) downloadedRawVideo else null
                    }

                    if (rawVideoFile != null && rawVideoFile.exists()) {
                        val mergedFile = if (!audioPath.isNullOrBlank() || !audioUrl.isNullOrBlank()) {
                            loadingDialog?.updateMessage("Đang ghép nhạc nền...", "Vui lòng chờ giây lát...")
                            val outMerged = File(requireContext().cacheDir, "merged_ai_${System.currentTimeMillis()}.mp4")
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

                        // Lưu bản cố định vào App Documents để lưu vào lịch sử xem offline vĩnh viễn
                        val permanentDoc = VideoGenerator.saveVideoToAppDocuments(
                            context = requireContext(),
                            sourceFile = mergedFile,
                            title = "Template_AI_${System.currentTimeMillis()}"
                        )
                        finalLocalVideoPath = permanentDoc.absolutePath
                    } else {
                        finalLocalVideoPath = videoUrl
                    }
                }

                hideLoadingDialog()
                binding.btnCreateVideo.isEnabled = true

                // 6. Điều hướng sang màn xem video kết quả và tự động lưu vào Lịch sử
                if (!finalLocalVideoPath.isNullOrBlank()) {
                    saveToHistory(finalLocalVideoPath, photoUri.toString())
                    navigateToResult(finalLocalVideoPath, photoUri.toString(), audioPath)
                } else {
                    navigateToResult(null, photoUri.toString(), audioPath)
                }

            } catch (e: Exception) {
                hideLoadingDialog()
                binding.btnCreateVideo.isEnabled = true
                Toast.makeText(requireContext(), "Đã xảy ra lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToResult(videoUrl: String?, inputPhotoUri: String, audioPath: String?) {
        val bundle = Bundle().apply {
            putString(TemplateVideoResultFragment.ARG_VIDEO_URL, videoUrl)
            putString(TemplateVideoResultFragment.ARG_MUSIC_TITLE, selectedMusicTitle.orEmpty())
            putString(TemplateVideoResultFragment.ARG_MUSIC_AUDIO_URL, selectedMusicAudioUrl)
            putString(TemplateVideoResultFragment.ARG_MUSIC_FILE_PATH, audioPath)
            putString(TemplateVideoResultFragment.ARG_MUSIC_IMAGE_URL, selectedMusicImageUrl)
        }
        findNavController().navigate(R.id.action_createTemplateVideoFragment_to_templateVideoResultFragment, bundle)
    }

    private fun saveToHistory(localVideoPath: String, photoUri: String) {
        try {
            val dateFormat = SimpleDateFormat("d 'thg' M, yyyy • HH:mm", Locale.getDefault())
            val dateStr = dateFormat.format(Date())
            val title = if (!selectedMusicTitle.isNullOrBlank()) {
                "$templateTitle • Nhạc: $selectedMusicTitle"
            } else {
                templateTitle
            }
            val item = HistoryItemModel(
                title = title,
                date = dateStr,
                type = "Mẫu AI",
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

    private fun showLoadingDialog(title: String, subtitle: String) {
        if (loadingDialog == null) {
            loadingDialog = AppLoadingDialog.show(requireContext(), title, subtitle, cancelable = false)
        } else {
            loadingDialog?.updateMessage(title, subtitle)
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
        hideLoadingDialog()
        super.onDestroyView()
    }

    companion object {
        const val ARG_TEMPLATE_CODE = "arg_template_code"
        const val ARG_TEMPLATE_TITLE = "arg_template_title"
        const val ARG_TEMPLATE_CATEGORY = "arg_template_category"
        const val ARG_TEMPLATE_THUMB = "arg_template_thumb"
    }
}
