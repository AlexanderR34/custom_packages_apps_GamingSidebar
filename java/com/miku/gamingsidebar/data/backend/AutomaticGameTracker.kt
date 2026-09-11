package com.miku.gamingsidebar.data.backend

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.miku.gamingsidebar.data.GameRepository
import com.miku.gamingsidebar.service.GamingOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Gestor nativo de detección y ciclo de vida de juegos para la ROM.
 * Monitorea el paquete en primer plano mediante UsageStatsManager y activa/desactiva
 * la barra lateral automáticamente sin dependencias externas.
 */
object AutomaticGameTracker {

    private const val TAG = "GameTracker"

    var currentActiveGamePkg: String? = null
        private set
    var currentActiveGameTitle: String? = null
        private set
    private var gameStartTime: Long = 0L

    private var monitorJob: Job? = null

    fun startMonitoring(
        context: Context,
        scope: CoroutineScope,
        onGameStatusChanged: (isPlaying: Boolean, gameName: String?) -> Unit
    ) {
        monitorJob?.cancel()
        monitorJob = scope.launch(Dispatchers.IO) {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val gameRepo = GameRepository(context)

            while (isActive) {
                try {
                    val isSidebarEnabled = com.miku.gamingsidebar.data.SettingsRepository(context).sidebarEnabled.value
                    if (!isSidebarEnabled) {
                        if (currentActiveGamePkg != null) {
                            currentActiveGamePkg = null
                            currentActiveGameTitle = null
                            onGameStatusChanged(false, null)
                            GamingOverlayService.stop(context)
                        }
                        delay(2000)
                        continue
                    }

                    val now = System.currentTimeMillis()
                    val events = usageStatsManager?.queryEvents(now - 4000, now)
                    var lastForegroundPkg: String? = null

                    if (events != null) {
                        val event = UsageEvents.Event()
                        while (events.hasNextEvent()) {
                            events.getNextEvent(event)
                            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                                lastForegroundPkg = event.packageName
                            }
                        }
                    }

                    if (lastForegroundPkg != null) {
                        val isGame = gameRepo.isGame(lastForegroundPkg)
                        if (isGame && lastForegroundPkg != currentActiveGamePkg) {
                            currentActiveGamePkg = lastForegroundPkg
                            currentActiveGameTitle = gameRepo.getAppName(lastForegroundPkg)
                            gameStartTime = now
                            Log.d(TAG, "🎮 Juego detectado en primer plano: $currentActiveGameTitle ($lastForegroundPkg)")
                            onGameStatusChanged(true, currentActiveGameTitle)
                            GamingOverlayService.start(context, targetPackage = lastForegroundPkg)
                        } else if (!isGame && currentActiveGamePkg != null) {
                            // Ignorar ventanas del sistema, overlay y launcher breve
                            val lower = lastForegroundPkg.lowercase()
                            if (!lower.contains("gamingsidebar") && !lower.contains("systemui")) {
                                Log.d(TAG, "👋 Salió del juego hacia: $lastForegroundPkg")
                                currentActiveGamePkg = null
                                currentActiveGameTitle = null
                                onGameStatusChanged(false, null)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error en monitor de juegos: ${e.message}")
                }
                delay(1000)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        currentActiveGamePkg = null
        currentActiveGameTitle = null
    }

    fun onSidebarGameStarted(context: Context, packageName: String) {
        currentActiveGamePkg = packageName
        currentActiveGameTitle = GameRepository(context).getAppName(packageName)
        gameStartTime = System.currentTimeMillis()
    }

    fun onSidebarGameExited(context: Context) {
        currentActiveGamePkg = null
        currentActiveGameTitle = null
    }
}
