package com.example.phortart_video_990.ui.screen.setting

import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.dialog.AppDialogHelper
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.databinding.FragmentSettingBinding

class SettingFragment : BaseFragment<FragmentSettingBinding>(FragmentSettingBinding::inflate) {

    override fun initView() {
        // Apply window insets for status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.llSettingContent.updatePadding(
                top = statusBarInset + (16 * resources.displayMetrics.density).toInt()
            )
            insets
        }

        val prefs = PreferencesManager.getInstance(requireContext())
        binding.tvCurrentLanguage.text = prefs.languageName
        binding.switchNotification.isChecked = prefs.isNotificationEnabled
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferencesManager.getInstance(requireContext())
        binding.tvCurrentLanguage.text = prefs.languageName
        binding.switchNotification.isChecked = prefs.isNotificationEnabled
    }

    override fun initListener() {
        val prefs = PreferencesManager.getInstance(requireContext())

        // Language Option
        binding.cardLanguageSetting.setOnClickListener {
            val navController = parentFragment?.parentFragment?.findNavController() ?: findNavController()
            navController.navigateSafe(R.id.action_mainFragment_to_languageFragment)
        }

        // Notification Toggle / Row
        binding.rowNotification.setOnClickListener {
            binding.switchNotification.toggle()
        }
        binding.switchNotification.onCheckedChangeListener = { isChecked ->
            prefs.isNotificationEnabled = isChecked
        }

        // IAP Upgrade Button
        binding.btnIapUpgrade.setOnClickListener {
            Toast.makeText(requireContext(), "Tính năng Nâng cấp Premium đang mở", Toast.LENGTH_SHORT).show()
        }

        // Rate App -> Opens interactive 5-star rating popup
        binding.rowRateApp.setOnClickListener {
            AppDialogHelper.showRateDialog(requireActivity())
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
