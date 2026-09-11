package com.miku.gamingsidebar.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.miku.gamingsidebar.data.GameRepository
import com.miku.gamingsidebar.ui.theme.ElectricLime
import com.miku.gamingsidebar.ui.theme.ElectricPink
import com.miku.gamingsidebar.ui.theme.MikuCyan
import com.miku.gamingsidebar.ui.theme.OnSurfaceVariant
import com.miku.gamingsidebar.ui.theme.SurfaceContainer
import com.miku.gamingsidebar.ui.theme.SurfaceContainerHigh
import com.miku.gamingsidebar.ui.theme.SurfaceDark

@Composable
fun GameStatsDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { GameRepository(context) }
    var selectedPeriod by remember { mutableStateOf(GameRepository.StatsPeriod.WEEKLY) }
    var statsList by remember { mutableStateOf(emptyList<GameRepository.GameUsageStat>()) }

    LaunchedEffect(selectedPeriod) {
        statsList = repository.getGameUsageStats(selectedPeriod)
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(26.dp),
            color = SurfaceDark.copy(alpha = 0.96f),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, MikuCyan.copy(alpha = 0.45f)),
            shadowElevation = 20.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MikuCyan.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BarChart,
                                contentDescription = "Estadísticas",
                                tint = MikuCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Estadísticas de Uso",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "Tiempo de juego registrado",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = OnSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Period Selector Tabs (Día, Semana, Mes)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceContainerHigh)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PeriodTabItem(
                        title = "Día",
                        isSelected = selectedPeriod == GameRepository.StatsPeriod.DAILY,
                        onClick = { selectedPeriod = GameRepository.StatsPeriod.DAILY },
                        modifier = Modifier.weight(1f)
                    )
                    PeriodTabItem(
                        title = "Semana",
                        isSelected = selectedPeriod == GameRepository.StatsPeriod.WEEKLY,
                        onClick = { selectedPeriod = GameRepository.StatsPeriod.WEEKLY },
                        modifier = Modifier.weight(1f)
                    )
                    PeriodTabItem(
                        title = "Mes",
                        isSelected = selectedPeriod == GameRepository.StatsPeriod.MONTHLY,
                        onClick = { selectedPeriod = GameRepository.StatsPeriod.MONTHLY },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Summary Overview Card
                val totalTimeMs = statsList.sumOf { it.totalTimeMs }
                val formattedTotal = repository.formatDuration(totalTimeMs)

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "TIEMPO TOTAL JUGADO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = OnSurfaceVariant
                            )
                            Text(
                                text = formattedTotal,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                ),
                                color = ElectricLime
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(ElectricLime.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "${statsList.size} juegos activos",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = ElectricLime
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Games Ranked List
                AnimatedContent(
                    targetState = statsList,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "statsListAnim"
                ) { list ->
                    if (list.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sin datos de uso para este período",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(list) { index, item ->
                                GameStatRow(rank = index + 1, stat = item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodTabItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) MikuCyan.copy(alpha = 0.25f) else Color.Transparent
            )
            .border(
                1.dp,
                if (isSelected) MikuCyan.copy(alpha = 0.6f) else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isSelected) MikuCyan else OnSurfaceVariant
        )
    }
}

@Composable
private fun GameStatRow(
    rank: Int,
    stat: GameRepository.GameUsageStat
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceContainerHigh.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Rank Badge
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when (rank) {
                                    1 -> MikuCyan.copy(alpha = 0.25f)
                                    2 -> ElectricLime.copy(alpha = 0.25f)
                                    3 -> Color(0xFFFFB74D).copy(alpha = 0.25f)
                                    else -> Color.White.copy(alpha = 0.08f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$rank",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            ),
                            color = when (rank) {
                                1 -> MikuCyan
                                2 -> ElectricLime
                                3 -> Color(0xFFFFB74D)
                                else -> OnSurfaceVariant
                            }
                        )
                    }

                    Text(
                        text = stat.appName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = stat.formattedTime,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = if (stat.totalTimeMs > 0) MikuCyan else OnSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Percentage Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(stat.percentage)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(MikuCyan, ElectricLime)
                            )
                        )
                )
            }
        }
    }
}
