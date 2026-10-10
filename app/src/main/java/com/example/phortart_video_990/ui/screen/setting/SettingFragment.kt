package com.example.phortart_video_990.ui.screen.setting

import android.content.Intent
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.dialog.AppDialogHelper
import com.example.phortart_video_990.core.utils.NotificationPermissionManager
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.databinding.FragmentSettingBinding
import com.example.phortart_video_990.ui.screen.uninstall.UninstallActivity

class SettingFragment : BaseFragment<FragmentSettingBinding>(FragmentSettingBinding::inflate) {

    override fun initView() {
        val prefs = PreferencesManager.getInstance(requireContext())
        binding.tvCurrentLanguage.text = prefs.languageName
        binding.switchNotification.isChecked = NotificationPermissionManager.isNotificationPermissionGranted(requireContext())
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferencesManager.getInstance(requireContext())
        binding.tvCurrentLanguage.text = prefs.languageName
        binding.switchNotification.isChecked = NotificationPermissionManager.isNotificationPermissionGranted(requireContext())
    }

    override fun initListener() {
        // Language Option
        binding.cardLanguageSetting.setOnClickListener {
            val navController = parentFragment?.parentFragment?.findNavController() ?: findNavController()
            navController.navigateSafe(R.id.action_mainFragment_to_languageFragment)
        }

        // Notification Toggle / Row
        binding.rowNotification.setOnClickListener {
            NotificationPermissionManager.openNotificationSettings(requireContext()) {
                binding.switchNotification.isChecked = NotificationPermissionManager.isNotificationPermissionGranted(requireContext())
            }
        }
        binding.switchNotification.setOnClickListener {
            NotificationPermissionManager.openNotificationSettings(requireContext()) {
                binding.switchNotification.isChecked = NotificationPermissionManager.isNotificationPermissionGranted(requireContext())
            }
        }

        // IAP Upgrade Button
        binding.btnIapUpgrade.setOnClickListener {
            Toast.makeText(requireContext(), "Tính năng Nâng cấp Premium đang mở", Toast.LENGTH_SHORT).show()
        }

        // Rate App -> Opens interactive 5-star rating popup
        binding.rowRateApp.setOnClickListener {
            AppDialogHelper.showRateDialog(requireActivity())
        }

        // Uninstall App Survey
        binding.rowUninstallApp.setOnClickListener {
            startActivity(Intent(requireContext(), UninstallActivity::class.java))
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
