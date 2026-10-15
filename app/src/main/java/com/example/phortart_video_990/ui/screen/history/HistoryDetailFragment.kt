package com.example.phortart_video_990.ui.screen.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.dialog.AppDialogHelper
import com.example.phortart_video_990.core.utils.ShareUtils
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.databinding.FragmentHistoryDetailBinding
import com.example.phortart_video_990.ui.screen.feature.enhance.EnhanceFragment
import androidx.lifecycle.lifecycleScope
import com.example.phortart_video_990.core.utils.VideoGenerator
import com.google.gson.Gson
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.SystemClock
import com.example.phortart_video_990.core.utils.MediaSaveUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class HistoryDetailFragment : BaseFragment<FragmentHistoryDetailBinding>(FragmentHistoryDetailBinding::inflate) {

    companion object {
        const val KEY_HISTORY_ITEM_JSON = "key_history_item_json"
    }

    private val historyRepository by lazy { HistoryRepository(requireContext()) }
    private var historyItem: HistoryItemModel? = null
    private var lastShareClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val json = arguments?.getString(KEY_HISTORY_ITEM_JSON)
        if (!json.isNullOrBlank()) {
            try {
                historyItem = Gson().fromJson(json, HistoryItemModel::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var btnShareTop: View? = null
    private var btnDeleteTop: View? = null

    override fun initView() {
        // Window Insets for edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            binding.layoutBottomBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navBarInset
            }
            insets
        }

        val actionView = layoutInflater.inflate(R.layout.layout_header_action_history_detail, binding.header.flRightAction, false)
        binding.header.setRightCustomView(actionView)
        btnShareTop = actionView.findViewById(R.id.btnShareTop)
        btnDeleteTop = actionView.findViewById(R.id.btnDeleteTop)

        val item = historyItem ?: return
        bindItemData(item)
    }

    private fun bindItemData(item: HistoryItemModel) {
        val localizedTitle = item.getLocalizedTitle(requireContext())
        binding.tvDetailTitle.text = localizedTitle.ifBlank { getString(R.string.detail_default_title) }
        binding.tvDetailDate.text = item.date.ifBlank { getString(R.string.detail_recently_created) }

        val type = item.type.lowercase()

        when {
            // 1. KHÔI PHỤC ẢNH CŨ
            type.contains("khôi phục") || type.contains("restore") -> {
                binding.header.setTitle(getString(R.string.detail_header_restore))
                binding.tvMediaTypeBadge.text = getString(R.string.detail_badge_restore)
                binding.tvMediaTypeBadge.setBackgroundResource(R.drawable.bg_badge_after)

                binding.sliderView.visibility = View.VISIBLE
                binding.ivDetailMedia.visibility = View.GONE
                binding.flPlayOverlay.visibility = View.GONE
                binding.tvVideoDuration.visibility = View.GONE
                binding.videoViewDetail.visibility = View.GONE

                val mediaSource: Any? = item.imageUri ?: item.imageUrl ?: if (item.imageRes != 0) item.imageRes else null
                val beforeSource: Any? = item.beforeUri

                val onSizeReady: (Int, Int) -> Unit = { w, h ->
                    if (w > 0 && h > 0 && isAdded && bindingOrNull != null) {
                        val density = resources.displayMetrics.density
                        val screenWidth = resources.displayMetrics.widthPixels
                        val padding = (32 * density).toInt()
                        val availWidth = (screenWidth - padding).coerceAtLeast(1)
                        val targetHeight = (availWidth * (h.toFloat() / w.toFloat())).toInt().coerceIn((180 * density).toInt(), (520 * density).toInt())
                        binding.cardMediaContainer.updateLayoutParams { height = targetHeight }
                    }
                }

                if (beforeSource != null) {
                    binding.sliderView.setBeforeImage(beforeSource, isFallback = false, onSizeReady = onSizeReady)
                    binding.sliderView.setAfterImage(mediaSource ?: beforeSource, onSizeReady = onSizeReady)
                } else if (mediaSource != null) {
                    // Fallback when beforeUri was not saved: use same image with vintage/B&W effect on Trước
                    binding.sliderView.setBeforeImage(mediaSource, isFallback = true, onSizeReady = onSizeReady)
                    binding.sliderView.setAfterImage(mediaSource, onSizeReady = onSizeReady)
                } else {
                    binding.sliderView.setBeforeImage(R.drawable.bg_card_white, isFallback = false)
                    binding.sliderView.setAfterImage(R.drawable.bg_card_white)
                }

                // Show Restore section card
                binding.cardRestoreSection.visibility = View.VISIBLE
                binding.cardPromptSection.visibility = View.GONE
                binding.cardMusicSection.visibility = View.GONE

                binding.btnRecreate.text = getString(R.string.detail_btn_restore_another)
                binding.btnRecreate.setOnClickListener {
                    findNavController().navigateSafe(R.id.action_historyDetailFragment_to_restoreFragment)
                }
            }

            // 2. PROMPT AI
            type.contains("prompt") -> {
                binding.header.setTitle(getString(R.string.detail_header_prompt))
                binding.tvMediaTypeBadge.text = getString(R.string.detail_badge_prompt)
                binding.tvMediaTypeBadge.setBackgroundResource(R.drawable.bg_media_badge_capsule)

                binding.sliderView.visibility = View.GONE
                binding.ivDetailMedia.visibility = View.VISIBLE
                binding.videoViewDetail.visibility = View.GONE
                binding.flPlayOverlay.visibility = View.GONE
                binding.tvVideoDuration.visibility = View.GONE

                loadMediaImage(item)

                // Show Prompt section card
                binding.cardPromptSection.visibility = View.VISIBLE
                binding.cardRestoreSection.visibility = View.GONE
                binding.cardMusicSection.visibility = View.GONE

                val promptText = if (item.title.isNotBlank()) item.getLocalizedTitle(requireContext()) else getString(R.string.prompt_fantasy_full)
                binding.tvPromptContent.text = promptText

                binding.btnCopyPrompt.setOnClickListener {
                    copyToClipboard("Prompt", promptText)
                }

                binding.btnRecreate.text = getString(R.string.detail_btn_reuse_prompt)
                binding.btnRecreate.setOnClickListener {
                    findNavController().navigateSafe(
                        R.id.action_historyDetailFragment_to_enhanceFragment,
                        bundleOf(
                            EnhanceFragment.KEY_INITIAL_PROMPT to promptText,
                            EnhanceFragment.KEY_INITIAL_STYLE to (item.styleName.orEmpty())
                        )
                    )
                }
            }

            // 3. GOM ẢNH / VIDEO GHÉP NHẠC
            type.contains("gom") || type.contains("music") || type.contains("video") -> {
                binding.header.setTitle(getString(R.string.detail_header_memories))
                binding.tvMediaTypeBadge.text = getString(R.string.detail_badge_memories)
                binding.tvMediaTypeBadge.setBackgroundResource(R.drawable.bg_media_badge)

                binding.sliderView.visibility = View.GONE
                binding.ivDetailMedia.visibility = View.VISIBLE
                binding.videoViewDetail.visibility = View.GONE
                binding.flPlayOverlay.visibility = View.VISIBLE
                binding.tvVideoDuration.visibility = View.VISIBLE
                binding.tvVideoDuration.text = "00:15"

                loadMediaImage(item)

                // Show Music section card
                binding.cardMusicSection.visibility = View.VISIBLE
                binding.cardPromptSection.visibility = View.GONE
                binding.cardRestoreSection.visibility = View.GONE

                val trackName = if (item.title.contains("Nhạc:") || item.title.contains("Music:")) {
                    item.title.substringAfter(":").trim()
                } else {
                    item.title
                }
                binding.tvMusicTrackTitle.text = getString(R.string.detail_music_label, trackName)

                binding.btnRecreate.text = getString(R.string.detail_btn_new_video)
                binding.btnRecreate.setOnClickListener {
                    findNavController().navigateSafe(R.id.action_historyDetailFragment_to_memoriesFragment)
                }

                binding.flPlayOverlay.setOnClickListener {
                    playVideoOrPreview(item)
                }
                binding.cardMediaContainer.setOnClickListener {
                    playVideoOrPreview(item)
                }
            }

            // 4. TEMPLATE / WAACKING
            else -> {
                binding.header.setTitle(getString(R.string.detail_header_template))
                binding.tvMediaTypeBadge.text = getString(R.string.detail_badge_template)
                binding.tvMediaTypeBadge.setBackgroundResource(R.drawable.bg_badge_hot)

                binding.sliderView.visibility = View.GONE
                binding.ivDetailMedia.visibility = View.VISIBLE
                binding.videoViewDetail.visibility = View.GONE
                binding.flPlayOverlay.visibility = View.VISIBLE
                binding.tvVideoDuration.visibility = View.VISIBLE
                binding.tvVideoDuration.text = "00:15"

                loadMediaImage(item)

                binding.cardMusicSection.visibility = View.VISIBLE
                binding.cardPromptSection.visibility = View.GONE
                binding.cardRestoreSection.visibility = View.GONE
                binding.tvMusicTrackTitle.text = item.title

                binding.btnRecreate.text = getString(R.string.detail_btn_use_template)
                binding.btnRecreate.setOnClickListener {
                    findNavController().navigateSafe(R.id.action_historyDetailFragment_to_createTemplateVideoFragment)
                }

                binding.flPlayOverlay.setOnClickListener {
                    playVideoOrPreview(item)
                }
                binding.cardMediaContainer.setOnClickListener {
                    playVideoOrPreview(item)
                }
            }
        }
    }

    private fun loadMediaImage(item: HistoryItemModel) {
        val rawSource: Any? = item.imageUrl ?: item.imageUri ?: if (item.imageRes != 0) item.imageRes else null
        val mediaSource: Any? = when {
            rawSource is String && rawSource.startsWith("/") -> File(rawSource)
            rawSource is String && rawSource.startsWith("content://") -> Uri.parse(rawSource)
            else -> rawSource
        }

        if (mediaSource != null) {
            binding.ivDetailMedia.load(mediaSource) {
                crossfade(true)
                placeholder(R.drawable.bg_card_white)
                error(R.drawable.bg_card_white)
            }
        } else {
            binding.ivDetailMedia.setImageResource(R.drawable.bg_card_white)
        }
    }

    override fun initListener() {
        // Back
        binding.header.setOnBackClickListener {
            findNavController().popBackStack()
        }

        // Delete confirmation (600Y Tree Scan custom dialog)
        btnDeleteTop?.setOnClickListener {
            val item = historyItem ?: return@setOnClickListener
            AppDialogHelper.showDeleteHistoryDialog(requireActivity()) {
                historyRepository.deleteHistoryItem(item)
                Toast.makeText(requireContext(), getString(R.string.toast_deleted_from_history), Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }

        // Share
        btnShareTop?.setOnClickListener {
            if (SystemClock.elapsedRealtime() - lastShareClickTime < 1000L) return@setOnClickListener
            lastShareClickTime = SystemClock.elapsedRealtime()
            shareCurrentItem()
        }

        // Save to device gallery
        binding.btnSaveMedia.setOnClickListener {
            saveCurrentMedia()
        }
    }

    private fun saveCurrentMedia() {
        val item = historyItem ?: run {
            Toast.makeText(requireContext(), getString(R.string.toast_no_image_to_save), Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.btnSaveMedia.isEnabled = false
            try {
                val type = item.type.lowercase()
                val isVideo = item.isVideo ||
                        type.contains("video") ||
                        type.contains("gom") ||
                        type.contains("music") ||
                        (item.imageUri?.endsWith(".mp4", ignoreCase = true) == true) ||
                        (item.imageUrl?.endsWith(".mp4", ignoreCase = true) == true)

                if (isVideo) {
                    saveVideoItem(item)
                } else {
                    saveImageItem(item)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (isAdded) {
                    Toast.makeText(requireContext(), getString(R.string.toast_cannot_read_image), Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (isAdded) {
                    binding.btnSaveMedia.isEnabled = true
                }
            }
        }
    }

    private suspend fun saveVideoItem(item: HistoryItemModel) {
        val rawPath = item.imageUri ?: item.imageUrl
        if (rawPath.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.toast_video_file_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        val videoFileToSave: File? = withContext(Dispatchers.IO) {
            when {
                // 1. Remote URL (http/https) -> download or use cached
                rawPath.startsWith("http://", ignoreCase = true) || rawPath.startsWith("https://", ignoreCase = true) -> {
                    val fileName = "history_vid_" + Math.abs(rawPath.hashCode()) + ".mp4"
                    val cachedFile = File(requireContext().cacheDir, fileName)
                    if (cachedFile.exists() && cachedFile.length() > 0) {
                        cachedFile
                    } else {
                        val ok = VideoGenerator.downloadVideoFile(rawPath, cachedFile)
                        if (ok && cachedFile.exists() && cachedFile.length() > 0) cachedFile else null
                    }
                }
                // 2. Content URI -> copy to cache temp file
                rawPath.startsWith("content://", ignoreCase = true) -> {
                    try {
                        val tempFile = File(requireContext().cacheDir, "history_save_${System.currentTimeMillis()}.mp4")
                        requireContext().contentResolver.openInputStream(Uri.parse(rawPath))?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 0) tempFile else null
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
                // 3. Local file path
                else -> {
                    val cleanPath = rawPath.removePrefix("file://")
                    val f = File(cleanPath)
                    if (f.exists() && f.length() > 0) f else null
                }
            }
        }

        if (videoFileToSave != null && videoFileToSave.exists()) {
            val title = if (item.title.isNotBlank()) "PhotoArt_${item.title.replace(" ", "_")}" else "PhotoArt_Video_${System.currentTimeMillis()}"
            val savedUri = VideoGenerator.copyVideoToGallery(
                context = requireContext(),
                videoFile = videoFileToSave,
                title = title
            )
            if (savedUri != null) {
                AppDialogHelper.showSaveSuccessDialog(requireActivity())
            } else {
                Toast.makeText(requireContext(), getString(R.string.toast_cannot_save_video), Toast.LENGTH_SHORT).show()
            }
        } else {
            // Nếu không tìm thấy file video MP4 cục bộ, fallback sang lưu ảnh preview nếu có
            saveImageItem(item)
        }
    }

    private suspend fun saveImageItem(item: HistoryItemModel) {
        val type = item.type.lowercase()
        val targetSource = item.imageUrl ?: item.imageUri ?: item.beforeUri

        val fallbackDrawable: Drawable? = if (type.contains("restore") || type.contains("khôi phục")) {
            binding.sliderView.getAfterDrawable() ?: binding.ivDetailMedia.drawable
        } else {
            binding.ivDetailMedia.drawable
        }

        val prefix = if (type.contains("restore") || type.contains("khôi phục")) {
            "PhotoArt_Restore"
        } else {
            "PhotoArt_Prompt"
        }

        val savedUri: Uri? = if (!targetSource.isNullOrBlank()) {
            MediaSaveUtils.saveImageToGallery(
                context = requireContext(),
                imageSource = targetSource,
                fallbackDrawable = fallbackDrawable,
                filenamePrefix = prefix
            )
        } else if (fallbackDrawable != null) {
            val bmp = MediaSaveUtils.drawableToBitmap(fallbackDrawable)
            MediaSaveUtils.saveBitmapToGallery(requireContext(), bmp, prefix)
        } else if (item.imageRes != 0) {
            val bmp = BitmapFactory.decodeResource(resources, item.imageRes)
            if (bmp != null) MediaSaveUtils.saveBitmapToGallery(requireContext(), bmp, prefix) else null
        } else null

        if (savedUri != null) {
            AppDialogHelper.showSaveSuccessDialog(requireActivity())
        } else {
            Toast.makeText(requireContext(), getString(R.string.toast_cannot_read_image), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareCurrentItem() {
        val item = historyItem ?: return
        val path = item.imageUri ?: item.imageUrl

        if (!path.isNullOrBlank() && (path.endsWith(".mp4", ignoreCase = true) || item.isVideo)) {
            ShareUtils.shareVideo(requireContext(), path, getString(R.string.share_artwork_title))
        } else {
            val imageSource: Any? = when {
                !path.isNullOrBlank() -> path
                item.imageRes != 0 -> item.imageRes
                !item.beforeUri.isNullOrBlank() -> item.beforeUri
                else -> null
            }

            if (imageSource != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val success = ShareUtils.shareImage(
                        context = requireContext(),
                        imageSource = imageSource,
                        chooserTitle = getString(R.string.share_artwork_title)
                    )
                    if (!success && isAdded) {
                        Toast.makeText(requireContext(), getString(R.string.toast_cannot_open_share), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.toast_cannot_read_image), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playVideoOrPreview(item: HistoryItemModel) {
        val videoUrlOrPath = item.imageUri ?: item.imageUrl
        if (videoUrlOrPath.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.toast_video_file_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        // Toggle pause/play if already prepared and visible
        if (binding.videoViewDetail.visibility == View.VISIBLE) {
            if (binding.videoViewDetail.isPlaying) {
                binding.videoViewDetail.pause()
                binding.flPlayOverlay.visibility = View.VISIBLE
            } else {
                binding.videoViewDetail.start()
                binding.flPlayOverlay.visibility = View.GONE
            }
            return
        }

        // If it's a remote URL (http/https), download/cache to cacheDir for 100% reliable local playback
        if (videoUrlOrPath.startsWith("http://", ignoreCase = true) || videoUrlOrPath.startsWith("https://", ignoreCase = true)) {
            val fileName = "history_vid_" + Math.abs(videoUrlOrPath.hashCode()) + ".mp4"
            val cachedFile = File(requireContext().cacheDir, fileName)
            if (cachedFile.exists() && cachedFile.length() > 0) {
                startPlayingFile(cachedFile, videoUrlOrPath)
            } else {
                binding.pbDetailLoading.visibility = View.VISIBLE
                binding.flPlayOverlay.visibility = View.GONE
                viewLifecycleOwner.lifecycleScope.launch {
                    val success = VideoGenerator.downloadVideoFile(videoUrlOrPath, cachedFile)
                    binding.pbDetailLoading.visibility = View.GONE
                    if (success && cachedFile.exists() && cachedFile.length() > 0) {
                        startPlayingFile(cachedFile, videoUrlOrPath)
                    } else {
                        startPlayingUri(Uri.parse(videoUrlOrPath), videoUrlOrPath)
                    }
                }
            }
            return
        }

        // Local content:// or file path
        val uri = when {
            videoUrlOrPath.startsWith("content://", ignoreCase = true) -> Uri.parse(videoUrlOrPath)
            videoUrlOrPath.startsWith("file://", ignoreCase = true) -> Uri.parse(videoUrlOrPath)
            else -> {
                val file = File(videoUrlOrPath)
                if (file.exists()) Uri.fromFile(file) else Uri.parse(videoUrlOrPath)
            }
        }
        startPlayingUri(uri, videoUrlOrPath)
    }

    private fun startPlayingFile(file: File, rawPath: String) {
        startPlayingUri(Uri.fromFile(file), file.absolutePath)
    }

    private fun startPlayingUri(uri: Uri, rawPath: String) {
        // VideoView extends SurfaceView: MUST be VISIBLE for SurfaceHolder to be created!
        binding.videoViewDetail.visibility = View.VISIBLE

        binding.videoViewDetail.setOnPreparedListener { mp ->
            mp.isLooping = true
            binding.ivDetailMedia.visibility = View.GONE
            binding.flPlayOverlay.visibility = View.GONE
            mp.start()
        }

        binding.videoViewDetail.setOnErrorListener { _, _, _ ->
            binding.videoViewDetail.visibility = View.GONE
            binding.ivDetailMedia.visibility = View.VISIBLE
            binding.flPlayOverlay.visibility = View.VISIBLE
            openVideoExternal(rawPath)
            true
        }

        binding.videoViewDetail.setOnClickListener {
            if (binding.videoViewDetail.isPlaying) {
                binding.videoViewDetail.pause()
                binding.flPlayOverlay.visibility = View.VISIBLE
            } else {
                binding.videoViewDetail.start()
                binding.flPlayOverlay.visibility = View.GONE
            }
        }

        val localFile = File(rawPath)
        if (localFile.exists()) {
            binding.videoViewDetail.setVideoPath(localFile.absolutePath)
        } else {
            binding.videoViewDetail.setVideoURI(uri)
        }
    }

    private fun openVideoExternal(path: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = when {
                path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true) || path.startsWith("content://", ignoreCase = true) -> {
                    Uri.parse(path)
                }
                else -> {
                    val file = File(path.removePrefix("file://"))
                    if (file.exists()) {
                        androidx.core.content.FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.fileprovider",
                            file
                        )
                    } else Uri.parse(path)
                }
            }
            intent.setDataAndType(uri, "video/*")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), getString(R.string.toast_video_play_error), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        if (binding.videoViewDetail.isPlaying) {
            binding.videoViewDetail.pause()
            binding.flPlayOverlay.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        binding.videoViewDetail.stopPlayback()
        super.onDestroyView()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(requireContext(), getString(R.string.toast_prompt_copied), Toast.LENGTH_SHORT).show()
    }
}
