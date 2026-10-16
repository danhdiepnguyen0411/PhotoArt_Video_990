package com.aiart.photo.beauti.editor.ui.screen.home

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
import com.aiart.photo.beauti.editor.MainActivity
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.core.base.BaseFragment
import com.aiart.photo.beauti.editor.core.dialog.NotificationDialogHelper
import com.aiart.photo.beauti.editor.core.utils.AudioCacheManager
import com.aiart.photo.beauti.editor.core.utils.NotificationPermissionManager
import com.aiart.photo.beauti.editor.core.utils.navigateSafe
import com.aiart.photo.beauti.editor.data.local.PreferencesManager
import com.aiart.photo.beauti.editor.data.model.BannerItemModel
import com.aiart.photo.beauti.editor.data.model.CategoryModel
import com.aiart.photo.beauti.editor.data.model.HomeMediaItemModel
import com.aiart.photo.beauti.editor.data.model.TemplateModel
import com.aiart.photo.beauti.editor.data.repository.HistoryRepository
import com.aiart.photo.beauti.editor.data.repository.TemplateRepository
import com.aiart.photo.beauti.editor.databinding.FragmentHomeBinding
import com.aiart.photo.beauti.editor.ui.screen.feature.createtemplate.CreateTemplateVideoFragment
import com.aiart.photo.beauti.editor.ui.screen.main.MainFragment
import com.aiart.photo.beauti.editor.ui.screen.template.CategoryAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HomeFragment : BaseFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {

    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private var autoScrollRunnable: Runnable? = null

    private val templateRepository = TemplateRepository()
    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private lateinit var homeCategoryAdapter: CategoryAdapter
    private var selectedCategoryCode: String = "ALL"

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
                titleLine1 = getString(R.string.banner_1_title_line1),
                titleLine2Prefix = getString(R.string.banner_1_title_line2),
                highlight = getString(R.string.banner_1_highlight),
                description = getString(R.string.banner_1_desc),
                buttonText = getString(R.string.banner_1_btn),
                targetFeature = "video"
            ),
            BannerItemModel(
                imageRes = R.drawable.banner_2,
                titleLine1 = getString(R.string.banner_2_title_line1),
                titleLine2Prefix = getString(R.string.banner_2_title_line2),
                highlight = getString(R.string.banner_2_highlight),
                description = getString(R.string.banner_2_desc),
                buttonText = getString(R.string.banner_2_btn),
                targetFeature = "prompt"
            ),
            BannerItemModel(
                imageRes = R.drawable.banner_3,
                titleLine1 = getString(R.string.banner_3_title_line1),
                titleLine2Prefix = getString(R.string.banner_3_title_line2),
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
        setupHomeCategories()
        setupRecentHistory()
        setupForYouTemplates()
        setupHotTemplates()
        loadHomeData()
        setupNetworkObserver()
        setupMarqueeTitles()
    }

    private fun setupMarqueeTitles() {
        bindingOrNull?.let { b ->
            b.tvFeatureDanceTitle.isSelected = true
            b.tvFeaturePromptTitle.isSelected = true
            b.tvFeatureRestoreTitle.isSelected = true
            b.tvFeatureVideoTitle.isSelected = true
        }
    }

    private fun setupNetworkObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                var wasOffline = false
                com.aiart.photo.beauti.editor.core.utils.NetworkMonitor.observeNetwork(requireContext()).collect { isOnline ->
                    if (isOnline) {
                        val needReload = cachedDancingTemplates.isEmpty() ||
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
        setupMarqueeTitles()
        startAutoScroll()
        loadRecentHistory()
        checkCustomNotificationPermission()
        restoreScrollPosition()

        if ((cachedDancingTemplates.isEmpty() || forYouAdapter.itemCount == 0) &&
            com.aiart.photo.beauti.editor.core.utils.NetworkMonitor.isOnline(requireContext())) {
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
        val ctx = context ?: return
        if (NotificationDialogHelper.isNotificationGranted(ctx)) {
            MainActivity.shouldCheckNotificationOnResume = false
            return
        }

        val prefs = PreferencesManager.getInstance(ctx)
        val shouldShowFromSplash = prefs.shouldShowNotiDialogOnHome
        val shouldShowFromManager = NotificationPermissionManager.shouldShowNotificationDialog(ctx)

        if (shouldShowFromSplash || shouldShowFromManager) {
            prefs.shouldShowNotiDialogOnHome = false
            MainActivity.shouldCheckNotificationOnResume = false
            binding.root.post {
                if (isAdded) {
                    NotificationDialogHelper.showNotificationDialog(requireActivity())
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
        binding.headerHome.applyBrandAiGradient()
    }

    override fun initListener() {
        // 4 Core Features
        binding.cardFeatureDance.setOnClickListener {
            if (canNavigate()) navigateToFeature("dance")
        }

        binding.cardFeaturePrompt.setOnClickListener {
            if (canNavigate()) navigateToFeature("prompt")
        }

        binding.cardFeatureRestore.setOnClickListener {
            if (canNavigate()) navigateToFeature("restore")
        }

        binding.cardFeatureVideo.setOnClickListener {
            if (canNavigate()) navigateToFeature("video")
        }

        // See all actions -> switches to Tab 1 (Music)
        binding.btnSeeAllHistory.setOnClickListener {
            (parentFragment as? MainFragment)?.selectTab(2)
        }

        binding.btnSeeAllSuggestions.setOnClickListener {
            stopAudio()
            val main = (parentFragment as? MainFragment)
                ?: (parentFragment?.parentFragment as? MainFragment)
            main?.selectTab(1, "ALL")
        }

        binding.btnSeeAllTemplates.setOnClickListener {
            stopAudio()
            val main = (parentFragment as? MainFragment)
                ?: (parentFragment?.parentFragment as? MainFragment)
            main?.selectTab(1, "ALL")
        }

        binding.swipeRefreshLayout.setColorSchemeColors(
            Color.parseColor("#4A7BFE"),
            Color.parseColor("#9153EE")
        )
        binding.swipeRefreshLayout.setOnRefreshListener {
            savedScrollY = 0
            setupBanners()
            loadRecentHistory()
            com.aiart.photo.beauti.editor.core.utils.TemplateRecommendationHelper.clearSessionCache()
            loadHomeData(forceRefresh = true)
        }

        binding.headerHome.setOnProClickListener {
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
                    com.aiart.photo.beauti.editor.ui.screen.history.HistoryDetailFragment.KEY_HISTORY_ITEM_JSON,
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

    private fun setupHomeCategories() {
        homeCategoryAdapter = CategoryAdapter(
            categories = listOf(CategoryModel.ALL),
            selectedCategoryCode = selectedCategoryCode
        ) { selectedCat ->
            selectedCategoryCode = selectedCat.code
            applyCategoryFilter(selectedCat)
        }
        binding.rvHomeCategories.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvHomeCategories.adapter = homeCategoryAdapter
        binding.rvHomeCategories.visibility = View.GONE
    }

    private fun applyCategoryFilter(category: CategoryModel) {
        val pool = cachedDancingTemplates
        if (pool.isEmpty()) return

        val isAll = category.code.isBlank() ||
                category.code.equals("ALL", ignoreCase = true) ||
                category.code.equals("Tất cả", ignoreCase = true)

        if (isAll) {
            val recommended = com.aiart.photo.beauti.editor.core.utils.TemplateRecommendationHelper
                .recommendForYouTemplates(requireContext(), pool, forceRefresh = false)
            forYouAdapter.submitList(recommended)

            val availableHot = com.aiart.photo.beauti.editor.core.utils.TemplateRecommendationHelper
                .computeAndCacheHotTemplates(pool, recommended, forceRefresh = false)
            hotTemplateAdapter.submitList(availableHot)
        } else {
            val target = category.code.trim().lowercase()
            val targetName = category.name.trim().lowercase()
            val filtered = pool.filter { item ->
                val itemCat = item.category.trim().lowercase()
                itemCat == target || itemCat == targetName || itemCat.contains(target) || itemCat.contains(targetName)
            }
            val displayPool = if (filtered.isNotEmpty()) filtered else pool
            val forYou = displayPool.take(8)
            val hot = if (displayPool.size > 8) displayPool.drop(8) else displayPool
            forYouAdapter.submitList(forYou)
            hotTemplateAdapter.submitList(hot)
        }
    }

    private fun onTemplateSelected(template: TemplateModel) {
        stopAudio()
        bindingOrNull?.let {
            savedScrollY = it.nestedScrollView.scrollY
        }
        // Record template category usage to boost category ranking for future sessions and priority
        com.aiart.photo.beauti.editor.data.local.PreferencesManager.getInstance(requireContext())
            .recordSelectedCategory(template.category)

        val activeCat = if (selectedCategoryCode.isNotBlank() &&
            !selectedCategoryCode.equals("ALL", ignoreCase = true)
        ) {
            selectedCategoryCode
        } else {
            template.category
        }

        val bundle = android.os.Bundle().apply {
            putString(CreateTemplateVideoFragment.ARG_TEMPLATE_CODE, template.id)
            putString("template_code", template.id)
            putString(CreateTemplateVideoFragment.ARG_TEMPLATE_TITLE, template.title)
            putString("template_title", template.title)
            putString(CreateTemplateVideoFragment.ARG_TEMPLATE_CATEGORY, activeCat)
            putString("template_category", activeCat)
            putString(CreateTemplateVideoFragment.ARG_TEMPLATE_THUMB, template.safeImageUrl)
            putString("template_thumb", template.safeImageUrl)
            putString(CreateTemplateVideoFragment.ARG_TEMPLATE_TYPE, template.templateType)
            putString("template_type", template.templateType)
            putString("template_audio_url", template.safeAudioUrl)
            putFloat("selected_template_duration_sec", template.durationSeconds.toFloat())
            putInt("selected_template_photo_count", template.targetPhotoCount)
        }
        val navController = parentFragment?.parentFragment?.findNavController()
            ?: parentFragment?.findNavController()
            ?: findNavController()
        navController.navigateSafe(R.id.action_mainFragment_to_createTemplateVideoFragment, bundle)
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

    private var cachedDancingTemplates: List<TemplateModel> = emptyList()

    private fun refreshRecommendations() {
        if (!isAdded) return
        val currentPool = cachedDancingTemplates
        if (currentPool.isEmpty()) return
        val recommended = com.aiart.photo.beauti.editor.core.utils.TemplateRecommendationHelper
            .recommendForYouTemplates(requireContext(), currentPool, forceRefresh = false)
        forYouAdapter.submitList(recommended)

        val availableHot = com.aiart.photo.beauti.editor.core.utils.TemplateRecommendationHelper
            .computeAndCacheHotTemplates(currentPool, recommended, forceRefresh = false)
        hotTemplateAdapter.submitList(availableHot)
    }

    private fun loadHomeData(forceRefresh: Boolean = false) {
        // Nếu trong phiên mở app này đã có danh sách "Dành riêng cho bạn" và không phải do user chủ động swipe-refresh
        if (!forceRefresh && com.aiart.photo.beauti.editor.core.utils.TemplateRecommendationHelper.hasSessionRecommendations()) {
            val sessionForYou = com.aiart.photo.beauti.editor.core.utils.TemplateRecommendationHelper.getSessionForYouTemplates() ?: emptyList()
            val sessionHot = com.aiart.photo.beauti.editor.core.utils.TemplateRecommendationHelper.getSessionHotTemplates() ?: emptyList()
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

        // Fetch dancing templates from API in background (NO hardcoded fake templates)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1. Fetch dancing categories (exclude ANIME and APP_LEGAL_LINKS)
                val apiCategories = templateRepository.getDancingCategories()
                val categoriesList = mutableListOf(CategoryModel.ALL)
                categoriesList.addAll(apiCategories.filter { 
                    !it.code.equals("ALL", ignoreCase = true) && !it.isAnimeCategory && !it.isLegalLinkCategory 
                })

                // 2. Fetch dancing templates (exclude ANIME and APP_LEGAL_LINKS)
                var apiTracks = templateRepository.getDancingTemplates()
                    .filter { !it.isAnimeTemplate && !it.isLegalLinkTemplate }
                if (apiTracks.isEmpty()) {
                    apiTracks = templateRepository.getAllTemplateApps()
                        .filter { !it.isMusic && !it.isAnimeTemplate && !it.isLegalLinkTemplate }
                }

                // 3. Dynamically discover categories from dancing templates
                val existingCodes = categoriesList.map { it.code.lowercase() }.toMutableSet()
                apiTracks.forEach { item ->
                    if (item.isAnimeTemplate || item.isLegalLinkTemplate) return@forEach
                    val cat = item.category.trim()
                    if (cat.isNotBlank() && !cat.startsWith("MUSIC_", ignoreCase = true)) {
                        val cleanCat = cat.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                        val catModel = CategoryModel(code = cat, name = cleanCat, active = true)
                        if (!catModel.isAnimeCategory && !catModel.isLegalLinkCategory) {
                            if (existingCodes.add(cat.lowercase())) {
                                categoriesList.add(catModel)
                            }
                        }
                    }
                }
                homeCategoryAdapter.submitList(categoriesList)

                if (apiTracks.isNotEmpty()) {
                    cachedDancingTemplates = apiTracks
                    com.aiart.photo.beauti.editor.core.notification.NotificationScheduler.checkAndNotifyNewTemplates(requireContext(), apiTracks)
                    applyCategoryFilter(CategoryModel(code = selectedCategoryCode, name = selectedCategoryCode))

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
            "dance" -> navController.navigateSafe(R.id.action_mainFragment_to_createTemplateVideoFragment)
            "video" -> navController.navigateSafe(R.id.action_mainFragment_to_memoriesFragment)
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
