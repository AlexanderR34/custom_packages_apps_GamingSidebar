package com.miku.gamingsidebar.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miku.gamingsidebar.data.GameModel
import com.miku.gamingsidebar.data.GameRepository
import com.miku.gamingsidebar.data.LauncherHideManager
import com.miku.gamingsidebar.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HubViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GameRepository(application)
    private val settingsRepository = SettingsRepository(application)

    val gameModeEnabled: StateFlow<Boolean> = settingsRepository.gameModeEnabled
    val sidebarEnabled: StateFlow<Boolean> = settingsRepository.sidebarEnabled
    val isSetupCompleted: StateFlow<Boolean> = settingsRepository.isSetupCompleted

    fun setSetupCompleted(completed: Boolean) {
        settingsRepository.setSetupCompleted(completed)
    }

    fun setSidebarEnabled(enabled: Boolean) {
        settingsRepository.setSidebarEnabled(enabled)
        if (!enabled) {
            com.miku.gamingsidebar.service.GamingOverlayService.stop(getApplication())
        }
    }

    private val _appLanguage = MutableStateFlow(com.miku.gamingsidebar.util.LocaleHelper.getSavedLanguage(application))
    val appLanguage: StateFlow<String> = _appLanguage

    fun setAppLanguage(lang: String) {
        _appLanguage.value = lang
        com.miku.gamingsidebar.util.LocaleHelper.setLanguage(getApplication(), lang)
        checkPermissionAndLoad()
    }

    private val _allGames = MutableStateFlow<List<GameModel>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    val recentlyPlayedGames: StateFlow<List<GameModel>> = _allGames.combine(_isPermissionGranted) { games, _ ->
        val recents = games.filter { it.isRecentlyPlayed || it.lastTimeUsed > 0 }
            .sortedByDescending { it.lastTimeUsed }
        if (recents.isNotEmpty()) recents else games.take(4)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryGames: StateFlow<List<GameModel>> = combine(_allGames, _searchQuery) { games, query ->
        if (query.isBlank()) {
            games
        } else {
            games.filter { it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _installedApps = MutableStateFlow<List<com.miku.gamingsidebar.data.InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<com.miku.gamingsidebar.data.InstalledAppItem>> = _installedApps

    private val _isLoadingInstalledApps = MutableStateFlow(false)
    val isLoadingInstalledApps: StateFlow<Boolean> = _isLoadingInstalledApps

    init {
        checkPermissionAndLoad()
    }

    fun checkPermissionAndLoad(silent: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _isPermissionGranted.value = repository.hasUsageStatsPermission()
            if (_allGames.value.isEmpty()) {
                if (!silent) _isLoading.value = true
                val games = repository.getInstalledGames()
                _allGames.value = games
                _isLoading.value = false
            } else if (!silent) {
                val games = repository.getInstalledGames()
                _allGames.value = games
            }
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingInstalledApps.value = true
            val apps = repository.getAllInstalledApps()
            _installedApps.value = apps
            _isLoadingInstalledApps.value = false
        }
    }

    fun toggleCustomApp(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleCustomGame(packageName)
            val updatedGames = repository.getInstalledGames()
            _allGames.value = updatedGames
            val updatedApps = repository.getAllInstalledApps()
            _installedApps.value = updatedApps
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun setGameModeEnabled(enabled: Boolean) {
        settingsRepository.setGameModeEnabled(enabled)
    }

    fun launchGame(packageName: String): Boolean {
        val context = getApplication<android.app.Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val savedScale = com.miku.gamingsidebar.service.GamingActionsHelper.getSavedResolutionScale(context, packageName)
            if (savedScale in 0.20f..0.98f) {
                com.miku.gamingsidebar.service.GamingActionsHelper.setGameResolutionScale(packageName, savedScale, relaunch = false, context = context)
            } else {
                com.miku.gamingsidebar.service.GamingActionsHelper.resetResolutionScale(packageName, relaunch = false)
            }
            val savedMode = com.miku.gamingsidebar.service.GamingActionsHelper.getSavedPerformanceMode(context, packageName)
            com.miku.gamingsidebar.service.GamingActionsHelper.setPerformanceMode(context, savedMode, pkg = packageName, persist = false)
        }

        // Start Gaming Sidebar HUD Overlay for this specific game (if enabled by user)
        if (settingsRepository.sidebarEnabled.value && android.provider.Settings.canDrawOverlays(getApplication())) {
            com.miku.gamingsidebar.service.GamingOverlayService.start(getApplication(), targetPackage = packageName)
        }

        val success = repository.launchGame(packageName)
        if (success) {
            // Re-check after playing
            viewModelScope.launch(Dispatchers.IO) {
                _allGames.value = repository.getInstalledGames()
            }
        }
        return success
    }
}
