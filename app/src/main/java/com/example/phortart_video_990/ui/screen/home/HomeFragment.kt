package com.example.phortart_video_990.ui.screen.home

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.model.BannerItemModel
import com.example.phortart_video_990.data.model.HomeMediaItemModel
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.data.repository.TemplateRepository
import com.example.phortart_video_990.databinding.FragmentHomeBinding
import com.example.phortart_video_990.ui.screen.main.MainFragment
import kotlinx.coroutines.launch

class HomeFragment : BaseFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {

    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private var autoScrollRunnable: Runnable? = null

    private val templateRepository = TemplateRepository()
    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private lateinit var recentHistoryAdapter: HomeMediaAdapter
    private lateinit var suggestionAdapter: HomeMediaAdapter
    private lateinit var hotTemplateAdapter: HomeMediaAdapter

    private val bannerList by lazy {
        listOf(
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
        setupSuggestions()
        setupHotTemplates()
        loadApiTemplates()
    }

    override fun onResume() {
        super.onResume()
        loadRecentHistory()
    }

    private fun setupHeader() {
        binding.tvBrandAi.post {
            val width = binding.tvBrandAi.paint.measureText(binding.tvBrandAi.text.toString()).coerceAtLeast(1f)
            val shader = LinearGradient(
                0f, 0f, width, 0f,
                intArrayOf(
                    Color.parseColor("#3D86F4"),
                    Color.parseColor("#8A5CF0")
                ),
                null,
                Shader.TileMode.CLAMP
            )
            binding.tvBrandAi.paint.shader = shader
            binding.tvBrandAi.invalidate()
        }
    }

    override fun initListener() {
        // 3 Core Features
        binding.cardFeatureVideo.setOnClickListener {
            navigateToFeature("video")
        }

        binding.cardFeaturePrompt.setOnClickListener {
            navigateToFeature("prompt")
        }

        binding.cardFeatureRestore.setOnClickListener {
            navigateToFeature("restore")
        }

        // See all actions
        binding.btnSeeAllHistory.setOnClickListener {
            (parentFragment as? MainFragment)?.selectTab(2)
        }

        binding.btnSeeAllSuggestions.setOnClickListener {
            (parentFragment as? MainFragment)?.selectTab(1)
        }

        binding.btnSeeAllTemplates.setOnClickListener {
            (parentFragment as? MainFragment)?.selectTab(1)
        }

        binding.btnPro.setOnClickListener {
            (parentFragment as? MainFragment)?.selectTab(3)
        }
    }

    private fun setupBanners() {
        val bannerAdapter = BannerAdapter(bannerList) { banner ->
            navigateToFeature(banner.targetFeature)
        }
        binding.vpBanners.adapter = bannerAdapter

        binding.bannerIndicator.setDotCount(bannerList.size)
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
                val next = (current + 1) % bannerList.size
                binding.vpBanners.setCurrentItem(next, true)
                autoScrollHandler.postDelayed(this, 4500L)
            }
        }
        autoScrollHandler.postDelayed(autoScrollRunnable!!, 4500L)
    }

    private fun setupRecentHistory() {
        recentHistoryAdapter = HomeMediaAdapter(emptyList()) {
            (parentFragment as? MainFragment)?.selectTab(2)
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

    private fun setupSuggestions() {
        suggestionAdapter = HomeMediaAdapter(emptyList()) {
            (parentFragment as? MainFragment)?.selectTab(1)
        }
        binding.rvSuggestions.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvSuggestions.adapter = suggestionAdapter
    }

    private fun setupHotTemplates() {
        hotTemplateAdapter = HomeMediaAdapter(emptyList()) {
            (parentFragment as? MainFragment)?.selectTab(1)
        }
        binding.rvHotTemplates.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvHotTemplates.adapter = hotTemplateAdapter
    }

    private fun loadApiTemplates() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Must have a valid image and not belong to AI TOOL / AI GEN
                val validTemplates = templateRepository.getTemplates().filter {
                    !it.isAiTool && !it.safeImageUrl.isNullOrBlank()
                }
                if (validTemplates.isNotEmpty()) {
                    val flameColor = Color.parseColor("#FF7A2F")
                    val hotList = validTemplates.take(8).map {
                        HomeMediaItemModel(
                            label = it.title,
                            category = it.category,
                            imageUrl = it.safeImageUrl,
                            iconRes = R.drawable.ic_flame,
                            iconTint = flameColor
                        )
                    }
                    hotTemplateAdapter.submitList(hotList)

                    val remaining = if (validTemplates.size > 8) validTemplates.drop(8) else validTemplates
                    val suggestionList = remaining.take(8).map {
                        HomeMediaItemModel(
                            label = it.title,
                            category = it.category,
                            imageUrl = it.safeImageUrl,
                            iconRes = R.drawable.ic_star_section
                        )
                    }
                    suggestionAdapter.submitList(suggestionList)
                }
            } catch (e: Exception) {
                // If API fails, lists remain empty (no fake data)
            }
        }
    }

    private fun navigateToFeature(featureId: String) {
        val navController = parentFragment?.parentFragment?.findNavController() ?: findNavController()
        when (featureId) {
            "video" -> navController.navigate(R.id.action_mainFragment_to_memoriesFragment)
            "prompt" -> navController.navigate(R.id.action_mainFragment_to_enhanceFragment)
            "restore" -> navController.navigate(R.id.action_mainFragment_to_restoreFragment)
            "music" -> navController.navigate(R.id.action_mainFragment_to_musicFragment)
        }
    }

    override fun onDestroyView() {
        autoScrollRunnable?.let { autoScrollHandler.removeCallbacks(it) }
        super.onDestroyView()
    }
}
