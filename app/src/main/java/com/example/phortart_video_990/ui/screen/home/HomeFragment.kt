package com.example.phortart_video_990.ui.screen.home

import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
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
                title = "${getString(R.string.banner_1_title)}\n${getString(R.string.banner_1_highlight)}",
                description = getString(R.string.banner_1_desc),
                buttonText = getString(R.string.banner_1_btn),
                targetFeature = "video"
            ),
            BannerItemModel(
                imageRes = R.drawable.banner_2,
                chip = getString(R.string.banner_2_chip),
                title = "${getString(R.string.banner_2_title)}\n${getString(R.string.banner_2_highlight)}",
                description = getString(R.string.banner_2_desc),
                buttonText = getString(R.string.banner_2_btn),
                targetFeature = "prompt"
            ),
            BannerItemModel(
                imageRes = R.drawable.banner_3,
                chip = getString(R.string.banner_3_chip),
                title = "${getString(R.string.banner_3_title)}\n${getString(R.string.banner_3_highlight)}",
                description = getString(R.string.banner_3_desc),
                buttonText = getString(R.string.banner_3_btn),
                targetFeature = "restore"
            )
        )
    }

    private val historyList by lazy {
        listOf(
            HomeMediaItemModel(R.drawable.history_1, "Video"),
            HomeMediaItemModel(R.drawable.history_2, "Ảnh"),
            HomeMediaItemModel(R.drawable.history_3, "Ảnh"),
            HomeMediaItemModel(R.drawable.history_4, "Ảnh"),
            HomeMediaItemModel(R.drawable.history_5, "Ảnh")
        )
    }

    private val suggestionList by lazy {
        listOf(
            HomeMediaItemModel(R.drawable.suggestion_ai_effect, "AI Effect"),
            HomeMediaItemModel(R.drawable.suggestion_ai_style, "AI Style"),
            HomeMediaItemModel(R.drawable.suggestion_ai_dance, "AI Dance"),
            HomeMediaItemModel(R.drawable.suggestion_ai_fantasy, "AI Fantasy"),
            HomeMediaItemModel(R.drawable.suggestion_ai_portrait, "AI Portrait")
        )
    }

    private val templateList by lazy {
        listOf(
            HomeMediaItemModel(R.drawable.template_selfie, "Selfie"),
            HomeMediaItemModel(R.drawable.template_fantasy, "Fantasy"),
            HomeMediaItemModel(R.drawable.template_trendy, "Trendy"),
            HomeMediaItemModel(R.drawable.template_cinematic, "Cinematic"),
            HomeMediaItemModel(R.drawable.template_vintage, "Vintage")
        )
    }

    override fun initView() {
        setupBanners()
        setupRecentHistory()
        setupSuggestions()
        setupHotTemplates()
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

        setupBannerDots(bannerList.size)
        updateBannerDots(0)

        binding.vpBanners.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateBannerDots(position)
            }
        })

        startAutoScroll()
    }

    private fun setupBannerDots(count: Int) {
        binding.llBannerDots.removeAllViews()
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(6, 0, 6, 0)
        }

        for (i in 0 until count) {
            val dot = ImageView(requireContext()).apply {
                layoutParams = params
                setImageResource(R.drawable.bg_indicator_inactive)
            }
            binding.llBannerDots.addView(dot)
        }
    }

    private fun updateBannerDots(position: Int) {
        for (i in 0 until binding.llBannerDots.childCount) {
            val dot = binding.llBannerDots.getChildAt(i) as? ImageView ?: continue
            if (i == position) {
                dot.setImageResource(R.drawable.bg_indicator_active)
            } else {
                dot.setImageResource(R.drawable.bg_indicator_inactive)
            }
        }
    }

    private fun startAutoScroll() {
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
