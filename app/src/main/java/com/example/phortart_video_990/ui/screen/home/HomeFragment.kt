package com.example.phortart_video_990.ui.screen.home

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.AudioCacheManager
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.data.model.BannerItemModel
import com.example.phortart_video_990.data.model.HomeMediaItemModel
import com.example.phortart_video_990.data.model.TemplateModel
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.data.repository.TemplateRepository
import com.example.phortart_video_990.databinding.FragmentHomeBinding
import com.example.phortart_video_990.ui.screen.main.MainFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HomeFragment : BaseFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {

    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private var autoScrollRunnable: Runnable? = null

    private val templateRepository = TemplateRepository()
    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private lateinit var recentHistoryAdapter: HomeRecentHistoryAdapter
    private lateinit var forYouAdapter: HomeForYouAdapter
    private lateinit var hotTemplateAdapter: HomeHotAdapter

    private var mediaPlayer: MediaPlayer? = null
    private var activePlayingTrackId: String? = null
    private var previewJob: Job? = null

    private fun getBannerList(): List<BannerItemModel> {
        return listOf(
            BannerItemModel(
                imageRes = R.drawable.banner_1,
                chip = getString(R.string.banner_1_chip),
                title = getString(R.string.banner_1_title),
                highlight = getString(R.string.banner_1_highlight),
                description = getString(R.string.banner_1_desc),
                buttonText = getString(R.string.banner_1_btn),
                targetFeature = "video"
            ),
            BannerItemModel(
                imageRes = R.drawable.banner_2,
                chip = getString(R.string.banner_2_chip),
                title = getString(R.string.banner_2_title),
                highlight = getString(R.string.banner_2_highlight),
                description = getString(R.string.banner_2_desc),
                buttonText = getString(R.string.banner_2_btn),
                targetFeature = "prompt"
            ),
            BannerItemModel(
                imageRes = R.drawable.banner_3,
                chip = getString(R.string.banner_3_chip),
                title = getString(R.string.banner_3_title),
                highlight = getString(R.string.banner_3_highlight),
                description = getString(R.string.banner_3_desc),
                buttonText = getString(R.string.banner_3_btn),
                targetFeature = "restore"
            )
        )
    }

    override fun initView() {
        setupHeader()
        setupBanners()
        setupRecentHistory()
        setupForYouTemplates()
        setupHotTemplates()
        loadHomeData()
    }

    override fun onResume() {
        super.onResume()
        setupBanners()
        loadRecentHistory()
        refreshRecommendations()
        checkCustomNotificationPermission()
    }

    override fun onPause() {
        stopAudio()
        super.onPause()
    }

    private fun checkCustomNotificationPermission() {
        if (com.example.phortart_video_990.core.utils.NotificationPermissionManager.shouldShowNotificationDialog(requireContext())) {
            binding.root.post {
                if (isAdded && com.example.phortart_video_990.core.utils.NotificationPermissionManager.shouldShowNotificationDialog(requireContext())) {
                    com.example.phortart_video_990.core.dialog.NotificationDialogHelper.showNotificationDialog(requireActivity())
                }
            }
        }
    }

    private var lastNavTime = 0L
    private fun canNavigate(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastNavTime < 600L) return false
        lastNavTime = now
        return true
    }

    private fun setupHeader() {
        binding.tvBrandAi.post {
            if (!isAdded || bindingOrNull == null) return@post
            val tv = bindingOrNull?.tvBrandAi ?: return@post
            val width = tv.paint.measureText(tv.text.toString()).coerceAtLeast(1f)
            val shader = LinearGradient(
                0f, 0f, width, 0f,
                intArrayOf(
                    Color.parseColor("#3D86F4"),
                    Color.parseColor("#8A5CF0")
                ),
                null,
                Shader.TileMode.CLAMP
            )
            tv.paint.shader = shader
            tv.invalidate()
        }
    }

    override fun initListener() {
        // 3 Core Features
        binding.cardFeatureVideo.setOnClickListener {
            if (canNavigate()) navigateToFeature("video")
        }

        binding.cardFeaturePrompt.setOnClickListener {
            if (canNavigate()) navigateToFeature("prompt")
        }

        binding.cardFeatureRestore.setOnClickListener {
            if (canNavigate()) navigateToFeature("restore")
        }

        // See all actions -> switches to Tab 1 (Music)
        binding.btnSeeAllHistory.setOnClickListener {
            (parentFragment as? MainFragment)?.selectTab(2)
        }

        binding.btnSeeAllSuggestions.setOnClickListener {
            if (canNavigate()) {
                val navController = parentFragment?.parentFragment?.findNavController()
                    ?: parentFragment?.findNavController()
                    ?: findNavController()
                navController.navigateSafe(R.id.action_mainFragment_to_musicFragment)
            }
        }

        binding.btnSeeAllTemplates.setOnClickListener {
            if (canNavigate()) {
                val navController = parentFragment?.parentFragment?.findNavController()
                    ?: parentFragment?.findNavController()
                    ?: findNavController()
                navController.navigateSafe(R.id.action_mainFragment_to_musicFragment)
            }
        }

        binding.swipeRefreshLayout.setColorSchemeColors(
            Color.parseColor("#4A7BFE"),
            Color.parseColor("#9153EE")
        )
        binding.swipeRefreshLayout.setOnRefreshListener {
            setupBanners()
            loadRecentHistory()
            loadHomeData()
        }

        binding.btnPro.setOnClickListener {
            (parentFragment as? MainFragment)?.selectTab(3)
        }
    }

    private fun setupBanners() {
        val banners = getBannerList()
        val bannerAdapter = BannerAdapter(banners) { banner ->
            if (canNavigate()) navigateToFeature(banner.targetFeature)
        }
        binding.vpBanners.adapter = bannerAdapter

        binding.bannerIndicator.setDotCount(banners.size)
        binding.bannerIndicator.setSelectedPage(0)
        binding.bannerIndicator.onDotClickListener = { index ->
            binding.vpBanners.setCurrentItem(index, true)
        }

        binding.vpBanners.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)
                binding.bannerIndicator.setProgress(position, positionOffset)
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.bannerIndicator.setSelectedPage(position)
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> {
                        pauseAutoScroll()
                    }
                    ViewPager2.SCROLL_STATE_IDLE -> {
                        restartAutoScroll()
                    }
                }
            }
        })

        startAutoScroll()
    }

    private fun pauseAutoScroll() {
        autoScrollRunnable?.let { autoScrollHandler.removeCallbacks(it) }
    }

    private fun restartAutoScroll() {
        pauseAutoScroll()
        startAutoScroll()
    }

    private fun startAutoScroll() {
        pauseAutoScroll()
        autoScrollRunnable = object : Runnable {
            override fun run() {
                val current = binding.vpBanners.currentItem
                val count = binding.vpBanners.adapter?.itemCount ?: 1
                val next = if (count > 0) (current + 1) % count else 0
                binding.vpBanners.setCurrentItem(next, true)
                autoScrollHandler.postDelayed(this, 4500L)
            }
        }
        autoScrollHandler.postDelayed(autoScrollRunnable!!, 4500L)
    }

    private fun setupRecentHistory() {
        recentHistoryAdapter = HomeRecentHistoryAdapter(emptyList()) { item ->
            val historyItems = historyRepository.getHistoryList()
            val matched = historyItems.find { it.title == item.title || (item.imageUrl != null && it.imageUrl == item.imageUrl) }
            if (matched != null) {
                val bundle = android.os.Bundle().apply {
                    putString(
                        com.example.phortart_video_990.ui.screen.history.HistoryDetailFragment.KEY_HISTORY_ITEM_JSON,
                        com.google.gson.Gson().toJson(matched)
                    )
                }
                val navController = parentFragment?.parentFragment?.findNavController() ?: findNavController()
                navController.navigateSafe(R.id.action_mainFragment_to_historyDetailFragment, bundle)
            } else {
                (parentFragment as? MainFragment)?.selectTab(2)
            }
        }
        binding.rvRecentHistory.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecentHistory.adapter = recentHistoryAdapter
        loadRecentHistory()
    }

    private fun loadRecentHistory() {
        val historyItems = historyRepository.getHistoryList()
        val displayList = if (historyItems.isNotEmpty()) {
            historyItems
        } else {
            com.example.phortart_video_990.core.utils.TemplateRecommendationHelper.getDefaultRecentHistory()
        }
        recentHistoryAdapter.submitList(displayList)
        binding.llRecentHistorySection.visibility = View.VISIBLE
    }

    private fun setupForYouTemplates() {
        forYouAdapter = HomeForYouAdapter(
            items = emptyList(),
            onItemClick = { template -> onTemplateSelected(template) },
            onPlayClick = { template, pos -> toggleAudioPreview(template, pos, isForYou = true) }
        )
        binding.rvSuggestions.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvSuggestions.adapter = forYouAdapter
    }

    private fun setupHotTemplates() {
        hotTemplateAdapter = HomeHotAdapter(
            items = emptyList(),
            onItemClick = { template -> onTemplateSelected(template) },
            onPlayClick = { template, pos -> toggleAudioPreview(template, pos, isForYou = false) }
        )
        binding.rvHotTemplates.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvHotTemplates.adapter = hotTemplateAdapter
    }

    private fun onTemplateSelected(template: TemplateModel) {
        stopAudio()
        // Record template category usage to boost category ranking for future sessions and priority
        com.example.phortart_video_990.data.local.PreferencesManager.getInstance(requireContext())
            .recordSelectedCategory(template.category)

        val bundle = android.os.Bundle().apply {
            putString("template_code", template.id)
            putString("template_title", template.title)
            putString("template_category", template.category)
            putString("template_thumb", template.safeImageUrl)
            putString("template_audio_url", template.safeAudioUrl)
            putString("selected_music_title", template.title)
            putString("selected_music_audio_url", template.safeAudioUrl)
            putString("selected_music_image_url", template.safeImageUrl)
            putFloat("selected_template_duration_sec", template.durationSeconds.toFloat())
            putInt("selected_template_photo_count", template.targetPhotoCount)
        }
        val navController = parentFragment?.parentFragment?.findNavController()
            ?: parentFragment?.findNavController()
            ?: findNavController()
        navController.navigateSafe(R.id.action_mainFragment_to_memoriesFragment, bundle)
    }

    private fun setAdapterLoading(isForYou: Boolean, position: Int) {
        if (isForYou) forYouAdapter.setLoadingState(position) else hotTemplateAdapter.setLoadingState(position)
    }

    private fun setAdapterPlaying(isForYou: Boolean, position: Int, isPlaying: Boolean) {
        if (isForYou) forYouAdapter.setPlayingState(position, isPlaying) else hotTemplateAdapter.setPlayingState(position, isPlaying)
    }

    private fun toggleAudioPreview(template: TemplateModel, position: Int, isForYou: Boolean) {
        if (activePlayingTrackId == template.id) {
            stopAudio()
            return
        }

        stopAudio()

        val audioUrl = template.safeAudioUrl
        if (audioUrl.isNullOrBlank()) return

        setAdapterLoading(isForYou, position)

        previewJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val cachedFile = AudioCacheManager.getOrDownloadAudio(requireContext(), audioUrl)
                if (!isAdded || view == null) return@launch

                if (cachedFile == null || !cachedFile.exists() || cachedFile.length() == 0L) {
                    setAdapterPlaying(isForYou, position, false)
                    return@launch
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(cachedFile.absolutePath)
                    setOnPreparedListener { mp ->
                        mp.start()
                        activePlayingTrackId = template.id
                        setAdapterPlaying(isForYou, position, true)
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
                setAdapterPlaying(isForYou, position, false)
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
        forYouAdapter.setPlayingState(-1, false)
        hotTemplateAdapter.setPlayingState(-1, false)
    }

    private var cachedMusicTracks: List<TemplateModel> = emptyList()

    private fun refreshRecommendations() {
        if (!isAdded) return
        val currentPool = cachedMusicTracks
        val recommended = com.example.phortart_video_990.core.utils.TemplateRecommendationHelper
            .recommendForYouTemplates(requireContext(), currentPool)
        forYouAdapter.submitList(recommended)

        val recKeys = recommended.map { it.id.ifBlank { it.title }.trim().lowercase() }.toSet()
        val defaultHot = com.example.phortart_video_990.core.utils.TemplateRecommendationHelper.getDefaultHotTemplates()
        val availableHot = (currentPool + defaultHot)
            .distinctBy { it.id.ifBlank { it.title }.trim().lowercase() }
            .filterNot { recKeys.contains(it.id.ifBlank { it.title }.trim().lowercase()) }
        hotTemplateAdapter.submitList(availableHot.take(8))
    }

    private fun loadHomeData() {
        // 1. Immediately recommend and display templates using the ranking, priority, and anti-collision engine
        val initialForYou = com.example.phortart_video_990.core.utils.TemplateRecommendationHelper
            .recommendForYouTemplates(requireContext(), emptyList())
        forYouAdapter.submitList(initialForYou)

        val forYouKeys = initialForYou.map { it.id.ifBlank { it.title }.trim().lowercase() }.toSet()
        val initialHot = com.example.phortart_video_990.core.utils.TemplateRecommendationHelper
            .getDefaultHotTemplates()
            .filterNot { forYouKeys.contains(it.id.ifBlank { it.title }.trim().lowercase()) }
        hotTemplateAdapter.submitList(initialHot)

        // 2. Fetch music tracks from API in background and apply ranking, priority, and anti-collision
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val apiMusicTracks = templateRepository.getMusicTracks()
                if (apiMusicTracks.isNotEmpty()) {
                    cachedMusicTracks = apiMusicTracks
                    val recommended = com.example.phortart_video_990.core.utils.TemplateRecommendationHelper
                        .recommendForYouTemplates(requireContext(), apiMusicTracks)
                    forYouAdapter.submitList(recommended)

                    val recKeys = recommended.map { it.id.ifBlank { it.title }.trim().lowercase() }.toSet()
                    val defaultHot = com.example.phortart_video_990.core.utils.TemplateRecommendationHelper.getDefaultHotTemplates()
                    val availableHot = (apiMusicTracks + defaultHot)
                        .distinctBy { it.id.ifBlank { it.title }.trim().lowercase() }
                        .filterNot { recKeys.contains(it.id.ifBlank { it.title }.trim().lowercase()) }
                    hotTemplateAdapter.submitList(availableHot.take(8))
                }
            } catch (_: Exception) {
                // Keep default recommendation and hot music lists
            } finally {
                bindingOrNull?.swipeRefreshLayout?.postDelayed({
                    if (isAdded && bindingOrNull != null) {
                        bindingOrNull?.swipeRefreshLayout?.isRefreshing = false
                    }
                }, 500L)
            }
        }
    }

    private fun navigateToFeature(featureId: String) {
        if (!isAdded) return
        val navController = parentFragment?.parentFragment?.findNavController()
            ?: parentFragment?.findNavController()
            ?: findNavController()
        when (featureId) {
            "video" -> {
                val bundle = android.os.Bundle().apply {
                    putBoolean("is_start_merge_flow", true)
                }
                navController.navigateSafe(R.id.action_mainFragment_to_musicFragment, bundle)
            }
            "prompt" -> navController.navigateSafe(R.id.action_mainFragment_to_enhanceFragment)
            "restore" -> navController.navigateSafe(R.id.action_mainFragment_to_restoreFragment)
            "music" -> navController.navigateSafe(R.id.action_mainFragment_to_musicFragment)
        }
    }

    override fun onDestroyView() {
        autoScrollRunnable?.let { autoScrollHandler.removeCallbacks(it) }
        stopAudio()
        super.onDestroyView()
    }
}
