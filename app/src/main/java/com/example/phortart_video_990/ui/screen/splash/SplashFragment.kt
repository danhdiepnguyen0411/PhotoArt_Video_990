package com.example.phortart_video_990.ui.screen.splash

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.LocaleHelper
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

        // Reset nếu user đã chọn ngôn ngữ nhưng chưa hoàn thành intro
        // (kill app tại màn Intro → mở lại phải bắt đầu từ đầu)
        resetIncompleteOnboardingIfNeeded()

        setSubtitleForDeviceLocale()
    }

    /**
     * Nếu user chưa xem xong intro (hasCompletedIntro = false) → họ đã kill app giữa chừng hoặc lần đầu mở app.
     * Reset về trạng thái ban đầu: ngôn ngữ sẽ theo hệ thống máy, bắt đầu lại từ LanguageScreen.
     */
    private fun resetIncompleteOnboardingIfNeeded() {
        val ctx = context ?: return
        val prefs = PreferencesManager.getInstance(ctx)
        if (!prefs.hasCompletedIntro) {
            prefs.isFirstLanguageSelected = false
            val deviceLang = LocaleHelper.getDeviceLanguage()
            prefs.languageCode = deviceLang
            activity?.let { act ->
                LocaleHelper.setLocale(act, deviceLang)
                val isRtl = deviceLang.lowercase() == "ar"
                act.window.decorView.layoutDirection = if (isRtl) {
                    android.view.View.LAYOUT_DIRECTION_RTL
                } else {
                    android.view.View.LAYOUT_DIRECTION_LTR
                }
            }
        }
    }

    /**
     * Khi lần đầu vào app (chưa chọn ngôn ngữ), hiển thị subtitle theo ngôn ngữ hệ thống máy.
     * Nếu ngôn ngữ máy không nằm trong danh sách hỗ trợ, fallback về tiếng Anh.
     * Sau khi người dùng đã chọn ngôn ngữ và hoàn thành intro, Android resource system tự xử lý đúng locale.
     */
    private fun setSubtitleForDeviceLocale() {
        val ctx = context ?: return
        val prefs = PreferencesManager.getInstance(ctx)
        if (prefs.isFirstLanguageSelected && prefs.hasCompletedIntro) return

        val supportedLang = LocaleHelper.getDeviceLanguage()
        val locale = LocaleHelper.getLocale(supportedLang)
        val config = Configuration(ctx.resources.configuration)
        config.setLocale(locale)
        val localizedCtx = ctx.createConfigurationContext(config)
        binding.tvSubtitle.text = localizedCtx.getString(R.string.splash_subtitle)
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
