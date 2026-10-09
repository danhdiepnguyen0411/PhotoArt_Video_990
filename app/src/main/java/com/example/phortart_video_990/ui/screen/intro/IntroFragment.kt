package com.example.phortart_video_990.ui.screen.intro

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
                iconRes = R.drawable.icon_intro_1,
                illustrationRes = R.drawable.intro_illustration_create_video,
                buttonText = getString(R.string.btn_next)
            ),
            IntroPageModel(
                stepNumber = "02",
                title = getString(R.string.intro_2_title),
                description = getString(R.string.intro_2_desc),
                iconRes = R.drawable.icon_intro_2,
                illustrationRes = R.drawable.intro_illustration_ai_prompt,
                buttonText = getString(R.string.btn_next)
            ),
            IntroPageModel(
                stepNumber = "03",
                title = getString(R.string.intro_3_title),
                description = getString(R.string.intro_3_desc),
                iconRes = R.drawable.icon_intro_3,
                illustrationRes = R.drawable.intro_illustration_restore_photo,
                buttonText = getString(R.string.btn_get_started)
            )
        )
    }

    override fun initView() {
        val adapter = IntroAdapter(introPages)
        binding.vpIntro.adapter = adapter

        setupIndicators(3)
        updateIndicators(0)

        binding.vpIntro.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicators(position)
                if (position == introPages.size - 1) {
                    binding.tvBtnNextText.text = getString(R.string.btn_get_started)
                } else {
                    binding.tvBtnNextText.text = getString(R.string.btn_next)
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
    }

    private fun setupIndicators(count: Int) {
        binding.llIndicators.removeAllViews()
        val activeDotSizePx = (13 * resources.displayMetrics.density).toInt()
        val inactiveDotSizePx = (10 * resources.displayMetrics.density).toInt()
        val marginPx = (5 * resources.displayMetrics.density).toInt()

        for (i in 0 until count) {
            val sizePx = if (i == 0) activeDotSizePx else inactiveDotSizePx
            val params = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                setMargins(marginPx, 0, marginPx, 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val dot = ImageView(requireContext()).apply {
                layoutParams = params
                setImageResource(if (i == 0) R.drawable.bg_indicator_active else R.drawable.bg_indicator_inactive)
            }
            binding.llIndicators.addView(dot)
        }
    }

    private fun updateIndicators(position: Int) {
        val activeDotSizePx = (13 * resources.displayMetrics.density).toInt()
        val inactiveDotSizePx = (10 * resources.displayMetrics.density).toInt()
        val marginPx = (5 * resources.displayMetrics.density).toInt()

        for (i in 0 until binding.llIndicators.childCount) {
            val dot = binding.llIndicators.getChildAt(i) as? ImageView ?: continue
            if (i == position) {
                dot.layoutParams = LinearLayout.LayoutParams(activeDotSizePx, activeDotSizePx).apply {
                    setMargins(marginPx, 0, marginPx, 0)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                dot.setImageResource(R.drawable.bg_indicator_active)
            } else {
                dot.layoutParams = LinearLayout.LayoutParams(inactiveDotSizePx, inactiveDotSizePx).apply {
                    setMargins(marginPx, 0, marginPx, 0)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                dot.setImageResource(R.drawable.bg_indicator_inactive)
            }
        }
    }

    private fun completeIntroAndNavigate() {
        PreferencesManager.getInstance(requireContext()).hasCompletedIntro = true
        findNavController().navigate(R.id.action_introFragment_to_mainFragment)
    }
}
