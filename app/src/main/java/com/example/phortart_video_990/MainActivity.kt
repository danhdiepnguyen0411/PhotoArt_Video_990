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
        SystemBarInsetsHelper.setupNormalSystemBars(window)

        // Initialize launcher shortcut for Uninstall
        com.example.phortart_video_990.core.utils.ShortcutHelper.initUninstallShortcut(this)

        handleUninstallIntent(intent)

        NotificationPermissionManager.resetSessionState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUninstallIntent(intent)
    }

    private fun handleUninstallIntent(intent: Intent?) {
        if (intent?.action == "com.example.phortart_video_990.ACTION_UNINSTALL" ||
            intent?.getStringExtra("target_screen") == "uninstall") {
            intent.action = null
            intent.removeExtra("target_screen")
            val uninstallIntent = Intent(this, UninstallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(uninstallIntent)
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