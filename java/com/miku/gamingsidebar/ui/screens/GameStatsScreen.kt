package com.miku.gamingsidebar.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miku.gamingsidebar.R
import com.miku.gamingsidebar.data.GameRepository

@Composable
fun GameStatsScreen(
    onBack: () -> Unit,
    onLaunchGame: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { GameRepository(context) }
    var selectedPeriod by remember { mutableStateOf(GameRepository.StatsPeriod.WEEKLY) }
    var statsList by remember { mutableStateOf(emptyList<GameRepository.GameUsageStat>()) }

    BackHandler { onBack() }

    LaunchedEffect(selectedPeriod) {
        val stats = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            repository.getGameUsageStats(selectedPeriod)
        }
        statsList = stats
    }

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
                @Suppress("DEPRECATION")
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.btn_back),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = stringResource(R.string.stats_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.stats_subtitle),
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
            // Period Selector Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        stringResource(R.string.stats_today) to GameRepository.StatsPeriod.DAILY,
                        stringResource(R.string.stats_week) to GameRepository.StatsPeriod.WEEKLY,
                        stringResource(R.string.stats_month) to GameRepository.StatsPeriod.MONTHLY
                    ).forEach { (label, period) ->
                        val isSelected = selectedPeriod == period
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { selectedPeriod = period },
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Overview Card
            item {
                val totalTimeMs = statsList.sumOf { it.totalTimeMs }
                val formattedTotal = repository.formatDuration(totalTimeMs)

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.stats_total_time),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formattedTotal,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 28.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.stats_games_count, statsList.size),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Ranking Header
            item {
                Text(
                    text = stringResource(R.string.stats_breakdown),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Games List
            if (statsList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.stats_no_records),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(statsList, key = { _, item -> item.packageName }) { index, stat ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rank Number
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (index) {
                                            0 -> MaterialTheme.colorScheme.primaryContainer
                                            1 -> MaterialTheme.colorScheme.secondaryContainer
                                            2 -> MaterialTheme.colorScheme.tertiaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                    color = when (index) {
                                        0 -> MaterialTheme.colorScheme.onPrimaryContainer
                                        1 -> MaterialTheme.colorScheme.onSecondaryContainer
                                        2 -> MaterialTheme.colorScheme.onTertiaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Game Info & Bar
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stat.appName,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stat.formattedTime,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Progress Bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(stat.percentage.coerceIn(0.04f, 1f))
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        if (index == 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                                                    )
                                                )
                                            )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Play Button
                            FilledIconButton(
                                onClick = { onLaunchGame(stat.packageName) },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.play),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
