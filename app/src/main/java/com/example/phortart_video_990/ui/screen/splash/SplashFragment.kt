package com.example.phortart_video_990.ui.screen.splash

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.NotificationPermissionManager
import com.example.phortart_video_990.core.utils.SystemBarInsetsHelper
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.databinding.FragmentSplashBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashFragment : BaseFragment<FragmentSplashBinding>(FragmentSplashBinding::inflate) {

    private var hasNavigated = false
    private var isProgressComplete = false
    private var isWaitingForPermission = false
    private var progressJob: Job? = null
    private var fallbackJob: Job? = null

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isWaitingForPermission = false
        context?.let { ctx ->
            if (isGranted) {
                NotificationPermissionManager.setPreviouslyGranted(ctx, true)
                NotificationPermissionManager.setUserRevoked(ctx, false)
                NotificationPermissionManager.resetDeniedCount(ctx)
            } else {
                NotificationPermissionManager.incrementDeniedCount(ctx)
            }
        }
        navigateToNext()
    }

    override fun initView() {
        activity?.window?.let { SystemBarInsetsHelper.hideSystemBars(it) }
        binding.progressBar.progress = 0
    }

    override fun initData() {
        startProgressSimulation()
    }

    override fun onResume() {
        super.onResume()
        activity?.window?.let { SystemBarInsetsHelper.hideSystemBars(it) }

        // If progress completed and not waiting for permission dialog, navigate immediately
        if (isProgressComplete && !isWaitingForPermission && !hasNavigated) {
            navigateToNext()
        }
    }

    private fun startProgressSimulation() {
        progressJob?.cancel()
        progressJob = viewLifecycleOwner.lifecycleScope.launch {
            var progress = 0
            while (progress < 100) {
                delay(20L)
                progress += 2
                binding.progressBar.progress = progress
            }
            binding.progressBar.progress = 100
            isProgressComplete = true
            handleSplashComplete()
        }

        // Safety fallback timer: guarantee splash never hangs even if interrupted
        fallbackJob?.cancel()
        fallbackJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(2800L)
            if (!hasNavigated && !isWaitingForPermission) {
                isProgressComplete = true
                binding.progressBar.progress = 100
                navigateToNext()
            }
        }
    }

    private fun handleSplashComplete() {
        if (hasNavigated) return

        val ctx = context ?: return

        // Request POST_NOTIFICATIONS up to 2 times at Splash (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = NotificationPermissionManager.isNotificationPermissionGranted(ctx)
            val isRevoked = NotificationPermissionManager.isUserRevoked(ctx)
            val deniedCount = NotificationPermissionManager.getDeniedCount(ctx)
            if (!isGranted && !isRevoked && deniedCount < 2) {
                NotificationPermissionManager.systemPermissionRequestedThisSession = true
                isWaitingForPermission = true
                try {
                    requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    return
                } catch (e: Exception) {
                    e.printStackTrace()
                    isWaitingForPermission = false
                }
            }
        }

        navigateToNext()
    }

    private fun navigateToNext() {
        if (hasNavigated) return
        if (!isAdded) return

        val ctx = context ?: return
        hasNavigated = true

        try {
            val prefs = PreferencesManager.getInstance(ctx)
            val navController = findNavController()

            when {
                !prefs.isFirstLanguageSelected -> {
                    navController.navigateSafe(R.id.action_splashFragment_to_languageFragment)
                }
                !prefs.hasCompletedIntro -> {
                    navController.navigateSafe(R.id.action_splashFragment_to_introFragment)
                }
                else -> {
                    navController.navigateSafe(R.id.action_splashFragment_to_mainFragment)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Reset to retry in onResume if navigation threw an exception
            hasNavigated = false
        }
    }

    override fun onDestroyView() {
        progressJob?.cancel()
        fallbackJob?.cancel()
        super.onDestroyView()
    }
}
