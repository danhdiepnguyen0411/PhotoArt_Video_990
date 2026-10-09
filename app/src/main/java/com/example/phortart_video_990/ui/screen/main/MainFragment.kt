package com.example.phortart_video_990.ui.screen.main

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

        // Apply bottom insets as padding ONLY to cardBottomNav container so background extends to edge
        // while bottomNavigationView height remains intact at 64dp
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            binding.cardBottomNav.setPadding(0, 0, 0, navBarInset)
            insets
        }

        val adapter = MainPagerAdapter(this)
        binding.vpMainTabs.adapter = adapter
        binding.vpMainTabs.isUserInputEnabled = false
    }

    override fun initListener() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    binding.vpMainTabs.setCurrentItem(0, false)
                    true
                }
                R.id.nav_template -> {
                    binding.vpMainTabs.setCurrentItem(1, false)
                    true
                }
                R.id.nav_history -> {
                    binding.vpMainTabs.setCurrentItem(2, false)
                    true
                }
                R.id.nav_setting -> {
                    binding.vpMainTabs.setCurrentItem(3, false)
                    true
                }
                else -> false
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
    }
}
