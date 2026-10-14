package com.example.phortart_video_990

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.example.phortart_video_990.core.base.BaseActivity
import com.example.phortart_video_990.core.dialog.NotificationDialogHelper
import com.example.phortart_video_990.core.utils.NotificationPermissionManager
import com.example.phortart_video_990.core.utils.SystemBarInsetsHelper
import com.example.phortart_video_990.databinding.ActivityMainBinding
import com.example.phortart_video_990.ui.screen.uninstall.UninstallActivity

class MainActivity : BaseActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {

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

        NotificationPermissionManager.resetSessionState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setupNavigationGraph(intent)
    }

    private fun shouldSkipSplash(intent: Intent?): Boolean {
        val isNewUninstallShortcut = intent?.action == "com.example.phortart_video_990.ACTION_UNINSTALL" ||
                intent?.getStringExtra("target_screen") == "uninstall"

        // Entering uninstall shortcut MUST show splash screen
        if (isNewUninstallShortcut) {
            return false
        }

        return intent?.getStringExtra("target_screen") == "home" ||
                intent?.getBooleanExtra("skip_splash", false) == true ||
                com.example.phortart_video_990.core.utils.UninstallSessionState.shouldSkipSplash
    }

    private fun consumeSkipSplashFlags() {
        com.example.phortart_video_990.core.utils.UninstallSessionState.shouldSkipSplash = false
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

        if (NotificationPermissionManager.isNotificationPermissionGranted(this)) {
            NotificationDialogHelper.dismissNotificationDialog(this)
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