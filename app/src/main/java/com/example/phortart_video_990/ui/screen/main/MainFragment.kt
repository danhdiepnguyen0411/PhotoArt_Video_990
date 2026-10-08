package com.example.phortart_video_990.ui.screen.main

import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.SystemBarInsetsHelper
import com.example.phortart_video_990.databinding.FragmentMainBinding

class MainFragment : BaseFragment<FragmentMainBinding>(FragmentMainBinding::inflate) {

    override fun initView() {
        SystemBarInsetsHelper.applyBottomInset(
            rootView = binding.root,
            marginViews = listOf(binding.cardBottomNav)
        )

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
                R.id.nav_history -> {
                    binding.vpMainTabs.setCurrentItem(1, false)
                    true
                }
                R.id.nav_setting -> {
                    binding.vpMainTabs.setCurrentItem(2, false)
                    true
                }
                else -> false
            }
        }
    }
}
