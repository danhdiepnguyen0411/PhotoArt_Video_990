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
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.repository.AIPromptRepository
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.databinding.FragmentEnhanceResultBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EnhanceResultFragment : BaseFragment<FragmentEnhanceResultBinding>(FragmentEnhanceResultBinding::inflate) {

    private val promptRepository by lazy { AIPromptRepository(requireContext()) }
    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private var userPrompt: String = ""
    private var imageUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userPrompt = arguments?.getString(KEY_USER_PROMPT).orEmpty()
        imageUrl = arguments?.getString(KEY_IMAGE_URL).orEmpty()
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            binding.topBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarInset
            }
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
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnDownload.setOnClickListener {
            saveImageToDevice()
        }

        binding.btnGenerateAgain.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnShare.setOnClickListener {
            shareImage()
        }
    }

    private fun saveImageToDevice() {
        if (imageUrl.isBlank()) {
            Toast.makeText(requireContext(), "Không có ảnh để lưu", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.btnDownload.isEnabled = false
            promptRepository.saveImageToGallery(imageUrl)
            binding.btnDownload.isEnabled = true
            com.example.phortart_video_990.core.dialog.AppDialogHelper.showSaveSuccessDialog(requireActivity())
        }
    }

    private fun shareImage() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Xem ảnh AI nghệ thuật tôi vừa tạo với prompt: \"$userPrompt\"\n$imageUrl")
            }
            startActivity(Intent.createChooser(shareIntent, "Chia sẻ ảnh"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Không thể mở ứng dụng chia sẻ", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val KEY_USER_PROMPT = "key_user_prompt"
        const val KEY_IMAGE_URL = "key_image_url"
    }
}
