package com.miku.gamingsidebar.ui.screens

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.miku.gamingsidebar.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataOutputStream

/**
 * Pantalla de Onboarding / Setup Inicial ("Permisos Requeridos").
 * Mantiene las barras del sistema visibles, permite conceder permisos mediante Intents directos,
 * y se auto-refresca de inmediato al volver de los menús de ajustes del sistema (Lifecycle Resume).
 */
@Composable
fun PermissionSetupScreen(
    onSetupCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    var hasOverlay by remember { mutableStateOf(checkOverlayPermission(context)) }
    var hasUsageStats by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var hasNotification by remember { mutableStateOf(checkNotificationPermission(context)) }
    var hasRoot by remember { mutableStateOf(false) }
    var hasBatteryOptIgnored by remember { mutableStateOf(checkBatteryOptimizationIgnored(context)) }
    var isCheckingRoot by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        hasOverlay = checkOverlayPermission(context)
        hasUsageStats = checkUsageStatsPermission(context)
        hasNotification = checkNotificationPermission(context)
        hasBatteryOptIgnored = checkBatteryOptimizationIgnored(context)
    }

    LaunchedEffect(Unit) {
        refreshPermissions()
        scope.launch(Dispatchers.IO) {
            val root = checkRootAccess()
            withContext(Dispatchers.Main) {
                hasRoot = root
            }
        }
    }

    // Auto-refrescar estado en ON_RESUME al volver de los menús de ajustes de Android
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotification = isGranted || checkNotificationPermission(context)
    }

    val allMandatoryGranted = hasOverlay && hasUsageStats && hasNotification

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF0A0F1D),
                        Color(0xFF050811)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header con Logo y Título
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(primary.copy(alpha = 0.85f), secondary.copy(alpha = 0.70f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SportsEsports,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(R.string.setup_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.setup_subtitle),
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Lista de Tarjetas de Permisos
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Sección A: Obligatorios
                item {
                    Text(
                        text = stringResource(R.string.setup_section_mandatory),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = primary,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                // 1. Superposición sobre otras apps
                item {
                    PermissionCard(
                        icon = Icons.Rounded.Layers,
                        title = stringResource(R.string.perm_overlay_title),
                        description = stringResource(R.string.perm_overlay_desc),
                        isGranted = hasOverlay,
                        isMandatory = true,
                        accentColor = Color(0xFF00E5FF),
                        onGrantClick = {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                                } catch (_: Exception) {}
                            }
                        }
                    )
                }

                // 2. Acceso a Datos de Uso
                item {
                    PermissionCard(
                        icon = Icons.Rounded.BarChart,
                        title = stringResource(R.string.perm_usage_title),
                        description = stringResource(R.string.perm_usage_desc),
                        isGranted = hasUsageStats,
                        isMandatory = true,
                        accentColor = Color(0xFF00E676),
                        onGrantClick = {
                            try {
                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                } catch (e2: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    )
                }

                // 3. Notificaciones en Primer Plano
                item {
                    PermissionCard(
                        icon = Icons.Rounded.NotificationsActive,
                        title = stringResource(R.string.perm_notif_title),
                        description = stringResource(R.string.perm_notif_desc),
                        isGranted = hasNotification,
                        isMandatory = true,
                        accentColor = Color(0xFFFFB74D),
                        onGrantClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (!hasNotification) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    try {
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                }
                            } else {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }
                    )
                }

                // Sección B: Opcionales / Avanzados
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.setup_section_optional),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.85f),
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }

                // 4. Acceso Root / KernelSU (Opcional)
                item {
                    PermissionCard(
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.perm_root_title),
                        description = stringResource(R.string.perm_root_desc),
                        isGranted = hasRoot,
                        isLoading = isCheckingRoot,
                        isMandatory = false,
                        accentColor = Color(0xFFFF4081),
                        actionButtonText = if (hasRoot) stringResource(R.string.setup_btn_granted) else stringResource(R.string.setup_btn_verify),
                        statusBadgeText = if (hasRoot) stringResource(R.string.perm_root_detected) else stringResource(R.string.perm_root_not_detected),
                        onGrantClick = {
                            if (!isCheckingRoot) {
                                scope.launch(Dispatchers.IO) {
                                    isCheckingRoot = true
                                    val root = checkRootAccess()
                                    withContext(Dispatchers.Main) {
                                        hasRoot = root
                                        isCheckingRoot = false
                                    }
                                }
                            }
                        }
                    )
                }

                // 5. Ignorar Optimización de Batería (Recomendado)
                item {
                    PermissionCard(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = stringResource(R.string.perm_battery_title),
                        description = stringResource(R.string.perm_battery_desc),
                        isGranted = hasBatteryOptIgnored,
                        isMandatory = false,
                        accentColor = Color(0xFF69F0AE),
                        onGrantClick = {
                            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                            val isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
                            if (!isIgnoring) {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(fallbackIntent)
                                    } catch (e2: Exception) {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                        } catch (_: Exception) {}
                                    }
                                }
                            } else {
                                try {
                                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(fallbackIntent)
                                } catch (e: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Botón Inferior de Avance
            val continueButtonBg by animateColorAsState(
                targetValue = if (allMandatoryGranted) primary else Color.White.copy(alpha = 0.12f),
                animationSpec = tween(250),
                label = "btnBg"
            )

            Button(
                onClick = {
                    if (allMandatoryGranted) {
                        onSetupCompleted()
                    }
                },
                enabled = allMandatoryGranted,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = continueButtonBg,
                    disabledContainerColor = Color.White.copy(alpha = 0.10f),
                    contentColor = Color.Black,
                    disabledContentColor = Color.White.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.setup_btn_continue),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isMandatory: Boolean,
    accentColor: Color,
    isLoading: Boolean = false,
    actionButtonText: String = stringResource(R.string.setup_btn_grant),
    statusBadgeText: String = stringResource(R.string.setup_btn_granted),
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = Color(0xFF1E293B).copy(alpha = 0.75f)
    val cardBorder = if (isGranted) Color(0xFF00E676).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.08f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
            .clickable { onGrantClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icono temático
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f))
                .border(1.dp, accentColor.copy(alpha = 0.40f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Textos
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (isMandatory) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "*",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 10.5.sp,
                color = Color.White.copy(alpha = 0.60f),
                lineHeight = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Botón o Badge de Estado
        if (isLoading) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = accentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else if (isGranted) {
            Surface(
                onClick = onGrantClick,
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF00E676).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.50f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusBadgeText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676)
                    )
                }
            }
        } else {
            Surface(
                onClick = onGrantClick,
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.20f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.60f))
            ) {
                Text(
                    text = actionButtonText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun checkOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

private fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun checkBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun checkRootAccess(): Boolean {
    return try {
        val process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)
        os.writeBytes("echo granted\nexit\n")
        os.flush()
        val exitCode = process.waitFor()
        exitCode == 0
    } catch (e: Exception) {
        false
    }
}
