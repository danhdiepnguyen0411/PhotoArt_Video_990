package com.example.phortart_video_990.core.utils

import android.os.Bundle
import android.util.Log
import androidx.annotation.IdRes
import androidx.navigation.NavController

fun NavController.navigateSafe(@IdRes actionId: Int, args: Bundle? = null) {
    try {
        val destination = currentDestination
        if (destination == null) {
            Log.w("NavigationExt", "currentDestination is null, skipping navigation")
            return
        }
        val action = destination.getAction(actionId)
        if (action != null) {
            navigate(actionId, args)
        } else {
            Log.w(
                "NavigationExt",
                "Action $actionId not found in current destination ${destination.label} (${destination.id}), skipping"
            )
        }
    } catch (e: Exception) {
        Log.e("NavigationExt", "navigateSafe error: ${e.message}", e)
    }
}
