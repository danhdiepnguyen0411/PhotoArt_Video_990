package com.example.phortart_video_990.ui.screen.setting

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.dialog.AppDialogHelper
import com.example.phortart_video_990.core.utils.PermissionReturnHelper
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.data.repository.TemplateRepository
import com.example.phortart_video_990.databinding.FragmentSettingBinding
import kotlinx.coroutines.launch

class SettingFragment : BaseFragment<FragmentSettingBinding>(FragmentSettingBinding::inflate) {

    private val templateRepository by lazy { TemplateRepository() }

    override fun initView() {
        val prefs = PreferencesManager.getInstance(requireContext())
        binding.tvCurrentLanguage.text = prefs.languageName
        updateRateVisibility()
        prefetchCmsLegalLinks()
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferencesManager.getInstance(requireContext())
        binding.tvCurrentLanguage.text = prefs.languageName
        updateRateVisibility()
    }

    private fun prefetchCmsLegalLinks() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                templateRepository.getLegalLinks(requireContext())
            } catch (e: Exception) {
                Log.w("SettingFragment", "Failed to prefetch legal links from CMS: ${e.message}")
            }
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

        // Permission Option: opens app details settings in device
        binding.rowPermission.setOnClickListener {
            openAppInfoSettings()
        }

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

        // Terms of Service
        binding.rowTerms.setOnClickListener {
            openTermsOfService()
        }

        // Privacy Policy
        binding.rowPrivacy.setOnClickListener {
            openPrivacyPolicy()
        }

        // About App
        binding.rowAboutApp.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.setting_about_version), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPrivacyPolicy() {
        val prefs = PreferencesManager.getInstance(requireContext())
        val fallbackUrl = prefs.legalPolicyUrl
        viewLifecycleOwner.lifecycleScope.launch {
            val finalUrl = try {
                templateRepository.getPrivacyPolicyUrl(requireContext()).ifBlank { fallbackUrl }
            } catch (_: Exception) {
                fallbackUrl
            }
            openBrowser(finalUrl)
        }
    }

    private fun openTermsOfService() {
        val prefs = PreferencesManager.getInstance(requireContext())
        val fallbackUrl = prefs.legalTermsUrl
        viewLifecycleOwner.lifecycleScope.launch {
            val finalUrl = try {
                templateRepository.getTermsOfServiceUrl(requireContext()).ifBlank { fallbackUrl }
            } catch (_: Exception) {
                fallbackUrl
            }
            openBrowser(finalUrl)
        }
    }

    private fun openBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), getString(R.string.setting_privacy), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppInfoSettings() {
        val act = activity ?: context ?: return
        PermissionReturnHelper.openAppSettings(act)
    }
}
