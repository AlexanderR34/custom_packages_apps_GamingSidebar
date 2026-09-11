package com.miku.gamingsidebar.data

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.miku.gamingsidebar.ui.theme.CardGradientDefault

@Immutable
data class GameModel(
    val id: String,
    val name: String,
    val packageName: String,
    val icon: Drawable? = null,
    val iconBitmap: ImageBitmap? = null,
    val lastTimeUsed: Long = 0L,
    val totalTimeInForeground: Long = 0L,
    val relativeTimePlayed: String = "",
    val gradientColors: List<Color> = CardGradientDefault,
    val isRecentlyPlayed: Boolean = false,
    val isInstalled: Boolean = true
)

@Immutable
data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isAutoDetectedGame: Boolean = false,
    val isCustomAdded: Boolean = false,
    val isTurboEnabled: Boolean = false
)
