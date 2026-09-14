package com.miku.gamingsidebar.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.miku.gamingsidebar.data.backend.AutomaticGameTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

/**
 * Servicio en segundo plano para rastreo nativo de juegos sin notificaciones.
 */
class GameTrackerService : Service() {

    companion object {
        private const val TAG = "GameTracker"

        fun start(context: Context) {
            try {
                val intent = Intent(context, GameTrackerService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo iniciar GameTrackerService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, GameTrackerService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Error al detener GameTrackerService: ${e.message}")
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "GameTrackerService creado.")

        // Iniciar rastreo de UsageStatsManager
        AutomaticGameTracker.startMonitoring(
            context = applicationContext,
            scope = serviceScope,
            onGameStatusChanged = { isPlaying, gameName ->
                // Background tracking active without intrusive notifications
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "GameTrackerService detenido.")
        AutomaticGameTracker.stopMonitoring()
        serviceScope.cancel()
    }
}
