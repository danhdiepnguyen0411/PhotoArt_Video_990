package com.example.phortart_video_990.ui.screen.setting

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.dialog.AppDialogHelper
import com.example.phortart_video_990.core.utils.NotificationPermissionManager
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.databinding.FragmentSettingBinding

class SettingFragment : BaseFragment<FragmentSettingBinding>(FragmentSettingBinding::inflate) {

    override fun initView() {
        val prefs = PreferencesManager.getInstance(requireContext())
        binding.tvCurrentLanguage.text = prefs.languageName
        updateNotificationState(animate = false)
        updateRateVisibility()
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferencesManager.getInstance(requireContext())
        binding.tvCurrentLanguage.text = prefs.languageName
        updateNotificationState(animate = true)
        updateRateVisibility()
    }

    private fun updateNotificationState(animate: Boolean = true) {
        val ctx = context ?: return
        val isGranted = NotificationPermissionManager.isNotificationPermissionGranted(ctx)
        PreferencesManager.getInstance(ctx).isNotificationEnabled = isGranted
        if (animate) {
            binding.switchNotification.isChecked = isGranted
        } else {
            binding.switchNotification.setCheckedSilently(isGranted)
        }
    }

    private fun updateRateVisibility() {
        val isRated = PreferencesManager.getInstance(requireContext()).hasRatedApp
        binding.rowRateApp.visibility = if (isRated) View.GONE else View.VISIBLE
        binding.dividerRateApp.visibility = if (isRated) View.GONE else View.VISIBLE
    }

    override fun initListener() {
        // Language Option
        binding.cardLanguageSetting.setOnClickListener {
            val navController = parentFragment?.parentFragment?.findNavController() ?: findNavController()
            navController.navigateSafe(R.id.action_mainFragment_to_languageFragment)
        }

        // Notification Toggle / Row: opens App Info screen for user to toggle notifications
        val onNotificationClick = View.OnClickListener {
            openAppInfoSettings()
        }
        binding.rowNotification.setOnClickListener(onNotificationClick)
        binding.switchNotification.setOnClickListener(onNotificationClick)

        // Header PRO Button
        binding.headerSetting.setOnProClickListener {
            binding.btnIapUpgrade.performClick()
        }

        // IAP Upgrade Button
        binding.btnIapUpgrade.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.settings_iap_title), Toast.LENGTH_SHORT).show()
        }

        // Rate App -> Opens interactive 5-star rating popup
        binding.rowRateApp.setOnClickListener {
            AppDialogHelper.showRateDialog(requireActivity()) {
                updateRateVisibility()
            }
        }

        // Privacy Policy
        binding.rowPrivacy.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.setting_privacy), Toast.LENGTH_SHORT).show()
        }

        // About App
        binding.rowAboutApp.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.setting_about_version), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppInfoSettings() {
        val act = activity ?: return
        com.example.phortart_video_990.core.utils.PermissionReturnHelper.openNotificationSettings(act)
        com.example.phortart_video_990.core.dialog.NotificationDialogHelper.startPollingForNotificationPermission(act) {
            updateNotificationState(animate = true)
        }
    }
}
