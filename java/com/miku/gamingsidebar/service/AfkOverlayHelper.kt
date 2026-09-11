package com.miku.gamingsidebar.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AfkOverlayHelper {

    private var windowManager: WindowManager? = null
    private var afkView: View? = null
    private val _isAfkActive = MutableStateFlow(false)
    val isAfkActive: StateFlow<Boolean> = _isAfkActive

    private var lastTapTime = 0L
    private var originalRefreshRate: Int = 120
    private var afkMonitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isAfkModeOn(context: Context): Boolean {
        return try {
            Settings.System.getInt(context.contentResolver, "afk_mode_active", 0) == 1 || _isAfkActive.value
        } catch (e: Exception) {
            _isAfkActive.value
        }
    }

    fun toggleAfkMode(context: Context): Boolean {
        val newState = !isAfkModeOn(context)
        if (newState) {
            startAfkMode(context)
        } else {
            stopAfkMode(context)
        }
        return newState
    }

    fun startAfkMode(context: Context) {
        if (_isAfkActive.value) return

        // 1. Notify ROM / System of AFK Mode
        try {
            originalRefreshRate = Settings.System.getInt(context.contentResolver, "peak_refresh_rate", 120)
            Settings.System.putInt(context.contentResolver, "afk_mode_active", 1)
            // Throttle display and GPU refresh rate down to 30 FPS to save maximum power & drop temperature
            Settings.System.putInt(context.contentResolver, "peak_refresh_rate", 30)
            Settings.System.putInt(context.contentResolver, "min_refresh_rate", 30)
            Settings.System.putInt(context.contentResolver, "user_refresh_rate", 30)
            // Enable low power mode to throttle background CPU/GPU clocks
            Settings.Global.putInt(context.contentResolver, "low_power", 1)
        } catch (e: Exception) {
            // Ignored
        }


        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        ).apply {
            screenBrightness = 0.01f // Lowest hardware display brightness
            gravity = Gravity.FILL
            x = 0
            y = 0

            // Ensure 100% full screen coverage across all camera notches and display cutouts
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true

            @Suppress("DEPRECATION")
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )

            val textView = TextView(context).apply {
                text = "🌙 MODO AFK ACTIVO\nEl juego sigue funcionando en segundo plano (30 FPS)\nToca dos veces para desbloquear"
                setTextColor(Color.parseColor("#55AACC"))
                textSize = 14f
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                gravity = Gravity.CENTER
                alpha = 0.75f
            }

            val tvParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            addView(textView, tvParams)

            val hideTextRunnable = object : Runnable {
                override fun run() {
                    textView.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .start()
                }
            }

            // Schedule initial auto-hide after 5 seconds
            mainHandler.postDelayed(hideTextRunnable, 5000)

            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 500) {
                        mainHandler.removeCallbacks(hideTextRunnable)
                        stopAfkMode(context)
                    } else {
                        // Cancel pending hide and smoothly show the text
                        mainHandler.removeCallbacks(hideTextRunnable)
                        textView.animate()
                            .alpha(0.85f)
                            .setDuration(200)
                            .start()
                        // Re-schedule hide in 5 seconds
                        mainHandler.postDelayed(hideTextRunnable, 5000)
                    }
                    lastTapTime = now
                }
                true
            }

            setOnKeyListener { _, keyCode, _ ->
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
                    mainHandler.removeCallbacks(hideTextRunnable)
                    stopAfkMode(context)
                    true
                } else {
                    false
                }
            }
        }

        afkView = container

        try {
            wm.addView(container, layoutParams)
            _isAfkActive.value = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAfkMode(context: Context) {
        if (!_isAfkActive.value && afkView == null) return

        afkMonitorJob?.cancel()
        afkMonitorJob = null

        // Restore ROM / System settings
        try {
            Settings.System.putInt(context.contentResolver, "afk_mode_active", 0)
            Settings.System.putInt(context.contentResolver, "peak_refresh_rate", if (originalRefreshRate > 30) originalRefreshRate else 120)
            Settings.System.putInt(context.contentResolver, "min_refresh_rate", if (originalRefreshRate > 30) originalRefreshRate else 120)
            Settings.System.putInt(context.contentResolver, "user_refresh_rate", if (originalRefreshRate > 30) originalRefreshRate else 120)
            Settings.Global.putInt(context.contentResolver, "low_power", 0)
        } catch (e: Exception) {
            // Ignored
        }


        val v = afkView
        if (v != null) {
            try {
                windowManager?.removeView(v)
            } catch (e: Exception) {
                // Ignored
            }
            afkView = null
        }
        _isAfkActive.value = false
    }
}

