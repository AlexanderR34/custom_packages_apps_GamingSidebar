package com.miku.gamingsidebar.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.miku.gamingsidebar.R
import com.miku.gamingsidebar.data.GameRepository
import com.miku.gamingsidebar.ui.theme.MikuHubTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GamingOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var performanceMonitor: PerformanceMonitor? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var foregroundMonitorJob: Job? = null
    private var autoStopJob: Job? = null
    private var collapseJob: Job? = null
    private var isClosingAnimationRunning = false
    private var lastCollapseTimestamp = 0L
    private var knownGamePackages: Set<String> = emptySet()
    private var pendingLaunchGamePackage: String? = null
    private var gameLaunchTimestamp: Long = 0L
    private var gameExitDebounceJob: Job? = null

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    GestureLockOverlayManager.removeGestureLock(this@GamingOverlayService)
                    serviceScope.launch {
                        GamingActionsHelper.restoreTouchShield(this@GamingOverlayService)
                    }
                }
                Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                    if (activeGamePackageFlow.value != null && GamingActionsHelper.isTouchShieldEnabled(this@GamingOverlayService)) {
                        GestureLockOverlayManager.applyGestureLock(this@GamingOverlayService)
                        serviceScope.launch {
                            GamingActionsHelper.applyTouchShieldSettings(this@GamingOverlayService)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        startForegroundNotification()
        performanceMonitor = PerformanceMonitor(this).apply { startMonitoring() }
        setupOverlayWindow()
        loadGamePackagesAndStartMonitoring()

        serviceScope.launch(Dispatchers.IO) {
            MediaPlaybackHelper.ensureNotificationPermissions(this@GamingOverlayService)
        }

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            registerReceiver(screenStateReceiver, screenFilter)
        } catch (e: Exception) {
            // Ignored
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val targetPkg = intent?.getStringExtra(EXTRA_TARGET_PACKAGE)
        if (!targetPkg.isNullOrEmpty()) {
            setPendingGameLaunch(targetPkg)
        }
        return START_NOT_STICKY
    }

    fun setPendingGameLaunch(pkg: String) {
        pendingLaunchGamePackage = pkg
        gameLaunchTimestamp = System.currentTimeMillis()
        autoStopJob?.cancel()
        autoStopJob = null
        activeGamePackageFlow.value = pkg
        performanceMonitor?.setActivePackage(pkg)
        com.miku.gamingsidebar.data.backend.AutomaticGameTracker.onSidebarGameStarted(applicationContext, pkg)
        if (overlayView?.visibility != View.VISIBLE) {
            overlayView?.visibility = View.VISIBLE
        }
        HorizontalHudOverlay.setGameActive(true)
        serviceScope.launch {
            val savedScale = GamingActionsHelper.getSavedResolutionScale(applicationContext, pkg)
            if (savedScale in 0.20f..0.98f) {
                GamingActionsHelper.setGameResolutionScale(pkg, savedScale, relaunch = false, context = applicationContext)
            }
            val savedMode = GamingActionsHelper.getSavedPerformanceMode(applicationContext, pkg)
            GamingActionsHelper.setPerformanceMode(applicationContext, savedMode, pkg = pkg, persist = false)
        }
    }

    private fun startForegroundNotification() {
        val channelId = "miku_gaming_hub_sidebar"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Miku Hub Sidebar Turbo",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Miku Hub Game Turbo")
            .setContentText("Barra lateral activa en juegos")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    private var isSnappedToRight = false
    private var handleYRatio = 0.45f
    private val isSidebarExpandedFlow = MutableStateFlow(false)
    private val isSnappedToRightFlow = MutableStateFlow(false)

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOverlayExpanded(isSidebarExpandedFlow.value)
    }

    private fun setupOverlayWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val prefs = getSharedPreferences("miku_sidebar_prefs", Context.MODE_PRIVATE)
        val density = resources.displayMetrics.density
        val screenHeight = resources.displayMetrics.heightPixels

        isSnappedToRight = prefs.getBoolean("snap_right", false)
        handleYRatio = prefs.getFloat("handle_y_ratio", 0.45f).coerceIn(0.08f, 0.88f)
        isSnappedToRightFlow.value = isSnappedToRight
        isSidebarExpandedFlow.value = false

        val collapsedWidth = (16 * density).toInt()
        val collapsedHeight = (85 * density).toInt()
        val startY = (handleYRatio * screenHeight).toInt()
            .coerceIn((20 * density).toInt(), (screenHeight - collapsedHeight - (20 * density).toInt()).coerceAtLeast((20 * density).toInt()))

        val params = WindowManager.LayoutParams(
            collapsedWidth,
            collapsedHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // Collapsed: only these 3 flags — no FLAG_LAYOUT_NO_LIMITS which bleeds touch area
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (isSnappedToRight) Gravity.END else Gravity.START) or Gravity.TOP
            x = 0
            y = startY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        overlayLayoutParams = params

        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        val hasActiveGame = (pendingLaunchGamePackage != null || activeGamePackageFlow.value != null)
        val localeContext = com.miku.gamingsidebar.util.LocaleHelper.wrap(this)
        overlayView = ComposeView(localeContext).apply {
            visibility = if (hasActiveGame) View.VISIBLE else View.GONE
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewTreeLifecycleOwner(this@GamingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@GamingOverlayService)
            setContent {
                val isExpanded by isSidebarExpandedFlow.collectAsState()
                val isSnapped by isSnappedToRightFlow.collectAsState()

                MikuHubTheme {
                    GamingSidebarLayout(
                        monitor = performanceMonitor ?: PerformanceMonitor(this@GamingOverlayService),
                        isSnappedRight = isSnapped,
                        isExpanded = isExpanded,
                        onCloseSidebar = { stopSelf() },
                        onExpandedChanged = { expanded ->
                            if (expanded) {
                                isSidebarExpandedFlow.value = true
                                updateOverlayExpanded(true)
                            } else {
                                isSidebarExpandedFlow.value = false
                                updateOverlayExpanded(false)
                            }
                        }
                    )
                }
            }

            setOnTouchListener { _, event ->
                if (isSidebarExpandedFlow.value) {
                    if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                        isSidebarExpandedFlow.value = false
                        updateOverlayExpanded(false)
                        return@setOnTouchListener true
                    }
                    return@setOnTouchListener false
                }

                // If closing animation is currently in progress, ignore ALL touches to prevent bouncing back!
                if (isClosingAnimationRunning) {
                    return@setOnTouchListener true
                }

                // Debounce: If sidebar finished closing in the last 300ms, ignore rapid spam taps
                if (System.currentTimeMillis() - lastCollapseTimestamp < 300L) {
                    return@setOnTouchListener true
                }

                val layoutParams = overlayLayoutParams ?: return@setOnTouchListener false
                val screenW = resources.displayMetrics.widthPixels
                val screenH = resources.displayMetrics.heightPixels

                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY

                        val dragThreshold = 8 * density
                        if (!isDragging && (kotlin.math.abs(deltaX) > dragThreshold || kotlin.math.abs(deltaY) > dragThreshold)) {
                            isDragging = true
                        }

                        if (isDragging) {
                            layoutParams.gravity = Gravity.START or Gravity.TOP
                            val targetX = (event.rawX - (8 * density)).toInt()
                                .coerceIn(0, (screenW - (16 * density).toInt()))
                            val targetY = (event.rawY - (42 * density)).toInt()
                                .coerceIn((20 * density).toInt(), (screenH - (90 * density).toInt()))

                            layoutParams.x = targetX
                            layoutParams.y = targetY
                            try {
                                windowManager?.updateViewLayout(overlayView, layoutParams)
                            } catch (e: Exception) {
                                // Ignored
                            }
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            // Dragged to reposition (up/down or to either side)
                            val dropX = event.rawX
                            val newSnappedRight = dropX >= (screenW / 2f)
                            isSnappedToRight = newSnappedRight
                            isSnappedToRightFlow.value = newSnappedRight
                            handleYRatio = ((event.rawY - (42 * density)) / screenH.toFloat()).coerceIn(0.08f, 0.88f)
                            prefs.edit()
                                .putBoolean("snap_right", isSnappedToRight)
                                .putFloat("handle_y_ratio", handleYRatio)
                                .apply()

                            val finalY = (handleYRatio * screenH).toInt()
                                .coerceIn((20 * density).toInt(), (screenH - (85 * density).toInt() - (20 * density).toInt()).coerceAtLeast((20 * density).toInt()))

                            layoutParams.gravity = (if (isSnappedToRight) Gravity.END else Gravity.START) or Gravity.TOP
                            layoutParams.x = 0
                            layoutParams.y = finalY
                            try {
                                windowManager?.updateViewLayout(overlayView, layoutParams)
                            } catch (e: Exception) {
                                // Ignored
                            }
                        } else {
                            // Touch / Tap on the island -> Open sidebar immediately!
                            isSidebarExpandedFlow.value = true
                            updateOverlayExpanded(true)
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        try {
            windowManager?.addView(overlayView, params)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateOverlayExpanded(isExpanded: Boolean) {
        val density = resources.displayMetrics.density
        val screenHeight = resources.displayMetrics.heightPixels
        val collapsedHeight = (85 * density).toInt()
        val calculatedY = (handleYRatio * screenHeight).toInt()
            .coerceIn((20 * density).toInt(), (screenHeight - collapsedHeight - (20 * density).toInt()).coerceAtLeast((20 * density).toInt()))

        if (isExpanded) {
            isClosingAnimationRunning = false
            collapseJob?.cancel()
            val params = overlayLayoutParams ?: return
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.START or Gravity.TOP
            params.x = 0
            params.y = 0
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            try {
                windowManager?.updateViewLayout(overlayView, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Mark closing animation as running so all touches during the 220ms exit animation are absorbed and ignored
            isClosingAnimationRunning = true
            collapseJob?.cancel()
            collapseJob = serviceScope.launch(Dispatchers.Main) {
                delay(220)
                isClosingAnimationRunning = false
                lastCollapseTimestamp = System.currentTimeMillis()
                if (!isSidebarExpandedFlow.value) {
                    val params = overlayLayoutParams ?: return@launch
                    params.width = (16 * density).toInt()
                    params.height = collapsedHeight
                    params.gravity = (if (isSnappedToRight) Gravity.END else Gravity.START) or Gravity.TOP
                    params.x = 0
                    params.y = calculatedY
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                    try {
                        windowManager?.updateViewLayout(overlayView, params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun setOverlayWindowBrightness(brightness: Float) {
        val params = overlayLayoutParams ?: return
        params.screenBrightness = brightness.coerceIn(0.01f, 1.0f)
        try {
            windowManager?.updateViewLayout(overlayView, params)
        } catch (e: Exception) {
            // Ignored
        }
    }

    private fun loadGamePackagesAndStartMonitoring() {
        foregroundMonitorJob?.cancel()
        foregroundMonitorJob = serviceScope.launch(Dispatchers.IO) {
            val repository = GameRepository(applicationContext)
            val games = repository.getInstalledGames()
            knownGamePackages = games.map { it.packageName }.toSet()

            while (isActive) {
                try {
                    val status = checkCurrentAppState(knownGamePackages)
                    when (status) {
                        is AppState.GameRunning -> {
                            withContext(Dispatchers.Main) {
                                handleGameActive(status.packageName)
                            }
                        }
                        is AppState.GameInactive -> {
                            withContext(Dispatchers.Main) {
                                handleGameInactive()
                            }
                        }
                        is AppState.NoChange -> {
                            // Keep current state, do nothing
                        }
                    }
                } catch (e: Exception) {
                    // Ignored
                }
                delay(1000)
            }
        }
    }

    private sealed class AppState {
        data class GameRunning(val packageName: String) : AppState()
        object GameInactive : AppState()
        object NoChange : AppState()
    }

    private fun checkCurrentAppState(knownGames: Set<String>): AppState {
        val now = System.currentTimeMillis()

        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usageStatsManager != null) {
                val events = usageStatsManager.queryEvents(now - 8000, now)
                val event = UsageEvents.Event()
                var latestGame: String? = null
                var latestGameTime = 0L
                var latestLauncherTime = 0L
                var latestMikuHubTime = 0L
                var latestOtherApp: String? = null
                var latestOtherTime = 0L

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    // Only track real UI activity resume events (not background service wakeups)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        val pkg = event.packageName
                        val lower = pkg.lowercase()

                        if (pkg == applicationContext.packageName) {
                            if (event.timeStamp >= latestMikuHubTime) {
                                latestMikuHubTime = event.timeStamp
                            }
                        } else if (lower.contains("launcher") || lower.contains(".home") || lower == "com.miui.home" || lower == "com.android.launcher3" || lower == "com.sec.android.app.launcher") {
                            if (event.timeStamp >= latestLauncherTime) {
                                latestLauncherTime = event.timeStamp
                            }
                        } else if (knownGames.contains(pkg) || GameRepository.isGamePackage(applicationContext, pkg)) {
                            if (event.timeStamp >= latestGameTime) {
                                latestGameTime = event.timeStamp
                                latestGame = pkg
                            }
                        } else if (!lower.contains("systemui") &&
                            !lower.contains("inputmethod") &&
                            !lower.contains("permissioncontroller") &&
                            !lower.contains("gms") &&
                            !lower.contains("android") &&
                            !lower.contains("settings") &&
                            !lower.contains("packageinstaller")
                        ) {
                            if (event.timeStamp >= latestOtherTime) {
                                latestOtherTime = event.timeStamp
                                latestOtherApp = pkg
                            }
                        }
                    }
                }

                val currentGame = activeGamePackageFlow.value

                if (currentGame != null) {
                    // A game is already actively running!
                    // Did another game start?
                    if (latestGame != null && latestGame != currentGame && latestGameTime > latestLauncherTime) {
                        return AppState.GameRunning(latestGame)
                    }

                    // If the game window is still visible on screen (e.g. chat bubble or floating window open)
                    if (MikuGamingAccessibilityService.isGameStillVisible(currentGame)) {
                        return AppState.NoChange
                    }

                    // User explicitly pressed Home / Back and returned to the Launcher
                    if (latestLauncherTime > 0L && (latestGame == null || latestLauncherTime > latestGameTime)) {
                        if (now - latestLauncherTime >= 4000L) {
                            return AppState.GameInactive
                        }
                        return AppState.NoChange
                    }

                    // User returned to Miku Hub itself (outside games)
                    if (latestMikuHubTime > 0L && (latestGame == null || latestMikuHubTime > latestGameTime)) {
                        return AppState.GameInactive
                    }

                    // User switched to another real user application (e.g. WhatsApp, Chrome, or chat bubble)
                    // Allow 10s grace period for floating bubbles or quick app switches
                    if (latestOtherTime > 0L && latestOtherApp != null && (latestGame == null || latestOtherTime > latestGameTime)) {
                        if (now - latestOtherTime >= 10000L) {
                            return AppState.GameInactive
                        }
                        return AppState.NoChange
                    }

                    // The game is running smoothly without new resume events. Keep running!
                    return AppState.NoChange
                } else {
                    // No game currently running. Check if a game was launched.
                    if (latestGame != null && latestGameTime >= latestLauncherTime && latestGameTime >= latestOtherTime && latestGameTime >= latestMikuHubTime) {
                        return AppState.GameRunning(latestGame)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignored
        }

        return AppState.NoChange
    }

    private fun handleGameActive(pkg: String) {
        val isAlreadyActive = (activeGamePackageFlow.value == pkg && overlayView?.visibility == View.VISIBLE)
        pendingLaunchGamePackage = null
        activeGamePackageFlow.value = pkg
        performanceMonitor?.setActivePackage(pkg)
        performanceMonitor?.let { GameSessionTracker.onGameStarted(applicationContext, pkg, it) }
        com.miku.gamingsidebar.data.backend.AutomaticGameTracker.onSidebarGameStarted(applicationContext, pkg)

        if (GamingActionsHelper.isTouchShieldEnabled(this)) {
            GestureLockOverlayManager.applyGestureLock(this)
            serviceScope.launch {
                GamingActionsHelper.applyTouchShieldSettings(this@GamingOverlayService)
            }
        }

        if (!isAlreadyActive) {
            isSidebarExpandedFlow.value = false
            updateOverlayExpanded(false)
            overlayView?.visibility = View.VISIBLE
            HorizontalHudOverlay.setGameActive(true)

            // Automatically re-apply user's saved performance profile and resolution downscale for this game
            serviceScope.launch {
                val savedScale = GamingActionsHelper.getSavedResolutionScale(applicationContext, pkg)
                if (savedScale in 0.20f..0.98f) {
                    GamingActionsHelper.setGameResolutionScale(pkg, savedScale, relaunch = false, context = applicationContext)
                } else {
                    GamingActionsHelper.resetResolutionScale(pkg, relaunch = false)
                }
                val savedMode = GamingActionsHelper.getSavedPerformanceMode(applicationContext, pkg)
                GamingActionsHelper.setPerformanceMode(applicationContext, savedMode, pkg = pkg, persist = false)
            }
        }
    }

    private fun handleGameInactive() {
        if (activeGamePackageFlow.value != null || overlayView?.visibility == View.VISIBLE) {
            isSidebarExpandedFlow.value = false
            updateOverlayExpanded(false)
            com.miku.gamingsidebar.data.backend.AutomaticGameTracker.onSidebarGameExited(applicationContext)
            performanceMonitor?.let { GameSessionTracker.onGameExited(applicationContext, it) }
            pendingLaunchGamePackage = null
            activeGamePackageFlow.value = null
            performanceMonitor?.setActivePackage(null)
            if (AfkOverlayHelper.isAfkModeOn(this)) {
                AfkOverlayHelper.stopAfkMode(this)
            }
            GestureLockOverlayManager.removeGestureLock(this)
            serviceScope.launch {
                GamingActionsHelper.resetResolutionScale()
                GamingActionsHelper.restoreLowLatency(this@GamingOverlayService)
                GamingActionsHelper.restoreTouchShield(this@GamingOverlayService)
                GamingActionsHelper.hideCrosshair()
                GamingActionsHelper.setPerformanceMode(this@GamingOverlayService, "balanced", persist = false)
            }
            overlayView?.visibility = View.GONE
            HorizontalHudOverlay.setGameActive(false)
        }
    }

    fun handleMikuHubResumed() {
        gameExitDebounceJob?.cancel()
        gameExitDebounceJob = null
        handleGameInactive()
    }

    fun handleForegroundPackage(pkg: String) {
        if (pkg == applicationContext.packageName) {
            gameExitDebounceJob?.cancel()
            gameExitDebounceJob = null
            handleGameInactive()
            return
        }
        val lowerPkg = pkg.lowercase()
        if (lowerPkg == "com.android.systemui" ||
            lowerPkg.contains("permissioncontroller") ||
            lowerPkg.contains("packageinstaller") ||
            lowerPkg.contains("inputmethod") ||
            lowerPkg == "android" ||
            lowerPkg.contains("devcheck") ||
            lowerPkg.contains("cpu_z")
        ) {
            return
        }

        val isGame = GameRepository.isGamePackage(applicationContext, pkg) ||
                knownGamePackages.contains(pkg)

        if (isGame) {
            gameExitDebounceJob?.cancel()
            gameExitDebounceJob = null
            handleGameActive(pkg)
        } else {
            val currentGame = activeGamePackageFlow.value
            if (currentGame != null) {
                // 1. Si la ventana del juego sigue presente en pantalla (ej. burbuja de chat o ventana flotante de Telegram/WhatsApp/Messenger abierta sobre el juego)
                if (MikuGamingAccessibilityService.isGameStillVisible(currentGame)) {
                    gameExitDebounceJob?.cancel()
                    gameExitDebounceJob = null
                    return
                }

                // 2. Si el usuario volvió al launcher o cambió de app, dar un periodo de gracia (10 segundos)
                // para evitar cerrar la sesión por burbujas, notificaciones expandidas o multitarea rápida.
                val isLauncher = lowerPkg.contains("launcher") || lowerPkg.contains(".home") ||
                        lowerPkg == "com.miui.home" || lowerPkg == "com.android.launcher3" ||
                        lowerPkg == "com.google.android.apps.nexuslauncher" || lowerPkg == "com.sec.android.app.launcher"
                val gracePeriod = if (isLauncher) 4000L else 10000L

                if (gameExitDebounceJob == null || !gameExitDebounceJob!!.isActive) {
                    gameExitDebounceJob = serviceScope.launch {
                        delay(gracePeriod)
                        if (currentGame == activeGamePackageFlow.value) {
                            if (MikuGamingAccessibilityService.isGameStillVisible(currentGame)) {
                                return@launch
                            }
                            handleGameInactive()
                        }
                    }
                }
            } else {
                handleGameInactive()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gameExitDebounceJob?.cancel()
        gameExitDebounceJob = null
        com.miku.gamingsidebar.data.backend.AutomaticGameTracker.onSidebarGameExited(applicationContext)
        activeInstance = null
        autoStopJob?.cancel()
        foregroundMonitorJob?.cancel()

        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            // Ignored
        }

        try {
            GestureLockOverlayManager.removeGestureLock(this)
        } catch (e: Exception) {
            // Ignored
        }

        serviceScope.launch {
            GamingActionsHelper.resetResolutionScale()
            GamingActionsHelper.restoreLowLatency(this@GamingOverlayService)
            GamingActionsHelper.restoreTouchShield(this@GamingOverlayService)
            GamingActionsHelper.hideCrosshair()
            GamingActionsHelper.setPerformanceMode(this@GamingOverlayService, "balanced")
        }
        HorizontalHudOverlay.setGameActive(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        performanceMonitor?.stopMonitoring()
        com.miku.gamingsidebar.data.backend.AutomaticGameTracker.onSidebarGameExited(applicationContext)
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                // Ignored
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"

        var activeInstance: GamingOverlayService? = null
            private set
        val activeGamePackageFlow = MutableStateFlow<String?>(null)

        fun isGameRunning(): Boolean = activeInstance != null && activeGamePackageFlow.value != null

        fun onForegroundPackageChanged(context: Context, pkg: String) {
            activeInstance?.handleForegroundPackage(pkg)
        }

        fun start(context: Context, targetPackage: String? = null) {
            val intent = Intent(context, GamingOverlayService::class.java).apply {
                if (targetPackage != null) {
                    putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            if (targetPackage != null) {
                activeInstance?.setPendingGameLaunch(targetPackage)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GamingOverlayService::class.java))
        }
    }
}
