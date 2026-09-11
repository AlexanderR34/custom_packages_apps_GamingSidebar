package com.miku.gamingsidebar.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.miku.gamingsidebar.data.SettingsRepository

class GamingSidebarTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val settings = SettingsRepository(applicationContext)
        val newState = !settings.sidebarEnabled.value
        settings.setSidebarEnabled(newState)
        if (newState) {
            GameTrackerService.start(applicationContext)
        } else {
            GamingOverlayService.stop(applicationContext)
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val settings = SettingsRepository(applicationContext)
        val isEnabled = settings.sidebarEnabled.value
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Game Turbo Sidebar"
        tile.updateTile()
    }
}
