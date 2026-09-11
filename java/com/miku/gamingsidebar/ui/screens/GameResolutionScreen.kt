package com.miku.gamingsidebar.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miku.gamingsidebar.R
import com.miku.gamingsidebar.data.GameModel
import com.miku.gamingsidebar.service.GamingActionsHelper
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class ResolutionProfileType(
    val titleRes: Int,
    val subtitleRes: Int,
    val scaleFactor: Float,
    val icon: ImageVector
) {
    NATIVE(R.string.res_profile_native, R.string.res_profile_native_desc, 1.0f, Icons.Rounded.Diamond),
    BALANCED(R.string.res_profile_balanced, R.string.res_profile_balanced_desc, 0.70f, Icons.Rounded.Bolt),
    PERFORMANCE(R.string.res_profile_performance, R.string.res_profile_performance_desc, 0.50f, Icons.Rounded.RocketLaunch),
    ULTRA_SAVER(R.string.res_profile_saver, R.string.res_profile_saver_desc, 0.35f, Icons.Rounded.BatteryChargingFull),
    CUSTOM(R.string.res_profile_custom, R.string.res_profile_custom_desc, 0.80f, Icons.Rounded.Tune)
}

@Composable
fun GameResolutionScreen(
    games: List<GameModel>,
    onBack: () -> Unit,
    onLaunchGame: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler { onBack() }

    var selectedGame by remember {
        mutableStateOf(games.firstOrNull())
    }

    var selectedProfile by remember {
        mutableStateOf(ResolutionProfileType.BALANCED)
    }

    var customScalePercent by remember {
        mutableFloatStateOf(70f)
    }

    var selectedFpsLimit by remember {
        mutableIntStateOf(0) // 0 = default / disabled
    }

    var isApplying by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onBack,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.btn_back),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = stringResource(R.string.res_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.res_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Game Selection Header & Carousel
            item {
                Text(
                    text = stringResource(R.string.res_step_select_game),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (games.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(games, key = { it.id }) { game ->
                            val isSelected = selectedGame?.id == game.id
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .clickable { selectedGame = game }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (game.iconBitmap != null) {
                                        Image(
                                            bitmap = game.iconBitmap,
                                            contentDescription = game.name,
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.VideogameAsset,
                                            contentDescription = null,
                                            modifier = Modifier.size(30.dp),
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = game.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = game.packageName.takeLast(18),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Privilege Status & Non-Root / Root Banner
            item {
                val isPrivileged = com.miku.gamingsidebar.data.RootHelper.isPrivilegeAvailable()
                val privilegeType = com.miku.gamingsidebar.data.RootHelper.getPrivilegeType()
                val isShizukuRunning = com.miku.gamingsidebar.data.RootHelper.isShizukuInstalledAndRunning()

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = if (isPrivileged) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isPrivileged) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isPrivileged) Icons.Rounded.Check else Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isPrivileged) stringResource(R.string.res_privilege_active, privilegeType) else stringResource(R.string.res_privilege_inactive),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isPrivileged) {
                                stringResource(R.string.res_privilege_active_desc, selectedGame?.name ?: "")
                            } else {
                                stringResource(R.string.res_privilege_inactive_desc)
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!isPrivileged) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (isShizukuRunning) {
                                    Button(
                                        onClick = {
                                            com.miku.gamingsidebar.data.RootHelper.requestShizukuPermission()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(stringResource(R.string.res_btn_auth_shizuku), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                                if (intent != null) {
                                                    context.startActivity(intent)
                                                } else {
                                                    val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://shizuku.rikka.app/download/"))
                                                    webIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    context.startActivity(webIntent)
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, context.getString(R.string.res_toast_open_shizuku), Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(stringResource(R.string.res_btn_config_shizuku), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Profiles Section
            item {
                Text(
                    text = stringResource(R.string.res_step_select_profile),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ResolutionProfileType.values().forEach { profile ->
                        val isSelected = selectedProfile == profile
                        val profileAccent = when (profile) {
                            ResolutionProfileType.NATIVE -> MaterialTheme.colorScheme.primary
                            ResolutionProfileType.BALANCED -> MaterialTheme.colorScheme.secondary
                            ResolutionProfileType.PERFORMANCE -> MaterialTheme.colorScheme.tertiary
                            ResolutionProfileType.ULTRA_SAVER -> MaterialTheme.colorScheme.error
                            ResolutionProfileType.CUSTOM -> MaterialTheme.colorScheme.onSurface
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProfile = profile
                                    if (profile != ResolutionProfileType.CUSTOM) {
                                        customScalePercent = profile.scaleFactor * 100f
                                    }
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) profileAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, profileAccent) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(profileAccent.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = profile.icon,
                                        contentDescription = null,
                                        tint = profileAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(profile.titleRes),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                    )
                                    Text(
                                        text = stringResource(profile.subtitleRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = profileAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Custom Slider
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.AspectRatio,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.res_fine_tune_scale),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = "${customScalePercent.roundToInt()}%",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Slider(
                            value = customScalePercent,
                            onValueChange = {
                                customScalePercent = it
                                selectedProfile = ResolutionProfileType.CUSTOM
                            },
                            valueRange = 30f..100f,
                            steps = 13,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Quick Preset Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(100f, 85f, 70f, 50f, 35f).forEach { preset ->
                                val isCur = (customScalePercent.roundToInt() == preset.roundToInt())
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isCur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.clickable {
                                        customScalePercent = preset
                                        selectedProfile = ResolutionProfileType.CUSTOM
                                    }
                                ) {
                                    Text(
                                        text = "${preset.roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isCur) FontWeight.Black else FontWeight.Medium,
                                            fontSize = 11.sp
                                        ),
                                        color = if (isCur) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Target FPS row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.res_fps_limit),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0 to stringResource(R.string.res_fps_off), 30 to "30", 60 to "60", 90 to "90", 120 to "120").forEach { (fpsVal, label) ->
                                    val isFpsCur = (selectedFpsLimit == fpsVal)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isFpsCur) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        border = if (isFpsCur) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier.clickable { selectedFpsLimit = fpsVal }
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isFpsCur) FontWeight.Black else FontWeight.Normal,
                                                fontSize = 11.sp
                                            ),
                                            color = if (isFpsCur) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Spacing for Floating Action Bar
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Bottom Action Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            ),
            tonalElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset Button
                FilledIconButton(
                    onClick = {
                        val pkg = selectedGame?.packageName
                        scope.launch {
                            GamingActionsHelper.resetResolutionScale(pkg)
                            customScalePercent = 100f
                            selectedProfile = ResolutionProfileType.NATIVE
                            Toast.makeText(context, context.getString(R.string.res_toast_reset), Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.res_btn_reset_100),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Apply Button
                Button(
                    onClick = {
                        val pkg = selectedGame?.packageName
                        val factor = customScalePercent / 100f
                        isApplying = true
                        scope.launch {
                            GamingActionsHelper.setGameResolutionScale(
                                packageName = pkg,
                                scaleFactor = factor,
                                fpsOverride = if (selectedFpsLimit > 0) selectedFpsLimit else null,
                                relaunch = false,
                                context = context
                            )
                            isApplying = false
                            Toast.makeText(
                                context,
                                context.getString(R.string.res_toast_applied, customScalePercent.roundToInt(), selectedGame?.name ?: "Game"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.res_btn_apply),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        maxLines = 1
                    )
                }

                // Apply and Play Button
                Button(
                    onClick = {
                        val pkg = selectedGame?.packageName
                        val factor = customScalePercent / 100f
                        scope.launch {
                            GamingActionsHelper.setGameResolutionScale(
                                packageName = pkg,
                                scaleFactor = factor,
                                fpsOverride = if (selectedFpsLimit > 0) selectedFpsLimit else null,
                                relaunch = true,
                                context = context
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1.35f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.res_btn_apply_play),
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
