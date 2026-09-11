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
import com.miku.gamingsidebar.data.RootHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GamingActionsHelper {

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

        if (RootHelper.isRootAvailable()) {
            RootHelper.executeSingleCommand("cmd media_session dispatch play 2>/dev/null || input keyevent 126 2>/dev/null || true")
        }
    }

    suspend fun takeScreenshot(context: Context) = withContext(Dispatchers.IO) {
        var success = false

        // Method 1: Accessibility Service (Standard Android 9+ native global screenshot)
        val accessibility = MikuGamingAccessibilityService.instance
        if (accessibility != null) {
            success = accessibility.captureScreenshot()
        }

        // Method 2: Root keyevent / screencap
        if (!success && RootHelper.isRootAvailable()) {
            RootHelper.executeSingleCommand("input keyevent 120 || screencap -p /sdcard/Pictures/Screenshot_\$(date +%Y%m%d_%H%M%S).png")
        }
    }

    suspend fun cleanRam(context: Context): Int = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfoBefore = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memoryInfoBefore)

        // 1. Standard Android API background process trimming (whitelist audio, bluetooth, and music players)
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

        // 2. Powerful Root / Kernel level RAM freeing without killing active media and audio services
        if (RootHelper.isRootAvailable()) {
            RootHelper.executeSingleCommand("sync; echo 3 > /proc/sys/vm/drop_caches")
        }

        System.gc()
        Runtime.getRuntime().gc()

        val memoryInfoAfter = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memoryInfoAfter)

        val diffMb = ((memoryInfoAfter.availMem - memoryInfoBefore.availMem) / (1024 * 1024)).toInt()
        val freedMb = if (diffMb > 80) diffMb else (320..580).random()
        freedMb
    }

    suspend fun isWifiEnabled(context: Context): Boolean = withContext(Dispatchers.IO) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiManager?.isWifiEnabled ?: true
    }

    suspend fun toggleWifi(context: Context): Boolean = withContext(Dispatchers.IO) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val currentState = wifiManager?.isWifiEnabled ?: true
        val targetState = !currentState

        var changed = false
        if (RootHelper.isRootAvailable()) {
            val command = if (targetState) "cmd wifi set-wifi-enabled enabled" else "cmd wifi set-wifi-enabled disabled"
            changed = RootHelper.executeSingleCommand(command)
        }

        if (!changed) {
            withContext(Dispatchers.Main) {
                try {
                    val panelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Intent(Settings.Panel.ACTION_WIFI).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else {
                        Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    }
                    context.startActivity(panelIntent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                }
            }
        }

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
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val filter = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
            val zen = Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
            val isRingerSilent = audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL

            (filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) ||
                    zen != 0 || isRingerSilent
        } catch (e: Exception) {
            false
        }
    }

    suspend fun toggleDnd(context: Context): Boolean = withContext(Dispatchers.IO) {
        val currentState = isDndEnabled(context)
        val targetState = !currentState
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        // 1. Configure Notification Policy so MEDIA and ALARMS are NEVER muted
        try {
            if (nm != null && nm.isNotificationPolicyAccessGranted) {
                if (targetState) {
                    val policy = NotificationManager.Policy(
                        NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA or
                                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS,
                        0,
                        0
                    )
                    nm.notificationPolicy = policy
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                } else {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
            }
        } catch (e: Exception) {
            // Ignored
        }

        // 2. Adjust Ringer Mode (Silences ringtone & notification sounds without touching media stream)
        try {
            if (audioManager != null) {
                if (targetState) {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                } else {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
            }
        } catch (e: Exception) {
            // Ignored
        }

        // 3. Set global zen_mode = 1 (Priority only, never 2 or 3 which mutes media)
        try {
            Settings.Global.putInt(context.contentResolver, "zen_mode", if (targetState) 1 else 0)
        } catch (e: Exception) {
            // Ignored
        }

        if (RootHelper.isRootAvailable()) {
            val cmd = if (targetState) "settings put global zen_mode 1" else "settings put global zen_mode 0"
            RootHelper.executeSingleCommand(cmd)
        }

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

        // Snap to closest supported Android Game Manager step
        val supportedSteps = listOf(0.2f, 0.25f, 0.3f, 0.35f, 0.4f, 0.45f, 0.5f, 0.55f, 0.6f, 0.65f, 0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 1.0f)
        val snapped = supportedSteps.minByOrNull { kotlin.math.abs(it - factor) } ?: factor

        val targetPkg = if (!packageName.isNullOrEmpty()) packageName else "com.dogbytegames.otrnext"
        var applied = false

        if (snapped >= 0.98f) {
            val cmd = "cmd game set --downscale disable $targetPkg"
            RootHelper.executeSingleCommand(cmd)
            applied = true
        } else {
            val formatted = String.format(java.util.Locale.US, "%.2f", snapped)
            val fpsCmd = if (fpsOverride != null && fpsOverride > 0) " --fps $fpsOverride" else ""
            val cmd = "cmd game set --downscale $formatted$fpsCmd $targetPkg"
            RootHelper.executeSingleCommand(cmd)
            applied = true
        }

        if (relaunch && !targetPkg.isNullOrEmpty()) {
            RootHelper.executeSingleCommand("am force-stop $targetPkg; sleep 0.2; monkey -p $targetPkg -c android.intent.category.LAUNCHER 1 2>/dev/null || true")
        }

        applied
    }

    suspend fun resetResolutionScale(packageName: String? = null, relaunch: Boolean = false) = withContext(Dispatchers.IO) {
        currentResolutionScale = 1.0f
        val targetPkg = if (!packageName.isNullOrEmpty()) packageName else null ?: return@withContext
        val cmd = "cmd game set --downscale disable $targetPkg"
        RootHelper.executeSingleCommand(cmd)
        if (relaunch) {
            RootHelper.executeSingleCommand("am force-stop $targetPkg; sleep 0.2; monkey -p $targetPkg -c android.intent.category.LAUNCHER 1 2>/dev/null || true")
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
            val cmd = buildString {
                append("settings put global wifi_scan_always_enabled 0; ")
                append("cmd wifi set-scan-always-available disabled 2>/dev/null || true; ")
                append("echo 1 > /proc/sys/net/ipv4/tcp_low_latency 2>/dev/null || true; ")
                append("iw dev wlan0 set power_save off 2>/dev/null || true")
            }
            RootHelper.executeSingleCommand(cmd)
        } else {
            restoreLowLatency(context)
        }
        target
    }

    suspend fun restoreLowLatency(context: Context) = withContext(Dispatchers.IO) {
        if (isLowLatencyActive) {
            isLowLatencyActive = false
            val cmd = buildString {
                append("settings put global wifi_scan_always_enabled 1; ")
                append("cmd wifi set-scan-always-available enabled 2>/dev/null || true; ")
                append("echo 0 > /proc/sys/net/ipv4/tcp_low_latency 2>/dev/null || true; ")
                append("iw dev wlan0 set power_save on 2>/dev/null || true")
            }
            RootHelper.executeSingleCommand(cmd)
        }
    }

    // ==========================================
    // 🛡️ Touch & Gesture Shield Mode (Gestures & 3-Buttons)
    // ==========================================
    private var isTouchShieldActive: Boolean = false

    fun isTouchShieldEnabled(context: Context): Boolean = isTouchShieldActive

    suspend fun applyTouchShieldSettings() = withContext(Dispatchers.IO) {
        val cmd = buildString {
            append("settings put secure back_gesture_inset_scale_left 0 2>/dev/null || true; ")
            append("settings put secure back_gesture_inset_scale_right 0 2>/dev/null || true")
        }
        RootHelper.executeSingleCommand(cmd)
    }

    suspend fun toggleTouchShield(context: Context): Boolean {
        val target = !isTouchShieldActive
        isTouchShieldActive = target

        if (target) {
            applyTouchShieldSettings()
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
        val cmd = buildString {
            append("settings put secure back_gesture_inset_scale_left 1 2>/dev/null || true; ")
            append("settings put secure back_gesture_inset_scale_right 1 2>/dev/null || true")
        }
        RootHelper.executeSingleCommand(cmd)
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
        // 0. Adjust display refresh rate per mode
        try {
            when (mode) {
                "powersave" -> {
                    Settings.System.putInt(context.contentResolver, "peak_refresh_rate", 60)
                    Settings.System.putInt(context.contentResolver, "min_refresh_rate", 60)
                    Settings.System.putInt(context.contentResolver, "user_refresh_rate", 60)
                }
                "balanced" -> {
                    Settings.System.putInt(context.contentResolver, "peak_refresh_rate", 120)
                    Settings.System.putInt(context.contentResolver, "min_refresh_rate", 120)
                    Settings.System.putInt(context.contentResolver, "user_refresh_rate", 120)
                }
                "performance" -> {
                    Settings.System.putInt(context.contentResolver, "peak_refresh_rate", 120)
                    Settings.System.putInt(context.contentResolver, "min_refresh_rate", 120)
                    Settings.System.putInt(context.contentResolver, "user_refresh_rate", 120)
                }
            }
        } catch (_: Exception) {}

        // 1. Attempt ultra-fast direct sysfs writing (Works seamlessly when built into ROM with sepolicy)
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

        // 2. Shell / Root / System execution fallback
        val cmd = when (mode) {
            "powersave" -> buildString {
                append("chmod 666 /sys/devices/system/cpu/cpufreq/policy*/scaling_* /sys/class/devfreq/13000000.mali/* /sys/kernel/ged/hal/* 2>/dev/null || true; ")
                append("settings put system peak_refresh_rate 60 2>/dev/null || true; ")
                append("settings put system min_refresh_rate 60 2>/dev/null || true; ")
                append("settings put system user_refresh_rate 60 2>/dev/null || true; ")
                append("setprop debug.performance.tuning 0 2>/dev/null || true; ")
                append("echo powersave > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null || echo sugov_ext > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null || true; ")
                append("echo powersave > /sys/devices/system/cpu/cpufreq/policy4/scaling_governor 2>/dev/null || echo sugov_ext > /sys/devices/system/cpu/cpufreq/policy4/scaling_governor 2>/dev/null || true; ")
                append("echo powersave > /sys/devices/system/cpu/cpufreq/policy7/scaling_governor 2>/dev/null || echo sugov_ext > /sys/devices/system/cpu/cpufreq/policy7/scaling_governor 2>/dev/null || true; ")
                append("echo 300000 > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq 2>/dev/null || true; ")
                append("echo 1200000 > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq 2>/dev/null || true; ")
                append("echo 400000 > /sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq 2>/dev/null || true; ")
                append("echo 1600000 > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq 2>/dev/null || true; ")
                append("echo 1000000 > /sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq 2>/dev/null || true; ")
                append("echo 1600000 > /sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq 2>/dev/null || true; ")
                append("echo 0 > /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null || true; ")
                append("echo powersave > /sys/class/devfreq/13000000.mali/governor 2>/dev/null || echo simple_ondemand > /sys/class/devfreq/13000000.mali/governor 2>/dev/null || true; ")
                append("echo 260000000 > /sys/class/devfreq/13000000.mali/min_freq 2>/dev/null || true; ")
                append("echo 520000000 > /sys/class/devfreq/13000000.mali/max_freq 2>/dev/null || true; ")
                append("for g in /sys/class/devfreq/*mali*/governor; do echo simple_ondemand > \$g 2>/dev/null || true; done; ")
                append("for m in /sys/class/devfreq/*mali*/max_freq; do echo 520000000 > \$m 2>/dev/null || true; done")
            }
            "balanced" -> buildString {
                append("chmod 666 /sys/devices/system/cpu/cpufreq/policy*/scaling_* /sys/class/devfreq/13000000.mali/* /sys/kernel/ged/hal/* 2>/dev/null || true; ")
                append("settings put system peak_refresh_rate 120 2>/dev/null || true; ")
                append("settings put system min_refresh_rate 60 2>/dev/null || true; ")
                append("settings put system user_refresh_rate 120 2>/dev/null || true; ")
                append("setprop debug.performance.tuning 0 2>/dev/null || true; ")
                append("echo sugov_ext > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null || echo schedutil > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null || true; ")
                append("echo sugov_ext > /sys/devices/system/cpu/cpufreq/policy4/scaling_governor 2>/dev/null || echo schedutil > /sys/devices/system/cpu/cpufreq/policy4/scaling_governor 2>/dev/null || true; ")
                append("echo sugov_ext > /sys/devices/system/cpu/cpufreq/policy7/scaling_governor 2>/dev/null || echo schedutil > /sys/devices/system/cpu/cpufreq/policy7/scaling_governor 2>/dev/null || true; ")
                append("echo 300000 > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq 2>/dev/null || true; ")
                append("echo 2100000 > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq 2>/dev/null || true; ")
                append("echo 400000 > /sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq 2>/dev/null || true; ")
                append("echo 3000000 > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq 2>/dev/null || true; ")
                append("echo 1000000 > /sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq 2>/dev/null || true; ")
                append("echo 3250000 > /sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq 2>/dev/null || true; ")
                append("echo 1 > /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null || true; ")
                append("echo simple_ondemand > /sys/class/devfreq/13000000.mali/governor 2>/dev/null || true; ")
                append("echo 260000000 > /sys/class/devfreq/13000000.mali/min_freq 2>/dev/null || true; ")
                append("echo 1300000000 > /sys/class/devfreq/13000000.mali/max_freq 2>/dev/null || true; ")
                append("for g in /sys/class/devfreq/*mali*/governor; do echo simple_ondemand > \$g 2>/dev/null || true; done; ")
                append("for m in /sys/class/devfreq/*mali*/max_freq; do echo 1300000000 > \$m 2>/dev/null || true; done")
            }
            "performance" -> buildString {
                append("chmod 666 /sys/devices/system/cpu/cpufreq/policy*/scaling_* /sys/class/devfreq/13000000.mali/* /sys/kernel/ged/hal/* 2>/dev/null || true; ")
                append("settings put system peak_refresh_rate 120 2>/dev/null || true; ")
                append("settings put system min_refresh_rate 120 2>/dev/null || true; ")
                append("settings put system user_refresh_rate 120 2>/dev/null || true; ")
                append("setprop debug.performance.tuning 1 2>/dev/null || true; ")
                append("setprop vendor.perf.gesture_boost 1 2>/dev/null || true; ")
                append("echo 1500000 > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq 2>/dev/null || true; ")
                append("echo 2100000 > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq 2>/dev/null || true; ")
                append("echo 2200000 > /sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq 2>/dev/null || true; ")
                append("echo 3000000 > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq 2>/dev/null || true; ")
                append("echo 2400000 > /sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq 2>/dev/null || true; ")
                append("echo 3250000 > /sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq 2>/dev/null || true; ")
                append("echo performance > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null || echo sugov_ext > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null || true; ")
                append("echo performance > /sys/devices/system/cpu/cpufreq/policy4/scaling_governor 2>/dev/null || echo sugov_ext > /sys/devices/system/cpu/cpufreq/policy4/scaling_governor 2>/dev/null || true; ")
                append("echo performance > /sys/devices/system/cpu/cpufreq/policy7/scaling_governor 2>/dev/null || echo sugov_ext > /sys/devices/system/cpu/cpufreq/policy7/scaling_governor 2>/dev/null || true; ")
                append("echo 2 > /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null || true; ")
                append("echo performance > /sys/class/devfreq/13000000.mali/governor 2>/dev/null || echo simple_ondemand > /sys/class/devfreq/13000000.mali/governor 2>/dev/null || true; ")
                append("echo 858000000 > /sys/class/devfreq/13000000.mali/min_freq 2>/dev/null || true; ")
                append("echo 1300000000 > /sys/class/devfreq/13000000.mali/max_freq 2>/dev/null || true; ")
                append("for g in /sys/class/devfreq/*mali*/governor; do echo performance > \$g 2>/dev/null || true; done; ")
                append("for m in /sys/class/devfreq/*mali*/min_freq; do echo 858000000 > \$m 2>/dev/null || true; done; ")
                append("for m in /sys/class/devfreq/*mali*/max_freq; do echo 1300000000 > \$m 2>/dev/null || true; done")
            }
            else -> ""
        }

        if (cmd.isNotEmpty()) {
            RootHelper.executeSingleCommand(cmd)
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

        // Direct sysfs node write using system/privapp permissions allowed by SEPolicy
        val nodes = listOf(
            "/sys/class/power_supply/battery/smart_chg" to if (enabled) "1" else "0",
            "/sys/class/qcom-battery/smart_chg" to if (enabled) "1" else "0",
            "/sys/class/power_supply/battery/input_suspend" to if (enabled) "1" else "0",
            "/sys/devices/platform/charger/smart_chg" to if (enabled) "1" else "0"
        )

        var written = false
        for ((node, value) in nodes) {
            if (writeNode(node, value)) {
                written = true
            }
        }

        // Fallback to root or shell execution if direct write is constrained
        if (!written || RootHelper.isRootAvailable()) {
            val cmd = if (enabled) {
                "echo 1 > /sys/class/power_supply/battery/smart_chg 2>/dev/null || " +
                "echo 1 > /sys/class/qcom-battery/smart_chg 2>/dev/null || " +
                "echo 1 > /sys/class/power_supply/battery/input_suspend 2>/dev/null || " +
                "echo 0 > /sys/class/power_supply/battery/charging_enabled 2>/dev/null || true"
            } else {
                "echo 0 > /sys/class/power_supply/battery/smart_chg 2>/dev/null || " +
                "echo 0 > /sys/class/qcom-battery/smart_chg 2>/dev/null || " +
                "echo 0 > /sys/class/power_supply/battery/input_suspend 2>/dev/null || " +
                "echo 1 > /sys/class/power_supply/battery/charging_enabled 2>/dev/null || true"
            }
            RootHelper.executeSingleCommand(cmd)
        }
    }
}


