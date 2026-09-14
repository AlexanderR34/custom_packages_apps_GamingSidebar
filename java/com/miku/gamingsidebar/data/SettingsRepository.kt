package com.miku.gamingsidebar.data

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("miku_hub_prefs", Context.MODE_PRIVATE)

    private val _hideGamesFromLauncher = MutableStateFlow(
        prefs.getBoolean(KEY_HIDE_FROM_LAUNCHER, false)
    )
    val hideGamesFromLauncher: StateFlow<Boolean> = _hideGamesFromLauncher

    private val _gameModeEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_GAME_MODE, true)
    )
    val gameModeEnabled: StateFlow<Boolean> = _gameModeEnabled

    private val _sidebarEnabled = MutableStateFlow(
        getSystemSidebarEnabled()
    )
    val sidebarEnabled: StateFlow<Boolean> = _sidebarEnabled

    private val _isSetupCompleted = MutableStateFlow(
        prefs.getBoolean(KEY_SETUP_COMPLETED, true)
    )
    val isSetupCompleted: StateFlow<Boolean> = _isSetupCompleted

    private fun getSystemSidebarEnabled(): Boolean {
        return try {
            Settings.System.getInt(context.contentResolver, KEY_SYSTEM_SIDEBAR_ENABLED, 1) == 1
        } catch (_: Exception) {
            prefs.getBoolean(KEY_SIDEBAR_ENABLED, true)
        }
    }

    fun setHideGamesFromLauncher(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_FROM_LAUNCHER, enabled).apply()
        _hideGamesFromLauncher.value = enabled
    }

    fun setSidebarEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SIDEBAR_ENABLED, enabled).apply()
        try {
            Settings.System.putInt(context.contentResolver, KEY_SYSTEM_SIDEBAR_ENABLED, if (enabled) 1 else 0)
        } catch (_: Exception) {}
        _sidebarEnabled.value = enabled
    }

    fun setGameModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GAME_MODE, enabled).apply()
        _gameModeEnabled.value = enabled
    }

    fun setSetupCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_SETUP_COMPLETED, completed).apply()
        _isSetupCompleted.value = completed
    }

    companion object {
        const val KEY_SYSTEM_SIDEBAR_ENABLED = "game_sidebar_enabled"
        private const val KEY_SETUP_COMPLETED = "is_setup_completed"
        private const val KEY_HIDE_FROM_LAUNCHER = "hide_games_from_launcher"
        private const val KEY_GAME_MODE = "game_mode_enabled"
        private const val KEY_SIDEBAR_ENABLED = "sidebar_enabled"
    }
}
