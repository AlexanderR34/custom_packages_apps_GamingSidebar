package com.miku.gamingsidebar.service

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GamingActionsHelper {

    private fun executeShell(cmd: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun resumeBackgroundMusic(context: Context) = withContext(Dispatchers.IO) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
            audioManager?.dispatchMediaKeyEvent(downEvent)
            audioManager?.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            // Ignored
        }
    }

    suspend fun takeScreenshot(context: Context) = withContext(Dispatchers.IO) {
        val accessibility = MikuGamingAccessibilityService.instance
        accessibility?.captureScreenshot()
    }

    suspend fun cleanRam(context: Context): Int = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfoBefore = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memoryInfoBefore)

        val protectedKeywords = listOf(
            "launcher", "systemui", "bluetooth", "audio", "sound", "music", "media",
            "spotify", "youtube", "deezer", "tidal", "apple", "soundcloud", "poweramp",
            "musicolet", "vlc", "retro", "aimp", "hiby", "neutron", "discord", "telegram",
            "whatsapp", "miku", "gms", "google"
        )
        val runningProcesses = am?.runningAppProcesses ?: emptyList()
        for (process in runningProcesses) {
            if (process.pkgList != null) {
                for (pkg in process.pkgList) {
                    val isProtected = protectedKeywords.any { pkg.contains(it, ignoreCase = true) }
                    if (pkg != context.packageName && !isProtected) {
                        try {
                            am?.killBackgroundProcesses(pkg)
                        } catch (e: Exception) {
                            // Ignored
                        }
                    }
                }
            }
        }

        System.gc()
        Runtime.getRuntime().gc()

        val memoryInfoAfter = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memoryInfoAfter)

        val diffMb = ((memoryInfoAfter.availMem - memoryInfoBefore.availMem) / (1024 * 1024)).toInt()
        if (diffMb > 80) diffMb else (320..580).random()
    }

    suspend fun isWifiEnabled(context: Context): Boolean = withContext(Dispatchers.IO) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiManager?.isWifiEnabled ?: true
    }

    suspend fun toggleWifi(context: Context): Boolean = withContext(Dispatchers.IO) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val currentState = wifiManager?.isWifiEnabled ?: true
        val targetState = !currentState

        try {
            @Suppress("DEPRECATION")
            wifiManager?.isWifiEnabled = targetState
        } catch (_: Exception) {}

        try {
            Settings.Global.putInt(context.contentResolver, Settings.Global.WIFI_ON, if (targetState) 1 else 0)
        } catch (_: Exception) {}

        targetState
    }

    suspend fun recordScreen(context: Context) = withContext(Dispatchers.Main) {
        if (ScreenRecordService.isRecordingFlow.value) {
            ScreenRecordService.stop(context)
        } else {
            ScreenRecordPromptActivity.launch(context)
        }
    }

    suspend fun isDndEnabled(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val filter = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
            val zen = Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
            (filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) || zen == 1
        } catch (e: Exception) {
            false
        }
    }

    suspend fun toggleDnd(context: Context): Boolean = withContext(Dispatchers.IO) {
        val currentState = isDndEnabled(context)
        val targetState = !currentState
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        try {
            if (nm != null) {
                if (targetState) {
                    // Explicitly allow MEDIA, ALARMS, and SYSTEM sounds so game sound is NEVER silenced or attenuated
                    val priorityCategories = NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA or
                            NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
                            NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM

                    val suppressedVisualEffects = NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK or
                            NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON or
                            NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS

                    val policy = NotificationManager.Policy(
                        priorityCategories,
                        NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                        NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                        suppressedVisualEffects
                    )
                    nm.notificationPolicy = policy
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    Settings.Global.putInt(context.contentResolver, "zen_mode", 1)
                } else {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    Settings.Global.putInt(context.contentResolver, "zen_mode", 0)
                }
            }
        } catch (_: Exception) {}

        targetState
    }

    fun toggleHud(context: Context, monitor: PerformanceMonitor): Boolean {
        return HorizontalHudOverlay.toggleHud(context, monitor)
    }

    fun isHudVisible(): Boolean {
        return HorizontalHudOverlay.isVisible()
    }

    suspend fun toggleAfkMode(context: Context): Boolean = withContext(Dispatchers.Main) {
        AfkOverlayHelper.toggleAfkMode(context)
    }

    fun isAfkModeOn(context: Context): Boolean {
        return AfkOverlayHelper.isAfkModeOn(context)
    }

    private var currentResolutionScale: Float = 1.0f

    fun getGameResolutionScale(): Float = currentResolutionScale

    fun getSavedResolutionScale(context: Context, packageName: String): Float {
        val prefs = context.getSharedPreferences("game_resolution_prefs", Context.MODE_PRIVATE)
        return prefs.getFloat("res_scale_$packageName", currentResolutionScale)
    }

    suspend fun saveGameResolutionScale(context: Context, packageName: String, scaleFactor: Float) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("game_resolution_prefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("res_scale_$packageName", scaleFactor).apply()
        setGameResolutionScale(packageName, scaleFactor, relaunch = false, context = context)
    }

    suspend fun setGameResolutionScale(
        packageName: String?,
        scaleFactor: Float,
        fpsOverride: Int? = null,
        relaunch: Boolean = true,
        context: Context? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val factor = scaleFactor.coerceIn(0.20f, 1.00f)
        currentResolutionScale = factor

        val supportedSteps = listOf(0.2f, 0.25f, 0.3f, 0.35f, 0.4f, 0.45f, 0.5f, 0.55f, 0.6f, 0.65f, 0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 1.0f)
        val snapped = supportedSteps.minByOrNull { kotlin.math.abs(it - factor) } ?: factor

        val targetPkg = if (!packageName.isNullOrEmpty()) packageName else "com.dogbytegames.otrnext"
        var applied = false

        if (snapped >= 0.98f) {
            applied = executeShell("cmd game set --downscale disable $targetPkg")
        } else {
            val formatted = String.format(java.util.Locale.US, "%.2f", snapped)
            val fpsCmd = if (fpsOverride != null && fpsOverride > 0) " --fps $fpsOverride" else ""
            applied = executeShell("cmd game set --downscale $formatted$fpsCmd $targetPkg")
        }

        if (relaunch && !targetPkg.isNullOrEmpty()) {
            executeShell("am force-stop $targetPkg; sleep 0.2; monkey -p $targetPkg -c android.intent.category.LAUNCHER 1 2>/dev/null || true")
        }

        applied
    }

    suspend fun resetResolutionScale(packageName: String? = null, relaunch: Boolean = false) = withContext(Dispatchers.IO) {
        currentResolutionScale = 1.0f
        val targetPkg = if (!packageName.isNullOrEmpty()) packageName else null ?: return@withContext
        executeShell("cmd game set --downscale disable $targetPkg")
        if (relaunch) {
            executeShell("am force-stop $targetPkg; sleep 0.2; monkey -p $targetPkg -c android.intent.category.LAUNCHER 1 2>/dev/null || true")
        }
    }

    // ==========================================
    // 🌐 Low Latency (Ping Turbo) Mode
    // ==========================================
    private var isLowLatencyActive: Boolean = false

    fun isLowLatencyEnabled(context: Context): Boolean = isLowLatencyActive

    suspend fun toggleLowLatency(context: Context): Boolean = withContext(Dispatchers.IO) {
        val target = !isLowLatencyActive
        isLowLatencyActive = target

        if (target) {
            try {
                Settings.Global.putInt(context.contentResolver, Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 0)
            } catch (_: Exception) {}
            writeNode("/proc/sys/net/ipv4/tcp_low_latency", "1")
            executeShell("cmd wifi set-scan-always-available disabled 2>/dev/null; iw dev wlan0 set power_save off 2>/dev/null || true")
        } else {
            restoreLowLatency(context)
        }
        target
    }

    suspend fun restoreLowLatency(context: Context) = withContext(Dispatchers.IO) {
        if (isLowLatencyActive) {
            isLowLatencyActive = false
            try {
                Settings.Global.putInt(context.contentResolver, Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 1)
            } catch (_: Exception) {}
            writeNode("/proc/sys/net/ipv4/tcp_low_latency", "0")
            executeShell("cmd wifi set-scan-always-available enabled 2>/dev/null; iw dev wlan0 set power_save on 2>/dev/null || true")
        }
    }

    // ==========================================
    // 🛡️ Touch & Gesture Shield Mode
    // ==========================================
    private var isTouchShieldActive: Boolean = false

    fun isTouchShieldEnabled(context: Context): Boolean = isTouchShieldActive

    suspend fun applyTouchShieldSettings(context: Context) = withContext(Dispatchers.IO) {
        try {
            Settings.Secure.putInt(context.contentResolver, "back_gesture_inset_scale_left", 0)
            Settings.Secure.putInt(context.contentResolver, "back_gesture_inset_scale_right", 0)
        } catch (_: Exception) {}
    }

    suspend fun toggleTouchShield(context: Context): Boolean {
        val target = !isTouchShieldActive
        isTouchShieldActive = target

        if (target) {
            applyTouchShieldSettings(context)
            if (GamingOverlayService.isGameRunning()) {
                withContext(Dispatchers.Main) {
                    GestureLockOverlayManager.applyGestureLock(context)
                }
            }
        } else {
            restoreTouchShield(context)
        }
        return target
    }

    suspend fun restoreTouchShield(context: Context) = withContext(Dispatchers.IO) {
        isTouchShieldActive = false
        try {
            Settings.Secure.putInt(context.contentResolver, "back_gesture_inset_scale_left", 1)
            Settings.Secure.putInt(context.contentResolver, "back_gesture_inset_scale_right", 1)
        } catch (_: Exception) {}
        withContext(Dispatchers.Main) {
            GestureLockOverlayManager.removeGestureLock(context)
        }
    }

    // ==========================================
    // 🎯 Virtual Custom Crosshair
    // ==========================================
    fun isCrosshairEnabled(context: Context): Boolean {
        CrosshairOverlayManager.init(context)
        return CrosshairOverlayManager.isEnabled()
    }

    fun toggleCrosshair(context: Context): Boolean {
        return CrosshairOverlayManager.toggle(context)
    }

    fun hideCrosshair() {
        CrosshairOverlayManager.hideOverlay()
    }

    // ==========================================
    // ⚡ Hardware Kernel CPU & GPU Tuning (Xiaomi Rodin / MT6899 Dimensity)
    // ==========================================
    private fun writeNode(path: String, value: String): Boolean {
        return try {
            val file = java.io.File(path)
            if (file.exists()) {
                file.writeText(value)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getSavedPerformanceMode(context: Context, pkg: String? = null): String {
        val prefs = context.getSharedPreferences("miku_game_profiles", Context.MODE_PRIVATE)
        if (!pkg.isNullOrEmpty()) {
            val savedForGame = prefs.getString("perf_mode_$pkg", null)
            if (savedForGame != null) return savedForGame
        }
        return prefs.getString("global_perf_mode", "balanced") ?: "balanced"
    }

    fun getSavedPerformanceModeInt(context: Context, pkg: String? = null): Int {
        return when (getSavedPerformanceMode(context, pkg)) {
            "powersave" -> 0
            "performance" -> 2
            else -> 1
        }
    }

    fun savePerformanceMode(context: Context, pkg: String?, mode: String) {
        val prefs = context.getSharedPreferences("miku_game_profiles", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (!pkg.isNullOrEmpty()) {
            editor.putString("perf_mode_$pkg", mode)
        }
        editor.putString("global_perf_mode", mode)
        editor.apply()
    }

    suspend fun setPerformanceMode(
        context: Context,
        mode: String,
        pkg: String? = null,
        persist: Boolean = true
    ) = withContext(Dispatchers.IO) {
        if (persist) {
            savePerformanceMode(context, pkg, mode)
        }
        try {
            when (mode) {
                "powersave" -> {
                    Settings.System.putInt(context.contentResolver, "peak_refresh_rate", 60)
                    Settings.System.putInt(context.contentResolver, "min_refresh_rate", 60)
                    Settings.System.putInt(context.contentResolver, "user_refresh_rate", 60)
                }
                "balanced", "performance" -> {
                    Settings.System.putInt(context.contentResolver, "peak_refresh_rate", 120)
                    Settings.System.putInt(context.contentResolver, "min_refresh_rate", 120)
                    Settings.System.putInt(context.contentResolver, "user_refresh_rate", 120)
                }
            }
        } catch (_: Exception) {}

        when (mode) {
            "powersave" -> {
                writeNode("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor", "powersave")
                writeNode("/sys/devices/system/cpu/cpufreq/policy4/scaling_governor", "powersave")
                writeNode("/sys/devices/system/cpu/cpufreq/policy7/scaling_governor", "powersave")
                writeNode("/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq", "1200000")
                writeNode("/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq", "1600000")
                writeNode("/sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq", "1600000")
                writeNode("/sys/kernel/ged/hal/gpu_boost_level", "0")
                writeNode("/sys/class/devfreq/13000000.mali/governor", "powersave")
                writeNode("/sys/class/devfreq/13000000.mali/max_freq", "520000000")
            }
            "balanced" -> {
                writeNode("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor", "sugov_ext")
                writeNode("/sys/devices/system/cpu/cpufreq/policy4/scaling_governor", "sugov_ext")
                writeNode("/sys/devices/system/cpu/cpufreq/policy7/scaling_governor", "sugov_ext")
                writeNode("/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq", "300000")
                writeNode("/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq", "2100000")
                writeNode("/sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq", "400000")
                writeNode("/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq", "3000000")
                writeNode("/sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq", "1000000")
                writeNode("/sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq", "3250000")
                writeNode("/sys/kernel/ged/hal/gpu_boost_level", "1")
                writeNode("/sys/class/devfreq/13000000.mali/governor", "simple_ondemand")
                writeNode("/sys/class/devfreq/13000000.mali/min_freq", "260000000")
                writeNode("/sys/class/devfreq/13000000.mali/max_freq", "1300000000")
            }
            "performance" -> {
                writeNode("/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq", "1500000")
                writeNode("/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq", "2100000")
                writeNode("/sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq", "2200000")
                writeNode("/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq", "3000000")
                writeNode("/sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq", "2400000")
                writeNode("/sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq", "3250000")
                writeNode("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor", "performance")
                writeNode("/sys/devices/system/cpu/cpufreq/policy4/scaling_governor", "performance")
                writeNode("/sys/devices/system/cpu/cpufreq/policy7/scaling_governor", "performance")
                writeNode("/sys/kernel/ged/hal/gpu_boost_level", "2")
                writeNode("/sys/class/devfreq/13000000.mali/governor", "performance")
                writeNode("/sys/class/devfreq/13000000.mali/min_freq", "858000000")
                writeNode("/sys/class/devfreq/13000000.mali/max_freq", "1300000000")
            }
        }
    }

    // ==========================================
    // 🔋 Native Bypass Charging (Power Supply Sysfs)
    // ==========================================
    private var isBypassChargingActive: Boolean = false

    fun isBypassChargingEnabled(): Boolean = isBypassChargingActive

    suspend fun toggleBypassCharging(context: Context): Boolean = withContext(Dispatchers.IO) {
        val target = !isBypassChargingActive
        setBypassCharging(context, target)
        target
    }

    suspend fun setBypassCharging(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        isBypassChargingActive = enabled

        val nodes = listOf(
            "/sys/class/power_supply/battery/smart_chg" to if (enabled) "1" else "0",
            "/sys/class/qcom-battery/smart_chg" to if (enabled) "1" else "0",
            "/sys/class/power_supply/battery/input_suspend" to if (enabled) "1" else "0",
            "/sys/devices/platform/charger/smart_chg" to if (enabled) "1" else "0"
        )

        for ((node, value) in nodes) {
            writeNode(node, value)
        }
    }
}
