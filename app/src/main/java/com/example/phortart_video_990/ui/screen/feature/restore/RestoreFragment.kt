package com.example.phortart_video_990.ui.screen.feature.restore

import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.databinding.FragmentRestoreBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RestoreFragment : BaseFragment<FragmentRestoreBinding>(FragmentRestoreBinding::inflate) {

    private var selectedImageUri: Uri? = null
    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            binding.ivPreviewRestore.visibility = View.VISIBLE
            binding.llRestoreUploadPrompt.visibility = View.GONE
            binding.ivPreviewRestore.load(uri) {
                crossfade(true)
            }
        }
    }

    override fun initView() {
        binding.tvTopTitle.text = getString(R.string.feature_restore_title).replace("\n", " ")
        binding.tvFunctionName.text = getString(R.string.feature_restore_title).replace("\n", " ")
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.cardUploadRestore.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnStartRestore.setOnClickListener {
            if (selectedImageUri == null) {
                Toast.makeText(requireContext(), "Vui lòng chọn ảnh từ thư viện để khôi phục", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dateFormat = SimpleDateFormat("d 'thg' M, yyyy • HH:mm", Locale.getDefault())
            val currentDate = dateFormat.format(Date())

            val historyItem = HistoryItemModel(
                type = "Khôi phục",
                title = "Khôi phục ảnh cũ",
                date = currentDate,
                imageUri = selectedImageUri.toString()
            )
            historyRepository.addHistoryItem(historyItem)

            Toast.makeText(requireContext(), "Đã phân tích và khôi phục ảnh thành công!", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }
}
