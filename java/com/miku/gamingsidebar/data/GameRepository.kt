package com.miku.gamingsidebar.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.miku.gamingsidebar.R
import com.miku.gamingsidebar.ui.theme.CardGradientFreeFire
import com.miku.gamingsidebar.ui.theme.CardGradientGrowCastle
import com.miku.gamingsidebar.ui.theme.CardGradientMinecraft
import com.miku.gamingsidebar.ui.theme.CardGradientMindustry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class GameRepository(private val context: Context) {

    private fun getAppIconDrawable(packageManager: PackageManager, app: ApplicationInfo): android.graphics.drawable.Drawable? {
        iconDrawableCache[app.packageName]?.let { return it }

        val loaded = try {
            packageManager.getApplicationIcon(app.packageName)
        } catch (_: Exception) {
            try {
                app.loadIcon(packageManager)
            } catch (_: Exception) {
                try {
                    val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        packageManager.getActivityIcon(intent)
                    } else null
                } catch (_: Exception) {
                    try {
                        packageManager.getDefaultActivityIcon()
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }

        if (loaded != null) {
            iconDrawableCache[app.packageName] = loaded
        }
        return loaded
    }

    private fun drawableToImageBitmap(pkg: String, drawable: android.graphics.drawable.Drawable?): ImageBitmap? {
        if (drawable == null) return null
        iconBitmapCache[pkg]?.let { return it }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is android.graphics.drawable.AdaptiveIconDrawable) {
                val size = 256
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                // Adaptive icons have 25% outer margin for system parallax (108dp canvas with 72dp active icon).
                // Scaling by 1.38x expands the actual game icon to fill the squircle container seamlessly!
                val scale = 1.38f
                val offset = (size * (scale - 1f)) / 2f

                drawable.background?.let { bg ->
                    canvas.save()
                    canvas.translate(-offset, -offset)
                    canvas.scale(scale, scale)
                    bg.setBounds(0, 0, size, size)
                    bg.draw(canvas)
                    canvas.restore()
                }

                drawable.foreground?.let { fg ->
                    canvas.save()
                    canvas.translate(-offset, -offset)
                    canvas.scale(scale, scale)
                    fg.setBounds(0, 0, size, size)
                    fg.draw(canvas)
                    canvas.restore()
                }

                val imageBitmap = bitmap.asImageBitmap()
                iconBitmapCache[pkg] = imageBitmap
                return imageBitmap
            }

            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceIn(64, 256) else 128
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceIn(64, 256) else 128
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            val imageBitmap = bitmap.asImageBitmap()
            iconBitmapCache[pkg] = imageBitmap
            imageBitmap
        } catch (e: Exception) {
            try {
                val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, 128, 128)
                drawable.draw(canvas)
                val imageBitmap = bitmap.asImageBitmap()
                iconBitmapCache[pkg] = imageBitmap
                imageBitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    fun hasUsageStatsPermission(): Boolean {
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

    fun isLikelyGameRaw(pkg: String, appName: String, category: Int, flags: Int): Boolean {
        val lowerPkg = pkg.lowercase()
        val lowerName = appName.lowercase()

        // 1. Strict blacklist for system overlays, frameworks, stores, and non-game utility apps
        if (lowerPkg.contains("overlay") ||
            lowerPkg.startsWith("android.") ||
            lowerPkg.startsWith("com.android.") ||
            lowerPkg.startsWith("com.google.android.") ||
            lowerPkg == "com.nintendo.znca" || // Nintendo Switch Online (Companion app)
            lowerPkg == "com.nintendo.znej" || // Nintendo Store
            lowerPkg == "com.google.android.play.games" || // Play Games hub
            lowerPkg.contains("databackup") ||
            lowerPkg.contains("hma_oss") ||
            lowerPkg.contains("devcheck") ||
            lowerPkg.contains("zarchiver") ||
            lowerPkg.contains("pixellab") ||
            lowerPkg.contains("ibispaint") ||
            lowerPkg.contains("wppenhacer") ||
            lowerPkg.contains("photo.editor") ||
            lowerPkg.contains("quickedit") ||
            lowerPkg.contains("steam") ||
            lowerPkg == "com.miku.gamingsidebar"
        ) {
            return false
        }

        // 2. Official Android Game Category
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && category == ApplicationInfo.CATEGORY_GAME) {
            return true
        }
        if (flags and ApplicationInfo.FLAG_IS_GAME != 0) {
            return true
        }

        // 3. Known game keywords
        val gameKeywords = listOf(
            "geode", "roblox", "geometry", "jump", "growcastle", "castle", "mindustry",
            "freefire", "minecraft", "brawlstars", "supercell", "otrnext", "dogbyte",
            "universaltruck", "pcsimulator", "pccreator", "rucoy", "infinitode", "tdi2",
            "n-player", "mupen"
        )

        return gameKeywords.any { lowerPkg.contains(it) || lowerName.contains(it) }
    }

    fun isLikelyGame(pkg: String, appName: String, category: Int, flags: Int): Boolean {
        if (getExcludedGamePackages().contains(pkg)) {
            return false
        }
        if (getCustomGamePackages().contains(pkg)) {
            return true
        }
        return isLikelyGameRaw(pkg, appName, category, flags)
    }

    fun getCustomGamePackages(): Set<String> {
        val prefs = context.getSharedPreferences("miku_custom_games", Context.MODE_PRIVATE)
        return prefs.getStringSet("custom_packages", emptySet()) ?: emptySet()
    }

    fun getExcludedGamePackages(): Set<String> {
        val prefs = context.getSharedPreferences("miku_custom_games", Context.MODE_PRIVATE)
        return prefs.getStringSet("excluded_packages", emptySet()) ?: emptySet()
    }

    fun addCustomGame(pkg: String) {
        val prefs = context.getSharedPreferences("miku_custom_games", Context.MODE_PRIVATE)
        val current = prefs.getStringSet("custom_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(pkg)
        val excluded = prefs.getStringSet("excluded_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
        excluded.remove(pkg)
        prefs.edit()
            .putStringSet("custom_packages", current)
            .putStringSet("excluded_packages", excluded)
            .apply()
        cachedGamePackages = (cachedGamePackages ?: emptySet()) + pkg
    }

    fun removeCustomGame(pkg: String) {
        val prefs = context.getSharedPreferences("miku_custom_games", Context.MODE_PRIVATE)
        val current = prefs.getStringSet("custom_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(pkg)
        prefs.edit().putStringSet("custom_packages", current).apply()
        cachedGamePackages = (cachedGamePackages ?: emptySet()) - pkg
    }

    fun toggleAppTurbo(pkg: String): Boolean {
        val prefs = context.getSharedPreferences("miku_custom_games", Context.MODE_PRIVATE)
        val custom = prefs.getStringSet("custom_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
        val excluded = prefs.getStringSet("excluded_packages", emptySet())?.toMutableSet() ?: mutableSetOf()

        val appInfo = try {
            context.packageManager.getApplicationInfo(pkg, 0)
        } catch (_: Exception) {
            null
        }
        val appName = if (appInfo != null) context.packageManager.getApplicationLabel(appInfo).toString() else ""
        val isAutoRaw = if (appInfo != null) isLikelyGameRaw(pkg, appName, appInfo.category, appInfo.flags) else false

        val currentlyActive = custom.contains(pkg) || (isAutoRaw && !excluded.contains(pkg))
        val willBeActive = !currentlyActive

        if (willBeActive) {
            excluded.remove(pkg)
            if (!isAutoRaw) {
                custom.add(pkg)
            }
        } else {
            custom.remove(pkg)
            if (isAutoRaw) {
                excluded.add(pkg)
            }
        }

        prefs.edit()
            .putStringSet("custom_packages", custom)
            .putStringSet("excluded_packages", excluded)
            .apply()

        cachedGamePackages = if (willBeActive) {
            (cachedGamePackages ?: emptySet()) + pkg
        } else {
            (cachedGamePackages ?: emptySet()) - pkg
        }
        return willBeActive
    }

    fun toggleCustomGame(pkg: String): Boolean {
        return toggleAppTurbo(pkg)
    }

    fun getAllInstalledApps(): List<InstalledAppItem> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            packageManager.queryIntentActivities(intent, 0)
        }

        val customGames = getCustomGamePackages()
        val excludedGames = getExcludedGamePackages()
        val result = mutableListOf<InstalledAppItem>()
        val seen = mutableSetOf<String>()

        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == context.packageName || seen.contains(pkg)) continue
            seen.add(pkg)

            val appName = resolveInfo.loadLabel(packageManager).toString()
            val icon = resolveInfo.loadIcon(packageManager)
            val appInfo = resolveInfo.activityInfo.applicationInfo

            val isAutoGame = isLikelyGameRaw(pkg, appName, appInfo.category, appInfo.flags)
            val isCustom = customGames.contains(pkg)
            val isTurboEnabled = isCustom || (isAutoGame && !excludedGames.contains(pkg))

            result.add(
                InstalledAppItem(
                    packageName = pkg,
                    appName = appName,
                    icon = icon,
                    isAutoDetectedGame = isAutoGame,
                    isCustomAdded = isCustom,
                    isTurboEnabled = isTurboEnabled
                )
            )
        }

        return result.sortedWith(
            compareByDescending<InstalledAppItem> { it.isTurboEnabled }
                .thenBy { it.appName.lowercase() }
        )
    }

    fun getInstalledGames(): List<GameModel> {
        val packageManager = context.packageManager
        val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            packageManager.getInstalledApplications(0)
        }

        val usageMap = getUsageStatsMap()
        val games = mutableListOf<GameModel>()

        for (app in installedApps) {
            val appName = packageManager.getApplicationLabel(app).toString()
            val isGame = isLikelyGame(app.packageName, appName, app.category, app.flags)

            if (isGame) {
                val icon = getAppIconDrawable(packageManager, app)
                val iconBmp = drawableToImageBitmap(app.packageName, icon)

                val usage = usageMap[app.packageName]
                val lastUsed = usage?.lastTimeUsed ?: 0L
                val foregroundTime = usage?.totalTimeInForeground ?: 0L

                games.add(
                    GameModel(
                        id = app.packageName,
                        name = appName,
                        packageName = app.packageName,
                        icon = icon,
                        iconBitmap = iconBmp,
                        lastTimeUsed = lastUsed,
                        totalTimeInForeground = foregroundTime,
                        relativeTimePlayed = formatRelativeTime(lastUsed),
                        gradientColors = getGradientForGame(appName, app.packageName),
                        isRecentlyPlayed = lastUsed > 0L,
                        isInstalled = true
                    )
                )
            }
        }

        val packageNames = games.map { it.packageName }.toSet()
        cachedGamePackages = packageNames
        return games
    }

    private fun getUsageStatsMap(): Map<String, UsageStats> {
        if (!hasUsageStatsPermission()) return emptyMap()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -30) // Query last 30 days
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            cal.timeInMillis,
            System.currentTimeMillis()
        )

        val map = mutableMapOf<String, UsageStats>()
        for (usage in stats) {
            val existing = map[usage.packageName]
            if (existing == null || usage.lastTimeUsed > existing.lastTimeUsed) {
                map[usage.packageName] = usage
            }
        }
        return map
    }

    fun launchGame(packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0L) return context.getString(R.string.time_unplayed)

        val now = System.currentTimeMillis()
        val diffMillis = now - timestamp

        if (diffMillis < 0) return context.getString(R.string.time_just_now)

        val minutes = diffMillis / (1000 * 60)
        val hours = diffMillis / (1000 * 60 * 60)

        val calNow = Calendar.getInstance().apply { timeInMillis = now }
        val calTime = Calendar.getInstance().apply { timeInMillis = timestamp }

        val isToday = calNow.get(Calendar.YEAR) == calTime.get(Calendar.YEAR) &&
                calNow.get(Calendar.DAY_OF_YEAR) == calTime.get(Calendar.DAY_OF_YEAR)

        calNow.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterday = calNow.get(Calendar.YEAR) == calTime.get(Calendar.YEAR) &&
                calNow.get(Calendar.DAY_OF_YEAR) == calTime.get(Calendar.DAY_OF_YEAR)

        return when {
            minutes < 1 -> context.getString(R.string.time_just_now)
            minutes < 60 && isToday -> context.getString(R.string.time_mins_ago, minutes)
            isToday -> if (hours == 1L) context.getString(R.string.time_one_hour_ago) else context.getString(R.string.time_hours_ago, hours)
            isYesterday -> context.getString(R.string.time_yesterday)
            else -> {
                val days = (diffMillis / (1000 * 60 * 60 * 24)).coerceAtLeast(2)
                if (days in 2..7) {
                    context.getString(R.string.time_days_ago, days)
                } else {
                    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    formatter.format(Date(timestamp))
                }
            }
        }
    }

    private fun getGradientForGame(name: String, pkg: String): List<Color> {
        val lower = name.lowercase()
        return when {
            lower.contains("grow") || lower.contains("castle") -> CardGradientGrowCastle
            lower.contains("free fire") || lower.contains("fire") -> CardGradientFreeFire
            lower.contains("mine") || lower.contains("craft") -> CardGradientMinecraft
            lower.contains("mindustry") -> CardGradientMindustry
            else -> {
                val hash = abs((name + pkg).hashCode())
                val hue1 = (hash % 360).toFloat()
                val color1 = Color.hsl(hue1, 0.45f, 0.22f)
                val color2 = Color.hsl(hue1, 0.45f, 0.10f)
                listOf(color1, color2)
            }
        }
    }

    enum class StatsPeriod {
        DAILY, WEEKLY, MONTHLY
    }

    @androidx.compose.runtime.Immutable
    data class GameUsageStat(
        val packageName: String,
        val appName: String,
        val totalTimeMs: Long,
        val formattedTime: String,
        val percentage: Float,
        val icon: android.graphics.drawable.Drawable?
    )

    fun getGameUsageStats(period: StatsPeriod): List<GameUsageStat> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val now = System.currentTimeMillis()

        val centralAmericaZone = java.time.ZoneId.of("America/Managua")

        val startTime = when (period) {
            StatsPeriod.DAILY -> {
                // Exact start of today in Central America (00:00:00.000)
                java.time.ZonedDateTime.now(centralAmericaZone)
                    .with(java.time.LocalTime.MIN)
                    .toInstant()
                    .toEpochMilli()
            }
            StatsPeriod.WEEKLY -> {
                // 7 days ago at 00:00:00 in Central America
                java.time.ZonedDateTime.now(centralAmericaZone)
                    .minusDays(7)
                    .with(java.time.LocalTime.MIN)
                    .toInstant()
                    .toEpochMilli()
            }
            StatsPeriod.MONTHLY -> {
                // 30 days ago at 00:00:00 in Central America
                java.time.ZonedDateTime.now(centralAmericaZone)
                    .minusDays(30)
                    .with(java.time.LocalTime.MIN)
                    .toInstant()
                    .toEpochMilli()
            }
        }

        val timeMap = mutableMapOf<String, Long>()

        if (hasUsageStatsPermission() && usageStatsManager != null) {
            if (period == StatsPeriod.DAILY) {
                // For Today: compute exact foreground sessions using UsageEvents
                try {
                    val events = usageStatsManager.queryEvents(startTime, now)
                    val event = UsageEvents.Event()
                    val resumeTimes = mutableMapOf<String, Long>()

                    while (events.hasNextEvent()) {
                        events.getNextEvent(event)
                        val pkg = event.packageName ?: continue
                        when (event.eventType) {
                            UsageEvents.Event.ACTIVITY_RESUMED, 1 -> { // 1 = MOVE_TO_FOREGROUND
                                resumeTimes[pkg] = event.timeStamp.coerceAtLeast(startTime)
                            }
                            UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED, 2 -> { // 2 = MOVE_TO_BACKGROUND
                                val start = resumeTimes.remove(pkg)
                                if (start != null && event.timeStamp > start) {
                                    val sessionDuration = event.timeStamp - start
                                    timeMap[pkg] = (timeMap[pkg] ?: 0L) + sessionDuration
                                }
                            }
                        }
                    }

                    // If a game is still running right now in foreground
                    for ((pkg, start) in resumeTimes) {
                        if (now > start) {
                            timeMap[pkg] = (timeMap[pkg] ?: 0L) + (now - start)
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to queryUsageStats
                    val stats = usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        startTime,
                        now
                    ) ?: emptyList()
                    for (u in stats) {
                        if (u.lastTimeUsed >= startTime) {
                            timeMap[u.packageName] = (timeMap[u.packageName] ?: 0L) + u.totalTimeInForeground
                        }
                    }
                }
            } else {
                // Weekly / Monthly: aggregate usage stats for the requested window
                val interval = when (period) {
                    StatsPeriod.WEEKLY -> UsageStatsManager.INTERVAL_WEEKLY
                    StatsPeriod.MONTHLY -> UsageStatsManager.INTERVAL_MONTHLY
                    else -> UsageStatsManager.INTERVAL_DAILY
                }
                val stats = usageStatsManager.queryUsageStats(
                    interval,
                    startTime,
                    now
                ) ?: emptyList()

                for (u in stats) {
                    if (u.lastTimeUsed >= startTime) {
                        timeMap[u.packageName] = (timeMap[u.packageName] ?: 0L) + u.totalTimeInForeground
                    }
                }
            }
        }

        val allGames = getInstalledGames()
        val list = mutableListOf<GameUsageStat>()

        for (game in allGames) {
            val time = timeMap[game.packageName] ?: 0L
            list.add(
                GameUsageStat(
                    packageName = game.packageName,
                    appName = game.name,
                    totalTimeMs = time,
                    formattedTime = formatDuration(time),
                    percentage = 0f,
                    icon = game.icon
                )
            )
        }

        val maxTime = list.maxOfOrNull { it.totalTimeMs } ?: 1L
        val effectiveMax = if (maxTime > 0) maxTime else 1L

        return list.map { stat ->
            stat.copy(
                percentage = if (stat.totalTimeMs > 0) {
                    (stat.totalTimeMs.toFloat() / effectiveMax.toFloat()).coerceIn(0f, 1f)
                } else 0f
            )
        }.sortedByDescending { it.totalTimeMs }
    }

    fun formatDuration(timeMs: Long): String {
        if (timeMs <= 0) return "0 min"
        val totalMinutes = timeMs / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    fun isGame(packageName: String): Boolean {
        return isGamePackage(context, packageName)
    }

    fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    companion object {
        private val iconBitmapCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()
        private val iconDrawableCache = java.util.concurrent.ConcurrentHashMap<String, android.graphics.drawable.Drawable>()

        @Volatile
        var cachedGamePackages: Set<String>? = null

        fun isGamePackage(context: Context, pkg: String): Boolean {
            val lowerPkg = pkg.lowercase()

            if (lowerPkg == "com.android.systemui" ||
                lowerPkg.contains("launcher") ||
                lowerPkg.contains("home") ||
                lowerPkg.contains("quickstep") ||
                lowerPkg.contains("trebuchet") ||
                lowerPkg.contains("lawnchair") ||
                lowerPkg.startsWith("android") ||
                lowerPkg.startsWith("com.android.") ||
                lowerPkg.startsWith("com.google.android.") ||
                lowerPkg == "com.miku.gamingsidebar"
            ) {
                return false
            }

            val prefs = context.getSharedPreferences("miku_custom_games", Context.MODE_PRIVATE)
            val excluded = prefs.getStringSet("excluded_packages", emptySet()) ?: emptySet()
            if (excluded.contains(pkg)) return false

            val customGames = prefs.getStringSet("custom_packages", emptySet()) ?: emptySet()
            if (customGames.contains(pkg)) return true

            cachedGamePackages?.let {
                if (it.contains(pkg)) return true
            }

            val gameKeywords = listOf(
                "game", "geode", "roblox", "geometry", "jump", "growcastle", "castle", "mindustry",
                "freefire", "minecraft", "brawlstars", "supercell", "otrnext", "dogbyte",
                "universaltruck", "pcsimulator", "pccreator", "rucoy", "infinitode", "tdi2",
                "n-player", "mupen", "unity", "unreal"
            )
            if (gameKeywords.any { lowerPkg.contains(it) }) return true

            return try {
                val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                    true
                } else {
                    appInfo.flags and ApplicationInfo.FLAG_IS_GAME != 0
                }
            } catch (e: Exception) {
                false
            }
        }
    }
}


