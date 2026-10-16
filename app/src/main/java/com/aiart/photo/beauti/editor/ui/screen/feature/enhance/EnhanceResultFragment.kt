package com.aiart.photo.beauti.editor.ui.screen.feature.enhance

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
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.core.base.BaseFragment
import com.aiart.photo.beauti.editor.data.model.HistoryItemModel
import com.aiart.photo.beauti.editor.data.repository.AIPromptRepository
import com.aiart.photo.beauti.editor.data.repository.HistoryRepository
import com.aiart.photo.beauti.editor.databinding.FragmentEnhanceResultBinding
import kotlinx.coroutines.launch
import android.os.SystemClock
import com.aiart.photo.beauti.editor.core.utils.ShareUtils
import java.io.File
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

        val cleanPrompt = userPrompt.replace(Regex("\\n{2,}"), "\n").trim()
        binding.tvUserPrompt.text = cleanPrompt

        if (imageUrl.isNotBlank()) {
            binding.pbImageLoading.visibility = android.view.View.VISIBLE
            val imgSource: Any = if (File(imageUrl).exists()) File(imageUrl) else imageUrl
            binding.ivResultImage.load(imgSource) {
                crossfade(true)
                listener(
                    onSuccess = { _, _ ->
                        binding.pbImageLoading.visibility = android.view.View.GONE
                    },
                    onError = { _, result ->
                        binding.pbImageLoading.visibility = android.view.View.GONE
                        android.util.Log.e("EnhanceResult", "Failed to load result image from $imageUrl: ${result.throwable.message}", result.throwable)
                    }
                )
            }
        } else {
            binding.pbImageLoading.visibility = android.view.View.GONE
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
                com.aiart.photo.beauti.editor.core.dialog.AppDialogHelper.showSaveSuccessDialog(requireActivity())
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
                    currentDrawable?.let { com.aiart.photo.beauti.editor.core.utils.MediaSaveUtils.drawableToBitmap(it) } ?: return@launch
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
