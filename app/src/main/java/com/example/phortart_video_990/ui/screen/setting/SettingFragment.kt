package com.example.phortart_video_990.ui.screen.setting

import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.databinding.FragmentSettingBinding

class SettingFragment : BaseFragment<FragmentSettingBinding>(FragmentSettingBinding::inflate) {

    override fun initView() {
        val prefs = PreferencesManager.getInstance(requireContext())
        binding.tvCurrentLanguage.text = prefs.languageName
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferencesManager.getInstance(requireContext())
        binding.tvCurrentLanguage.text = prefs.languageName
    }

    override fun initListener() {
        // Language Option
        binding.cardLanguageSetting.setOnClickListener {
            parentFragment?.parentFragment?.findNavController()?.navigate(
                R.id.action_mainFragment_to_languageFragment
            ) ?: findNavController().navigate(R.id.action_mainFragment_to_languageFragment)
        }

        // IAP Upgrade Button
        binding.btnIapUpgrade.setOnClickListener {
            Toast.makeText(requireContext(), "Tính năng Nâng cấp Premium đang mở", Toast.LENGTH_SHORT).show()
        }

        // Rate App
        binding.rowRateApp.setOnClickListener {
            Toast.makeText(requireContext(), "Cảm ơn bạn đã đánh giá Photo AI 5 sao!", Toast.LENGTH_SHORT).show()
        }

        // Privacy Policy
        binding.rowPrivacy.setOnClickListener {
            Toast.makeText(requireContext(), "Chính sách bảo mật Photo AI", Toast.LENGTH_SHORT).show()
        }

        // About App
        binding.rowAboutApp.setOnClickListener {
            Toast.makeText(requireContext(), "Photo AI phiên bản 1.0.0", Toast.LENGTH_SHORT).show()
        }
    }
}
