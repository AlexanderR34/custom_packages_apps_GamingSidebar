package com.miku.gamingsidebar.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.miku.gamingsidebar.ui.theme.MikuHubTheme
import kotlinx.coroutines.delay

object PostGameSummaryOverlay {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isShowing = false

    fun show(context: Context, stats: GameSessionStats) {
        if (isShowing) hide()

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 40
        }

        val lifecycleOwner = object : LifecycleOwner, SavedStateRegistryOwner {
            private val lifecycleRegistry = LifecycleRegistry(this)
            private val savedStateRegistryController = SavedStateRegistryController.create(this)

            init {
                savedStateRegistryController.performRestore(null)
                lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            }

            override val lifecycle: Lifecycle get() = lifecycleRegistry
            override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        }

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                MikuHubTheme {
                    PostGameSummaryCard(
                        stats = stats,
                        onDismiss = { hide() }
                    )
                }
            }
        }

        try {
            wm.addView(composeView, layoutParams)
            overlayView = composeView
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hide() {
        if (!isShowing) return
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            isShowing = false
        }
    }
}

@Composable
private fun PostGameSummaryCard(
    stats: GameSessionStats,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(9000) // Auto-dismiss after 9s
        visible = false
        delay(350)
        onDismiss()
    }

    val primary = MaterialTheme.colorScheme.primary
    val surfaceLowest = MaterialTheme.colorScheme.surfaceContainerLowest
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(primary.copy(alpha = 0.7f), Color(0x3300E676), primary.copy(alpha = 0.5f))
                    ),
                    RoundedCornerShape(22.dp)
                ),
            color = surfaceLowest.copy(alpha = 0.95f),
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(primary.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.SportsEsports,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stats.appName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = "Sesión: ${formatDuration(stats.durationSeconds)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable {
                                visible = false
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Cerrar",
                            tint = onSurface,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Stats Metrics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // FPS & Stability
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(8.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Speed, contentDescription = null, tint = primary, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(androidx.compose.ui.res.stringResource(com.miku.gamingsidebar.R.string.summary_avg_fps), fontSize = 9.sp, color = onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "%.1f".format(stats.averageFps),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = primary
                            )
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.miku.gamingsidebar.R.string.summary_stability, stats.stabilityPercent),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (stats.stabilityPercent >= 90) Color(0xFF00E676) else Color(0xFFFFD600)
                            )
                        }
                    }

                    // Max Temperature
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(8.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(androidx.compose.ui.res.stringResource(com.miku.gamingsidebar.R.string.summary_max_temp), fontSize = 9.sp, color = onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${stats.maxCpuTemp}°C",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = if (stats.maxCpuTemp >= 70) Color(0xFFFF5252) else onSurface
                            )
                            Text(
                                text = "GPU: ${stats.maxGpuTemp}°C",
                                fontSize = 9.sp,
                                color = onSurfaceVariant
                            )
                        }
                    }

                    // Battery Drain
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(8.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.BatteryAlert, contentDescription = null, tint = Color(0xFFFFD600), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(androidx.compose.ui.res.stringResource(com.miku.gamingsidebar.R.string.summary_battery), fontSize = 9.sp, color = onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "-${stats.batteryConsumedPercent}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = if (stats.batteryConsumedPercent > 15) Color(0xFFFF5252) else onSurface
                            )
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.miku.gamingsidebar.R.string.summary_total_drain),
                                fontSize = 9.sp,
                                color = onSurfaceVariant
                            )
                        }
                    }
                }

                // Sparkline Graph of FPS
                if (stats.fpsHistory.size > 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        FpsSparkline(
                            samples = stats.fpsHistory,
                            lineColor = primary,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FpsSparkline(
    samples: List<Int>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas

        val maxVal = (samples.maxOrNull() ?: 60).toFloat().coerceAtLeast(30f)
        val minVal = (samples.minOrNull() ?: 0).toFloat().coerceAtMost(maxVal - 10f)
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val stepX = size.width / (samples.size - 1)
        val path = Path()

        samples.forEachIndexed { i, fps ->
            val x = i * stepX
            val normY = (fps - minVal) / range
            val y = size.height - (normY * size.height)
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
