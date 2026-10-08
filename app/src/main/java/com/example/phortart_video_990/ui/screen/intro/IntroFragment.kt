package com.example.phortart_video_990.ui.screen.intro

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.data.model.IntroPageModel
import com.example.phortart_video_990.databinding.FragmentIntroBinding

class IntroFragment : BaseFragment<FragmentIntroBinding>(FragmentIntroBinding::inflate) {

    private val introPages by lazy {
        listOf(
            IntroPageModel(
                stepNumber = "01",
                title = getString(R.string.intro_1_title),
                description = getString(R.string.intro_1_desc),
                iconRes = R.drawable.ic_func_memories,
                badgeBgRes = R.drawable.bg_badge_01
            ),
            IntroPageModel(
                stepNumber = "02",
                title = getString(R.string.intro_2_title),
                description = getString(R.string.intro_2_desc),
                iconRes = R.drawable.ic_func_restore,
                badgeBgRes = R.drawable.bg_badge_02
            ),
            IntroPageModel(
                stepNumber = "03",
                title = getString(R.string.intro_3_title),
                description = getString(R.string.intro_3_desc),
                iconRes = R.drawable.ic_func_enhance,
                badgeBgRes = R.drawable.bg_badge_03
            ),
            IntroPageModel(
                stepNumber = "04",
                title = getString(R.string.intro_4_title),
                description = getString(R.string.intro_4_desc),
                iconRes = R.drawable.ic_func_music,
                badgeBgRes = R.drawable.bg_badge_04
            )
        )
    }

    override fun initView() {
        val adapter = IntroAdapter(introPages)
        binding.vpIntro.adapter = adapter

        setupIndicators(introPages.size)
        updateIndicators(0)

        binding.vpIntro.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicators(position)
                if (position == introPages.size - 1) {
                    binding.btnNext.text = getString(R.string.btn_get_started)
                    binding.btnSkip.visibility = View.INVISIBLE
                } else {
                    binding.btnNext.text = getString(R.string.btn_next)
                    binding.btnSkip.visibility = View.VISIBLE
                }
            }
        })
    }

    override fun initListener() {
        binding.btnNext.setOnClickListener {
            val current = binding.vpIntro.currentItem
            if (current < introPages.size - 1) {
                binding.vpIntro.currentItem = current + 1
            } else {
                completeIntroAndNavigate()
            }
        }

        binding.btnSkip.setOnClickListener {
            completeIntroAndNavigate()
        }
    }

    private fun setupIndicators(count: Int) {
        binding.llIndicators.removeAllViews()
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(8, 0, 8, 0)
        }

        for (i in 0 until count) {
            val dot = ImageView(requireContext()).apply {
                layoutParams = params
                setImageResource(R.drawable.bg_indicator_inactive)
            }
            binding.llIndicators.addView(dot)
        }
    }

    private fun updateIndicators(selectedIndex: Int) {
        for (i in 0 until binding.llIndicators.childCount) {
            val dot = binding.llIndicators.getChildAt(i) as ImageView
            if (i == selectedIndex) {
                dot.setImageResource(R.drawable.bg_indicator_active)
            } else {
                dot.setImageResource(R.drawable.bg_indicator_inactive)
            }
        }
    }

    private fun completeIntroAndNavigate() {
        PreferencesManager.getInstance(requireContext()).hasCompletedIntro = true
        findNavController().navigate(R.id.action_introFragment_to_mainFragment)
    }
}
