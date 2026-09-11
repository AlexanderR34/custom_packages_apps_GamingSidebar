package com.miku.gamingsidebar.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.miku.gamingsidebar.ui.theme.ElectricLime
import com.miku.gamingsidebar.ui.theme.ElectricPink
import com.miku.gamingsidebar.ui.theme.MikuCyan
import com.miku.gamingsidebar.ui.theme.MikuHubTheme
import com.miku.gamingsidebar.ui.theme.OnSurfaceVariant
import com.miku.gamingsidebar.ui.theme.SurfaceDark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class HudStyle {
    CLASSIC,  // FPS / RAM % / CPU % / GPU %
    ADVANCED  // FPS / RAM MB (ej: 4210MB) / CPU °C / BAT °C / GPU °C
}

object HorizontalHudOverlay {

    private var windowManager: WindowManager? = null
    private var hudView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val _isHudVisible = MutableStateFlow(false)
    val isHudVisible: StateFlow<Boolean> = _isHudVisible

    private val _hudStyle = MutableStateFlow(HudStyle.CLASSIC)
    val hudStyle: StateFlow<HudStyle> = _hudStyle

    private val _isTransparentBackground = MutableStateFlow(false)
    val isTransparentBackground: StateFlow<Boolean> = _isTransparentBackground

    private val _isTouchThrough = MutableStateFlow(false)
    val isTouchThrough: StateFlow<Boolean> = _isTouchThrough

    private var isUserEnabled = false
    private var isGameActive = false

    fun isVisible(): Boolean = _isHudVisible.value

    fun getHudStyle(): HudStyle = _hudStyle.value

    fun setHudStyle(style: HudStyle, context: Context? = null) {
        _hudStyle.value = style
        if (context != null) {
            val prefs = context.getSharedPreferences("hud_overlay_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("hud_style", style.name).apply()
        }
    }

    fun toggleHudStyle(context: Context? = null): HudStyle {
        val newStyle = if (_hudStyle.value == HudStyle.CLASSIC) HudStyle.ADVANCED else HudStyle.CLASSIC
        setHudStyle(newStyle, context)
        return newStyle
    }

    fun toggleTransparentBackground(context: Context): Boolean {
        val target = !_isTransparentBackground.value
        _isTransparentBackground.value = target
        val prefs = context.getSharedPreferences("hud_overlay_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("hud_transparent_bg", target).apply()
        return target
    }

    fun toggleTouchThrough(context: Context): Boolean {
        val target = !_isTouchThrough.value
        _isTouchThrough.value = target
        val prefs = context.getSharedPreferences("hud_overlay_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("hud_touch_through", target).apply()
        updateWindowFlags()
        return target
    }

    private fun updateWindowFlags() {
        val params = layoutParams ?: return
        val v = hudView ?: return
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (_isTouchThrough.value) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        params.flags = flags
        try {
            windowManager?.updateViewLayout(v, params)
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun setGameActive(active: Boolean) {
        isGameActive = active
        if (!isUserEnabled) {
            hudView?.visibility = View.GONE
            return
        }
        hudView?.visibility = if (active) View.VISIBLE else View.GONE
    }

    fun toggleHud(context: Context, monitor: PerformanceMonitor): Boolean {
        if (_isHudVisible.value) {
            hideHud(context)
            return false
        } else {
            showHud(context, monitor)
            return true
        }
    }

    fun showHud(context: Context, monitor: PerformanceMonitor) {
        isUserEnabled = true
        isGameActive = (GamingOverlayService.activeInstance != null && GamingOverlayService.activeGamePackageFlow.value != null)

        val prefs = context.getSharedPreferences("hud_overlay_prefs", Context.MODE_PRIVATE)
        val savedStyleName = prefs.getString("hud_style", HudStyle.CLASSIC.name) ?: HudStyle.CLASSIC.name
        _hudStyle.value = try { HudStyle.valueOf(savedStyleName) } catch (e: Exception) { HudStyle.CLASSIC }
        _isTransparentBackground.value = prefs.getBoolean("hud_transparent_bg", false)
        _isTouchThrough.value = prefs.getBoolean("hud_touch_through", false)

        if (_isHudVisible.value && hudView != null) {
            hudView?.visibility = if (isGameActive) View.VISIBLE else View.GONE
            updateWindowFlags()
            return
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val density = context.resources.displayMetrics.density
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (_isTouchThrough.value) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = (10 * density).toInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        layoutParams = params

        val composeView = ComposeView(context).apply {
            visibility = if (isGameActive) View.VISIBLE else View.GONE
            val owner = GamingOverlayService.activeInstance
            if (owner != null) {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
            }

            setContent {
                MikuHubTheme {
                    HorizontalHudContent(
                        monitor = monitor,
                        onDrag = { dx, dy ->
                            params.x = (params.x + dx).toInt()
                            params.y = (params.y + dy).toInt()
                            try {
                                wm.updateViewLayout(this@apply, params)
                            } catch (e: Exception) {
                                // Ignored
                            }
                        },
                        onToggleStyle = {
                            toggleHudStyle(context)
                        }
                    )
                }
            }
        }

        hudView = composeView

        try {
            wm.addView(composeView, params)
            _isHudVisible.value = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideHud(context: Context) {
        isUserEnabled = false
        val v = hudView ?: return
        try {
            windowManager?.removeView(v)
        } catch (e: Exception) {
            // Ignored
        }
        hudView = null
        _isHudVisible.value = false
    }
}

@Composable
fun HorizontalHudContent(
    monitor: PerformanceMonitor,
    onDrag: (Float, Float) -> Unit,
    onToggleStyle: () -> Unit = {}
) {
    val fps by monitor.fps.collectAsState()
    val ramUsedMb by monitor.ramUsedMb.collectAsState()
    val cpuPercent by monitor.cpuUsagePercent.collectAsState()
    val gpuPercent by monitor.gpuUsagePercent.collectAsState()
    val cpuTemp by monitor.cpuTemperature.collectAsState()
    val gpuTemp by monitor.gpuTemperature.collectAsState()
    val batteryTemp by monitor.batteryTemperature.collectAsState()
    val isTransparentBg by HorizontalHudOverlay.isTransparentBackground.collectAsState()
    val isTouchThrough by HorizontalHudOverlay.isTouchThrough.collectAsState()

    val hudModifier = if (isTouchThrough) {
        Modifier.wrapContentSize()
    } else {
        Modifier
            .wrapContentSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable(onClick = onToggleStyle)
    }

    CompactMetricHUD(
        fps = fps,
        ramUsedMb = ramUsedMb,
        cpuPercent = cpuPercent,
        cpuTemp = cpuTemp,
        gpuPercent = gpuPercent,
        gpuTemp = gpuTemp,
        batteryTemp = batteryTemp,
        isTransparentBg = isTransparentBg,
        modifier = hudModifier
    )
}

@Composable
fun CompactMetricHUD(
    fps: Int,
    ramUsedMb: Int,
    cpuPercent: Int,
    cpuTemp: Int,
    gpuPercent: Int,
    gpuTemp: Int,
    batteryTemp: Int,
    isTransparentBg: Boolean = false,
    modifier: Modifier = Modifier
) {
    val textShadow = if (isTransparentBg) {
        androidx.compose.ui.graphics.Shadow(
            color = Color.Black,
            offset = androidx.compose.ui.geometry.Offset(2f, 2f),
            blurRadius = 4f
        )
    } else null

    val rowModifier = if (isTransparentBg) {
        modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .height(42.dp)
    } else {
        modifier
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .height(42.dp)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = rowModifier
    ) {
        // FPS
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "FPS ",
                fontSize = 9.sp,
                color = Color.LightGray,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
            )
            Text(
                text = if (fps > 0) "$fps" else "--",
                fontSize = 12.sp,
                color = Color(0xFF00E5FF),
                fontWeight = FontWeight.ExtraBold,
                style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
            )
        }
        VerticalDivider(isTransparentBg)

        // RAM exacto en MB
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "RAM ",
                fontSize = 9.sp,
                color = Color.LightGray,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
            )
            Text(
                text = "$ramUsedMb",
                fontSize = 12.sp,
                color = Color(0xFF00E676),
                fontWeight = FontWeight.ExtraBold,
                style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
            )
        }
        VerticalDivider(isTransparentBg)

        // CPU (Uso arriba / Temp abajo)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "CPU $cpuPercent%",
                fontSize = 10.sp,
                lineHeight = 11.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
            )
            Text(
                text = if (cpuTemp > 0) "$cpuTemp°C" else "--°C",
                fontSize = 8.sp,
                lineHeight = 9.sp,
                color = Color(0xFFFFB74D),
                fontWeight = FontWeight.SemiBold,
                style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
            )
        }
        VerticalDivider(isTransparentBg)

        // GPU (Uso arriba / Temp abajo)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "GPU $gpuPercent%",
                fontSize = 10.sp,
                lineHeight = 11.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
            )
            Text(
                text = if (gpuTemp > 0) "$gpuTemp°C" else "--°C",
                fontSize = 8.sp,
                lineHeight = 9.sp,
                color = Color(0xFFFFB74D),
                fontWeight = FontWeight.SemiBold,
                style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
            )
        }
        VerticalDivider(isTransparentBg)

        // Batería Temp
        Text(
            text = "BAT $batteryTemp°C",
            fontSize = 10.sp,
            color = Color(0xFFFFEE58),
            fontWeight = FontWeight.SemiBold,
            style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
        )
    }
}

@Composable
private fun VerticalDivider(isTransparentBg: Boolean = false) {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 18.dp)
            .background(Color.White.copy(alpha = if (isTransparentBg) 0.35f else 0.15f))
    )
}
