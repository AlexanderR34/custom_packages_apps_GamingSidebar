package com.miku.gamingsidebar.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/**
 * Android 13/14/15 Material You Full Music Controller Card
 * Includes live Album Art, Title, Artist, Device/App Pill, Squiggly Progress Waveform, and Controls.
 */
@Composable
fun AndroidMaterialYouPlayer(
    context: Context,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = true
) {
    val trackInfo by MediaPlaybackHelper.trackInfo.collectAsState()
    val isPlaying by MediaPlaybackHelper.isPlaying.collectAsState()

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val surfaceLowest = MaterialTheme.colorScheme.surfaceContainerLowest
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val durationMs = trackInfo.durationMs.coerceAtLeast(1000L)
    val positionMs = trackInfo.positionMs.coerceIn(0L, durationMs)
    val progressFraction = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(surfaceLowest)
    ) {
        // 1. Background Blurred Artwork with subtle, vibrant overlay
        if (trackInfo.albumArt != null) {
            Image(
                bitmap = trackInfo.albumArt!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(10.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                surfaceLowest.copy(alpha = 0.35f),
                                surfaceLowest.copy(alpha = 0.65f)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                primary.copy(alpha = 0.25f),
                                secondary.copy(alpha = 0.12f),
                                surfaceLowest
                            ),
                            radius = 650f
                        )
                    )
            )
        }

        // 2. Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isLandscape) 16.dp else 12.dp,
                    vertical = if (isLandscape) 10.dp else 8.dp
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- Top Bar: Back Arrow & App / Device Pill ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(0.8.dp, primary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable { onBackToDashboard() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Volver",
                        tint = primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Game Space",
                        color = Color.White,
                        fontSize = if (isLandscape) 10.sp else 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Output / App Pill with App Icon (Android 13/14 style)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(0.8.dp, primary.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .clickable {
                            if (!trackInfo.hasActiveSession) {
                                try {
                                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    if (trackInfo.appIcon != null) {
                        Image(
                            bitmap = trackInfo.appIcon!!.asImageBitmap(),
                            contentDescription = trackInfo.appName,
                            modifier = Modifier
                                .size(15.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = trackInfo.appName,
                        color = primary,
                        fontSize = if (isLandscape) 10.sp else 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // --- Middle Section: Album Art Thumbnail + Title & Artist ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Album Art Card
                Box(
                    modifier = Modifier
                        .size(if (isLandscape) 64.dp else 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(surfaceHigh)
                        .border(1.2.dp, primary.copy(alpha = 0.55f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (trackInfo.albumArt != null) {
                        Image(
                            bitmap = trackInfo.albumArt!!.asImageBitmap(),
                            contentDescription = "Carátula",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = primary.copy(alpha = 0.85f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Title & Artist Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = trackInfo.title,
                        color = Color.White,
                        fontSize = if (isLandscape) 14.5.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = trackInfo.artist,
                        color = onSurfaceVariant.copy(alpha = 0.95f),
                        fontSize = if (isLandscape) 11.5.sp else 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // --- Squiggly / Wavy Progress Bar (Android 13/14/15 Signature) ---
            Column(modifier = Modifier.fillMaxWidth()) {
                AndroidSquigglyProgressBar(
                    progress = progressFraction,
                    isPlaying = isPlaying,
                    accentColor = primary,
                    onSeek = { fraction ->
                        val targetMs = (fraction * durationMs).toLong()
                        MediaPlaybackHelper.seekTo(targetMs)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )

                // Time labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(positionMs),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatTime(durationMs),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // --- Bottom Controls Row: Previous, Play/Pause, Next ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Previous Track
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable { MediaPlaybackHelper.skipToPrevious(context) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 2. Play/Pause Button
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(primary)
                        .clickable { MediaPlaybackHelper.togglePlayPause(context) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = surfaceLowest,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // 3. Next Track
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable { MediaPlaybackHelper.skipToNext(context) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Siguiente",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Android 13/14 Signature Squiggly / Wavy Progress Bar
 */
@Composable
fun AndroidSquigglyProgressBar(
    progress: Float,
    isPlaying: Boolean,
    accentColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveShift")
    val waveShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveShiftAnim"
    )

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek(frac)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek(frac)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            if (width <= 0f) return@Canvas

            val strokeW = 3.dp.toPx()
            val thumbRadius = 5.dp.toPx()
            val playedWidth = (width * progress.coerceIn(0f, 1f))

            // 1. Inactive Flat Track (Unplayed)
            if (playedWidth < width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.28f),
                    start = Offset(playedWidth, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }

            // 2. Active Wavy/Squiggly Track (Played)
            if (playedWidth > 0f) {
                val waveLength = 22.dp.toPx()
                val waveAmplitude = 3.5.dp.toPx()
                val wavePath = Path()
                wavePath.moveTo(0f, centerY)

                var x = 0f
                val step = 2f
                while (x <= playedWidth) {
                    val phase = (x / waveLength * 2 * Math.PI) - (waveShift * 2 * Math.PI)
                    val y = centerY + (sin(phase) * waveAmplitude).toFloat()
                    wavePath.lineTo(x, y)
                    x += step
                }
                wavePath.lineTo(playedWidth, centerY)

                drawPath(
                    path = wavePath,
                    color = accentColor,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }

            // 3. Thumb Indicator Dot at current progress
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(playedWidth.coerceIn(0f, width), centerY)
            )
            drawCircle(
                color = accentColor,
                radius = thumbRadius * 0.5f,
                center = Offset(playedWidth.coerceIn(0f, width), centerY)
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
