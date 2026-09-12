package com.miku.gamingsidebar.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.miku.gamingsidebar.MainActivity
import com.miku.gamingsidebar.R
import com.miku.gamingsidebar.data.backend.AutomaticGameTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

/**
 * Foreground Service que mantiene el proceso de Miku Hub activo en segundo plano
 * y ejecuta el rastreo continuo de UsageStatsManager y Supabase Realtime Presence.
 */
class GameTrackerService : Service() {

    companion object {
        private const val TAG = "GameTracker"
        private const val NOTIFICATION_ID = 4001
        private const val CHANNEL_ID = "miku_game_tracker_channel"

        fun start(context: Context) {
            try {
                val intent = Intent(context, GameTrackerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
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
