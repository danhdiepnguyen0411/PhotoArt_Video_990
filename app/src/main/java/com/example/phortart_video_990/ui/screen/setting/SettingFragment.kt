package com.example.phortart_video_990.ui.screen.setting

import android.content.Intent
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

        // Share App
        binding.rowShareApp.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                putExtra(Intent.EXTRA_TEXT, "Trải nghiệm Photo AI - Biến ảnh thành video và phục hồi ảnh nghệ thuật tuyệt đẹp!")
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.setting_share)))
        }

        // Rate App
        binding.rowRateApp.setOnClickListener {
            Toast.makeText(requireContext(), "Cảm ơn bạn đã đánh giá Photo AI 5 sao!", Toast.LENGTH_SHORT).show()
        }

        // Privacy Policy
        binding.rowPrivacy.setOnClickListener {
            Toast.makeText(requireContext(), "Chính sách bảo mật Photo AI", Toast.LENGTH_SHORT).show()
        }
    }
}
