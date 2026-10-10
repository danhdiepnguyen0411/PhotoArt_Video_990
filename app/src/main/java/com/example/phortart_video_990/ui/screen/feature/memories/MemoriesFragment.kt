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
    private var selectedMusicAudioUrl: String? = null
    private var selectedMusicImageUrl: String? = null

    override fun initView() {
        binding.tvTopTitle.text = getString(R.string.feature_video_title).replace("\n", " ")

        updatePhotosUi()

        // Read any already set music selection immediately from savedStateHandle (for first-time navigation)
        val stateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        stateHandle?.get<String>("selected_music_title")?.let { musicTitle ->
            if (musicTitle.isNotBlank()) {
                selectedMusicTitle = musicTitle
                binding.tvSelectedMusicTitle.text = musicTitle
                binding.tvSelectedMusicSubtitle.text = "Đã chọn nhạc nền cho video"
            }
        }
        stateHandle?.get<String?>("selected_music_audio_url")?.let { audioUrl ->
            selectedMusicAudioUrl = audioUrl
        }
        stateHandle?.get<String?>("selected_music_image_url")?.let { imageUrl ->
            selectedMusicImageUrl = imageUrl
            if (!imageUrl.isNullOrBlank()) {
                binding.ivMusicIcon.load(imageUrl) {
                    crossfade(true)
                    error(R.drawable.ic_func_music)
                    placeholder(R.drawable.ic_func_music)
                }
            } else {
                binding.ivMusicIcon.setImageResource(R.drawable.ic_func_music)
            }
        }

        // Observe returned music selection from MusicFragment
        stateHandle?.getLiveData<String>("selected_music_title")
            ?.observe(viewLifecycleOwner) { musicTitle ->
                if (!musicTitle.isNullOrBlank()) {
                    selectedMusicTitle = musicTitle
                    binding.tvSelectedMusicTitle.text = musicTitle
                    binding.tvSelectedMusicSubtitle.text = "Đã chọn nhạc nền cho video"
                }
            }

        stateHandle?.getLiveData<String?>("selected_music_audio_url")
            ?.observe(viewLifecycleOwner) { audioUrl ->
                selectedMusicAudioUrl = audioUrl
            }

        stateHandle?.getLiveData<String?>("selected_music_image_url")
            ?.observe(viewLifecycleOwner) { imageUrl ->
                selectedMusicImageUrl = imageUrl
                if (!imageUrl.isNullOrBlank()) {
                    binding.ivMusicIcon.load(imageUrl) {
                        crossfade(true)
                        error(R.drawable.ic_func_music)
                        placeholder(R.drawable.ic_func_music)
                    }
                } else {
                    binding.ivMusicIcon.setImageResource(R.drawable.ic_func_music)
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

            val stateHandle = findNavController().currentBackStackEntry?.savedStateHandle
            val finalTrack = selectedMusicTitle ?: stateHandle?.get<String>("selected_music_title")
            val finalAudioUrl = selectedMusicAudioUrl ?: stateHandle?.get<String?>("selected_music_audio_url")
            val finalImageUrl = selectedMusicImageUrl ?: stateHandle?.get<String?>("selected_music_image_url")

            val bundle = VideoResultFragment.createBundle(
                photos = selectedPhotos.map { it.toString() },
                track = finalTrack,
                audioUrl = finalAudioUrl,
                imageUrl = finalImageUrl
            )
            findNavController().navigate(R.id.action_memoriesFragment_to_videoResultFragment, bundle)
        }
    }
}
