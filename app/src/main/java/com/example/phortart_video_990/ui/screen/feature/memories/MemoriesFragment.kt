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

    private val selectedPhotos = mutableListOf<Uri>()
    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private val pickMultipleImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val newUris = uris.filter { it !in selectedPhotos }
            if (newUris.isNotEmpty()) {
                selectedPhotos.addAll(newUris)
                updatePhotosUi()
            }
        }
    }

    private var selectedMusicTitle: String? = null

    override fun initView() {
        binding.tvTopTitle.text = getString(R.string.feature_video_title).replace("\n", " ")

        updatePhotosUi()

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

    private fun updatePhotosUi() {
        val count = selectedPhotos.size
        if (count > 0) {
            binding.cardUploadPhoto.visibility = View.GONE
            binding.llSelectedPhotosContainer.visibility = View.VISIBLE
            binding.tvSelectedCount.visibility = View.VISIBLE
            binding.tvSelectedCount.text = "$count ảnh đã chọn"

            // Layer 1 (Top image)
            binding.ivStackImage1.load(selectedPhotos[0]) {
                crossfade(true)
            }

            // Layer 2 (Middle image)
            if (count > 1) {
                binding.cardStack2.visibility = View.VISIBLE
                binding.ivStackImage2.load(selectedPhotos[1]) {
                    crossfade(true)
                }
            } else {
                binding.cardStack2.visibility = View.GONE
            }

            // Layer 3 (Bottom image)
            if (count > 2) {
                binding.cardStack3.visibility = View.VISIBLE
                binding.ivStackImage3.load(selectedPhotos[2]) {
                    crossfade(true)
                }
            } else {
                binding.cardStack3.visibility = View.GONE
            }

            // Badge overlay text on top image (+N or count)
            if (count > 1) {
                binding.tvStackCountBadge.visibility = View.VISIBLE
                binding.tvStackCountBadge.text = "+${count - 1}"
            } else {
                binding.tvStackCountBadge.visibility = View.GONE
            }
        } else {
            binding.cardUploadPhoto.visibility = View.VISIBLE
            binding.llSelectedPhotosContainer.visibility = View.GONE
            binding.tvSelectedCount.visibility = View.GONE
        }
    }

    private fun openPhotoViewer(initialPosition: Int = 0) {
        if (selectedPhotos.isEmpty()) return
        PhotoViewerDialog(
            context = requireContext(),
            photos = selectedPhotos,
            initialPosition = initialPosition,
            onPhotosChanged = {
                updatePhotosUi()
            },
            onAddMoreRequested = {
                pickMultipleImagesLauncher.launch("image/*")
            }
        ).show()
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.cardUploadPhoto.setOnClickListener {
            pickMultipleImagesLauncher.launch("image/*")
        }

        binding.btnAddMorePhotos.setOnClickListener {
            pickMultipleImagesLauncher.launch("image/*")
        }

        binding.cardPhotoStackContainer.setOnClickListener {
            openPhotoViewer(0)
        }

        binding.btnViewAllPhotos.setOnClickListener {
            openPhotoViewer(0)
        }

        binding.cardSelectMusic.setOnClickListener {
            findNavController().navigate(R.id.action_memoriesFragment_to_musicFragment)
        }

        binding.btnCreateVideo.setOnClickListener {
            if (selectedPhotos.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng chọn ít nhất 1 ảnh để tạo video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dateFormat = SimpleDateFormat("d 'thg' M, yyyy • HH:mm", Locale.getDefault())
            val currentDate = dateFormat.format(Date())

            val videoTitle = if (!selectedMusicTitle.isNullOrBlank()) {
                "Video tạo từ ${selectedPhotos.size} ảnh AI • Nhạc: $selectedMusicTitle"
            } else {
                "Video tạo từ ${selectedPhotos.size} ảnh AI"
            }

            val historyItem = HistoryItemModel(
                type = "Gom ảnh",
                title = videoTitle,
                date = currentDate,
                imageUri = selectedPhotos.first().toString()
            )
            historyRepository.addHistoryItem(historyItem)

            Toast.makeText(
                requireContext(),
                "Đã tạo video thành công từ ${selectedPhotos.size} ảnh của bạn!",
                Toast.LENGTH_SHORT
            ).show()
            findNavController().popBackStack()
        }
    }
}
