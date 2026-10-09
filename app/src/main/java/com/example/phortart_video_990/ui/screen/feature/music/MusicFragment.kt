package com.example.phortart_video_990.ui.screen.feature.music

import android.media.MediaPlayer
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.model.CategoryModel
import com.example.phortart_video_990.data.model.TemplateModel
import com.example.phortart_video_990.data.repository.TemplateRepository
import com.example.phortart_video_990.databinding.FragmentMusicBinding
import kotlinx.coroutines.launch

class MusicFragment : BaseFragment<FragmentMusicBinding>(FragmentMusicBinding::inflate) {

    private val repository = TemplateRepository()
    private lateinit var categoryAdapter: MusicCategoryAdapter
    private lateinit var musicAdapter: MusicAdapter

    private var musicCategories: List<CategoryModel> = listOf(CategoryModel.ALL)
    private var selectedCategoryCode: String = "ALL"
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingPosition: Int = -1

    override fun initView() {
        binding.tvTopTitle.text = getString(R.string.func_04_music)

        // Setup horizontal category chips
        categoryAdapter = MusicCategoryAdapter(
            categories = musicCategories,
            selectedCategoryCode = selectedCategoryCode
        ) { selectedCategory ->
            selectedCategoryCode = selectedCategory.code
            loadMusicTracks(selectedCategory.code)
        }
        binding.rvMusicCategories.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvMusicCategories.adapter = categoryAdapter

        // Setup vertical music tracks list
        musicAdapter = MusicAdapter(
            tracks = emptyList(),
            onPreviewClick = { track, position ->
                toggleAudioPreview(track, position)
            },
            onSelectClick = { track ->
                selectTrack(track)
            }
        )
        binding.rvMusicTracks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMusicTracks.adapter = musicAdapter

        loadCategoriesAndTracks()
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            stopAudio()
            findNavController().popBackStack()
        }
    }

    private fun loadCategoriesAndTracks() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.pbLoading.visibility = View.VISIBLE
            binding.llEmptyState.visibility = View.GONE
            try {
                // Load music categories from API
                val apiMusicCategories = repository.getMusicCategories()
                musicCategories = apiMusicCategories
                categoryAdapter.submitList(musicCategories)

                // Load all music tracks initially
                val tracks = repository.getMusicTracks(categoryCode = selectedCategoryCode)
                musicAdapter.submitList(tracks)
                binding.llEmptyState.visibility = if (tracks.isEmpty()) View.VISIBLE else View.GONE

                // Dynamically discover extra categories from tracks
                discoverExtraMusicCategories(tracks)
            } catch (e: Exception) {
                binding.llEmptyState.visibility = View.VISIBLE
            } finally {
                binding.pbLoading.visibility = View.GONE
            }
        }
    }

    private fun loadMusicTracks(categoryCode: String) {
        stopAudio()
        viewLifecycleOwner.lifecycleScope.launch {
            binding.pbLoading.visibility = View.VISIBLE
            binding.llEmptyState.visibility = View.GONE
            try {
                val tracks = repository.getMusicTracks(categoryCode = categoryCode)
                musicAdapter.submitList(tracks)
                binding.llEmptyState.visibility = if (tracks.isEmpty()) View.VISIBLE else View.GONE
                discoverExtraMusicCategories(tracks)
            } catch (e: Exception) {
                binding.llEmptyState.visibility = View.VISIBLE
            } finally {
                binding.pbLoading.visibility = View.GONE
            }
        }
    }

    private fun discoverExtraMusicCategories(tracks: List<TemplateModel>) {
        val currentCats = musicCategories.toMutableList()
        var updated = false
        val existingCodes = currentCats.map { it.code.lowercase() }.toSet()

        tracks.forEach { tpl ->
            val code = tpl.category.trim()
            val isMusic = code.startsWith("MUSIC_", ignoreCase = true) || tpl.isMusic
            if (code.isNotBlank() && isMusic && !existingCodes.contains(code.lowercase())) {
                currentCats.add(CategoryModel(code = code, name = code, active = true))
                updated = true
            }
        }
        if (updated) {
            musicCategories = currentCats
            categoryAdapter.submitList(currentCats)
        }
    }

    private fun toggleAudioPreview(track: TemplateModel, position: Int) {
        val audioUrl = track.safeAudioUrl
        if (audioUrl.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Bản nhạc xem trước không khả dụng", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentPlayingPosition == position && mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            musicAdapter.setPlayingState(position, false)
            return
        }

        stopAudio()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioUrl)
                setOnPreparedListener { mp ->
                    mp.start()
                    currentPlayingPosition = position
                    musicAdapter.setPlayingState(position, true)
                }
                setOnCompletionListener {
                    musicAdapter.setPlayingState(position, false)
                    currentPlayingPosition = -1
                }
                setOnErrorListener { _, _, _ ->
                    musicAdapter.setPlayingState(position, false)
                    currentPlayingPosition = -1
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            musicAdapter.setPlayingState(position, false)
        }
    }

    private fun selectTrack(track: TemplateModel) {
        stopAudio()
        Toast.makeText(requireContext(), "Đã chọn nhạc: ${track.title}", Toast.LENGTH_SHORT).show()

        // Return selected music title to parent screen (e.g. MemoriesFragment)
        findNavController().previousBackStackEntry?.savedStateHandle?.set(
            "selected_music_title",
            track.title
        )

        findNavController().popBackStack()
    }

    private fun stopAudio() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignored
        } finally {
            mediaPlayer = null
            if (currentPlayingPosition != -1) {
                musicAdapter.setPlayingState(currentPlayingPosition, false)
                currentPlayingPosition = -1
            }
        }
    }

    override fun onPause() {
        stopAudio()
        super.onPause()
    }

    override fun onDestroyView() {
        stopAudio()
        super.onDestroyView()
    }
}
