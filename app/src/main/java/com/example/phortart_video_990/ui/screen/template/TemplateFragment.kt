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
        // Setup Dynamic Music Category Filter RecyclerView
        categoryAdapter = CategoryAdapter(
            categories = viewModel.categories.value,
            selectedCategoryCode = viewModel.selectedCategoryCode.value
        ) { selectedCategory ->
            com.example.phortart_video_990.data.local.PreferencesManager.getInstance(requireContext())
                .recordSelectedCategory(selectedCategory.code)
            viewModel.selectCategory(selectedCategory)
            scrollToTop()
        }
        binding.rvTemplateCategories.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvTemplateCategories.adapter = categoryAdapter

        // Setup Dancing Templates Grid (2 Columns)
        templateAdapter = TemplateAdapter(
            items = emptyList(),
            onItemClick = { selectedTemplate ->
                stopAudio()
                com.example.phortart_video_990.data.local.PreferencesManager.getInstance(requireContext())
                    .recordSelectedCategory(selectedTemplate.category)
                val activeCat = if (viewModel.selectedCategoryCode.value.isNotBlank() &&
                    !viewModel.selectedCategoryCode.value.equals("ALL", ignoreCase = true)
                ) {
                    viewModel.selectedCategoryCode.value
                } else {
                    selectedTemplate.category
                }
                val bundle = Bundle().apply {
                    putString(com.example.phortart_video_990.ui.screen.feature.createtemplate.CreateTemplateVideoFragment.ARG_TEMPLATE_CODE, selectedTemplate.id)
                    putString("template_code", selectedTemplate.id)
                    putString(com.example.phortart_video_990.ui.screen.feature.createtemplate.CreateTemplateVideoFragment.ARG_TEMPLATE_TITLE, selectedTemplate.title)
                    putString("template_title", selectedTemplate.title)
                    putString(com.example.phortart_video_990.ui.screen.feature.createtemplate.CreateTemplateVideoFragment.ARG_TEMPLATE_CATEGORY, activeCat)
                    putString("template_category", activeCat)
                    putString(com.example.phortart_video_990.ui.screen.feature.createtemplate.CreateTemplateVideoFragment.ARG_TEMPLATE_THUMB, selectedTemplate.safeImageUrl)
                    putString("template_thumb", selectedTemplate.safeImageUrl)
                    putString(com.example.phortart_video_990.ui.screen.feature.createtemplate.CreateTemplateVideoFragment.ARG_TEMPLATE_TYPE, selectedTemplate.templateType)
                    putString("template_type", selectedTemplate.templateType)
                    putString("template_audio_url", selectedTemplate.safeAudioUrl)
                    putFloat("selected_template_duration_sec", selectedTemplate.durationSeconds.toFloat())
                    putInt("selected_template_photo_count", selectedTemplate.targetPhotoCount)
                }
                val navController = parentFragment?.parentFragment?.findNavController()
                    ?: parentFragment?.findNavController()
                    ?: findNavController()
                navController.navigateSafe(R.id.action_mainFragment_to_createTemplateVideoFragment, bundle)
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

        templateAdapter.setLoadingTrack(track.id)

        previewJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val cachedFile = AudioCacheManager.getOrDownloadAudio(requireContext(), audioUrl)
                if (!isAdded || view == null) return@launch

                if (cachedFile == null || !cachedFile.exists() || cachedFile.length() == 0L) {
                    templateAdapter.setPlayingTrack(track.id, false)
                    return@launch
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(cachedFile.absolutePath)
                    setOnPreparedListener { mp ->
                        mp.start()
                        activePlayingTrackId = track.id
                        templateAdapter.setPlayingTrack(track.id, true)
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
                templateAdapter.setPlayingTrack(track.id, false)
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
            templateAdapter.setPlayingTrack(null, false)
        }
    }


    fun selectCategory(categoryCode: String = "ALL") {
        context?.let { ctx ->
            com.example.phortart_video_990.data.local.PreferencesManager.getInstance(ctx)
                .recordSelectedCategory(categoryCode)
        }
        viewModel.selectCategoryByCode(categoryCode)
        if (::categoryAdapter.isInitialized) {
            categoryAdapter.setSelected(categoryCode)
            binding.rvTemplateCategories.scrollToPosition(0)
        }
        scrollToTop()
    }

    override fun onResume() {
        super.onResume()
        (parentFragment as? MainFragment)?.pendingTemplateCategory?.let { pendingCat ->
            (parentFragment as? MainFragment)?.pendingTemplateCategory = null
            selectCategory(pendingCat)
        }
        if (::templateAdapter.isInitialized && templateAdapter.itemCount > 0) {
            templateAdapter.notifyDataSetChanged()
        } else if (isAdded && context != null && com.example.phortart_video_990.core.utils.NetworkMonitor.isOnline(requireContext())) {
            viewModel.loadData()
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

        binding.llEmptyState.setOnClickListener {
            stopAudio()
            viewModel.loadData()
        }

        binding.headerTemplate.setOnProClickListener {
            (parentFragment as? MainFragment)?.selectTab(3)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Auto-reload data when network reconnects
                launch {
                    var wasOffline = false
                    com.example.phortart_video_990.core.utils.NetworkMonitor.observeNetwork(requireContext()).collect { isOnline ->
                        if (isOnline) {
                            val isEmptyOrError = viewModel.templates.value.isEmpty() || viewModel.isError.value
                            if (wasOffline || isEmptyOrError) {
                                viewModel.loadData()
                            }
                            wasOffline = false
                        } else {
                            wasOffline = true
                        }
                    }
                }
                launch {
                    viewModel.categories.collect { categories ->
                        categoryAdapter.submitList(categories)
                    }
                }
                launch {
                    var lastCategoryCode: String? = null
                    viewModel.selectedCategoryCode.collect { code ->
                        categoryAdapter.setSelected(code)
                        if (lastCategoryCode != null && lastCategoryCode != code) {
                            scrollToTop()
                        }
                        lastCategoryCode = code
                    }
                }
                launch {
                    viewModel.templates.collect { list ->
                        templateAdapter.submitList(list)
                        val isLoading = viewModel.isLoading.value
                        if (!isLoading) {
                            binding.layoutSkeletonLoading.root.visibility = View.GONE
                            binding.rvTemplates.visibility = View.VISIBLE
                            binding.llEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        }
                        scrollToTop()
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.pbLoading.visibility = View.GONE
                        if (loading) {
                            if (!binding.swipeRefreshLayout.isRefreshing || templateAdapter.itemCount == 0) {
                                binding.layoutSkeletonLoading.root.visibility = View.VISIBLE
                                binding.rvTemplates.visibility = View.GONE
                            }
                            binding.llEmptyState.visibility = View.GONE
                        } else {
                            binding.layoutSkeletonLoading.root.visibility = View.GONE
                            binding.rvTemplates.visibility = View.VISIBLE
                            binding.swipeRefreshLayout.postDelayed({
                                if (isAdded) binding.swipeRefreshLayout.isRefreshing = false
                            }, 500L)
                            binding.llEmptyState.visibility = if (viewModel.templates.value.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun scrollToTop() {
        binding.rvTemplates.stopScroll()
        val layoutManager = binding.rvTemplates.layoutManager as? LinearLayoutManager
        layoutManager?.scrollToPositionWithOffset(0, 0)
        binding.rvTemplates.post {
            if (isAdded && view != null) {
                binding.rvTemplates.stopScroll()
                (binding.rvTemplates.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
            }
        }
    }
}
