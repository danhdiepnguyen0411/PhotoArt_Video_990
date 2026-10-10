package com.example.phortart_video_990.ui.screen.history

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.utils.ShareUtils
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.databinding.DialogHistoryPreviewBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class HistoryPreviewDialog : DialogFragment() {

    private var _binding: DialogHistoryPreviewBinding? = null
    private val binding get() = _binding!!

    private var historyItem: HistoryItemModel? = null
    private var onDeleteConfirmed: ((HistoryItemModel) -> Unit)? = null

    private var isPlaying = false
    private var progressPollingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogHistoryPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
    }

    private fun setupViews() {
        val item = historyItem ?: return

        binding.tvPreviewTitle.text = item.title
        binding.tvPreviewCategory.text = item.type
        binding.tvPreviewDate.text = item.date

        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnDelete.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Xóa mục này")
                .setMessage("Bạn có chắc chắn muốn xóa khỏi lịch sử và bộ nhớ?")
                .setPositiveButton("Xóa") { _, _ ->
                    onDeleteConfirmed?.invoke(item)
                    dismiss()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        binding.btnShare.setOnClickListener {
            handleShare(item)
        }

        // Determine content type: Video, Image, or Prompt
        val isVideo = item.type.equals("Gom ảnh", ignoreCase = true) ||
                item.type.equals("Mẫu AI", ignoreCase = true) ||
                item.imageUri?.endsWith(".mp4", ignoreCase = true) == true

        val isPrompt = item.type.equals("Prompt AI", ignoreCase = true)

        if (isVideo) {
            setupVideoPreview(item)
        } else if (isPrompt) {
            setupPromptPreview(item)
        } else {
            setupImagePreview(item)
        }
    }

    private fun setupVideoPreview(item: HistoryItemModel) {
        val path = item.imageUri ?: item.imageUrl
        if (path.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Không tìm thấy file video", Toast.LENGTH_SHORT).show()
            return
        }

        binding.previewVideoView.isVisible = true
        binding.btnPlayPause.isVisible = true
        binding.previewProgressBar.isVisible = true

        val videoUri = if (path.startsWith("content://") || path.startsWith("file://") ||
            path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)
        ) {
            Uri.parse(path)
        } else {
            Uri.fromFile(File(path))
        }

        binding.previewVideoView.setVideoURI(videoUri)
        binding.previewVideoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            binding.previewVideoView.start()
            isPlaying = true
            updatePlayIcon(true)
            startProgressPolling()
        }

        binding.previewVideoView.setOnErrorListener { _, _, _ ->
            Toast.makeText(requireContext(), "Không thể phát video này", Toast.LENGTH_SHORT).show()
            true
        }

        binding.btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        binding.previewVideoView.setOnClickListener {
            togglePlayPause()
        }
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            binding.previewVideoView.pause()
            isPlaying = false
            updatePlayIcon(false)
        } else {
            binding.previewVideoView.start()
            isPlaying = true
            updatePlayIcon(true)
            startProgressPolling()
        }
    }

    private fun updatePlayIcon(playing: Boolean) {
        binding.ivPlayPauseIcon.setImageResource(
            if (playing) R.drawable.ic_pause_small else R.drawable.ic_play_small
        )
        // Fade out play/pause button when playing
        if (playing) {
            binding.btnPlayPause.animate().alpha(0f).setDuration(600).withEndAction {
                if (isPlaying) binding.btnPlayPause.isVisible = false
            }.start()
        } else {
            binding.btnPlayPause.alpha = 1f
            binding.btnPlayPause.isVisible = true
        }
    }

    private fun startProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive && isPlaying) {
                val current = binding.previewVideoView.currentPosition
                val total = binding.previewVideoView.duration
                if (total > 0) {
                    val progress = (current.toFloat() / total * 1000).toInt()
                    binding.previewProgressBar.progress = progress
                }
                delay(100)
            }
        }
    }

    private fun setupPromptPreview(item: HistoryItemModel) {
        binding.cardPromptContainer.isVisible = true
        binding.previewProgressBar.isVisible = false
        binding.tvPromptContent.text = item.title

        val source: Any? = item.imageUri ?: item.imageUrl ?: if (item.imageRes != 0) item.imageRes else null
        if (source != null) {
            binding.cardPromptImage.isVisible = true
            binding.ivPromptResultImage.load(source) {
                crossfade(true)
                placeholder(R.drawable.bg_card_white)
                error(R.drawable.bg_card_white)
            }
        } else {
            binding.cardPromptImage.isVisible = false
        }

        binding.btnCopyPrompt.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Prompt", item.title)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Đã sao chép prompt!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupImagePreview(item: HistoryItemModel) {
        binding.previewImageView.isVisible = true
        binding.previewProgressBar.isVisible = false

        val source: Any? = item.imageUri ?: item.imageUrl ?: if (item.imageRes != 0) item.imageRes else null
        if (source != null) {
            binding.previewImageView.load(source) {
                crossfade(true)
                placeholder(R.drawable.bg_card_white)
                error(R.drawable.bg_card_white)
            }
        }
    }

    private fun handleShare(item: HistoryItemModel) {
        val path = item.imageUri ?: item.imageUrl
        val isVideo = item.type.equals("Gom ảnh", ignoreCase = true) ||
                item.type.equals("Mẫu AI", ignoreCase = true) ||
                path?.endsWith(".mp4", ignoreCase = true) == true

        if (isVideo && !path.isNullOrBlank()) {
            ShareUtils.shareVideo(requireContext(), path, chooserTitle = item.title)
        } else if (!path.isNullOrBlank()) {
            // Share image via intent
            try {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(android.content.Intent.EXTRA_STREAM, Uri.parse(path))
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(android.content.Intent.createChooser(shareIntent, "Chia sẻ ảnh"))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Không thể chia sẻ ảnh", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Share prompt text
            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, item.title)
            }
            startActivity(android.content.Intent.createChooser(sendIntent, "Chia sẻ prompt"))
        }
    }

    override fun onPause() {
        super.onPause()
        if (isPlaying) {
            binding.previewVideoView.pause()
            isPlaying = false
        }
        progressPollingJob?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressPollingJob?.cancel()
        _binding = null
    }

    companion object {
        fun newInstance(
            item: HistoryItemModel,
            onDelete: (HistoryItemModel) -> Unit
        ): HistoryPreviewDialog {
            return HistoryPreviewDialog().apply {
                this.historyItem = item
                this.onDeleteConfirmed = onDelete
            }
        }
    }
}
