package com.example.phortart_video_990.ui.screen.feature.memories

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
import com.example.phortart_video_990.databinding.FragmentMemoriesBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoriesFragment : BaseFragment<FragmentMemoriesBinding>(FragmentMemoriesBinding::inflate) {

    private var selectedImageUri: Uri? = null
    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            binding.ivSelectedPhoto.visibility = View.VISIBLE
            binding.llUploadPrompt.visibility = View.GONE
            binding.ivSelectedPhoto.load(uri) {
                crossfade(true)
            }
        }
    }

    private var selectedMusicTitle: String? = null

    override fun initView() {
        binding.tvTopTitle.text = getString(R.string.feature_video_title).replace("\n", " ")
        binding.tvFunctionName.text = getString(R.string.feature_video_title).replace("\n", " ")

        // Observe returned music selection from MusicFragment
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<String>("selected_music_title")
            ?.observe(viewLifecycleOwner) { musicTitle ->
                if (!musicTitle.isNullOrBlank()) {
                    selectedMusicTitle = musicTitle
                    binding.tvSelectedMusicTitle.text = musicTitle
                    binding.tvSelectedMusicSubtitle.text = "Đã chọn nhạc nền cho video"
                }
            }
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.cardUploadPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.cardSelectMusic.setOnClickListener {
            findNavController().navigate(R.id.action_memoriesFragment_to_musicFragment)
        }

        binding.btnCreateVideo.setOnClickListener {
            if (selectedImageUri == null) {
                Toast.makeText(requireContext(), "Vui lòng chọn ảnh từ thư viện để tạo video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dateFormat = SimpleDateFormat("d 'thg' M, yyyy • HH:mm", Locale.getDefault())
            val currentDate = dateFormat.format(Date())

            val videoTitle = if (!selectedMusicTitle.isNullOrBlank()) {
                "Video tạo từ ảnh AI • Nhạc: $selectedMusicTitle"
            } else {
                "Video tạo từ ảnh AI"
            }

            val historyItem = HistoryItemModel(
                type = "Gom ảnh",
                title = videoTitle,
                date = currentDate,
                imageUri = selectedImageUri.toString()
            )
            historyRepository.addHistoryItem(historyItem)

            Toast.makeText(requireContext(), "Đã tạo video thành công từ ảnh của bạn!", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }
}
