package com.miku.gamingsidebar.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miku.gamingsidebar.R
import com.miku.gamingsidebar.service.GamingActionsHelper
import com.miku.gamingsidebar.service.HorizontalHudOverlay
import com.miku.gamingsidebar.service.HudStyle
import com.miku.gamingsidebar.ui.components.CrosshairSettingsDialog
import com.miku.gamingsidebar.util.LocaleHelper
import com.miku.gamingsidebar.viewmodel.HubViewModel
import kotlinx.coroutines.launch

@Composable
fun GamingSettingsScreen(
    viewModel: HubViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sidebarEnabled by viewModel.sidebarEnabled.collectAsState()
    val gameModeEnabled by viewModel.gameModeEnabled.collectAsState()
    val libraryGames by viewModel.libraryGames.collectAsState()
    val currentLang by viewModel.appLanguage.collectAsState()

    var showAddAppDialog by remember { mutableStateOf(false) }
    var showCrosshairSettings by remember { mutableStateOf(false) }
    var showLanguageSelector by remember { mutableStateOf(false) }
    var defaultPerformanceMode by remember {
        mutableStateOf(GamingActionsHelper.getSavedPerformanceMode(context))
    }

    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val surfaceLowest = MaterialTheme.colorScheme.surfaceContainerLowest
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ==========================================
            // 1. Header (Material 3 Expressive Title)
            // ==========================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_game_turbo_sidebar),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            ),
                            color = onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_game_turbo_sidebar_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant
                        )
                    }
                }
            }

            // ==========================================
            // 2. Master Toggle Hero Card (M3 Expressive)
            // ==========================================
            item {
                val heroCardShape = RoundedCornerShape(28.dp)
                val isEnabled = sidebarEnabled

                val heroBgBrush = if (isEnabled) {
                    Brush.linearGradient(
                        listOf(primary.copy(alpha = 0.22f), surfaceHigh.copy(alpha = 0.85f))
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(surfaceHigh.copy(alpha = 0.5f), surfaceContainer.copy(alpha = 0.7f))
                    )
                }

                val heroBorderStroke = if (isEnabled) {
                    BorderStroke(1.5.dp, primary.copy(alpha = 0.6f))
                } else {
                    BorderStroke(1.dp, outlineVariant.copy(alpha = 0.35f))
                }

                Card(
                    shape = heroCardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = heroBorderStroke,
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, heroCardShape, spotColor = if (isEnabled) primary.copy(alpha = 0.3f) else Color.Transparent)
                        .clip(heroCardShape)
                        .background(heroBgBrush)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isEnabled) primary else surfaceContainer)
                                    .border(
                                        1.dp,
                                        if (isEnabled) primary.copy(alpha = 0.8f) else outlineVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(16.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SportsEsports,
                                    contentDescription = null,
                                    tint = if (isEnabled) Color.White else onSurfaceVariant,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = if (isEnabled) "Game Turbo Activo" else "Game Turbo Desactivado",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isEnabled) "El overlay se desplegará al abrir juegos" else "No se mostrará la barra lateral en juegos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { viewModel.setSidebarEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = primary,
                                uncheckedThumbColor = onSurfaceVariant,
                                uncheckedTrackColor = surfaceLowest
                            )
                        )
                    }
                }
            }

            // ==========================================
            // 3. Managed Games Whitelist Section
            // ==========================================
            item {
                ExpressiveSectionHeader(
                    title = "JUEGOS Y APPS GESTIONADAS",
                    icon = Icons.Rounded.Gamepad
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceHigh.copy(alpha = 0.7f)),
                    border = BorderStroke(1.dp, outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${libraryGames.size} Juegos Reconocidos",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = onSurface
                                )
                                Text(
                                    text = "Detectados automáticamente por el sistema AOSP",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariant
                                )
                            }

                            FilledTonalButton(
                                onClick = { showAddAppDialog = true },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Añadir", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 4. Performance Profile Default (Hardware & Governor)
            // ==========================================
            item {
                ExpressiveSectionHeader(
                    title = "PERFILES DE HARDWARE Y CPU",
                    icon = Icons.Rounded.Speed
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceHigh.copy(alpha = 0.7f)),
                    border = BorderStroke(1.dp, outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Modo de Rendimiento Predeterminado",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = onSurface
                        )

                        // Segmented Mode Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(surfaceLowest)
                                .border(1.dp, outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "powersave" to "Ahorro",
                                "balanced" to "Equilibrado",
                                "performance" to "Turbo Governor"
                            ).forEach { (modeKey, label) ->
                                val isSelected = defaultPerformanceMode == modeKey
                                val itemBg by animateColorAsState(
                                    if (isSelected) primary.copy(alpha = 0.35f) else Color.Transparent,
                                    label = "perfBg"
                                )
                                val itemBorder by animateColorAsState(
                                    if (isSelected) primary else Color.Transparent,
                                    label = "perfBorder"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(itemBg)
                                        .border(1.dp, itemBorder, RoundedCornerShape(12.dp))
                                        .clickable {
                                            defaultPerformanceMode = modeKey
                                            scope.launch {
                                                GamingActionsHelper.setPerformanceMode(context, modeKey, persist = true)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) primary else onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 5. Tactical Tools & In-Game Visuals
            // ==========================================
            item {
                ExpressiveSectionHeader(
                    title = "HERRAMIENTAS TÁCTICAS Y PANTALLA",
                    icon = Icons.Rounded.CenterFocusStrong
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceHigh.copy(alpha = 0.7f)),
                    border = BorderStroke(1.dp, outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Crosshair Calibrate Button
                        ExpressiveActionRow(
                            icon = Icons.Rounded.CenterFocusStrong,
                            title = "Mira Virtual Personalizada",
                            subtitle = "Ajusta tamaño, forma y color de la retícula en pantalla",
                            onClick = { showCrosshairSettings = true }
                        )

                        // Bypass charging status indicator
                        ExpressiveActionRow(
                            icon = Icons.Rounded.Power,
                            title = "Carga Bypass de Batería",
                            subtitle = "Alimentación directa por cable para reducir calor al jugar",
                            onClick = {
                                scope.launch {
                                    GamingActionsHelper.toggleBypassCharging(context)
                                }
                            }
                        )
                    }
                }
            }

            // ==========================================
            // 6. System & Tile Quick Settings Info
            // ==========================================
            item {
                ExpressiveSectionHeader(
                    title = "INTEGRACIÓN DEL SISTEMA",
                    icon = Icons.Rounded.Security
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceHigh.copy(alpha = 0.7f)),
                    border = BorderStroke(1.dp, outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Puedes activar o pausar la barra Game Turbo desde el panel de Ajustes Rápidos (Quick Settings Tile).",
                                style = MaterialTheme.typography.bodySmall,
                                color = onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        ExpressiveActionRow(
                            icon = Icons.Rounded.Language,
                            title = "Idioma / Language",
                            subtitle = when (currentLang) {
                                "es" -> "🇪🇸 Español"
                                "en" -> "🇺🇸 English"
                                else -> "🌐 Sistema (Automático)"
                            },
                            onClick = { showLanguageSelector = !showLanguageSelector }
                        )

                        AnimatedVisibility(visible = showLanguageSelector) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(surfaceLowest)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "system" to "🌐 Predeterminado del Sistema",
                                    "es" to "🇪🇸 Español",
                                    "en" to "🇺🇸 English"
                                ).forEach { (langCode, langName) ->
                                    val isSelected = currentLang == langCode
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                viewModel.setAppLanguage(langCode)
                                                showLanguageSelector = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = langName,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) primary else onSurface
                                        )
                                        if (isSelected) {
                                            Icon(Icons.Rounded.Check, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modal Dialogs
    if (showAddAppDialog) {
        com.miku.gamingsidebar.ui.screens.AddCustomAppDialog(
            viewModel = viewModel,
            onDismiss = { showAddAppDialog = false }
        )
    }

    if (showCrosshairSettings) {
        CrosshairSettingsDialog(
            onDismiss = { showCrosshairSettings = false }
        )
    }
}

@Composable
private fun ExpressiveSectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ExpressiveActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "rowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
