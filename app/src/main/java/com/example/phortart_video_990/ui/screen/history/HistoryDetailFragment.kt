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
import androidx.lifecycle.lifecycleScope
import com.example.phortart_video_990.core.utils.VideoGenerator
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.File

class HistoryDetailFragment : BaseFragment<FragmentHistoryDetailBinding>(FragmentHistoryDetailBinding::inflate) {

    companion object {
        const val KEY_HISTORY_ITEM_JSON = "key_history_item_json"
    }

    private val historyRepository by lazy { HistoryRepository(requireContext()) }
    private var historyItem: HistoryItemModel? = null

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

    override fun initView() {
        // Window Insets for edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            binding.layoutTopBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarInset
            }
            binding.layoutBottomBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navBarInset
            }
            insets
        }

        val item = historyItem ?: return
        bindItemData(item)
    }

    private fun bindItemData(item: HistoryItemModel) {
        binding.tvDetailTitle.text = item.title.ifBlank { "Tác phẩm sáng tạo" }
        binding.tvDetailDate.text = item.date.ifBlank { "Vừa tạo gần đây" }

        val type = item.type.lowercase()

        when {
            // 1. KHÔI PHỤC ẢNH CŨ
            type.contains("khôi phục") || type.contains("restore") -> {
                binding.tvHeaderTitle.text = "Chi tiết Khôi phục"
                binding.tvMediaTypeBadge.text = "Khôi phục 4K"
                binding.tvMediaTypeBadge.setBackgroundResource(R.drawable.bg_badge_after)

                binding.sliderView.visibility = View.VISIBLE
                binding.ivDetailMedia.visibility = View.GONE
                binding.flPlayOverlay.visibility = View.GONE
                binding.tvVideoDuration.visibility = View.GONE
                binding.videoViewDetail.visibility = View.GONE

                val mediaSource: Any? = item.imageUri ?: item.imageUrl ?: if (item.imageRes != 0) item.imageRes else null
                val beforeSource: Any? = item.beforeUri

                if (beforeSource != null) {
                    binding.sliderView.setBeforeImage(beforeSource, isFallback = false)
                    binding.sliderView.setAfterImage(mediaSource ?: beforeSource)
                } else if (mediaSource != null) {
                    // Fallback when beforeUri was not saved: use same image with vintage/B&W effect on Trước
                    binding.sliderView.setBeforeImage(mediaSource, isFallback = true)
                    binding.sliderView.setAfterImage(mediaSource)
                } else {
                    binding.sliderView.setBeforeImage(R.drawable.history_2, isFallback = false)
                    binding.sliderView.setAfterImage(R.drawable.history_2)
                }

                // Show Restore section card
                binding.cardRestoreSection.visibility = View.VISIBLE
                binding.cardPromptSection.visibility = View.GONE
                binding.cardMusicSection.visibility = View.GONE

                binding.btnRecreate.text = "Phục hồi ảnh khác"
                binding.btnRecreate.setOnClickListener {
                    findNavController().navigateSafe(R.id.action_historyDetailFragment_to_restoreFragment)
                }
            }

            // 2. PROMPT AI
            type.contains("prompt") -> {
                binding.tvHeaderTitle.text = "Chi tiết Prompt AI"
                binding.tvMediaTypeBadge.text = "Prompt AI Art"
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

                val promptText = item.title.ifBlank { "Lâu đài kỳ ảo bay giữa bầu trời thần tiên rực rỡ" }
                binding.tvPromptContent.text = promptText

                binding.btnCopyPrompt.setOnClickListener {
                    copyToClipboard("Prompt", promptText)
                }

                binding.btnRecreate.text = "Dùng lại Prompt này"
                binding.btnRecreate.setOnClickListener {
                    findNavController().navigateSafe(R.id.action_historyDetailFragment_to_enhanceFragment)
                }
            }

            // 3. GOM ẢNH / VIDEO GHÉP NHẠC
            type.contains("gom") || type.contains("music") || type.contains("video") -> {
                binding.tvHeaderTitle.text = "Chi tiết Gom ảnh"
                binding.tvMediaTypeBadge.text = "Video Gom ảnh"
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

                val trackName = if (item.title.contains("Nhạc:")) {
                    item.title.substringAfter("Nhạc:").trim()
                } else {
                    "Lumen Phonk (Slowed)"
                }
                binding.tvMusicTrackTitle.text = "Nhạc: $trackName"

                binding.btnRecreate.text = "Tạo video mới"
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
                binding.tvHeaderTitle.text = "Chi tiết Template Video"
                binding.tvMediaTypeBadge.text = "Trend Template"
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
                binding.tvMusicTrackTitle.text = "Template: ${item.title}"

                binding.btnRecreate.text = "Dùng Template này"
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
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Delete confirmation (600Y Tree Scan custom dialog)
        binding.btnDeleteTop.setOnClickListener {
            val item = historyItem ?: return@setOnClickListener
            AppDialogHelper.showDeleteHistoryDialog(requireActivity()) {
                historyRepository.deleteHistoryItem(item)
                Toast.makeText(requireContext(), "Đã xóa khỏi lịch sử", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }

        // Share
        binding.btnShareTop.setOnClickListener {
            shareCurrentItem()
        }

        // Save to device gallery (custom 600Y save success dialog)
        binding.btnSaveMedia.setOnClickListener {
            AppDialogHelper.showSaveSuccessDialog(requireActivity())
        }
    }

    private fun shareCurrentItem() {
        val item = historyItem ?: return
        val path = item.imageUri ?: item.imageUrl

        if (!path.isNullOrBlank() && (path.endsWith(".mp4", ignoreCase = true) || item.type.contains("Gom", ignoreCase = true))) {
            ShareUtils.shareVideo(requireContext(), path, "Chia sẻ tác phẩm")
        } else {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Xem tác phẩm sáng tạo AI: ${item.title}")
            }
            startActivity(Intent.createChooser(sendIntent, "Chia sẻ tác phẩm"))
        }
    }

    private fun playVideoOrPreview(item: HistoryItemModel) {
        val videoUrlOrPath = item.imageUri ?: item.imageUrl
        if (videoUrlOrPath.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Không tìm thấy video!", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(requireContext(), "Không thể phát video này", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(requireContext(), "Đã sao chép Prompt vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
    }
}
