package com.miku.gamingsidebar.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.view.doOnLayout

/**
 * 🛡️ GestureLockOverlayManager
 * Intercepts accidental edge navigation gestures (Back gestures) during gaming sessions.
 * Creates thin edge strips with FLAG_NOT_FOCUSABLE on screen borders.
 * Ensures immediate cleanup upon exiting games, switching apps, or screen-off.
 */
object GestureLockOverlayManager {

    private var leftEdgeView: View? = null
    private var rightEdgeView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isLockActive: Boolean = false

    fun isAttached(): Boolean {
        return (leftEdgeView?.isAttachedToWindow == true) || (rightEdgeView?.isAttachedToWindow == true)
    }

    /**
     * Applies gesture lock edge interceptors.
     * Must only be applied when in active game and user preference is enabled.
     */
    fun applyGestureLock(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { applyGestureLock(context) }
            return
        }

        val appContext = context.applicationContext
        if (!android.provider.Settings.canDrawOverlays(appContext)) {
            return
        }

        // Clean up any stale views first
        removeGestureLock(appContext)

        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val density = appContext.resources.displayMetrics.density
        val edgeStripWidth = (18 * density).toInt().coerceAtLeast(1)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        // 1. Left Edge Interceptor
        val leftParams = WindowManager.LayoutParams(
            edgeStripWidth,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val leftView = View(appContext).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnTouchListener { _, event ->
                // Consume edge touches to shield against accidental back navigation gestures
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> true
                    else -> true
                }
            }
        }

        // 2. Right Edge Interceptor
        val rightParams = WindowManager.LayoutParams(
            edgeStripWidth,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.TOP
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val rightView = View(appContext).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> true
                    else -> true
                }
            }
        }

        try {
            windowManager.addView(leftView, leftParams)
            leftEdgeView = leftView
        } catch (e: Exception) {
            leftEdgeView = null
        }

        try {
            windowManager.addView(rightView, rightParams)
            rightEdgeView = rightView
        } catch (e: Exception) {
            rightEdgeView = null
        }

        isLockActive = (leftEdgeView != null || rightEdgeView != null)
    }

    /**
     * Immediately removes gesture lock overlays and clears all exclusion rects.
     */
    fun removeGestureLock(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { removeGestureLock(context) }
            return
        }

        val appContext = context.applicationContext
        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        // Remove Left View
        try {
            leftEdgeView?.let { view ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        view.systemGestureExclusionRects = emptyList()
                    } catch (_: Exception) {}
                }
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                } else {
                    windowManager.removeView(view)
                }
            }
        } catch (_: Exception) {
        } finally {
            leftEdgeView = null
        }

        // Remove Right View
        try {
            rightEdgeView?.let { view ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        view.systemGestureExclusionRects = emptyList()
                    } catch (_: Exception) {}
                }
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                } else {
                    windowManager.removeView(view)
                }
            }
        } catch (_: Exception) {
        } finally {
            rightEdgeView = null
        }

        isLockActive = false
    }
}
