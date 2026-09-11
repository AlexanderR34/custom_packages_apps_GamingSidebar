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
        Log.d(TAG, "GameTrackerService creado. Iniciando Foreground...")
        createNotificationChannel()
        startForegroundWithNotification("Monitoreando tiempo de juego...")

        // Iniciar rastreo de UsageStatsManager
        AutomaticGameTracker.startMonitoring(
            context = applicationContext,
            scope = serviceScope,
            onGameStatusChanged = { isPlaying, gameName ->
                val notificationText = if (isPlaying && !gameName.isNullOrBlank()) {
                    "🎮 Jugando en vivo: $gameName"
                } else {
                    "Monitoreando tiempo de juego..."
                }
                updateNotification(notificationText)
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rastreador de Horas de Juego",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitorea en segundo plano el tiempo jugado para el ranking de Miku Hub"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Miku Hub - Game Tracker")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startForegroundWithNotification(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
                )
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (e: Exception) {
            Log.w(TAG, "Error actualizando notificación: ${e.message}")
        }
    }
}
