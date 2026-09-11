package com.miku.gamingsidebar.service

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.miku.gamingsidebar.R

enum class CrosshairStyle(val titleRes: Int) {
    DOT(R.string.crosshair_style_dot),
    CROSS(R.string.crosshair_style_cross),
    TACTICAL(R.string.crosshair_style_tactical),
    CIRCLE_DOT(R.string.crosshair_style_circle_dot),
    CHEVRON(R.string.crosshair_style_chevron)
}

data class CrosshairConfig(
    val isVisible: Boolean = false,
    val style: CrosshairStyle = CrosshairStyle.CROSS,
    val sizeDp: Float = 24f,
    val colorArgb: Long = 0xFF00FF88, // Default vibrant neon green
    val opacity: Float = 0.95f
)

private class CrosshairLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

object CrosshairOverlayManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var currentLifecycleOwner: CrosshairLifecycleOwner? = null
    private var prefs: SharedPreferences? = null

    private val _configFlow = MutableStateFlow(CrosshairConfig())
    val configFlow = _configFlow.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences("miku_crosshair_prefs", Context.MODE_PRIVATE)
            val isVis = prefs?.getBoolean("visible", false) ?: false
            val styleName = prefs?.getString("style", CrosshairStyle.CROSS.name) ?: CrosshairStyle.CROSS.name
            val style = try { CrosshairStyle.valueOf(styleName) } catch (e: Exception) { CrosshairStyle.CROSS }
            val size = prefs?.getFloat("size", 24f) ?: 24f
            val color = prefs?.getLong("color", 0xFF00FF88) ?: 0xFF00FF88
            val opacity = prefs?.getFloat("opacity", 0.95f) ?: 0.95f

            _configFlow.value = CrosshairConfig(
                isVisible = isVis,
                style = style,
                sizeDp = size,
                colorArgb = color,
                opacity = opacity
            )
        }
    }

    fun isEnabled(): Boolean = _configFlow.value.isVisible

    fun toggle(context: Context): Boolean {
        init(context)
        val current = _configFlow.value.isVisible
        val target = !current
        updateConfig(context) { it.copy(isVisible = target) }
        return target
    }

    fun setVisible(context: Context, visible: Boolean) {
        init(context)
        updateConfig(context) { it.copy(isVisible = visible) }
    }

    fun updateConfig(context: Context, update: (CrosshairConfig) -> CrosshairConfig) {
        init(context)
        val newConfig = update(_configFlow.value)
        _configFlow.value = newConfig

        prefs?.edit()?.apply {
            putBoolean("visible", newConfig.isVisible)
            putString("style", newConfig.style.name)
            putFloat("size", newConfig.sizeDp)
            putLong("color", newConfig.colorArgb)
            putFloat("opacity", newConfig.opacity)
            apply()
        }

        if (newConfig.isVisible) {
            showOverlay(context)
        } else {
            hideOverlay()
        }
    }

    private fun showOverlay(context: Context) {
        mainHandler.post {
            if (overlayView != null) return@post

            try {
                windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                if (windowManager == null) return@post

                val lifecycleOwner = CrosshairLifecycleOwner()
                lifecycleOwner.onCreate()
                currentLifecycleOwner = lifecycleOwner

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                    x = 0
                    y = 0
                }

                overlayView = ComposeView(context).apply {
                    setViewTreeLifecycleOwner(lifecycleOwner)
                    setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                    setContent {
                        val config by configFlow.collectAsState()
                        if (config.isVisible) {
                            CrosshairRenderer(config = config)
                        }
                    }
                }

                windowManager?.addView(overlayView, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hideOverlay() {
        mainHandler.post {
            if (overlayView != null) {
                try {
                    windowManager?.removeView(overlayView)
                } catch (e: Exception) {
                    // Ignored
                }
                overlayView = null
            }
            currentLifecycleOwner?.onDestroy()
            currentLifecycleOwner = null
        }
    }
}

@Composable
fun CrosshairRenderer(config: CrosshairConfig, modifier: Modifier = Modifier) {
    val baseColor = Color(config.colorArgb).copy(alpha = config.opacity)
    val shadowColor = Color.Black.copy(alpha = (config.opacity * 0.75f).coerceIn(0f, 1f))
    val sizeDp = config.sizeDp.dp

    Box(
        modifier = modifier.size(sizeDp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val strokeWidth = (size.width * 0.09f).coerceIn(2.5f, 6.5f)
            val shadowStrokeWidth = strokeWidth + 2.5f

            when (config.style) {
                CrosshairStyle.DOT -> {
                    val radius = (size.width * 0.18f).coerceAtLeast(3f)
                    // Outline shadow
                    drawCircle(color = shadowColor, radius = radius + 1.5f, center = center)
                    // Main dot
                    drawCircle(color = baseColor, radius = radius, center = center)
                }

                CrosshairStyle.CROSS -> {
                    val halfLen = size.width / 2f
                    // Shadow lines
                    drawCrossLines(center, halfLen, 0f, shadowStrokeWidth, shadowColor)
                    // Core lines
                    drawCrossLines(center, halfLen, 0f, strokeWidth, baseColor)
                    // Center dot
                    drawCircle(color = baseColor, radius = strokeWidth * 0.6f, center = center)
                }

                CrosshairStyle.TACTICAL -> {
                    val halfLen = size.width / 2f
                    val gap = size.width * 0.18f
                    // Shadow lines with gap
                    drawCrossLines(center, halfLen, gap, shadowStrokeWidth, shadowColor)
                    // Core lines with gap
                    drawCrossLines(center, halfLen, gap, strokeWidth, baseColor)
                    // Tiny center micro-dot
                    drawCircle(color = shadowColor, radius = strokeWidth * 0.7f + 1f, center = center)
                    drawCircle(color = baseColor, radius = strokeWidth * 0.7f, center = center)
                }

                CrosshairStyle.CIRCLE_DOT -> {
                    val outerRadius = (size.width * 0.42f)
                    val dotRadius = (size.width * 0.12f).coerceAtLeast(2.5f)
                    // Outer circle shadow
                    drawCircle(
                        color = shadowColor,
                        radius = outerRadius,
                        center = center,
                        style = Stroke(width = shadowStrokeWidth)
                    )
                    // Outer circle
                    drawCircle(
                        color = baseColor,
                        radius = outerRadius,
                        center = center,
                        style = Stroke(width = strokeWidth)
                    )
                    // Center dot
                    drawCircle(color = shadowColor, radius = dotRadius + 1.5f, center = center)
                    drawCircle(color = baseColor, radius = dotRadius, center = center)
                }

                CrosshairStyle.CHEVRON -> {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        moveTo(w * 0.15f, h * 0.75f)
                        lineTo(w * 0.50f, h * 0.25f)
                        lineTo(w * 0.85f, h * 0.75f)
                    }
                    // Shadow chevron
                    drawPath(
                        path = path,
                        color = shadowColor,
                        style = Stroke(width = shadowStrokeWidth, cap = StrokeCap.Round)
                    )
                    // Main chevron
                    drawPath(
                        path = path,
                        color = baseColor,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawCrossLines(
    center: Offset,
    length: Float,
    gap: Float,
    strokeWidth: Float,
    color: Color
) {
    // Horizontal left & right
    drawLine(
        color = color,
        start = Offset(center.x - length, center.y),
        end = Offset(center.x - gap, center.y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(center.x + gap, center.y),
        end = Offset(center.x + length, center.y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    // Vertical top & bottom
    drawLine(
        color = color,
        start = Offset(center.x, center.y - length),
        end = Offset(center.x, center.y - gap),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y + gap),
        end = Offset(center.x, center.y + length),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}
