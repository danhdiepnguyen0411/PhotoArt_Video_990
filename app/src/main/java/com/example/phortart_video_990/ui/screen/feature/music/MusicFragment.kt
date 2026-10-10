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
    private var audioActionJob: kotlinx.coroutines.Job? = null

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
            onSelectClick = { track, position ->
                selectTrack(track, position)
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

        // Hủy bất kỳ tác vụ tải hoặc chọn trước đó đang diễn ra
        audioActionJob?.cancel()
        audioActionJob = null

        if (currentPlayingPosition == position && mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            musicAdapter.setPlayingState(position, false)
            return
        }

        stopAudio()

        // Hiển thị loading indicator tại icon nghe thử của item (đồng thời adapter tự clear các loading khác)
        musicAdapter.setPreviewLoading(position)

        audioActionJob = viewLifecycleOwner.lifecycleScope.launch {
            val cachedFile = com.example.phortart_video_990.core.utils.AudioCacheManager.getOrDownloadAudio(
                requireContext(),
                audioUrl
            )

            if (!isAdded || view == null) return@launch

            if (cachedFile == null || !cachedFile.exists() || cachedFile.length() == 0L) {
                musicAdapter.clearAllLoading()
                Toast.makeText(requireContext(), "Không thể tải bản nhạc xem trước", Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(cachedFile.absolutePath)
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
    }

    private fun selectTrack(track: TemplateModel, position: Int) {
        // Hủy bất kỳ tác vụ tải nghe thử hoặc chọn trước đó
        audioActionJob?.cancel()
        audioActionJob = null

        stopAudio()

        val audioUrl = track.safeAudioUrl
        if (audioUrl.isNullOrBlank()) {
            returnTrackResult(track, null)
            return
        }

        // Hiển thị loading tại nút Chọn (tự động xóa loading ở tất cả nút khác)
        musicAdapter.setSelectLoading(position)

        audioActionJob = viewLifecycleOwner.lifecycleScope.launch {
            val cachedFile = com.example.phortart_video_990.core.utils.AudioCacheManager.getOrDownloadAudio(
                requireContext(),
                audioUrl
            )

            if (!isAdded || view == null) return@launch

            musicAdapter.clearAllLoading()
            returnTrackResult(track, cachedFile?.absolutePath)
        }
    }

    private fun returnTrackResult(track: TemplateModel, cachedFilePath: String?) {
        // Return selected music title, audio URL, cached local path, and cover image to parent screen
        findNavController().previousBackStackEntry?.savedStateHandle?.set(
            "selected_music_title",
            track.title
        )
        findNavController().previousBackStackEntry?.savedStateHandle?.set(
            "selected_music_audio_url",
            track.safeAudioUrl
        )
        findNavController().previousBackStackEntry?.savedStateHandle?.set(
            "selected_music_file_path",
            cachedFilePath
        )
        findNavController().previousBackStackEntry?.savedStateHandle?.set(
            "selected_music_image_url",
            track.safeImageUrl
        )

        findNavController().popBackStack()
    }

    private fun stopAudio() {
        audioActionJob?.cancel()
        audioActionJob = null
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
            musicAdapter.clearAllLoading()
        }
    }

    override fun onPause() {
        stopAudio()
        super.onPause()
    }

    override fun onDestroyView() {
        audioActionJob?.cancel()
        audioActionJob = null
        stopAudio()
        super.onDestroyView()
    }
}
