package com.aiart.photo.beauti.editor.core.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.aiart.photo.beauti.editor.MainActivity
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.ui.screen.uninstall.UninstallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ShortcutHelper {

    fun initUninstallShortcut(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val shortcutIntent = Intent(context, MainActivity::class.java).apply {
                    action = "com.aiart.photo.beauti.editor.ACTION_UNINSTALL"
                    putExtra("target_screen", "uninstall")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

                val shortLabel = context.getString(R.string.shortcut_uninstall)
                val longLabel = context.getString(R.string.shortcut_uninstall_desc)

                val shortcutInfo = ShortcutInfoCompat.Builder(context, "uninstall_shortcut")
                    .setShortLabel(shortLabel)
                    .setLongLabel(longLabel)
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_uninstall_trash))
                    .setIntent(shortcutIntent)
                    .build()

                // Add dynamic shortcut to app long-press launcher menu
                ShortcutManagerCompat.pushDynamicShortcut(context, shortcutInfo)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun pinUninstallShortcut(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                    val shortcutIntent = Intent(context, MainActivity::class.java).apply {
                        action = "com.aiart.photo.beauti.editor.ACTION_UNINSTALL"
                        putExtra("target_screen", "uninstall")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }

                    val shortLabel = context.getString(R.string.shortcut_uninstall)
                    val longLabel = context.getString(R.string.shortcut_uninstall_desc)

                    val shortcutInfo = ShortcutInfoCompat.Builder(context, "uninstall_shortcut")
                        .setShortLabel(shortLabel)
                        .setLongLabel(longLabel)
                        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_uninstall_trash))
                        .setIntent(shortcutIntent)
                        .build()

                    ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
