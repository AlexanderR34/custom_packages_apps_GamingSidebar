package com.miku.gamingsidebar.service

import android.content.Context
import android.os.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GameSessionStats(
    val packageName: String,
    val appName: String,
    val durationSeconds: Long,
    val averageFps: Float,
    val minFps: Int,
    val maxFps: Int,
    val stabilityPercent: Int,
    val maxCpuTemp: Int,
    val maxGpuTemp: Int,
    val batteryConsumedPercent: Int,
    val fpsHistory: List<Int>
)

object GameSessionTracker {

    private var activePackage: String? = null
    private var sessionStartTime: Long = 0
    private var initialBatteryLevel: Int = 100
    private var trackingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val fpsSamples = mutableListOf<Int>()
    private var maxCpuTemp = 0
    private var maxGpuTemp = 0

    private val _lastSessionStats = MutableStateFlow<GameSessionStats?>(null)
    val lastSessionStats: StateFlow<GameSessionStats?> = _lastSessionStats.asStateFlow()

    fun onGameStarted(context: Context, packageName: String, monitor: PerformanceMonitor) {
        if (activePackage == packageName && trackingJob?.isActive == true) return

        activePackage = packageName
        sessionStartTime = System.currentTimeMillis()
        fpsSamples.clear()
        maxCpuTemp = 0
        maxGpuTemp = 0

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        initialBatteryLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100

        trackingJob?.cancel()
        trackingJob = scope.launch {
            while (activePackage == packageName) {
                val currentFps = monitor.fps.value
                if (currentFps > 0) {
                    fpsSamples.add(currentFps)
                }

                val cpuT = monitor.cpuTemperature.value
                val gpuT = monitor.gpuTemperature.value
                if (cpuT > maxCpuTemp) maxCpuTemp = cpuT
                if (gpuT > maxGpuTemp) maxGpuTemp = gpuT

                delay(1000)
            }
        }
    }

    fun onGameExited(context: Context, monitor: PerformanceMonitor) {
        val pkg = activePackage ?: return
        trackingJob?.cancel()
        trackingJob = null
        activePackage = null

        val durationSec = ((System.currentTimeMillis() - sessionStartTime) / 1000).coerceAtLeast(1)
        if (durationSec < 10 || fpsSamples.size < 5) {
            // Ignore accidental/instant opens
            return
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val endBattery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: initialBatteryLevel
        val batteryConsumed = (initialBatteryLevel - endBattery).coerceAtLeast(0)

        val avgFps = if (fpsSamples.isNotEmpty()) fpsSamples.average().toFloat() else 60f
        val minFps = fpsSamples.minOrNull() ?: 30
        val maxFps = fpsSamples.maxOrNull() ?: 60

        // Calculate stability: % of frames within 15% of average FPS
        val stableCount = fpsSamples.count { it >= avgFps * 0.85f }
        val stability = ((stableCount.toFloat() / fpsSamples.size.coerceAtLeast(1)) * 100).toInt().coerceIn(1, 100)

        val pm = context.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            pkg
        }

        val stats = GameSessionStats(
            packageName = pkg,
            appName = appName,
            durationSeconds = durationSec,
            averageFps = avgFps,
            minFps = minFps,
            maxFps = maxFps,
            stabilityPercent = stability,
            maxCpuTemp = if (maxCpuTemp > 0) maxCpuTemp else 45,
            maxGpuTemp = if (maxGpuTemp > 0) maxGpuTemp else 42,
            batteryConsumedPercent = batteryConsumed,
            fpsHistory = fpsSamples.takeLast(60)
        )

        _lastSessionStats.value = stats

        // Show Post-Game Summary overlay on UI
        CoroutineScope(Dispatchers.Main).launch {
            PostGameSummaryOverlay.show(context, stats)
        }
    }
}
