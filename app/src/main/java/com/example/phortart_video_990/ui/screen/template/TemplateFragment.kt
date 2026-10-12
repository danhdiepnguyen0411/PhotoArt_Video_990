package com.example.phortart_video_990.ui.screen.template

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.AudioCacheManager
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.data.model.TemplateModel
import com.example.phortart_video_990.databinding.FragmentTemplateBinding
import com.example.phortart_video_990.ui.screen.main.MainFragment
import kotlinx.coroutines.launch

class TemplateFragment : BaseFragment<FragmentTemplateBinding>(FragmentTemplateBinding::inflate) {

    private val viewModel: TemplateViewModel by viewModels()
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var templateAdapter: TemplateAdapter

    private var mediaPlayer: MediaPlayer? = null
    private var activePlayingTrackId: String? = null
    private var previewJob: kotlinx.coroutines.Job? = null

    override fun initView() {
        setupTitleGradient()

        // Setup Dynamic Music Category Filter RecyclerView
        categoryAdapter = CategoryAdapter(
            categories = viewModel.categories.value,
            selectedCategoryCode = viewModel.selectedCategoryCode.value
        ) { selectedCategory ->
            stopAudio()
            viewModel.selectCategory(selectedCategory)
        }
        binding.rvTemplateCategories.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvTemplateCategories.adapter = categoryAdapter

        // Setup Music Tracks Grid (2 Columns)
        templateAdapter = TemplateAdapter(
            items = emptyList(),
            onItemClick = { selectedMusic ->
                stopAudio()
                val bundle = Bundle().apply {
                    putString("selected_music_title", selectedMusic.title)
                    putString("selected_music_audio_url", selectedMusic.safeAudioUrl)
                    putString("selected_music_image_url", selectedMusic.safeImageUrl)
                }
                val navController = parentFragment?.parentFragment?.findNavController()
                    ?: parentFragment?.findNavController()
                    ?: findNavController()
                navController.navigateSafe(R.id.action_mainFragment_to_memoriesFragment, bundle)
            },
            onPlayClick = { track, position ->
                toggleAudioPreview(track, position)
            }
        )
        binding.rvTemplates.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvTemplates.adapter = templateAdapter

        observeData()
    }

    private fun toggleAudioPreview(track: TemplateModel, position: Int) {
        if (activePlayingTrackId == track.id) {
            stopAudio()
            return
        }

        stopAudio()

        val audioUrl = track.safeAudioUrl
        if (audioUrl.isNullOrBlank()) return

        templateAdapter.setLoadingState(position)

        previewJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val cachedFile = AudioCacheManager.getOrDownloadAudio(requireContext(), audioUrl)
                if (!isAdded || view == null) return@launch

                if (cachedFile == null || !cachedFile.exists() || cachedFile.length() == 0L) {
                    templateAdapter.setPlayingState(position, false)
                    return@launch
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(cachedFile.absolutePath)
                    setOnPreparedListener { mp ->
                        mp.start()
                        activePlayingTrackId = track.id
                        templateAdapter.setPlayingState(position, true)
                    }
                    setOnCompletionListener {
                        stopAudio()
                    }
                    setOnErrorListener { _, _, _ ->
                        stopAudio()
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                templateAdapter.setPlayingState(position, false)
            }
        }
    }

    private fun stopAudio() {
        previewJob?.cancel()
        previewJob = null
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        activePlayingTrackId = null
        if (::templateAdapter.isInitialized) {
            templateAdapter.setPlayingState(-1, false)
        }
    }

    private fun setupTitleGradient() {
        binding.tvTemplateHeaderTitle.post {
            val paint = binding.tvTemplateHeaderTitle.paint
            val width = paint.measureText(binding.tvTemplateHeaderTitle.text.toString())
            if (width > 0) {
                val textShader = LinearGradient(
                    0f, 0f, width, 0f,
                    intArrayOf(Color.parseColor("#4A7BFE"), Color.parseColor("#9153EE")),
                    null,
                    Shader.TileMode.CLAMP
                )
                binding.tvTemplateHeaderTitle.paint.shader = textShader
                binding.tvTemplateHeaderTitle.invalidate()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::templateAdapter.isInitialized && templateAdapter.itemCount > 0) {
            templateAdapter.notifyDataSetChanged()
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

    override fun initListener() {
        binding.swipeRefreshLayout.setColorSchemeColors(
            Color.parseColor("#4A7BFE"),
            Color.parseColor("#9153EE")
        )
        binding.swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            binding.rvTemplates.canScrollVertically(-1)
        }
        binding.swipeRefreshLayout.setOnRefreshListener {
            stopAudio()
            viewModel.loadData()
        }

        binding.btnTemplatePro.setOnClickListener {
            (parentFragment as? MainFragment)?.selectTab(3)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect { categories ->
                        categoryAdapter.submitList(categories)
                    }
                }
                launch {
                    viewModel.selectedCategoryCode.collect { code ->
                        categoryAdapter.setSelected(code)
                    }
                }
                launch {
                    viewModel.templates.collect { list ->
                        templateAdapter.submitList(list)
                        val isLoading = viewModel.isLoading.value
                        binding.llEmptyState.visibility = if (list.isEmpty() && !isLoading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        if (!binding.swipeRefreshLayout.isRefreshing) {
                            binding.pbLoading.visibility = if (loading) View.VISIBLE else View.GONE
                        } else {
                            binding.pbLoading.visibility = View.GONE
                        }
                        if (!loading) {
                            binding.swipeRefreshLayout.postDelayed({
                                if (isAdded) binding.swipeRefreshLayout.isRefreshing = false
                            }, 500L)
                            binding.llEmptyState.visibility = if (viewModel.templates.value.isEmpty()) View.VISIBLE else View.GONE
                        } else {
                            binding.llEmptyState.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }
}
