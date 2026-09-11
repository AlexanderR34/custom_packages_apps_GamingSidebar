package com.miku.gamingsidebar.ui.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.miku.gamingsidebar.data.GameModel
import com.miku.gamingsidebar.service.GamingActionsHelper
import com.miku.gamingsidebar.ui.theme.ElectricLime
import com.miku.gamingsidebar.ui.theme.ElectricPink
import com.miku.gamingsidebar.ui.theme.MikuCyan
import com.miku.gamingsidebar.ui.theme.OnSurfaceVariant
import com.miku.gamingsidebar.ui.theme.SurfaceContainerHigh
import com.miku.gamingsidebar.ui.theme.SurfaceDark
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameResolutionDialog(
    games: List<GameModel>,
    onDismissRequest: () -> Unit,
    onLaunchGame: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedGame by remember {
        mutableStateOf(games.firstOrNull())
    }
    var scalePercent by remember {
        mutableFloatStateOf(GamingActionsHelper.getGameResolutionScale() * 100f)
    }
    var isApplying by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp)),
            color = SurfaceDark,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MikuCyan.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AspectRatio,
                                contentDescription = null,
                                tint = MikuCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Resolución del Juego",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "Renderizado interno exclusivo",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Game Selector Section
                Text(
                    text = "SELECCIONAR JUEGO",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    ),
                    color = MikuCyan
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (games.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(games, key = { it.id }) { game ->
                            val isSelected = selectedGame?.id == game.id
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MikuCyan.copy(alpha = 0.2f) else SurfaceContainerHigh,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MikuCyan) else null,
                                modifier = Modifier.clickable { selectedGame = game }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (game.iconBitmap != null) {
                                        Image(
                                            bitmap = game.iconBitmap,
                                            contentDescription = game.name,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.VideogameAsset,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = game.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Explanation Note
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceContainerHigh.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = ElectricLime,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Afecta solo al render del juego seleccionado. La barra de estado, menús y el sistema Android se mantienen en resolución completa nativa.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Slider Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Escala de Renderizado",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )

                            Text(
                                text = "${scalePercent.roundToInt()}%",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                ),
                                color = if (scalePercent >= 90f) ElectricLime else if (scalePercent >= 60f) MikuCyan else ElectricPink
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Slider(
                            value = scalePercent,
                            onValueChange = { scalePercent = it },
                            valueRange = 20f..100f,
                            steps = 15,
                            colors = SliderDefaults.colors(
                                thumbColor = MikuCyan,
                                activeTrackColor = MikuCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Preset Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val presets = listOf(100f, 80f, 60f, 40f, 20f)
                            presets.forEach { preset ->
                                val isCurrentPreset = (scalePercent.roundToInt() == preset.roundToInt())
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCurrentPreset) MikuCyan else Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier.clickable { scalePercent = preset }
                                ) {
                                    Text(
                                        text = "${preset.roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isCurrentPreset) FontWeight.Black else FontWeight.Normal,
                                            fontSize = 11.sp
                                        ),
                                        color = if (isCurrentPreset) Color.Black else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scalePercent = 100f
                            val pkg = selectedGame?.packageName
                            scope.launch {
                                GamingActionsHelper.setGameResolutionScale(pkg, 1.0f)
                                Toast.makeText(context, "Resolución 100% restablecida", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reset",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Button(
                        onClick = {
                            val pkg = selectedGame?.packageName
                            val factor = scalePercent / 100f
                            isApplying = true
                            scope.launch {
                                val success = GamingActionsHelper.setGameResolutionScale(pkg, factor)
                                isApplying = false
                                Toast.makeText(
                                    context,
                                    "Escala del ${scalePercent.roundToInt()}% aplicada a ${selectedGame?.name ?: "Juego"}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onDismissRequest()
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MikuCyan,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Aplicar",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
