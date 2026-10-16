package com.aiart.photo.beauti.editor.ui.screen.main

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.aiart.photo.beauti.editor.ui.screen.history.HistoryFragment
import com.aiart.photo.beauti.editor.ui.screen.home.HomeFragment
import com.aiart.photo.beauti.editor.ui.screen.setting.SettingFragment
import com.aiart.photo.beauti.editor.ui.screen.template.TemplateFragment

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
