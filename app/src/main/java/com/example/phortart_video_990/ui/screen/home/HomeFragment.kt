package com.example.phortart_video_990.ui.screen.home

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.model.BannerItemModel
import com.example.phortart_video_990.data.model.HomeMediaItemModel
import com.example.phortart_video_990.databinding.FragmentHomeBinding
import com.example.phortart_video_990.ui.screen.main.MainFragment

class HomeFragment : BaseFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {

    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private var autoScrollRunnable: Runnable? = null

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

    private val historyList by lazy {
        listOf(
            HomeMediaItemModel(R.drawable.history_1, "Video", "", R.drawable.ic_badge_play),
            HomeMediaItemModel(R.drawable.history_2, "Ảnh", "", R.drawable.ic_badge_image),
            HomeMediaItemModel(R.drawable.history_3, "Ảnh", "", R.drawable.ic_badge_image),
            HomeMediaItemModel(R.drawable.history_4, "Ảnh", "", R.drawable.ic_badge_image),
            HomeMediaItemModel(R.drawable.history_5, "Ảnh", "", R.drawable.ic_badge_image)
        )
    }

    private val suggestionList by lazy {
        listOf(
            HomeMediaItemModel(R.drawable.suggestion_ai_effect, "AI Effect", "", R.drawable.ic_badge_wand),
            HomeMediaItemModel(R.drawable.suggestion_ai_style, "AI Style", "", R.drawable.ic_badge_palette),
            HomeMediaItemModel(R.drawable.suggestion_ai_dance, "AI Dance", "", R.drawable.ic_badge_dance),
            HomeMediaItemModel(R.drawable.suggestion_ai_fantasy, "AI Fantasy", "", R.drawable.ic_badge_wand),
            HomeMediaItemModel(R.drawable.suggestion_ai_portrait, "AI Portrait", "", R.drawable.ic_badge_image)
        )
    }

    private val templateList by lazy {
        val flameColor = Color.parseColor("#FF7A2F")
        listOf(
            HomeMediaItemModel(R.drawable.template_selfie, "Selfie", "", R.drawable.ic_badge_flame, flameColor),
            HomeMediaItemModel(R.drawable.template_fantasy, "Fantasy", "", R.drawable.ic_badge_flame, flameColor),
            HomeMediaItemModel(R.drawable.template_trendy, "Trendy", "", R.drawable.ic_badge_flame, flameColor),
            HomeMediaItemModel(R.drawable.template_cinematic, "Cinematic", "", R.drawable.ic_badge_flame, flameColor),
            HomeMediaItemModel(R.drawable.template_vintage, "Vintage", "", R.drawable.ic_badge_flame, flameColor)
        )
    }

    override fun initView() {
        setupHeader()
        setupBanners()
        setupRecentHistory()
        setupSuggestions()
        setupHotTemplates()
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
        binding.rvRecentHistory.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecentHistory.adapter = HomeMediaAdapter(historyList) {
            (parentFragment as? MainFragment)?.selectTab(2)
        }
    }

    private fun setupSuggestions() {
        binding.rvSuggestions.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvSuggestions.adapter = HomeMediaAdapter(suggestionList) {
            (parentFragment as? MainFragment)?.selectTab(1)
        }
    }

    private fun setupHotTemplates() {
        binding.rvHotTemplates.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvHotTemplates.adapter = HomeMediaAdapter(templateList) {
            (parentFragment as? MainFragment)?.selectTab(1)
        }
    }

    private fun navigateToFeature(featureId: String) {
        val navController = parentFragment?.parentFragment?.findNavController() ?: findNavController()
        when (featureId) {
            "video" -> navController.navigate(R.id.action_mainFragment_to_memoriesFragment)
            "prompt" -> navController.navigate(R.id.action_mainFragment_to_enhanceFragment)
            "restore" -> navController.navigate(R.id.action_mainFragment_to_restoreFragment)
        }
    }

    override fun onDestroyView() {
        autoScrollRunnable?.let { autoScrollHandler.removeCallbacks(it) }
        super.onDestroyView()
    }
}
