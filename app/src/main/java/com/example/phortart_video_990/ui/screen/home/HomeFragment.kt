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

    private lateinit var recentHistoryAdapter: HomeMediaAdapter
    private lateinit var trendingMusicAdapter: HomeMusicAdapter
    private lateinit var hotMusicAdapter: HomeMusicAdapter

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
        setupTrendingMusic()
        setupHotMusic()
        loadApiMusic()
    }

    override fun onResume() {
        super.onResume()
        setupBanners()
        loadRecentHistory()
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
            (parentFragment as? MainFragment)?.selectTab(1)
        }

        binding.btnSeeAllTemplates.setOnClickListener {
            (parentFragment as? MainFragment)?.selectTab(1)
        }

        binding.swipeRefreshLayout.setColorSchemeColors(
            Color.parseColor("#4A7BFE"),
            Color.parseColor("#9153EE")
        )
        binding.swipeRefreshLayout.setOnRefreshListener {
            setupBanners()
            loadRecentHistory()
            loadApiMusic()
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
        recentHistoryAdapter = HomeMediaAdapter(emptyList()) { item ->
            val historyItems = historyRepository.getHistoryList()
            val matched = historyItems.find { it.title == item.label || (item.imageUrl != null && it.imageUrl == item.imageUrl) }
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
        val mediaItems = historyItems.map { item ->
            HomeMediaItemModel(
                label = item.title,
                category = item.type,
                imageUrl = item.imageUrl,
                imageUri = item.imageUri,
                iconRes = R.drawable.ic_badge_image
            )
        }
        recentHistoryAdapter.submitList(mediaItems)
        binding.llRecentHistorySection.visibility = if (mediaItems.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setupTrendingMusic() {
        trendingMusicAdapter = HomeMusicAdapter(
            items = emptyList(),
            onTrackClick = { track -> onMusicTrackSelected(track) },
            onPlayClick = { track, pos -> toggleAudioPreview(track, pos, isTrending = true) }
        )
        binding.rvSuggestions.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvSuggestions.adapter = trendingMusicAdapter
    }

    private fun setupHotMusic() {
        hotMusicAdapter = HomeMusicAdapter(
            items = emptyList(),
            onTrackClick = { track -> onMusicTrackSelected(track) },
            onPlayClick = { track, pos -> toggleAudioPreview(track, pos, isTrending = false) }
        )
        binding.rvHotTemplates.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvHotTemplates.adapter = hotMusicAdapter
    }

    private fun onMusicTrackSelected(track: TemplateModel) {
        stopAudio()
        val bundle = android.os.Bundle().apply {
            putString("selected_music_title", track.title)
            putString("selected_music_audio_url", track.safeAudioUrl)
            putString("selected_music_image_url", track.safeImageUrl)
        }
        val navController = parentFragment?.parentFragment?.findNavController()
            ?: parentFragment?.findNavController()
            ?: findNavController()
        navController.navigateSafe(R.id.action_mainFragment_to_memoriesFragment, bundle)
    }

    private fun toggleAudioPreview(track: TemplateModel, position: Int, isTrending: Boolean) {
        val targetAdapter = if (isTrending) trendingMusicAdapter else hotMusicAdapter

        if (activePlayingTrackId == track.id) {
            stopAudio()
            return
        }

        stopAudio()

        val audioUrl = track.safeAudioUrl
        if (audioUrl.isNullOrBlank()) return

        targetAdapter.setLoadingState(position)

        previewJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val cachedFile = AudioCacheManager.getOrDownloadAudio(requireContext(), audioUrl)
                if (!isAdded || view == null) return@launch

                if (cachedFile == null || !cachedFile.exists() || cachedFile.length() == 0L) {
                    targetAdapter.setPlayingState(position, false)
                    return@launch
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(cachedFile.absolutePath)
                    setOnPreparedListener { mp ->
                        mp.start()
                        activePlayingTrackId = track.id
                        targetAdapter.setPlayingState(position, true)
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
                targetAdapter.setPlayingState(position, false)
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
        trendingMusicAdapter.setPlayingState(-1, false)
        hotMusicAdapter.setPlayingState(-1, false)
    }

    private fun loadApiMusic() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val tracks = templateRepository.getMusicTracks()
                if (tracks.isNotEmpty()) {
                    val trending = tracks.take(8)
                    val hot = if (tracks.size > 8) tracks.drop(8).take(8) else tracks
                    trendingMusicAdapter.submitList(trending)
                    hotMusicAdapter.submitList(hot)
                }
            } catch (e: Exception) {
                // Keep empty list or fallback
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
