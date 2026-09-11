package com.miku.gamingsidebar.data

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LauncherHideManager(private val context: Context) {

    suspend fun resetAllToDefaultState(games: List<GameModel>) = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

        for (game in games) {
            try {
                pm.setApplicationEnabledSetting(
                    game.packageName,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    0
                )
            } catch (_: Exception) {}
        }

        val extraAliases = listOf(
            "com.geode.launcher/com.geode.launcher.MainActivityPride",
            "com.geode.launcher/com.geode.launcher.MainActivityTrans",
            "com.geode.launcher/com.geode.launcher.MainActivitySapphire"
        )
        for (alias in extraAliases) {
            try {
                val parts = alias.split("/")
                if (parts.size == 2) {
                    pm.setComponentEnabledSetting(
                        ComponentName(parts[0], parts[1]),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            } catch (_: Exception) {}
        }

        try {
            am?.killBackgroundProcesses("com.google.android.apps.nexuslauncher")
            am?.killBackgroundProcesses("com.android.launcher3")
        } catch (_: Exception) {}
    }
}
