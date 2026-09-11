package com.miku.gamingsidebar.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.miku.gamingsidebar.data.SettingsRepository
import com.miku.gamingsidebar.service.GameTrackerService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = SettingsRepository(context)
        if (settings.sidebarEnabled.value) {
            GameTrackerService.start(context)
        }
    }
}
