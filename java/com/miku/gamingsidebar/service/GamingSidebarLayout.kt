package com.miku.gamingsidebar.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt
import android.hardware.display.DisplayManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.stringResource
import com.miku.gamingsidebar.R

enum class SidebarTab(val titleRes: Int, val icon: ImageVector) {
    BASIC(R.string.tab_basic, Icons.Rounded.GridView),
    PERFORMANCE(R.string.tab_performance, Icons.Rounded.Speed),
    ANTI_INTERFERENCE(R.string.tab_anti_interference, Icons.Rounded.Shield),
    TOOLS(R.string.tab_tools, Icons.Rounded.AutoAwesome)
}

@Composable
fun GamingSidebarLayout(
    monitor: PerformanceMonitor,
    onCloseSidebar: () -> Unit,
    modifier: Modifier = Modifier,
    isSnappedRight: Boolean = false,
    isExpanded: Boolean = false,
    onExpandedChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- Monet Material 3 Dynamic Palette Tokens ---
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surfaceLowest = MaterialTheme.colorScheme.surfaceContainerLowest
    val surfaceLow = MaterialTheme.colorScheme.surfaceContainerLow
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    var selectedTab by remember { mutableStateOf(SidebarTab.BASIC) }
    val activeGamePkg by GamingOverlayService.activeGamePackageFlow.collectAsState()
    var performanceMode by remember { mutableIntStateOf(1) } // 0: Ahorro, 1: Equilibrado, 2: Rendimiento

    val fps by monitor.fps.collectAsState()
    val cpuPercent by monitor.cpuUsagePercent.collectAsState()
    val gpuPercent by monitor.gpuUsagePercent.collectAsState()
    val cpuTemp by monitor.cpuTemperature.collectAsState()
    val gpuTemp by monitor.gpuTemperature.collectAsState()
    val batteryTemp by monitor.batteryTemperature.collectAsState()
    val systemTemp by monitor.temperature.collectAsState()
    val ramPercent by monitor.ramUsagePercent.collectAsState()
    val ramUsedMb by monitor.ramUsedMb.collectAsState()
    val ramTotalMb by monitor.ramTotalMb.collectAsState()
    val hudStyle by HorizontalHudOverlay.hudStyle.collectAsState()
    val isTransparentBg by HorizontalHudOverlay.isTransparentBackground.collectAsState()
    val isTouchThrough by HorizontalHudOverlay.isTouchThrough.collectAsState()
    val ping by monitor.pingMs.collectAsState()
    val isRecording by ScreenRecordService.isRecordingFlow.collectAsState()

    var isWifiOn by remember { mutableStateOf(true) }
    var isDndOn by remember { mutableStateOf(false) }
    var isHudOn by remember { mutableStateOf(HorizontalHudOverlay.isVisible()) }
    var isAfkOn by remember { mutableStateOf(false) }
    var isPingTurboOn by remember { mutableStateOf(false) }
    var isTouchShieldOn by remember { mutableStateOf(false) }
    var isCrosshairOn by remember { mutableStateOf(false) }

    var isRamInMbMode by remember { mutableStateOf(false) }
    var showBatteryTempInHeader by remember { mutableStateOf(true) }

    var ramLabel by remember { mutableStateOf("") }
    var isCleaningRam by remember { mutableStateOf(false) }

    var showCrosshairSettings by remember { mutableStateOf(false) }
    var isMusicPageOpen by remember { mutableStateOf(false) }

    // Real-time clock and battery level
    var currentTime by remember { mutableStateOf("") }
    var batteryLevel by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }
    var isAutoBrightness by remember { mutableStateOf(false) }

    // Brightness slider level synced via BrightnessHelper
    var currentBrightness by remember {
        mutableFloatStateOf(BrightnessHelper.getBrightness(context))
    }

    val activeGameTitle = remember(activeGamePkg) {
        if (!activeGamePkg.isNullOrBlank()) {
            try {
                val pm = context.packageManager
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(activeGamePkg!!, android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    pm.getApplicationInfo(activeGamePkg!!, 0)
                }
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                activeGamePkg ?: "Miku Hub"
            }
        } else {
            "Miku Hub"
        }
    }

    LaunchedEffect(isExpanded, activeGamePkg) {
        performanceMode = GamingActionsHelper.getSavedPerformanceModeInt(context, activeGamePkg)
        if (!isExpanded) {
            isMusicPageOpen = false
        }
        if (isExpanded) {
            isWifiOn = GamingActionsHelper.isWifiEnabled(context)
            isDndOn = GamingActionsHelper.isDndEnabled(context)
            isHudOn = GamingActionsHelper.isHudVisible()
            isAfkOn = GamingActionsHelper.isAfkModeOn(context)
            isPingTurboOn = GamingActionsHelper.isLowLatencyEnabled(context)
            isTouchShieldOn = GamingActionsHelper.isTouchShieldEnabled(context)
            isCrosshairOn = GamingActionsHelper.isCrosshairEnabled(context)
            AudioMixerHelper.syncCurrentVolumes(context)
            MediaPlaybackHelper.syncState(context)

            try {
                val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val bIntent = context.registerReceiver(null, iFilter)
                val level = bIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = bIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) {
                    batteryLevel = (level * 100) / scale
                } else {
                    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    batteryLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: batteryLevel
                }
                val status = bIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
            } catch (e: Exception) {
                // Fallback
            }

            currentBrightness = BrightnessHelper.getBrightness(context)

            try {
                val mode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                isAutoBrightness = (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
            } catch (e: Exception) {
                // Ignored
            }
        }
    }

    LaunchedEffect(isMusicPageOpen) {
        if (isMusicPageOpen) {
            MediaPlaybackHelper.syncState(context)
        }
    }

    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        while (true) {
            currentTime = sdf.format(Date())
            try {
                val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val bIntent = context.registerReceiver(null, iFilter)
                val level = bIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = bIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) {
                    batteryLevel = (level * 100) / scale
                }
                val status = bIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
            } catch (e: Exception) {
                // Ignored
            }
            delay(4000)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp

    val modalWidth = if (isLandscape) 520.dp.coerceAtMost((screenWidthDp - 20).dp) else (screenWidthDp - 14).dp.coerceAtMost(380.dp)
    val modalHeight = if (isLandscape) 305.dp.coerceAtMost((screenHeightDp - 14).dp) else 370.dp.coerceAtMost((screenHeightDp - 40).dp)
    val navRailWidth = if (isLandscape) 78.dp else 64.dp

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWindowFullyExpanded = maxWidth > 200.dp

        // Outside scrim to collapse sidebar
        if (isExpanded && isWindowFullyExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onExpandedChanged(false)
                    }
            )
        }

        // Collapsed trigger handle - dynamic Monet colored trigger matching system theme
        if (!isExpanded && !isWindowFullyExpanded && !showCrosshairSettings) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (isSnappedRight) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(76.dp)
                        .clip(
                            if (isSnappedRight) RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                            else RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                        )
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    primary.copy(alpha = 0.95f),
                                    primaryContainer.copy(alpha = 0.85f),
                                    primary.copy(alpha = 0.95f)
                                )
                            )
                        )
                )
            }
        }

        // Expanded 2-Column HighBoost Game Space Modal (Responsive Landscape & Portrait)
        AnimatedVisibility(
            visible = isExpanded && isWindowFullyExpanded && !showCrosshairSettings,
            enter = slideInHorizontally(
                initialOffsetX = { if (isSnappedRight) it else -it },
                animationSpec = tween(240, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutHorizontally(
                targetOffsetX = { if (isSnappedRight) it else -it },
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(if (isSnappedRight) Alignment.CenterEnd else Alignment.CenterStart)
        ) {
            Surface(
                modifier = Modifier
                    .padding(
                        start = if (isSnappedRight) 0.dp else 6.dp,
                        end = if (isSnappedRight) 6.dp else 0.dp,
                        top = 6.dp,
                        bottom = 6.dp
                    )
                    .width(modalWidth)
                    .height(modalHeight)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                primary.copy(alpha = 0.50f),
                                outlineVariant.copy(alpha = 0.60f),
                                secondary.copy(alpha = 0.35f)
                            )
                        ),
                        RoundedCornerShape(24.dp)
                    )
                    .pointerInput(isMusicPageOpen, isSnappedRight) {
                        var totalDragX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDragX = 0f },
                            onDragEnd = {
                                val threshold = 32f
                                if (!isMusicPageOpen) {
                                    // Swipe inward away from screen edge to open music player
                                    if (!isSnappedRight && totalDragX < -threshold) {
                                        isMusicPageOpen = true
                                    } else if (isSnappedRight && totalDragX > threshold) {
                                        isMusicPageOpen = true
                                    }
                                } else {
                                    // Swipe back toward screen edge to return to dashboard
                                    if (!isSnappedRight && totalDragX > threshold) {
                                        isMusicPageOpen = false
                                    } else if (isSnappedRight && totalDragX < -threshold) {
                                        isMusicPageOpen = false
                                    }
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                totalDragX += dragAmount
                            }
                        )
                    },
                color = surfaceLowest.copy(alpha = 0.85f), // Monet translucent overlay glass
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 24.dp
            ) {
                AnimatedContent(
                    targetState = isMusicPageOpen,
                    transitionSpec = {
                        if (targetState) {
                            (slideInHorizontally(tween(200, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(200)))
                        } else {
                            (slideInHorizontally(tween(200, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200)))
                        }
                    },
                    label = "modalSidePageTransition"
                ) { showMusicPage ->
                    if (showMusicPage) {
                        AndroidMaterialYouPlayer(
                            context = context,
                            onBackToDashboard = { isMusicPageOpen = false },
                            isLandscape = isLandscape
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // ==========================================
                                // 1. Left Navigation Rail (Tabs)
                                // ==========================================
                                Column(
                        modifier = Modifier
                            .width(navRailWidth)
                            .fillMaxHeight()
                            .background(surfaceLow.copy(alpha = 0.85f))
                            .padding(vertical = if (isLandscape) 8.dp else 6.dp, horizontal = 4.dp),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SidebarTab.values().forEach { tab ->
                            val isSelected = selectedTab == tab
                            val activeBg by animateColorAsState(
                                targetValue = if (isSelected) primary.copy(alpha = 0.22f) else Color.Transparent,
                                label = "tabBg"
                            )
                            val iconTint by animateColorAsState(
                                targetValue = if (isSelected) primary else onSurfaceVariant,
                                label = "tabTint"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isLandscape) 48.dp else 52.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(activeBg)
                                    .clickable { selectedTab = tab }
                                    .padding(horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Left vertical accent bar
                                    Box(
                                        modifier = Modifier
                                            .width(2.5.dp)
                                            .height(if (isLandscape) 22.dp else 24.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (isSelected) primary else Color.Transparent)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = stringResource(tab.titleRes),
                                            tint = iconTint,
                                            modifier = Modifier.size(if (isLandscape) 17.dp else 18.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(tab.titleRes),
                                            color = if (isSelected) onSurface else onSurfaceVariant,
                                            fontSize = if (isLandscape) 8.5.sp else 7.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            lineHeight = if (isLandscape) 10.sp else 9.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(primary.copy(alpha = 0.20f))
                    )

                    // ==========================================
                    // 2. Right Main Panel
                    // ==========================================
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(
                                horizontal = if (isLandscape) 12.dp else 10.dp,
                                vertical = if (isLandscape) 8.dp else 8.dp
                            ),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // --- Top Header ---
                        val displayManager = remember { context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager }
                        val defaultDisplay = remember { displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY) }
                        val refreshRate = remember { defaultDisplay?.refreshRate?.roundToInt() ?: 60 }
                        val displayFps = if (fps > 0) fps else refreshRate

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Logo
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "MIKU",
                                    color = primary,
                                    fontSize = if (isLandscape) 13.5.sp else 12.sp,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Italic
                                )
                                Text(
                                    text = "BOOST",
                                    color = onSurface,
                                    fontSize = if (isLandscape) 13.5.sp else 12.sp,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Italic
                                )
                            }

                            // Right: Status Badges Row (Ping, Battery Temp, Dynamic Battery, FPS)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 4.dp)
                            ) {
                                // 1. Ping Badge Pill
                                val pingColor = when {
                                    ping <= 0f -> Color(0xFF9E9E9E)
                                    ping <= 45f -> Color(0xFF00E676)
                                    ping <= 90f -> Color(0xFFFFD600)
                                    else -> Color(0xFFFF5252)
                                }
                                Box(
                                    modifier = Modifier
                                        .height(if (isLandscape) 22.dp else 20.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(pingColor.copy(alpha = 0.14f))
                                        .border(0.8.dp, pingColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Wifi,
                                            contentDescription = "Ping",
                                            tint = pingColor,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "${ping.roundToInt()} ms",
                                            color = pingColor,
                                            fontSize = if (isLandscape) 9.sp else 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                // 2. Temperature Badge Pill (Tapping toggles Battery Temp vs System Temp)
                                val headerTemp = if (showBatteryTempInHeader) batteryTemp else systemTemp
                                val tempColor = when {
                                    headerTemp >= 46 -> Color(0xFFFF5252)
                                    headerTemp >= 41 -> Color(0xFFFFD600)
                                    else -> Color(0xFF00E676)
                                }
                                Box(
                                    modifier = Modifier
                                        .height(if (isLandscape) 22.dp else 20.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(tempColor.copy(alpha = 0.14f))
                                        .border(0.8.dp, tempColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                        .clickable { showBatteryTempInHeader = !showBatteryTempInHeader }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Thermostat,
                                            contentDescription = "Temp",
                                            tint = tempColor,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = if (showBatteryTempInHeader) "BAT $headerTemp°C" else "SYS $headerTemp°C",
                                            color = tempColor,
                                            fontSize = if (isLandscape) 9.sp else 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                // 3. Dynamic Animated Battery Indicator
                                DynamicBatteryIndicator(
                                    batteryLevel = batteryLevel,
                                    isCharging = isCharging,
                                    isLandscape = isLandscape
                                )

                                // 4. FPS Badge Pill (Single balanced horizontal pill)
                                val fpsColor = when {
                                    displayFps >= 55 -> Color(0xFF00E676)
                                    displayFps in 30..54 -> Color(0xFFFFD600)
                                    displayFps in 1..29 -> Color(0xFFFF5252)
                                    else -> primary
                                }
                                Box(
                                    modifier = Modifier
                                        .height(if (isLandscape) 22.dp else 20.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(fpsColor.copy(alpha = 0.14f))
                                        .border(0.8.dp, fpsColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Speed,
                                            contentDescription = "FPS",
                                            tint = fpsColor,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "$displayFps FPS",
                                            color = fpsColor,
                                            fontSize = if (isLandscape) 9.sp else 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        // --- Dynamic Center Content ---
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(140)) },
                            label = "tabContent",
                            modifier = Modifier.weight(1f, fill = false)
                        ) { currentTab ->
                            when (currentTab) {
                                SidebarTab.BASIC -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 10.dp)
                                    ) {
                                        // Tachometers Row: 3 medidores circulares independientes (CPU %, GPU % y RAM %)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularMetricCard(
                                                title = "CPU",
                                                percentage = cpuPercent,
                                                badgeText = "${cpuTemp}°C",
                                                badgeColor = tertiary,
                                                glowColor = primary,
                                                isLandscape = isLandscape
                                            )
                                            CircularMetricCard(
                                                title = "GPU",
                                                percentage = gpuPercent,
                                                badgeText = "${gpuTemp}°C",
                                                badgeColor = secondary,
                                                glowColor = primary,
                                                isLandscape = isLandscape
                                            )
                                            CircularMetricCard(
                                                title = "RAM",
                                                percentage = ramPercent,
                                                badgeText = if (ramUsedMb >= 1024) String.format(java.util.Locale.US, "%.1f GB", ramUsedMb / 1024f) else "${ramUsedMb} MB",
                                                badgeColor = primary,
                                                glowColor = primary,
                                                isLandscape = isLandscape
                                            )
                                        }

                                        // Performance Mode Segmented Pills
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(surfaceLow.copy(alpha = 0.70f))
                                                .border(1.dp, outlineVariant.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                                                .padding(2.5.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf(
                                                0 to stringResource(R.string.mode_powersave),
                                                1 to stringResource(R.string.mode_balanced),
                                                2 to stringResource(R.string.mode_performance)
                                            ).forEach { (mode, label) ->
                                                val isSelected = performanceMode == mode
                                                val pillBg by animateColorAsState(
                                                    targetValue = if (isSelected) primary.copy(alpha = 0.30f) else Color.Transparent,
                                                    label = "pillBg"
                                                )
                                                val pillBorder by animateColorAsState(
                                                    targetValue = if (isSelected) primary else Color.Transparent,
                                                    label = "pillBorder"
                                                )

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(if (isLandscape) 25.dp else 27.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(pillBg)
                                                        .border(1.dp, pillBorder, RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            performanceMode = mode
                                                            scope.launch {
                                                                val modeStr = when (mode) {
                                                                    0 -> "powersave"
                                                                    2 -> "performance"
                                                                    else -> "balanced"
                                                                }
                                                                GamingActionsHelper.setPerformanceMode(
                                                                    context,
                                                                    modeStr,
                                                                    pkg = activeGamePkg,
                                                                    persist = true
                                                                )
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = label,
                                                        color = if (isSelected) primary else onSurfaceVariant,
                                                        fontSize = if (isLandscape) 9.5.sp else 9.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }

                                        // Action Tiles Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            HighBoostActionTile(
                                                icon = Icons.Rounded.ContentCut,
                                                label = stringResource(R.string.action_screenshot),
                                                isActive = false,
                                                onClick = {
                                                    scope.launch {
                                                        onExpandedChanged(false)
                                                        GamingActionsHelper.takeScreenshot(context)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            HighBoostActionTile(
                                                icon = Icons.Rounded.Videocam,
                                                label = if (isRecording) stringResource(R.string.action_recording) else stringResource(R.string.action_record),
                                                isActive = isRecording,
                                                onClick = {
                                                    scope.launch {
                                                        if (!isRecording) {
                                                            onExpandedChanged(false)
                                                        }
                                                        GamingActionsHelper.recordScreen(context)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            HighBoostActionTile(
                                                icon = Icons.Rounded.Wifi,
                                                label = if (isWifiOn) stringResource(R.string.action_wifi_on) else stringResource(R.string.action_wifi_off),
                                                isActive = isWifiOn,
                                                onClick = {
                                                    scope.launch {
                                                        isWifiOn = GamingActionsHelper.toggleWifi(context)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            HighBoostActionTile(
                                                icon = Icons.Rounded.CleaningServices,
                                                label = if (isCleaningRam) stringResource(R.string.action_cleaning_ram) else if (ramLabel.isNotEmpty()) ramLabel else stringResource(R.string.action_clean_ram),
                                                isActive = isCleaningRam,
                                                onClick = {
                                                    if (!isCleaningRam) {
                                                        scope.launch {
                                                            isCleaningRam = true
                                                            ramLabel = "..."
                                                            val freed = GamingActionsHelper.cleanRam(context)
                                                            ramLabel = "+${freed}MB"
                                                            delay(2000)
                                                            isCleaningRam = false
                                                            ramLabel = ""
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }


                                SidebarTab.PERFORMANCE -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = if (isLandscape) 4.dp else 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            HighBoostActionTile(
                                                icon = Icons.Rounded.NetworkCheck,
                                                label = if (isPingTurboOn) stringResource(R.string.action_ping_turbo_active, ping.toInt()) else stringResource(R.string.action_ping_turbo_normal),
                                                isActive = isPingTurboOn,
                                                onClick = {
                                                    scope.launch {
                                                        isPingTurboOn = GamingActionsHelper.toggleLowLatency(context)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            // HUD Toggle Tile — Si está activo, permite alternar con clic largo o muestra estilo Pro/Mini
                                            val isProHud = hudStyle == HudStyle.ADVANCED
                                            val hudTileLabel = when {
                                                !isHudOn -> stringResource(R.string.action_hud_hidden)
                                                isProHud -> stringResource(R.string.action_hud_pro_active)
                                                else     -> stringResource(R.string.action_hud_mini_active)
                                            }
                                            HighBoostActionTile(
                                                icon = Icons.Rounded.Speed,
                                                label = hudTileLabel,
                                                isActive = isHudOn,
                                                onClick = {
                                                    if (!isHudOn) {
                                                        isHudOn = GamingActionsHelper.toggleHud(context, monitor)
                                                    } else {
                                                        // Si ya está activo, al tocar cambia de HUD Mini a HUD Pro (o se apaga si se prefiere)
                                                        if (hudStyle == HudStyle.CLASSIC) {
                                                            HorizontalHudOverlay.setHudStyle(HudStyle.ADVANCED, context)
                                                        } else {
                                                            isHudOn = GamingActionsHelper.toggleHud(context, monitor)
                                                            HorizontalHudOverlay.setHudStyle(HudStyle.CLASSIC, context)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            HighBoostActionTile(
                                                icon = Icons.Rounded.Memory,
                                                label = stringResource(R.string.action_cpu_governor),
                                                isActive = performanceMode == 2,
                                                onClick = {
                                                    performanceMode = if (performanceMode == 2) 1 else 2
                                                    scope.launch {
                                                        GamingActionsHelper.setPerformanceMode(
                                                            context,
                                                            if (performanceMode == 2) "performance" else "balanced",
                                                            pkg = activeGamePkg,
                                                            persist = true
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        // Segunda fila: Configuración avanzada del HUD flotante
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            HighBoostActionTile(
                                                icon = if (isTransparentBg) Icons.Rounded.LayersClear else Icons.Rounded.Layers,
                                                label = if (isTransparentBg) stringResource(R.string.action_hud_transparent) else stringResource(R.string.action_hud_solid),
                                                isActive = isTransparentBg,
                                                onClick = {
                                                    HorizontalHudOverlay.toggleTransparentBackground(context)
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            HighBoostActionTile(
                                                icon = if (isTouchThrough) Icons.Rounded.TouchApp else Icons.Rounded.PanTool,
                                                label = if (isTouchThrough) stringResource(R.string.action_hud_touch_through) else stringResource(R.string.action_hud_touchable),
                                                isActive = isTouchThrough,
                                                onClick = {
                                                    HorizontalHudOverlay.toggleTouchThrough(context)
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }

                                SidebarTab.ANTI_INTERFERENCE -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = if (isLandscape) 8.dp else 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            HighBoostActionTile(
                                                icon = Icons.Rounded.DoNotDisturbOn,
                                                label = if (isDndOn) stringResource(R.string.action_dnd_on) else stringResource(R.string.action_dnd_off),
                                                isActive = isDndOn,
                                                onClick = {
                                                    scope.launch {
                                                        isDndOn = GamingActionsHelper.toggleDnd(context)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            HighBoostActionTile(
                                                icon = Icons.Rounded.Shield,
                                                label = if (isTouchShieldOn) stringResource(R.string.action_touch_shield_on) else stringResource(R.string.action_touch_shield_off),
                                                isActive = isTouchShieldOn,
                                                onClick = {
                                                    scope.launch {
                                                        isTouchShieldOn = GamingActionsHelper.toggleTouchShield(context)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            HighBoostActionTile(
                                                icon = Icons.Rounded.NetworkCheck,
                                                label = if (isPingTurboOn) stringResource(R.string.action_ping_turbo_on) else stringResource(R.string.action_ping_turbo_off),
                                                isActive = isPingTurboOn,
                                                onClick = {
                                                    scope.launch {
                                                        isPingTurboOn = GamingActionsHelper.toggleLowLatency(context)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            HighBoostActionTile(
                                                icon = Icons.Rounded.NightlightRound,
                                                label = if (isAfkOn) stringResource(R.string.action_afk_on) else stringResource(R.string.action_afk_off),
                                                isActive = isAfkOn,
                                                onClick = {
                                                    scope.launch {
                                                        onExpandedChanged(false)
                                                        isAfkOn = GamingActionsHelper.toggleAfkMode(context)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                SidebarTab.TOOLS -> {
                                    var showTimerSubmenu by remember { mutableStateOf(false) }

                                    if (showTimerSubmenu) {
                                        // --- Tactical Timer Presets Sub-menu ---
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Rounded.Timer,
                                                        contentDescription = null,
                                                        tint = primary,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = stringResource(R.string.timer_presets_title),
                                                        color = primary,
                                                        fontSize = if (isLandscape) 11.5.sp else 10.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    if (TacticalTimerOverlay.isShowing()) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
                                                                .clickable {
                                                                    TacticalTimerOverlay.hide()
                                                                }
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.btn_stop),
                                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(surfaceHigh.copy(alpha = 0.7f))
                                                            .clickable { showTimerSubmenu = false }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                        Text(
                                                            text = stringResource(R.string.btn_back),
                                                            color = onSurfaceVariant,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                            }

                                            // Grid of Preset Buttons
                                            val timerPresets = listOf(
                                                Triple(10, "10s", stringResource(R.string.preset_flash)),
                                                Triple(15, "15s", stringResource(R.string.preset_ability)),
                                                Triple(30, "30s", stringResource(R.string.preset_respawn)),
                                                Triple(45, "45s", stringResource(R.string.preset_ultimate)),
                                                Triple(60, "60s", stringResource(R.string.preset_buff)),
                                                Triple(90, "90s", stringResource(R.string.preset_zone)),
                                                Triple(120, "120s", stringResource(R.string.preset_boss)),
                                                Triple(180, "180s", stringResource(R.string.preset_airdrop))
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                timerPresets.take(4).forEach { (seconds, timeLabel, desc) ->
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(surfaceHigh.copy(alpha = 0.7f))
                                                            .border(1.dp, outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                showTimerSubmenu = false
                                                                onExpandedChanged(false)
                                                                TacticalTimerOverlay.startTimer(context, seconds, desc)
                                                            }
                                                            .padding(vertical = 4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Text(
                                                                text = timeLabel,
                                                                color = primary,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Black
                                                            )
                                                            Text(
                                                                text = desc,
                                                                color = onSurfaceVariant,
                                                                fontSize = 7.sp,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                timerPresets.drop(4).take(4).forEach { (seconds, timeLabel, desc) ->
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(surfaceHigh.copy(alpha = 0.7f))
                                                            .border(1.dp, outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                showTimerSubmenu = false
                                                                onExpandedChanged(false)
                                                                TacticalTimerOverlay.startTimer(context, seconds, desc)
                                                            }
                                                            .padding(vertical = 4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Text(
                                                                text = timeLabel,
                                                                color = secondary,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Black
                                                            )
                                                            Text(
                                                                text = desc,
                                                                color = onSurfaceVariant,
                                                                fontSize = 7.sp,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = if (isLandscape) 4.dp else 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                HighBoostActionTile(
                                                    icon = Icons.Rounded.CenterFocusStrong,
                                                    label = if (isCrosshairOn) stringResource(R.string.action_crosshair_on) else stringResource(R.string.action_crosshair_off),
                                                    isActive = isCrosshairOn,
                                                    onClick = {
                                                        isCrosshairOn = GamingActionsHelper.toggleCrosshair(context)
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                HighBoostActionTile(
                                                    icon = Icons.Rounded.AspectRatio,
                                                    label = stringResource(R.string.action_crosshair_calibrate),
                                                    isActive = false,
                                                    onClick = {
                                                        showCrosshairSettings = true
                                                        onExpandedChanged(true)
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                HighBoostActionTile(
                                                    icon = Icons.Rounded.Timer,
                                                    label = if (TacticalTimerOverlay.isShowing()) stringResource(R.string.action_timer_active) else stringResource(R.string.action_timer_tactical),
                                                    isActive = TacticalTimerOverlay.isShowing(),
                                                    onClick = {
                                                        showTimerSubmenu = true
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                HighBoostActionTile(
                                                    icon = Icons.Rounded.NightlightRound,
                                                    label = if (isAfkOn) stringResource(R.string.action_afk_on) else stringResource(R.string.action_afk_off),
                                                    isActive = isAfkOn,
                                                    onClick = {
                                                        scope.launch {
                                                            onExpandedChanged(false)
                                                            isAfkOn = GamingActionsHelper.toggleAfkMode(context)
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // --- Dual Audio Mixer & Brightness Control Section ---
                        val gameVol by AudioMixerHelper.gameVolume.collectAsState()
                        val isGameMuted by AudioMixerHelper.isGameMuted.collectAsState()
                        val voiceVol by AudioMixerHelper.voiceVolume.collectAsState()
                        val isVoiceMuted by AudioMixerHelper.isVoiceMuted.collectAsState()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(surfaceLow.copy(alpha = 0.70f))
                                .border(1.dp, outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = if (isLandscape) 5.dp else 7.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 3.dp else 5.dp)
                        ) {
                            if (isLandscape) {
                                // Landscape: Row 1 has Game + Voice audio, Row 2 has Brightness
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MikuSmoothSlider(
                                        value = gameVol,
                                        onValueChange = { AudioMixerHelper.setGameVolume(context, it) },
                                        icon = Icons.Rounded.SportsEsports,
                                        iconTint = if (isGameMuted) onSurfaceVariant.copy(alpha = 0.4f) else primary,
                                        accentColor = primary,
                                        onIconClick = { AudioMixerHelper.toggleGameMute(context) },
                                        modifier = Modifier.weight(1f)
                                    )

                                    MikuSmoothSlider(
                                        value = voiceVol,
                                        onValueChange = { AudioMixerHelper.setVoiceVolume(context, it) },
                                        icon = Icons.Rounded.Mic,
                                        iconTint = if (isVoiceMuted) onSurfaceVariant.copy(alpha = 0.4f) else secondary,
                                        accentColor = secondary,
                                        onIconClick = { AudioMixerHelper.toggleVoiceMute(context) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MikuSmoothSlider(
                                        value = currentBrightness,
                                        onValueChange = { newBright ->
                                            currentBrightness = newBright
                                            BrightnessHelper.setBrightness(context, newBright)
                                        },
                                        icon = Icons.Rounded.LightMode,
                                        iconTint = tertiary,
                                        accentColor = tertiary,
                                        valueRange = 0.02f..1f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            } else {
                                MikuSmoothSlider(
                                    value = gameVol,
                                    onValueChange = { AudioMixerHelper.setGameVolume(context, it) },
                                    icon = Icons.Rounded.SportsEsports,
                                    iconTint = if (isGameMuted) onSurfaceVariant.copy(alpha = 0.4f) else primary,
                                    accentColor = primary,
                                    onIconClick = { AudioMixerHelper.toggleGameMute(context) },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                MikuSmoothSlider(
                                    value = voiceVol,
                                    onValueChange = { AudioMixerHelper.setVoiceVolume(context, it) },
                                    icon = Icons.Rounded.Mic,
                                    iconTint = if (isVoiceMuted) onSurfaceVariant.copy(alpha = 0.4f) else secondary,
                                    accentColor = secondary,
                                    onIconClick = { AudioMixerHelper.toggleVoiceMute(context) },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                MikuSmoothSlider(
                                    value = currentBrightness,
                                    onValueChange = { newBright ->
                                        currentBrightness = newBright
                                        BrightnessHelper.setBrightness(context, newBright)
                                    },
                                    icon = Icons.Rounded.LightMode,
                                    iconTint = tertiary,
                                    accentColor = tertiary,
                                    valueRange = 0.02f..1f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // --- Floating Slide-to-Music Arrow Indicator on the edge ---
                val infiniteTransition = rememberInfiniteTransition(label = "musicSlidePulse")
                val arrowPulse by infiniteTransition.animateFloat(
                    initialValue = 0.55f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "arrowPulse"
                )

                Box(
                    modifier = Modifier
                        .align(if (isSnappedRight) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(end = if (isSnappedRight) 0.dp else 2.dp, start = if (isSnappedRight) 2.dp else 0.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(surfaceHigh.copy(alpha = 0.85f))
                        .border(1.dp, primary.copy(alpha = 0.65f * arrowPulse), RoundedCornerShape(8.dp))
                        .clickable { isMusicPageOpen = true }
                        .padding(horizontal = 3.5.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (isSnappedRight) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,
                            contentDescription = "Deslizar para Música",
                            tint = primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
    }
}
}

        // Crosshair Settings Modal
        if (showCrosshairSettings) {
            com.miku.gamingsidebar.ui.components.CrosshairSettingsDialog(
                onDismiss = {
                    showCrosshairSettings = false
                    onExpandedChanged(false)
                }
            )
        }
    }
}

/**
 * Ultra-sleek, zero-overhead custom slider built specifically for floating game overlays.
 * Prevents Compose Material3 Slider touch-box collisions and rendering glitches.
 */
@Composable
fun MikuSmoothSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    icon: ImageVector,
    iconTint: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onIconClick: (() -> Unit)? = null
) {
    val rangeSpan = valueRange.endInclusive - valueRange.start
    val normalizedValue = if (rangeSpan > 0f) {
        ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
    } else 0f

    Row(
        modifier = modifier.height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .size(13.dp)
                .then(
                    if (onIconClick != null) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onIconClick
                    ) else Modifier
                )
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .pointerInput(rangeSpan, valueRange.start, valueRange.endInclusive) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val trackW = size.width.toFloat()
                            if (trackW > 0f && rangeSpan > 0f) {
                                val ratio = (offset.x / trackW).coerceIn(0f, 1f)
                                val newVal = valueRange.start + (ratio * rangeSpan)
                                onValueChange(newVal.coerceIn(valueRange.start, valueRange.endInclusive))
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val trackW = size.width.toFloat()
                            if (trackW > 0f && rangeSpan > 0f) {
                                val ratio = (change.position.x / trackW).coerceIn(0f, 1f)
                                val newVal = valueRange.start + (ratio * rangeSpan)
                                onValueChange(newVal.coerceIn(valueRange.start, valueRange.endInclusive))
                            }
                        }
                    )
                }
                .pointerInput(rangeSpan, valueRange.start, valueRange.endInclusive) {
                    detectTapGestures { offset ->
                        val trackW = size.width.toFloat()
                        if (trackW > 0f && rangeSpan > 0f) {
                            val ratio = (offset.x / trackW).coerceIn(0f, 1f)
                            val newVal = valueRange.start + (ratio * rangeSpan)
                            onValueChange(newVal.coerceIn(valueRange.start, valueRange.endInclusive))
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (size.width <= 0f || size.height <= 0f) return@Canvas

                val trackHeight = 4.5.dp.toPx()
                val thumbRadius = 5.5.dp.toPx()
                val yCenter = size.height / 2

                // 1. Inactive Track
                drawRoundRect(
                    color = Color(0x33FFFFFF),
                    topLeft = Offset(0f, yCenter - trackHeight / 2),
                    size = Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2, trackHeight / 2)
                )

                // 2. Active Track
                val activeWidth = (size.width * normalizedValue).coerceIn(0f, size.width)
                if (activeWidth > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(accentColor.copy(alpha = 0.75f), accentColor)
                        ),
                        topLeft = Offset(0f, yCenter - trackHeight / 2),
                        size = Size(activeWidth, trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2, trackHeight / 2)
                    )
                }

                // 3. Glowing Thumb (Guarded against size.width <= thumbRadius * 2)
                val maxThumbX = (size.width - thumbRadius).coerceAtLeast(thumbRadius)
                val thumbCenterX = if (size.width <= thumbRadius * 2) {
                    size.width / 2
                } else {
                    (size.width * normalizedValue).coerceIn(thumbRadius, maxThumbX)
                }

                // Halo
                drawCircle(
                    color = accentColor.copy(alpha = 0.35f),
                    radius = thumbRadius + 2.5.dp.toPx(),
                    center = Offset(thumbCenterX, yCenter)
                )
                // Solid Core
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(thumbCenterX, yCenter)
                )
                // Inner Ring
                drawCircle(
                    color = accentColor,
                    radius = (thumbRadius - 1.5.dp.toPx()).coerceAtLeast(1f),
                    center = Offset(thumbCenterX, yCenter)
                )
            }
        }
    }
}

@Composable
fun NeonTachometer(
    percent: Int,
    label: String,
    glowColor: Color,
    modifier: Modifier = Modifier,
    displayValue: String? = null,
    onClick: (() -> Unit)? = null
) {
    val animatedPercent by animateFloatAsState(
        targetValue = percent.coerceIn(0, 100).toFloat(),
        animationSpec = tween(durationMillis = 400),
        label = "tachoAnim"
    )

    Box(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ) else Modifier
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)
            val startAngle = 135f
            val totalSweep = 270f

            // 1. Background Track Arc
            drawArc(
                color = glowColor.copy(alpha = 0.18f),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Active Gradient Glow Arc
            val activeSweep = (animatedPercent / 100f) * totalSweep
            drawArc(
                brush = Brush.sweepGradient(
                    0f to glowColor.copy(alpha = 0.5f),
                    0.5f to glowColor,
                    1f to glowColor
                ),
                startAngle = startAngle,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth + 1.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = displayValue ?: "${percent}%",
                color = Color.White,
                fontSize = if ((displayValue?.length ?: 0) > 4) 10.sp else 12.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = label,
                color = glowColor,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun CircularMetricCard(
    title: String,
    percentage: Int,
    badgeText: String,
    badgeColor: Color,
    glowColor: Color = Color(0xFFFF80AB),
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val circleSize = if (isLandscape) 46.dp else 42.dp
    val strokeWidth = if (isLandscape) 3.5.dp else 3.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
    ) {
        // Tacómetro circular
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(circleSize)
        ) {
            // Fondo del arco
            CircularProgressIndicator(
                progress = { 1f },
                color = Color.White.copy(alpha = 0.1f),
                strokeWidth = strokeWidth,
                modifier = Modifier.fillMaxSize()
            )
            // Progreso real con acento temático
            CircularProgressIndicator(
                progress = { (percentage.coerceIn(0, 100)) / 100f },
                color = glowColor,
                strokeWidth = strokeWidth,
                modifier = Modifier.fillMaxSize()
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$percentage%",
                    fontSize = if (isLandscape) 11.sp else 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = title,
                    fontSize = if (isLandscape) 8.sp else 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Badge centrado debajo del tacómetro
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0x33000000),
            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f)),
            modifier = Modifier.padding(top = 1.dp)
        ) {
            Text(
                text = badgeText,
                fontSize = if (isLandscape) 9.sp else 8.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
fun HighBoostActionTile(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "tileScale"
    )

    val shape = remember { RoundedCornerShape(16.dp) }
    val bgBrush = if (isActive) {
        Brush.linearGradient(
            colors = listOf(primary, primaryContainer)
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(surfaceHigh.copy(alpha = 0.85f), surfaceContainer.copy(alpha = 0.95f))
        )
    }

    val borderStroke = if (isActive) {
        BorderStroke(1.2.dp, primary)
    } else {
        BorderStroke(1.dp, outlineVariant.copy(alpha = 0.35f))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(shape)
                .background(bgBrush)
                .border(borderStroke, shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color.White else onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isActive) primary else onSurfaceVariant,
            fontSize = 8.5.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 10.sp,
            maxLines = 2
        )
    }
}

@Composable
fun DynamicBatteryIndicator(
    batteryLevel: Int,
    isCharging: Boolean,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedLevel by animateFloatAsState(
        targetValue = (batteryLevel.coerceIn(0, 100)) / 100f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "batteryFill"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "chargingPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val batteryColor = when {
        isCharging -> Color(0xFF00E676)
        batteryLevel <= 15 -> Color(0xFFFF5252)
        batteryLevel <= 30 -> Color(0xFFFFD600)
        else -> Color(0xFF00E676)
    }

    Box(
        modifier = modifier
            .height(if (isLandscape) 22.dp else 20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(batteryColor.copy(alpha = 0.14f))
            .border(0.8.dp, batteryColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Animated Canvas Battery Icon
            Canvas(
                modifier = Modifier
                    .size(width = 15.dp, height = 8.5.dp)
                    .graphicsLayer(alpha = if (isCharging) pulseAlpha else 1f)
            ) {
                val strokeWidth = 1.1.dp.toPx()
                val bodyWidth = size.width - 2.5.dp.toPx()
                val bodyHeight = size.height
                val cornerRadius = CornerRadius(1.8.dp.toPx(), 1.8.dp.toPx())

                // Outer border
                drawRoundRect(
                    color = batteryColor,
                    topLeft = Offset.Zero,
                    size = Size(bodyWidth, bodyHeight),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = strokeWidth)
                )

                // Battery positive terminal nub on the right
                val nubWidth = 1.2.dp.toPx()
                val nubHeight = bodyHeight * 0.45f
                val nubY = (bodyHeight - nubHeight) / 2f
                drawRoundRect(
                    color = batteryColor,
                    topLeft = Offset(bodyWidth + 0.8.dp.toPx(), nubY),
                    size = Size(nubWidth, nubHeight),
                    cornerRadius = CornerRadius(0.8.dp.toPx(), 0.8.dp.toPx())
                )

                // Inner fill level
                val innerPad = strokeWidth + 0.6.dp.toPx()
                val maxFillWidth = (bodyWidth - (innerPad * 2)).coerceAtLeast(0f)
                val fillWidth = maxFillWidth * animatedLevel
                val fillHeight = (bodyHeight - (innerPad * 2)).coerceAtLeast(0f)

                if (fillWidth > 0f) {
                    drawRoundRect(
                        color = batteryColor,
                        topLeft = Offset(innerPad, innerPad),
                        size = Size(fillWidth, fillHeight),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                }
            }

            if (isCharging) {
                Icon(
                    imageVector = Icons.Rounded.ElectricBolt,
                    contentDescription = "Cargando",
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(9.dp)
                )
            }

            Text(
                text = "$batteryLevel%",
                color = batteryColor,
                fontSize = if (isLandscape) 9.sp else 8.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


