package com.aiart.photo.beauti.editor.ui.screen.splash

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aiart.photo.beauti.editor.MainActivity
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.core.base.BaseFragment
import com.aiart.photo.beauti.editor.core.dialog.NotificationDialogHelper
import com.aiart.photo.beauti.editor.core.notification.NotificationScheduler
import com.aiart.photo.beauti.editor.core.utils.LocaleHelper
import com.aiart.photo.beauti.editor.core.utils.NotificationPermissionManager
import com.aiart.photo.beauti.editor.core.utils.SystemBarInsetsHelper
import com.aiart.photo.beauti.editor.core.utils.navigateSafe
import com.aiart.photo.beauti.editor.data.local.PreferencesManager
import com.aiart.photo.beauti.editor.databinding.FragmentSplashBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashFragment : BaseFragment<FragmentSplashBinding>(FragmentSplashBinding::inflate) {

    private var hasNavigated = false
    private var isProgressComplete = false
    private var isWaitingForPermission = false
    private var hasRequestedPermission = false
    private var progressJob: Job? = null
    private var fallbackJob: Job? = null

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isWaitingForPermission = false
        context?.let { ctx ->
            val prefs = PreferencesManager.getInstance(ctx)
            if (isGranted) {
                prefs.shouldShowNotiDialogOnHome = false
                MainActivity.shouldCheckNotificationOnResume = false
                prefs.notificationDeniedCount = 0
                prefs.isNotificationEnabled = true
                NotificationPermissionManager.setPreviouslyGranted(ctx, true)
                NotificationPermissionManager.setUserRevoked(ctx, false)
                NotificationPermissionManager.resetDeniedCount(ctx)
                NotificationScheduler.scheduleInactivityNotifications(ctx)
            } else {
                NotificationPermissionManager.incrementDeniedCount(ctx)
            }
            // Do not show custom noti dialog on Home in the same session where user just saw native popup
            prefs.shouldShowNotiDialogOnHome = false
            MainActivity.shouldCheckNotificationOnResume = false
        }
        navigateToNext()
    }

    override fun initView() {
        activity?.window?.let { SystemBarInsetsHelper.hideSystemBars(it) }

        val ctx = context
        if (ctx != null) {
            val prefs = PreferencesManager.getInstance(ctx)
            val isNewUninstallShortcut = activity?.intent?.action == "com.aiart.photo.beauti.editor.ACTION_UNINSTALL" ||
                    activity?.intent?.getStringExtra("target_screen") == "uninstall"

            val isSkip = !isNewUninstallShortcut && (
                    activity?.intent?.getStringExtra("target_screen") == "home" ||
                    activity?.intent?.getBooleanExtra("skip_splash", false) == true ||
                    com.aiart.photo.beauti.editor.core.utils.UninstallSessionState.shouldSkipSplash ||
                    prefs.shouldSkipSplashOnce
            )

            if (isSkip) {
                if (activity?.intent?.getStringExtra("target_screen") == "home") {
                    com.aiart.photo.beauti.editor.core.utils.UninstallSessionState.markUninstallClosed(ctx)
                } else {
                    com.aiart.photo.beauti.editor.core.utils.UninstallSessionState.shouldSkipSplash = false
                    prefs.shouldSkipSplashOnce = false
                }
                hasNavigated = true
                findNavController().navigateSafe(R.id.action_splashFragment_to_mainFragment)
                return
            }
        }

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
        if (hasNavigated) return
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
                handleSplashComplete()
            }
        }
    }

    private fun handleSplashComplete() {
        if (hasNavigated) return

        val ctx = context ?: return
        val prefs = PreferencesManager.getInstance(ctx)

        if (NotificationDialogHelper.isNotificationGranted(ctx)) {
            prefs.shouldShowNotiDialogOnHome = false
            MainActivity.shouldCheckNotificationOnResume = false
            NotificationScheduler.scheduleInactivityNotifications(ctx)
            navigateToNext()
            return
        }

        // Request POST_NOTIFICATIONS up to 2 times at Splash (Android 13+)
        val isUserRevoked = NotificationPermissionManager.isUserRevoked(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && prefs.notificationDeniedCount < 2 && !isUserRevoked) {
            if (!hasRequestedPermission) {
                hasRequestedPermission = true
                isWaitingForPermission = true
                NotificationPermissionManager.systemPermissionRequestedThisSession = true
                try {
                    requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    return
                } catch (e: Exception) {
                    e.printStackTrace()
                    isWaitingForPermission = false
                    navigateToNext()
                }
            } else {
                navigateToNext()
            }
        } else {
            // User denied twice already, or 3rd access without permission:
            // Prepare custom notification popup for Home screen
            prefs.shouldShowNotiDialogOnHome = true
            MainActivity.shouldCheckNotificationOnResume = true
            navigateToNext()
        }
    }

    private fun navigateToNext() {
        if (hasNavigated) return
        if (!isAdded) return

        val ctx = context ?: return
        hasNavigated = true

        val act = activity
        val intent = act?.intent
        val isUninstallTarget = intent?.action == "com.aiart.photo.beauti.editor.ACTION_UNINSTALL" ||
                intent?.getStringExtra("target_screen") == "uninstall"

        if (isUninstallTarget) {
            intent?.action = null
            intent?.removeExtra("target_screen")

            com.aiart.photo.beauti.editor.core.utils.UninstallSessionState.markUninstallActive(ctx)
            val uninstallIntent = android.content.Intent(ctx, com.aiart.photo.beauti.editor.ui.screen.uninstall.UninstallActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(uninstallIntent)
            act?.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            return
        }

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
