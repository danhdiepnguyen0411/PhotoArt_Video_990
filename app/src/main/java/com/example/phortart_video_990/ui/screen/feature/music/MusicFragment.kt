package com.example.phortart_video_990.ui.screen.feature.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.AudioCacheManager
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.data.model.CategoryModel
import com.example.phortart_video_990.data.model.TemplateModel
import com.example.phortart_video_990.data.repository.TemplateRepository
import com.example.phortart_video_990.databinding.FragmentMusicBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MusicFragment : BaseFragment<FragmentMusicBinding>(FragmentMusicBinding::inflate) {

    private val repository = TemplateRepository()
    private lateinit var categoryAdapter: MusicCategoryAdapter
    private lateinit var musicAdapter: MusicAdapter

    private var allMusicTracks: List<TemplateModel> = emptyList()
    private var musicCategories: List<CategoryModel> = listOf(CategoryModel.ALL)
    private var selectedCategoryCode: String = "ALL"
    private var currentSearchQuery: String = ""

    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingPosition: Int = -1
    private var audioActionJob: Job? = null
    private var isStartMergeFlow: Boolean = false

    private val isTabMode: Boolean
        get() = (parentFragment is com.example.phortart_video_990.ui.screen.main.MainFragment) ||
                (parentFragment?.parentFragment is com.example.phortart_video_990.ui.screen.main.MainFragment)

    override fun initView() {
        isStartMergeFlow = arguments?.getBoolean("is_start_merge_flow", false) ?: false

        if (isTabMode) {
            binding.btnBack.visibility = View.GONE
            binding.tvTopTitle.text = getString(R.string.func_04_music)
        } else {
            binding.btnBack.visibility = View.VISIBLE
            binding.tvTopTitle.text = getString(R.string.func_04_music)
        }

        // Setup horizontal category chips
        categoryAdapter = MusicCategoryAdapter(
            categories = musicCategories,
            selectedCategoryCode = selectedCategoryCode
        ) { selectedCategory ->
            selectedCategoryCode = selectedCategory.code
            com.example.phortart_video_990.data.local.PreferencesManager.getInstance(requireContext())
                .recordSelectedCategory(selectedCategory.code)
            applyFilter()

        }
        binding.rvMusicCategories.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvMusicCategories.adapter = categoryAdapter

        // Setup vertical music tracks list
        musicAdapter = MusicAdapter(
            tracks = emptyList(),
            onTrackSelected = { _, _ ->
                // Selected state is updated inside MusicAdapter
            },
            onPreviewClick = { track, position ->
                toggleAudioPreview(track, position)
            }
        )
        binding.rvMusicTracks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMusicTracks.adapter = musicAdapter

        loadCategoriesAndTracks()
    }

    override fun initListener() {
        binding.swipeRefreshLayout.setColorSchemeColors(
            android.graphics.Color.parseColor("#4A7BFE"),
            android.graphics.Color.parseColor("#9153EE")
        )
        binding.swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            binding.rvMusicTracks.canScrollVertically(-1)
        }
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadCategoriesAndTracks()
        }

        binding.btnBack.setOnClickListener {
            stopAudio()
            findNavController().popBackStack()
        }

        // Search text watcher
        binding.etSearchMusic.addTextChangedListener { text ->
            currentSearchQuery = text?.toString()?.trim() ?: ""
            binding.ivClearSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
            applyFilter()
        }

        binding.ivClearSearch.setOnClickListener {
            binding.etSearchMusic.setText("")
        }

        binding.etSearchMusic.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(binding.etSearchMusic.windowToken, 0)
                true
            } else {
                false
            }
        }

        // Bottom Continue button
        binding.btnConfirmMusic.setOnClickListener {
            onContinueClicked()
        }
    }

    private fun loadCategoriesAndTracks() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!binding.swipeRefreshLayout.isRefreshing) {
                binding.pbLoading.visibility = View.VISIBLE
            }
            binding.llEmptyState.visibility = View.GONE
            try {
                // Load music categories from API
                val apiMusicCategories = repository.getMusicCategories()
                musicCategories = apiMusicCategories
                categoryAdapter.submitList(musicCategories)

                // Load all music tracks initially
                val tracks = repository.getMusicTracks(categoryCode = "ALL")
                allMusicTracks = tracks

                // Dynamically discover extra categories from tracks
                discoverExtraMusicCategories(tracks)

                // Apply initial category & search filter
                applyFilter()
            } catch (e: Exception) {
                binding.llEmptyState.visibility = View.VISIBLE
            } finally {
                binding.pbLoading.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun applyFilter() {
        stopAudio()
        var filtered = allMusicTracks

        // Category filter
        val isAll = selectedCategoryCode.isBlank() ||
                selectedCategoryCode.equals("ALL", ignoreCase = true) ||
                selectedCategoryCode.equals("Tất cả", ignoreCase = true)

        if (!isAll) {
            val target = selectedCategoryCode.trim()
            val cleanTarget = target.removePrefix("MUSIC_").lowercase()
            filtered = filtered.filter {
                it.category.equals(target, ignoreCase = true) ||
                        it.category.removePrefix("MUSIC_").lowercase() == cleanTarget
            }
        }

        // Search query filter
        if (currentSearchQuery.isNotBlank()) {
            val q = currentSearchQuery.lowercase()
            filtered = filtered.filter { track ->
                track.title.lowercase().contains(q) ||
                        track.category.lowercase().contains(q) ||
                        track.category.removePrefix("MUSIC_").lowercase().contains(q)
            }
        }

        musicAdapter.submitList(filtered)
        binding.llEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
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
            Toast.makeText(requireContext(), getString(R.string.toast_music_preview_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        audioActionJob?.cancel()
        audioActionJob = null

        if (currentPlayingPosition == position && mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            musicAdapter.setPlayingState(position, false)
            return
        }

        stopAudio()

        musicAdapter.setPreviewLoading(position)

        audioActionJob = viewLifecycleOwner.lifecycleScope.launch {
            val cachedFile = AudioCacheManager.getOrDownloadAudio(
                requireContext(),
                audioUrl
            )

            if (!isAdded || view == null) return@launch

            if (cachedFile == null || !cachedFile.exists() || cachedFile.length() == 0L) {
                musicAdapter.clearAllLoading()
                Toast.makeText(requireContext(), getString(R.string.toast_cannot_download_preview), Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
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

    private fun onContinueClicked() {
        val selectedTrack = musicAdapter.getSelectedTrack()
        if (selectedTrack == null) {
            Toast.makeText(requireContext(), getString(R.string.toast_please_select_music), Toast.LENGTH_SHORT).show()
            return
        }

        stopAudio()

        val audioUrl = selectedTrack.safeAudioUrl
        if (audioUrl.isNullOrBlank()) {
            returnTrackResult(selectedTrack, null)
            return
        }

        // Show loading state on bottom Continue button
        binding.btnConfirmMusic.isEnabled = false
        binding.ivConfirmArrow.visibility = View.GONE
        binding.pbConfirmLoading.visibility = View.VISIBLE

        audioActionJob?.cancel()
        audioActionJob = viewLifecycleOwner.lifecycleScope.launch {
            val cachedFile = AudioCacheManager.getOrDownloadAudio(
                requireContext(),
                audioUrl
            )

            if (!isAdded || view == null) return@launch

            binding.btnConfirmMusic.isEnabled = true
            binding.pbConfirmLoading.visibility = View.GONE
            binding.ivConfirmArrow.visibility = View.VISIBLE

            returnTrackResult(selectedTrack, cachedFile?.absolutePath)
        }
    }

    private fun returnTrackResult(track: TemplateModel, cachedFilePath: String?) {

        com.example.phortart_video_990.data.local.PreferencesManager.getInstance(requireContext())
            .recordSelectedCategory(track.category)

        val artistText = (if (track.category.startsWith("MUSIC_", ignoreCase = true)) {
            track.category.substring(6)
        } else {
            track.category
        }).ifBlank { "Photo AI" }
        val musicSubtitle = "$artistText • ${track.displayDuration}"

        if (isTabMode) {
            val bundle = android.os.Bundle().apply {
                putString("selected_music_title", track.title)
                putString("selected_music_subtitle", musicSubtitle)
                putString("selected_music_audio_url", track.safeAudioUrl)
                putString("selected_music_file_path", cachedFilePath)
                putString("selected_music_image_url", track.safeImageUrl)
                putFloat("selected_template_duration_sec", track.durationSeconds.toFloat())
                putInt("selected_template_photo_count", track.targetPhotoCount)
            }
            val navController = parentFragment?.parentFragment?.findNavController()
                ?: parentFragment?.findNavController()
                ?: findNavController()
            navController.navigateSafe(R.id.action_mainFragment_to_memoriesFragment, bundle)
        } else if (isStartMergeFlow) {
            val bundle = android.os.Bundle().apply {
                putString("selected_music_title", track.title)
                putString("selected_music_subtitle", musicSubtitle)
                putString("selected_music_audio_url", track.safeAudioUrl)
                putString("selected_music_file_path", cachedFilePath)
                putString("selected_music_image_url", track.safeImageUrl)
                putFloat("selected_template_duration_sec", track.durationSeconds.toFloat())
                putInt("selected_template_photo_count", track.targetPhotoCount)
            }
            findNavController().navigateSafe(R.id.action_musicFragment_to_memoriesFragment, bundle)
        } else {
            // Return selected music title, audio URL, cached local path, and cover image to parent screen
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                "selected_music_title",
                track.title
            )
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                "selected_music_subtitle",
                musicSubtitle
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
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                "selected_template_duration_sec",
                track.durationSeconds.toFloat()
            )
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                "selected_template_photo_count",
                track.targetPhotoCount
            )

            findNavController().popBackStack()
        }
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
