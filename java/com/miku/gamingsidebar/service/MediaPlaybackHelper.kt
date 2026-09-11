package com.miku.gamingsidebar.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object MediaPlaybackHelper {

    private val _trackInfo = MutableStateFlow(MediaTrackInfo())
    val trackInfo: StateFlow<MediaTrackInfo> = _trackInfo.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var activeController: MediaController? = null
    private var positionJob: Job? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val isPlay = state?.state == PlaybackState.STATE_PLAYING
            _isPlaying.value = isPlay
            val currentPos = calculateLivePosition(state)
            _trackInfo.value = _trackInfo.value.copy(
                isPlaying = isPlay,
                positionMs = currentPos
            )
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            activeController?.let { ctrl ->
                appContext?.let { ctx ->
                    extractMetadata(ctx, metadata, ctrl.packageName)
                }
            }
        }
    }

    private fun calculateLivePosition(state: PlaybackState?): Long {
        if (state == null) return 0L
        var pos = state.position
        if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0) {
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            if (elapsed > 0) {
                pos += (elapsed * state.playbackSpeed).toLong()
            }
        }
        return pos.coerceAtLeast(0L)
    }

    fun setController(controller: MediaController, context: Context) {
        appContext = context.applicationContext
        if (activeController != controller) {
            activeController?.unregisterCallback(controllerCallback)
            activeController = controller
            activeController?.registerCallback(controllerCallback)
        }

        extractMetadata(context, controller.metadata, controller.packageName)
        val isPlay = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        _isPlaying.value = isPlay
        val appLabel = getAppLabel(context, controller.packageName)
        val appIcon = getAppIcon(context, controller.packageName)
        val livePos = calculateLivePosition(controller.playbackState)

        _trackInfo.value = _trackInfo.value.copy(
            isPlaying = isPlay,
            appName = appLabel,
            appIcon = appIcon ?: _trackInfo.value.appIcon,
            packageName = controller.packageName,
            hasActiveSession = true,
            positionMs = livePos
        )
        startPositionTicker()
    }

    fun ensureNotificationPermissions(context: Context) {
        try {
            val pkg = context.packageName
            val service = "$pkg/$pkg.service.MikuNotificationListenerService"
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_NOTIFICATION_LISTENERS) ?: ""
            if (!enabled.contains(pkg)) {
                val newEnabled = if (enabled.isBlank()) service else "$enabled:$service"
                Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_NOTIFICATION_LISTENERS, newEnabled)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val componentName = ComponentName(context, MikuNotificationListenerService::class.java)
                android.service.notification.NotificationListenerService.requestRebind(componentName)
            }
        } catch (_: Exception) {}
    }

    private fun syncFromDumpsys(context: Context) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys media_session"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()
            process.destroy()
            if (output.isBlank()) return

            var currentPkg = ""
            var currentTitle = ""
            var currentArtist = ""
            var isPlaying = false
            var duration = 180_000L
            var position = 0L

            for (rawLine in output.lines()) {
                val trimmed = rawLine.trim()
                if (trimmed.contains("package=") && currentPkg.isEmpty()) {
                    val candidate = trimmed.substringAfter("package=").substringBefore(" ").substringBefore("}")
                    if (!candidate.contains("telecom") && !candidate.contains("system")) {
                        currentPkg = candidate
                    }
                }
                if (trimmed.contains("state=PLAYING") || trimmed.contains("state=3")) {
                    isPlaying = true
                } else if (trimmed.contains("state=PAUSED") || trimmed.contains("state=STOPPED")) {
                    isPlaying = false
                }
                if (trimmed.contains("description=")) {
                    val desc = trimmed.substringAfter("description=").trim()
                    val parts = desc.split(",")
                    if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                        currentTitle = parts[0].trim()
                    }
                    if (parts.size > 1 && parts[1].isNotBlank()) {
                        currentArtist = parts[1].trim()
                    }
                }
                if (trimmed.startsWith("title=") && currentTitle.isEmpty()) {
                    currentTitle = trimmed.substringAfter("title=").trim()
                }
                if ((trimmed.startsWith("artist=") || trimmed.startsWith("subtitle=")) && currentArtist.isEmpty()) {
                    currentArtist = trimmed.substringAfter("=").trim()
                }
                if (trimmed.startsWith("position=") && position == 0L) {
                    position = trimmed.substringAfter("position=").substringBefore(",").trim().toLongOrNull() ?: 0L
                }
                if (trimmed.startsWith("duration=") && duration == 180_000L) {
                    val d = trimmed.substringAfter("duration=").substringBefore(",").trim().toLongOrNull() ?: 0L
                    if (d > 0) duration = d
                }
                if (currentPkg.isNotBlank() && currentTitle.isNotBlank()) {
                    break
                }
            }

            if (currentTitle.isNotBlank() && currentPkg.isNotBlank()) {
                val appLabel = getAppLabel(context, currentPkg)
                val appIcon = getAppIcon(context, currentPkg)
                var art: Bitmap? = null
                try {
                    val notifs = MikuNotificationListenerService.instance?.activeNotifications
                    if (notifs != null) {
                        for (sbn in notifs) {
                            if (sbn.packageName == currentPkg) {
                                val notif = sbn.notification
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val icon = notif.getLargeIcon()
                                    val drawable = icon?.loadDrawable(context)
                                    if (drawable != null) art = drawableToBitmap(drawable)
                                }
                                if (art == null) {
                                    @Suppress("DEPRECATION")
                                    art = notif.extras.getParcelable(Notification.EXTRA_PICTURE)
                                        ?: notif.extras.getParcelable(Notification.EXTRA_LARGE_ICON)
                                }
                                break
                            }
                        }
                    }
                } catch (_: Exception) {}

                _isPlaying.value = isPlaying
                _trackInfo.value = _trackInfo.value.copy(
                    title = currentTitle,
                    artist = if (currentArtist.isNotBlank()) currentArtist else "Artista desconocido",
                    appName = appLabel,
                    appIcon = appIcon ?: _trackInfo.value.appIcon,
                    albumArt = art ?: _trackInfo.value.albumArt,
                    packageName = currentPkg,
                    isPlaying = isPlaying,
                    durationMs = duration,
                    positionMs = position,
                    hasActiveSession = true
                )
            }
        } catch (_: Exception) {}
    }

    fun syncState(context: Context) {
        appContext = context.applicationContext
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val musicActive = am?.isMusicActive ?: false

        ensureNotificationPermissions(context)

        MikuNotificationListenerService.instance?.scanActiveNotifications()
        tryFindActiveSession(context)

        if (activeController == null && !_trackInfo.value.hasActiveSession) {
            syncFromDumpsys(context)
        }

        if (activeController == null && !_trackInfo.value.hasActiveSession) {
            _isPlaying.value = musicActive
            _trackInfo.value = _trackInfo.value.copy(
                isPlaying = musicActive,
                title = if (musicActive) "Reproduciendo música" else "Música en pausa",
                artist = if (musicActive) "Dispositivo Android" else "Toca reproducir para iniciar",
                hasActiveSession = false
            )
        }
        startPositionTicker()
    }

    fun updateFromNotificationListener(context: Context) {
        appContext = context.applicationContext
        tryFindActiveSession(context)
    }

    fun onMediaNotificationRemoved(packageName: String) {
        if (activeController == null && _trackInfo.value.packageName == packageName) {
            _trackInfo.value = MediaTrackInfo()
            _isPlaying.value = false
        }
    }

    fun updateFromNotificationFallback(
        title: String,
        artist: String,
        albumArt: Bitmap?,
        packageName: String,
        context: Context
    ) {
        val lowerPkg = packageName.lowercase()
        if (lowerPkg.contains("whatsapp") || lowerPkg.contains("orca") || lowerPkg.contains("messenger") ||
            lowerPkg.contains("telegram") || lowerPkg.contains("discord") || lowerPkg.contains("facebook") ||
            lowerPkg.contains("instagram") || lowerPkg.contains("twitter") || lowerPkg.contains("x.android") ||
            lowerPkg.contains("gmail") || lowerPkg.contains("systemui") || lowerPkg.contains("android.dialer") ||
            lowerPkg.contains("google.android.apps.messaging")
        ) {
            return
        }
        appContext = context.applicationContext
        val appLabel = getAppLabel(context, packageName)
        val appIcon = getAppIcon(context, packageName)
        _isPlaying.value = true
        _trackInfo.value = _trackInfo.value.copy(
            title = title,
            artist = artist,
            albumArt = albumArt ?: _trackInfo.value.albumArt,
            appIcon = appIcon ?: _trackInfo.value.appIcon,
            appName = appLabel,
            packageName = packageName,
            isPlaying = true,
            hasActiveSession = true
        )
    }

    private fun tryFindActiveSession(context: Context) {
        try {
            val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            val componentName = ComponentName(context, MikuNotificationListenerService::class.java)

            var controllers: List<MediaController> = emptyList()
            try {
                controllers = mediaSessionManager.getActiveSessions(componentName)
            } catch (e: SecurityException) {
                ensureNotificationPermissions(context)
                try {
                    controllers = mediaSessionManager.getActiveSessions(componentName)
                } catch (_: Exception) {}
            }

            if (controllers.isNotEmpty()) {
                val newController = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                    ?: controllers.first()
                setController(newController, context)
            } else {
                MikuNotificationListenerService.instance?.scanActiveNotifications()
            }
        } catch (e: Exception) {
            // General fallback
        }
    }

    private fun extractMetadata(context: Context, metadata: MediaMetadata?, packageName: String) {
        if (metadata == null) {
            try {
                val notifs = MikuNotificationListenerService.instance?.activeNotifications
                if (notifs != null) {
                    for (sbn in notifs) {
                        if (sbn.packageName == packageName) {
                            val notif = sbn.notification
                            val extras = notif.extras
                            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                                ?: extras.getCharSequence("android.title")?.toString()
                            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                            var art: Bitmap? = null
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val icon = notif.getLargeIcon()
                                val drawable = icon?.loadDrawable(context)
                                if (drawable != null) art = drawableToBitmap(drawable)
                            }
                            if (art == null) {
                                @Suppress("DEPRECATION")
                                art = extras.getParcelable(Notification.EXTRA_PICTURE)
                                    ?: extras.getParcelable(Notification.EXTRA_LARGE_ICON)
                            }
                            if (!title.isNullOrBlank()) {
                                val appLabel = getAppLabel(context, packageName)
                                val appIcon = getAppIcon(context, packageName)
                                _trackInfo.value = _trackInfo.value.copy(
                                    title = title,
                                    artist = text ?: "Artista desconocido",
                                    albumArt = art ?: _trackInfo.value.albumArt,
                                    appIcon = appIcon ?: _trackInfo.value.appIcon,
                                    appName = appLabel,
                                    packageName = packageName,
                                    hasActiveSession = true
                                )
                                return
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            return
        }
        val desc = metadata.description
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: desc?.title?.toString()
            ?: "Pista de audio"
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: desc?.subtitle?.toString()
            ?: "Artista desconocido"
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).let {
            if (it > 0) it else 180_000L
        }
        var art: Bitmap? = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: desc?.iconBitmap

        if (art == null) {
            val artUri = desc?.iconUri
                ?: (metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI))?.let {
                    try { Uri.parse(it) } catch (_: Exception) { null }
                }

            if (artUri != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, artUri)
                        art = ImageDecoder.decodeBitmap(source)
                    } else {
                        @Suppress("DEPRECATION")
                        art = MediaStore.Images.Media.getBitmap(context.contentResolver, artUri)
                    }
                } catch (e: Exception) {
                    try {
                        context.contentResolver.openInputStream(artUri)?.use { stream ->
                            art = BitmapFactory.decodeStream(stream)
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        if (art == null) {
            try {
                val notifs = MikuNotificationListenerService.instance?.activeNotifications
                if (notifs != null) {
                    for (sbn in notifs) {
                        if (sbn.packageName == packageName) {
                            val notif = sbn.notification
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val icon = notif.getLargeIcon()
                                val drawable = icon?.loadDrawable(context)
                                if (drawable != null) art = drawableToBitmap(drawable)
                            }
                            if (art == null) {
                                @Suppress("DEPRECATION")
                                art = notif.extras.getParcelable(Notification.EXTRA_PICTURE)
                                    ?: notif.extras.getParcelable(Notification.EXTRA_LARGE_ICON)
                            }
                            break
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val appLabel = getAppLabel(context, packageName)
        val appIcon = getAppIcon(context, packageName)

        _trackInfo.value = _trackInfo.value.copy(
            title = title,
            artist = artist,
            durationMs = duration,
            albumArt = art ?: _trackInfo.value.albumArt,
            appIcon = appIcon ?: _trackInfo.value.appIcon,
            appName = appLabel,
            packageName = packageName,
            hasActiveSession = true
        )
    }

    private fun getAppLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            "Música"
        }
    }

    private fun getAppIcon(context: Context, packageName: String): Bitmap? {
        if (packageName.isBlank()) return null
        return try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            drawableToBitmap(drawable)
        } catch (e: Exception) {
            null
        }
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 72
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 72
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }

    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                delay(500L)
                val current = _trackInfo.value
                val livePos = calculateLivePosition(activeController?.playbackState)
                if (activeController != null && livePos > 0) {
                    _trackInfo.value = current.copy(positionMs = livePos.coerceIn(0L, current.durationMs))
                } else if (_isPlaying.value) {
                    val nextPos = (current.positionMs + 500L).coerceAtMost(current.durationMs)
                    _trackInfo.value = current.copy(positionMs = nextPos)
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _trackInfo.value.durationMs)
        _trackInfo.value = _trackInfo.value.copy(positionMs = clamped)
        try {
            activeController?.transportControls?.seekTo(clamped)
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun toggleLike(context: Context): Boolean {
        val isNowFav = !_trackInfo.value.hasActiveSession || !_isPlaying.value
        // Try rating/action
        activeController?.let { ctrl ->
            try {
                ctrl.transportControls.setRating(Rating.newHeartRating(true))
            } catch (e: Exception) {
                try {
                    ctrl.transportControls.sendCustomAction("LIKE", null)
                } catch (ex: Exception) {}
            }
        }
        return true
    }

    private fun sendMediaKeyEvent(context: Context, keyCode: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val eventTime = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
        try {
            am.dispatchMediaKeyEvent(downEvent)
            am.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            try {
                Runtime.getRuntime().exec(arrayOf("input", "keyevent", keyCode.toString()))
            } catch (ex: Exception) {
                // Ignore
            }
        }
    }

    fun skipToPrevious(context: Context) {
        _trackInfo.value = _trackInfo.value.copy(positionMs = 0L)
        if (activeController != null) {
            try {
                activeController?.transportControls?.skipToPrevious()
                _isPlaying.value = true
                return
            } catch (e: Exception) {}
        }
        sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        _isPlaying.value = true
        _trackInfo.value = _trackInfo.value.copy(isPlaying = true)
    }

    fun togglePlayPause(context: Context) {
        if (activeController != null) {
            try {
                val isPlay = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
                if (isPlay) {
                    activeController?.transportControls?.pause()
                    _isPlaying.value = false
                    _trackInfo.value = _trackInfo.value.copy(isPlaying = false)
                } else {
                    activeController?.transportControls?.play()
                    _isPlaying.value = true
                    _trackInfo.value = _trackInfo.value.copy(isPlaying = true)
                }
                return
            } catch (e: Exception) {}
        }
        sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        val currentlyPlaying = _isPlaying.value
        _isPlaying.value = !currentlyPlaying
        _trackInfo.value = _trackInfo.value.copy(isPlaying = !currentlyPlaying)
    }

    fun skipToNext(context: Context) {
        _trackInfo.value = _trackInfo.value.copy(positionMs = 0L)
        if (activeController != null) {
            try {
                activeController?.transportControls?.skipToNext()
                _isPlaying.value = true
                return
            } catch (e: Exception) {}
        }
        sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
        _isPlaying.value = true
        _trackInfo.value = _trackInfo.value.copy(isPlaying = true)
    }
}
