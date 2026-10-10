package com.example.phortart_video_990.ui.screen.feature.restore

import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.databinding.FragmentRestoreBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RestoreFragment : BaseFragment<FragmentRestoreBinding>(FragmentRestoreBinding::inflate) {

    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val dateFormat = SimpleDateFormat("d 'thg' M, yyyy • HH:mm", Locale.getDefault())
            val currentDate = dateFormat.format(Date())

            val historyItem = HistoryItemModel(
                type = "Khôi phục",
                title = "Khôi phục ảnh cũ",
                date = currentDate,
                imageUri = uri.toString()
            )
            historyRepository.addHistoryItem(historyItem)

            Toast.makeText(requireContext(), "Đã chọn ảnh và bắt đầu khôi phục!", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            binding.btnBack.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarInset + (14 * resources.displayMetrics.density).toInt()
            }
            binding.btnStartRestore.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navBarInset + (24 * resources.displayMetrics.density).toInt()
            }
            insets
        }
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnStartRestore.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }
}
