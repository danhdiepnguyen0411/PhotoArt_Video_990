package com.example.phortart_video_990.ui.screen.main

import android.view.animation.DecelerateInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.databinding.FragmentMainBinding

class MainFragment : BaseFragment<FragmentMainBinding>(FragmentMainBinding::inflate) {

    override fun initView() {
        // Prevent BottomNavigationView from adding internal bottom padding that squashes icons and text
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigationView) { _, insets ->
            insets
        }


        val adapter = MainPagerAdapter(this)
        binding.vpMainTabs.adapter = adapter
        binding.vpMainTabs.isUserInputEnabled = false

        binding.bottomNavigationView.post {
            updateIndicator(binding.vpMainTabs.currentItem, animate = false)
        }
        binding.bottomNavigationView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateIndicator(binding.vpMainTabs.currentItem, animate = false)
        }
    }

    override fun initListener() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.vpMainTabs.currentItem != 0) {
                    selectTab(0)
                } else {
                    com.example.phortart_video_990.core.dialog.ExitAppDialogFragment.newInstance {
                        requireActivity().finish()
                    }.show(parentFragmentManager, "exit_app")
                }
            }
        })

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            val position = when (item.itemId) {
                R.id.nav_home -> 0
                R.id.nav_template -> 1
                R.id.nav_history -> 2
                R.id.nav_setting -> 3
                else -> -1
            }
            if (position != -1) {
                binding.vpMainTabs.setCurrentItem(position, false)
                updateIndicator(position, animate = true)
                true
            } else {
                false
            }
        }
    }

    fun selectTab(index: Int) {
        val itemId = when (index) {
            0 -> R.id.nav_home
            1 -> R.id.nav_template
            2 -> R.id.nav_history
            3 -> R.id.nav_setting
            else -> R.id.nav_home
        }
        binding.bottomNavigationView.selectedItemId = itemId
        updateIndicator(index, animate = true)
    }

    private fun updateIndicator(position: Int, animate: Boolean) {
        val totalWidth = binding.bottomNavigationView.width
        if (totalWidth <= 0) return
        val tabWidth = totalWidth / 4f
        val indicatorWidth = if (binding.navIndicator.width > 0) {
            binding.navIndicator.width.toFloat()
        } else {
            resources.displayMetrics.density * 24f
        }
        val targetX = position * tabWidth + (tabWidth - indicatorWidth) / 2f

        if (animate) {
            binding.navIndicator.animate()
                .translationX(targetX)
                .setDuration(200)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            binding.navIndicator.translationX = targetX
        }
    }
}
