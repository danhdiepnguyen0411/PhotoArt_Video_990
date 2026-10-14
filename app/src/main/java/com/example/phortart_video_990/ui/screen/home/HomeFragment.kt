package com.example.phortart_video_990.ui.screen.home

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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

    private var savedScrollY: Int = 0

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        savedInstanceState?.getInt("saved_home_scroll_y")?.let {
            if (it > 0) savedScrollY = it
        }
        super.onViewCreated(view, savedInstanceState)
    }

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
        setupNetworkObserver()
    }

    private fun setupNetworkObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                var wasOffline = false
                com.example.phortart_video_990.core.utils.NetworkMonitor.observeNetwork(requireContext()).collect { isOnline ->
                    if (isOnline) {
                        val needReload = cachedMusicTracks.isEmpty() ||
                                forYouAdapter.itemCount == 0 ||
                                hotTemplateAdapter.itemCount == 0
                        if (wasOffline || needReload) {
                            loadHomeData(forceRefresh = true)
                        }
                        wasOffline = false
                    } else {
                        wasOffline = true
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startAutoScroll()
        loadRecentHistory()
        checkCustomNotificationPermission()
        restoreScrollPosition()

        if ((cachedMusicTracks.isEmpty() || forYouAdapter.itemCount == 0) &&
            com.example.phortart_video_990.core.utils.NetworkMonitor.isOnline(requireContext())) {
            loadHomeData(forceRefresh = true)
        }
    }

    override fun onPause() {
        pauseAutoScroll()
        stopAudio()
        bindingOrNull?.let {
            savedScrollY = it.nestedScrollView.scrollY
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        val scrollY = bindingOrNull?.nestedScrollView?.scrollY ?: savedScrollY
        outState.putInt("saved_home_scroll_y", scrollY)
    }

    private fun restoreScrollPosition() {
        if (savedScrollY > 0) {
            bindingOrNull?.nestedScrollView?.post {
                if (isAdded && bindingOrNull != null && savedScrollY > 0) {
                    bindingOrNull?.nestedScrollView?.scrollTo(0, savedScrollY)
                }
            }
            bindingOrNull?.nestedScrollView?.postDelayed({
                if (isAdded && bindingOrNull != null && savedScrollY > 0) {
                    bindingOrNull?.nestedScrollView?.scrollTo(0, savedScrollY)
                }
            }, 100L)
        }
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
            stopAudio()
            (parentFragment as? MainFragment)?.selectTab(1)
                ?: (parentFragment?.parentFragment as? MainFragment)?.selectTab(1)
        }

        binding.btnSeeAllTemplates.setOnClickListener {
            stopAudio()
            (parentFragment as? MainFragment)?.selectTab(1)
                ?: (parentFragment?.parentFragment as? MainFragment)?.selectTab(1)
        }

        binding.swipeRefreshLayout.setColorSchemeColors(
            Color.parseColor("#4A7BFE"),
            Color.parseColor("#9153EE")
        )
        binding.swipeRefreshLayout.setOnRefreshListener {
            savedScrollY = 0
            setupBanners()
            loadRecentHistory()
            com.example.phortart_video_990.core.utils.TemplateRecommendationHelper.clearSessionCache()
            loadHomeData(forceRefresh = true)
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
            val bundle = android.os.Bundle().apply {
                putString(
                    com.example.phortart_video_990.ui.screen.history.HistoryDetailFragment.KEY_HISTORY_ITEM_JSON,
                    com.google.gson.Gson().toJson(item)
                )
            }
            val navController = parentFragment?.parentFragment?.findNavController() ?: findNavController()
            navController.navigateSafe(R.id.action_mainFragment_to_historyDetailFragment, bundle)
        }
        binding.rvRecentHistory.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecentHistory.adapter = recentHistoryAdapter
        loadRecentHistory()
    }

    private fun loadRecentHistory() {
        val historyItems = historyRepository.getHistoryList()
        if (historyItems.isNotEmpty()) {
            recentHistoryAdapter.submitList(historyItems)
            binding.llRecentHistorySection.visibility = View.VISIBLE
        } else {
            recentHistoryAdapter.submitList(emptyList())
            binding.llRecentHistorySection.visibility = View.GONE
        }
    }

    private fun setupForYouTemplates() {
        forYouAdapter = HomeForYouAdapter(
            items = emptyList(),
            onItemClick = { template -> onTemplateSelected(template) }
        )
        binding.rvSuggestions.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvSuggestions.adapter = forYouAdapter
    }

    private fun setupHotTemplates() {
        hotTemplateAdapter = HomeHotAdapter(
            items = emptyList(),
            onItemClick = { template -> onTemplateSelected(template) }
        )
        binding.rvHotTemplates.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvHotTemplates.adapter = hotTemplateAdapter
    }

    private fun onTemplateSelected(template: TemplateModel) {
        stopAudio()
        bindingOrNull?.let {
            savedScrollY = it.nestedScrollView.scrollY
        }
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
        if (currentPool.isEmpty()) return
        val recommended = com.example.phortart_video_990.core.utils.TemplateRecommendationHelper
            .recommendForYouTemplates(requireContext(), currentPool, forceRefresh = false)
        forYouAdapter.submitList(recommended)

        val availableHot = com.example.phortart_video_990.core.utils.TemplateRecommendationHelper
            .computeAndCacheHotTemplates(currentPool, recommended, forceRefresh = false)
        hotTemplateAdapter.submitList(availableHot)
    }

    private fun loadHomeData(forceRefresh: Boolean = false) {
        // Nếu trong phiên mở app này đã có danh sách "Dành riêng cho bạn" và không phải do user chủ động swipe-refresh
        if (!forceRefresh && com.example.phortart_video_990.core.utils.TemplateRecommendationHelper.hasSessionRecommendations()) {
            val sessionForYou = com.example.phortart_video_990.core.utils.TemplateRecommendationHelper.getSessionForYouTemplates() ?: emptyList()
            val sessionHot = com.example.phortart_video_990.core.utils.TemplateRecommendationHelper.getSessionHotTemplates() ?: emptyList()
            forYouAdapter.submitList(sessionForYou)
            hotTemplateAdapter.submitList(sessionHot)
            bindingOrNull?.layoutSkeletonSuggestions?.root?.visibility = View.GONE
            bindingOrNull?.rvSuggestions?.visibility = View.VISIBLE
            bindingOrNull?.layoutSkeletonHot?.root?.visibility = View.GONE
            bindingOrNull?.rvHotTemplates?.visibility = View.VISIBLE
            restoreScrollPosition()
            return
        }

        // Show skeleton shimmer loading for both sections while calling API
        bindingOrNull?.layoutSkeletonSuggestions?.root?.visibility = View.VISIBLE
        bindingOrNull?.rvSuggestions?.visibility = View.GONE
        bindingOrNull?.layoutSkeletonHot?.root?.visibility = View.VISIBLE
        bindingOrNull?.rvHotTemplates?.visibility = View.GONE

        // Fetch templates from API in background (NO hardcoded fake templates)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                var apiTracks = templateRepository.getMusicTracks()
                if (apiTracks.isEmpty()) {
                    apiTracks = templateRepository.getAllTemplateApps()
                }
                if (apiTracks.isNotEmpty()) {
                    cachedMusicTracks = apiTracks
                    val recommended = com.example.phortart_video_990.core.utils.TemplateRecommendationHelper
                        .recommendForYouTemplates(requireContext(), apiTracks, forceRefresh = forceRefresh)
                    forYouAdapter.submitList(recommended)

                    val availableHot = com.example.phortart_video_990.core.utils.TemplateRecommendationHelper
                        .computeAndCacheHotTemplates(apiTracks, recommended, forceRefresh = forceRefresh)
                    hotTemplateAdapter.submitList(availableHot)

                    bindingOrNull?.layoutSkeletonSuggestions?.root?.visibility = View.GONE
                    bindingOrNull?.rvSuggestions?.visibility = View.VISIBLE
                    bindingOrNull?.layoutSkeletonHot?.root?.visibility = View.GONE
                    bindingOrNull?.rvHotTemplates?.visibility = View.VISIBLE
                    restoreScrollPosition()
                } else {
                    forYouAdapter.submitList(emptyList())
                    hotTemplateAdapter.submitList(emptyList())
                    bindingOrNull?.layoutSkeletonSuggestions?.root?.visibility = View.GONE
                    bindingOrNull?.rvSuggestions?.visibility = View.VISIBLE
                    bindingOrNull?.layoutSkeletonHot?.root?.visibility = View.GONE
                    bindingOrNull?.rvHotTemplates?.visibility = View.VISIBLE
                }
            } catch (_: Exception) {
                forYouAdapter.submitList(emptyList())
                hotTemplateAdapter.submitList(emptyList())
                bindingOrNull?.layoutSkeletonSuggestions?.root?.visibility = View.GONE
                bindingOrNull?.rvSuggestions?.visibility = View.VISIBLE
                bindingOrNull?.layoutSkeletonHot?.root?.visibility = View.GONE
                bindingOrNull?.rvHotTemplates?.visibility = View.VISIBLE
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
        bindingOrNull?.let {
            savedScrollY = it.nestedScrollView.scrollY
        }
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
