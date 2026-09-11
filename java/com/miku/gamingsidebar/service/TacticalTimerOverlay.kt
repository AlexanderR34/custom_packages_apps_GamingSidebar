package com.miku.gamingsidebar.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TacticalTimerOverlay {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isVisible = false

    private val _currentRemainingSeconds = MutableStateFlow(0)
    val currentRemainingSeconds: StateFlow<Int> = _currentRemainingSeconds.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private var currentTotalDuration = 30
    private var currentTimerLabel = ""

    fun startTimer(context: Context, totalSeconds: Int, label: String = "") {
        currentTotalDuration = totalSeconds.coerceAtLeast(1)
        currentTimerLabel = label
        _currentRemainingSeconds.value = currentTotalDuration
        _isTimerRunning.value = true

        if (isVisible) return

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
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 160
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
                    FloatingCircleTimer(
                        totalDuration = currentTotalDuration,
                        onClose = { hide() },
                        onVibrate = { triggerHaptic(context) }
                    )
                }
            }
        }

        setupDragListener(composeView, layoutParams, wm)

        try {
            wm.addView(composeView, layoutParams)
            overlayView = composeView
            isVisible = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hide() {
        if (!isVisible) return
        _isTimerRunning.value = false
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            isVisible = false
        }
    }

    fun isShowing(): Boolean = isVisible

    private fun triggerHaptic(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(140, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(140, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(140)
                }
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener(view: View, params: WindowManager.LayoutParams, wm: WindowManager) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoving = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10 || isMoving) {
                        isMoving = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            wm.updateViewLayout(view, params)
                        } catch (e: Exception) {
                            // Ignored
                        }
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    isMoving
                }
                else -> false
            }
        }
    }
}

@Composable
private fun FloatingCircleTimer(
    totalDuration: Int,
    onClose: () -> Unit,
    onVibrate: () -> Unit
) {
    var remainingSeconds by remember { mutableIntStateOf(totalDuration) }
    var isRunning by remember { mutableStateOf(true) }

    val primary = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val surfaceLowest = MaterialTheme.colorScheme.surfaceContainerLowest
    val onSurface = MaterialTheme.colorScheme.onSurface

    // Countdown timer loop
    LaunchedEffect(isRunning, remainingSeconds) {
        if (isRunning && remainingSeconds > 0) {
            delay(1000)
            remainingSeconds -= 1
            if (remainingSeconds == 0) {
                isRunning = false
                onVibrate()
            }
        }
    }

    val progressFraction = if (totalDuration > 0) {
        (remainingSeconds.toFloat() / totalDuration).coerceIn(0f, 1f)
    } else 0f

    val arcColor by animateColorAsState(
        targetValue = when {
            remainingSeconds == 0 -> errorColor
            progressFraction < 0.25f -> Color(0xFFFF5252)
            progressFraction < 0.5f -> Color(0xFFFFD600)
            else -> primary
        },
        label = "timerArcColor"
    )

    // Mini Circular Overlay with Attached Close X button
    Box(
        modifier = Modifier.size(68.dp),
        contentAlignment = Alignment.Center
    ) {
        // Main Circular Countdown Body
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(surfaceLowest.copy(alpha = 0.94f))
                .border(1.5.dp, arcColor.copy(alpha = 0.50f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (remainingSeconds == 0) {
                        remainingSeconds = totalDuration
                        isRunning = true
                    } else {
                        isRunning = !isRunning
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Sweeping Progress Arc
            Canvas(modifier = Modifier.fillMaxSize().padding(3.5.dp)) {
                val strokeWidth = 3.5.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = Offset(size.width / 2, size.height / 2)

                // Track Background
                drawCircle(
                    color = arcColor.copy(alpha = 0.18f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )

                // Active Arc
                val sweepAngle = progressFraction * 360f
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Clean, Centered Numbers Only (No other text)
            Text(
                text = if (remainingSeconds == 0) "0" else formatMiniTime(remainingSeconds),
                color = if (remainingSeconds == 0) errorColor else if (!isRunning) onSurface.copy(alpha = 0.55f) else onSurface,
                fontSize = if (remainingSeconds >= 60) 12.sp else 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )
        }

        // Clean Red/Error 'X' Button on top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-1).dp, y = 1.dp)
                .size(19.dp)
                .clip(CircleShape)
                .background(Color(0xFFE53935))
                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Cerrar",
                tint = Color.White,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}

private fun formatMiniTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return if (m > 0) String.format(java.util.Locale.US, "%d:%02d", m, s) else "$s"
}
