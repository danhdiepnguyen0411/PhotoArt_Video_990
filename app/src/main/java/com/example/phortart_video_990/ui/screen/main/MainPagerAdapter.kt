package com.example.phortart_video_990.ui.screen.main

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.phortart_video_990.ui.screen.history.HistoryFragment
import com.example.phortart_video_990.ui.screen.home.HomeFragment
import com.example.phortart_video_990.ui.screen.setting.SettingFragment
import com.example.phortart_video_990.ui.screen.template.TemplateFragment

class MainPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> TemplateFragment()
            2 -> HistoryFragment()
            3 -> SettingFragment()
            else -> HomeFragment()
        }
    }
}
