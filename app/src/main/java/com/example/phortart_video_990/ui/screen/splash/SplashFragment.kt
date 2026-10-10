package com.example.phortart_video_990.ui.screen.splash

import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.NotificationPermissionManager
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.databinding.FragmentSplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashFragment : BaseFragment<FragmentSplashBinding>(FragmentSplashBinding::inflate) {

    private var hasHandledComplete = false

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            NotificationPermissionManager.setPreviouslyGranted(requireContext(), true)
            NotificationPermissionManager.setUserRevoked(requireContext(), false)
            NotificationPermissionManager.resetDeniedCount(requireContext())
        } else {
            NotificationPermissionManager.incrementDeniedCount(requireContext())
        }
        navigateToNext()
    }

    override fun initView() {
        binding.progressBar.progress = 0
    }

    override fun initData() {
        startProgressSimulation()
    }

    private fun startProgressSimulation() {
        viewLifecycleOwner.lifecycleScope.launch {
            var progress = 0
            while (progress <= 100) {
                binding.progressBar.progress = progress
                delay(25L)
                progress += 2
            }
            handleSplashComplete()
        }
    }

    private fun handleSplashComplete() {
        if (hasHandledComplete) return
        hasHandledComplete = true

        // Request POST_NOTIFICATIONS up to 2 times at Splash (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = NotificationPermissionManager.isNotificationPermissionGranted(requireContext())
            val isRevoked = NotificationPermissionManager.isUserRevoked(requireContext())
            val deniedCount = NotificationPermissionManager.getDeniedCount(requireContext())
            if (!isGranted && !isRevoked && deniedCount < 2) {
                NotificationPermissionManager.systemPermissionRequestedThisSession = true
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        navigateToNext()
    }

    private fun navigateToNext() {
        if (!isAdded) return
        val prefs = PreferencesManager.getInstance(requireContext())
        when {
            !prefs.isFirstLanguageSelected -> {
                findNavController().navigate(R.id.action_splashFragment_to_languageFragment)
            }
            !prefs.hasCompletedIntro -> {
                findNavController().navigate(R.id.action_splashFragment_to_introFragment)
            }
            else -> {
                findNavController().navigate(R.id.action_splashFragment_to_mainFragment)
            }
        }
    }
}
