package com.example.phortart_video_990

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.example.phortart_video_990.core.base.BaseActivity
import com.example.phortart_video_990.core.dialog.NotificationDialogHelper
import com.example.phortart_video_990.core.notification.NotificationScheduler
import com.example.phortart_video_990.core.utils.NotificationPermissionManager
import com.example.phortart_video_990.core.utils.SystemBarInsetsHelper
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.databinding.ActivityMainBinding
import com.example.phortart_video_990.ui.screen.main.MainFragment
import com.example.phortart_video_990.ui.screen.uninstall.UninstallActivity

class MainActivity : BaseActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {

    companion object {
        var shouldCheckNotificationOnResume: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fix: Avoid duplicate activity instances when opened from launcher icon
        if (!isTaskRoot && intent.hasCategory(Intent.CATEGORY_LAUNCHER) && Intent.ACTION_MAIN == intent.action) {
            finish()
            return
        }

        SystemBarInsetsHelper.setupNormalSystemBars(window)

        // Initialize launcher shortcut for Uninstall
        com.example.phortart_video_990.core.utils.ShortcutHelper.initUninstallShortcut(this)

        setupNavigationGraph(intent)
        checkAndResumeUninstallIfActive(intent)
        handleNotificationNavigation(intent)

        NotificationPermissionManager.resetSessionState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setupNavigationGraph(intent)
        checkAndResumeUninstallIfActive(intent)
        handleNotificationNavigation(intent)
    }

    private fun checkAndResumeUninstallIfActive(intent: Intent?): Boolean {
        if (intent?.getStringExtra("target_screen") == "home") {
            // User explicitly navigated to Home from UninstallActivity
            com.example.phortart_video_990.core.utils.UninstallSessionState.markUninstallClosed(this)
            return false
        }

        val isNewUninstallShortcut = intent?.action == "com.example.phortart_video_990.ACTION_UNINSTALL" ||
                intent?.getStringExtra("target_screen") == "uninstall"

        // If it's a new uninstall action from shortcut, let SplashFragment run first
        if (isNewUninstallShortcut) {
            return false
        }

        // If user was already in UninstallActivity and reopened app from launcher icon, resume it
        if (com.example.phortart_video_990.core.utils.UninstallSessionState.isUninstallActive(this)) {
            val uninstallIntent = Intent(this, UninstallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(uninstallIntent)
            return true
        }
        return false
    }

    private fun shouldSkipSplash(intent: Intent?): Boolean {
        val isNewUninstallShortcut = intent?.action == "com.example.phortart_video_990.ACTION_UNINSTALL" ||
                intent?.getStringExtra("target_screen") == "uninstall"

        // User requested: entering uninstall MUST show splash screen
        if (isNewUninstallShortcut) {
            return false
        }

        val target = intent?.getStringExtra("target_screen")
        val isNotificationTarget = target in listOf("home", "discover", "memories")
        val prefs = com.example.phortart_video_990.data.local.PreferencesManager.getInstance(this)
        return isNotificationTarget ||
                intent?.getBooleanExtra("skip_splash", false) == true ||
                com.example.phortart_video_990.core.utils.UninstallSessionState.shouldSkipSplash ||
                prefs.shouldSkipSplashOnce
    }

    private fun handleNotificationNavigation(intent: Intent?) {
        val target = intent?.getStringExtra("target_screen") ?: return
        intent.removeExtra("target_screen")

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                as? androidx.navigation.fragment.NavHostFragment ?: return
        val navController = navHost.navController

        if (navController.currentDestination?.id != R.id.mainFragment && navController.currentDestination?.id != R.id.splashFragment) {
            navController.popBackStack(R.id.mainFragment, false)
        }

        fun dispatch(mainFrag: MainFragment) {
            when (target) {
                "discover" -> mainFrag.selectTab(1)
                "home" -> mainFrag.selectTab(0)
                "memories" -> {
                    try {
                        navController.navigateSafe(R.id.action_mainFragment_to_memoriesFragment)
                    } catch (_: Exception) {}
                }
            }
        }

        binding.root.post {
            val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                    as? androidx.navigation.fragment.NavHostFragment
            val primary = navHost?.childFragmentManager?.primaryNavigationFragment
            if (primary is MainFragment) {
                dispatch(primary)
            } else {
                binding.root.postDelayed({
                    val retryFrag = navHost?.childFragmentManager?.primaryNavigationFragment
                    if (retryFrag is MainFragment) {
                        dispatch(retryFrag)
                    }
                }, 300L)
            }
        }
    }

    private fun consumeSkipSplashFlags() {
        com.example.phortart_video_990.core.utils.UninstallSessionState.shouldSkipSplash = false
        com.example.phortart_video_990.data.local.PreferencesManager.getInstance(this).shouldSkipSplashOnce = false
    }

    private fun setupNavigationGraph(intent: Intent?) {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                as? androidx.navigation.fragment.NavHostFragment ?: return
        val navController = navHostFragment.navController
        val isGraphSet = try {
            navController.graph
            true
        } catch (e: Exception) {
            false
        }

        val skip = shouldSkipSplash(intent)

        if (!isGraphSet) {
            val graph = navController.navInflater.inflate(R.navigation.nav_graph)
            if (skip) {
                graph.setStartDestination(R.id.mainFragment)
                consumeSkipSplashFlags()
            } else {
                graph.setStartDestination(R.id.splashFragment)
            }
            navController.graph = graph
        } else if (skip) {
            consumeSkipSplashFlags()
            if (navController.currentDestination?.id != R.id.mainFragment) {
                try {
                    val navOptions = androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(navController.graph.id, true)
                        .build()
                    navController.navigate(R.id.mainFragment, null, navOptions)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SystemBarInsetsHelper.hideSystemBars(window)
        NotificationPermissionManager.onAppForegrounded(this)

        if (NotificationDialogHelper.isNotificationGranted(this)) {
            NotificationDialogHelper.dismissNotificationDialog(this)
            NotificationDialogHelper.stopPolling()
            NotificationScheduler.scheduleInactivityNotifications(this)
        }
    }

    override fun onPause() {
        super.onPause()
        NotificationPermissionManager.onAppBackgrounded()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            SystemBarInsetsHelper.hideSystemBars(window)
        }
    }
}