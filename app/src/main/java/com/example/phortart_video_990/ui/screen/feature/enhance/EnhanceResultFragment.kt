package com.example.phortart_video_990.ui.screen.feature.enhance

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.repository.AIPromptRepository
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.databinding.FragmentEnhanceResultBinding
import kotlinx.coroutines.launch
import android.os.SystemClock
import com.example.phortart_video_990.core.utils.ShareUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EnhanceResultFragment : BaseFragment<FragmentEnhanceResultBinding>(FragmentEnhanceResultBinding::inflate) {

    private val promptRepository by lazy { AIPromptRepository(requireContext()) }
    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private var userPrompt: String = ""
    private var imageUrl: String = ""
    private var lastShareClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userPrompt = arguments?.getString(KEY_USER_PROMPT).orEmpty()
        imageUrl = arguments?.getString(KEY_IMAGE_URL).orEmpty()
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            binding.btnShare.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navBarInset + (20 * resources.displayMetrics.density).toInt()
            }
            insets
        }

        binding.tvUserPrompt.text = userPrompt

        if (imageUrl.isNotBlank()) {
            binding.ivResultImage.load(imageUrl) {
                crossfade(true)
            }
        }
    }

    override fun initListener() {
        binding.header.setOnBackClickListener {
            findNavController().popBackStack()
        }

        binding.btnDownload.setOnClickListener {
            saveImageToDevice()
        }

        binding.btnGenerateAgain.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnShare.setOnClickListener {
            if (SystemClock.elapsedRealtime() - lastShareClickTime < 1000L) return@setOnClickListener
            lastShareClickTime = SystemClock.elapsedRealtime()
            shareImage()
        }
    }

    private fun saveImageToDevice() {
        val currentDrawable = binding.ivResultImage.drawable
        if (imageUrl.isBlank() && currentDrawable == null) {
            Toast.makeText(requireContext(), getString(R.string.toast_no_image_to_save), Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.btnDownload.isEnabled = false
            val savedUri = promptRepository.saveImageToGallery(
                imageSource = imageUrl,
                fallbackDrawable = currentDrawable
            )
            binding.btnDownload.isEnabled = true

            if (savedUri != null) {
                com.example.phortart_video_990.core.dialog.AppDialogHelper.showSaveSuccessDialog(requireActivity())
            } else {
                Toast.makeText(requireContext(), getString(R.string.toast_cannot_read_image), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareImage() {
        val currentDrawable = binding.ivResultImage.drawable
        if (imageUrl.isBlank() && currentDrawable == null) {
            Toast.makeText(requireContext(), getString(R.string.toast_cannot_open_share), Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.btnShare.isEnabled = false
            try {
                val effectiveSource: Any = if (imageUrl.isNotBlank()) {
                    imageUrl
                } else {
                    currentDrawable?.let { com.example.phortart_video_990.core.utils.MediaSaveUtils.drawableToBitmap(it) } ?: return@launch
                }

                val success = ShareUtils.shareImage(
                    context = requireContext(),
                    imageSource = effectiveSource,
                    chooserTitle = getString(R.string.share_artwork_title)
                )
                if (!success && isAdded) {
                    Toast.makeText(requireContext(), getString(R.string.toast_cannot_open_share), Toast.LENGTH_SHORT).show()
                }
            } finally {
                binding.btnShare.isEnabled = true
            }
        }
    }

    companion object {
        const val KEY_USER_PROMPT = "key_user_prompt"
        const val KEY_IMAGE_URL = "key_image_url"
    }
}
