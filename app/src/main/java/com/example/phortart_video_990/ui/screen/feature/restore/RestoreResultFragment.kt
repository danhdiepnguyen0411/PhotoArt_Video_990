package com.example.phortart_video_990.ui.screen.feature.restore

import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.data.repository.RestoreRepository
import com.example.phortart_video_990.databinding.FragmentRestoreResultBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RestoreResultFragment : BaseFragment<FragmentRestoreResultBinding>(FragmentRestoreResultBinding::inflate) {

    private val restoreRepository by lazy { RestoreRepository(requireContext()) }
    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private var originalUri: String? = null
    private var restoredUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        originalUri = arguments?.getString(KEY_ORIGINAL_URI)
        restoredUrl = arguments?.getString(KEY_RESTORED_URL)
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            binding.btnBack.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarInset + (14 * resources.displayMetrics.density).toInt()
            }
            binding.btnSaveImage.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navBarInset + (24 * resources.displayMetrics.density).toInt()
            }
            insets
        }

        // Setup Before and After images
        originalUri?.let { binding.sliderView.setBeforeImage(it) }
        restoredUrl?.let { binding.sliderView.setAfterImage(it) }
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnSaveImage.setOnClickListener {
            saveImageToDevice()
        }
    }

    private fun saveImageToDevice() {
        val targetSource = restoredUrl ?: originalUri
        if (targetSource.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Không có ảnh để lưu", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.btnSaveImage.isEnabled = false
            val savedUri = restoreRepository.saveImageToGallery(targetSource)

            val dateFormat = SimpleDateFormat("d 'thg' M, yyyy • HH:mm", Locale.getDefault())
            val currentDate = dateFormat.format(Date())

            val historyItem = HistoryItemModel(
                type = "Khôi phục",
                title = "Khôi phục ảnh cũ",
                date = currentDate,
                imageUri = savedUri?.toString() ?: targetSource
            )
            historyRepository.addHistoryItem(historyItem)

            binding.btnSaveImage.isEnabled = true
            com.example.phortart_video_990.core.dialog.AppDialogHelper.showSaveSuccessDialog(requireActivity())
        }
    }

    companion object {
        const val KEY_ORIGINAL_URI = "key_original_uri"
        const val KEY_RESTORED_URL = "key_restored_url"
    }
}
