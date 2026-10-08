package com.example.phortart_video_990.ui.screen.setting

import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.databinding.FragmentSettingBinding

class SettingFragment : BaseFragment<FragmentSettingBinding>(FragmentSettingBinding::inflate) {

    override fun initView() {
        val prefs = PreferencesManager.getInstance(requireContext())
        binding.tvCurrentLanguage.text = "${getString(R.string.setting_language)}: ${prefs.languageName}"
    }

    override fun initListener() {
        binding.cardLanguageSetting.setOnClickListener {
            parentFragment?.parentFragment?.findNavController()?.navigate(
                R.id.action_mainFragment_to_languageFragment
            ) ?: findNavController().navigate(R.id.action_mainFragment_to_languageFragment)
        }
    }
}
