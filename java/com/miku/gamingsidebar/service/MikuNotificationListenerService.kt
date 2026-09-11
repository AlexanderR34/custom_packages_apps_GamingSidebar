package com.miku.gamingsidebar.service

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

data class MediaTrackInfo(
    val title: String = "Sin reproducción activa",
    val artist: String = "Toca para reproducir música",
    val albumArt: Bitmap? = null,
    val appIcon: Bitmap? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 180_000L,
    val positionMs: Long = 0L,
    val appName: String = "Música",
    val packageName: String = "",
    val hasActiveSession: Boolean = false
)

class MikuNotificationListenerService : NotificationListenerService() {

    companion object {
        var instance: MikuNotificationListenerService? = null
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        MediaPlaybackHelper.updateFromNotificationListener(this)
        scanActiveNotifications()
    }

    fun scanActiveNotifications() {
        try {
            val activeNotifs = activeNotifications ?: return
            for (sbn in activeNotifs) {
                inspectMediaNotification(sbn)
            }
        } catch (_: Exception) {}
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        MediaPlaybackHelper.updateFromNotificationListener(this)
        inspectMediaNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.packageName?.let { pkg ->
            MediaPlaybackHelper.onMediaNotificationRemoved(pkg)
        }
        MediaPlaybackHelper.updateFromNotificationListener(this)
    }

    private fun inspectMediaNotification(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        // 1. Strictly ignore messaging, chat, social, and system notifications
        val pkg = sbn.packageName.lowercase()
        if (pkg.contains("whatsapp") || pkg.contains("orca") || pkg.contains("messenger") ||
            pkg.contains("telegram") || pkg.contains("discord") || pkg.contains("facebook") ||
            pkg.contains("instagram") || pkg.contains("twitter") || pkg.contains("x.android") ||
            pkg.contains("gmail") || pkg.contains("systemui") || pkg.contains("android.dialer") ||
            pkg.contains("google.android.apps.messaging")
        ) {
            return
        }

        // 2. Extract MediaSession.Token directly if present
        val token: android.media.session.MediaSession.Token? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, android.media.session.MediaSession.Token::class.java)
                ?: extras.getParcelable("android.mediaSession", android.media.session.MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            (extras.getParcelable<android.os.Parcelable>(Notification.EXTRA_MEDIA_SESSION)
                ?: extras.getParcelable<android.os.Parcelable>("android.mediaSession")) as? android.media.session.MediaSession.Token
        }

        if (token != null) {
            try {
                val controller = android.media.session.MediaController(this, token)
                MediaPlaybackHelper.setController(controller, this)
                return
            } catch (_: Exception) {}
        }

        // 3. Fallback: verify that this notification is actually a media or transport notification
        val isMedia = notif.category == Notification.CATEGORY_TRANSPORT ||
                extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
                extras.containsKey("android.mediaSession") ||
                extras.getCharSequence(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true

        if (!isMedia) {
            return
        }

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()
            ?: "Artista desconocido"

        if (!title.isNullOrBlank()) {
            val pkg = sbn.packageName
            var art: Bitmap? = null
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val icon = notif.getLargeIcon()
                    if (icon != null) {
                        val drawable = icon.loadDrawable(this)
                        if (drawable != null) {
                            art = MediaPlaybackHelper.drawableToBitmap(drawable)
                        }
                    }
                }
                if (art == null) {
                    @Suppress("DEPRECATION")
                    art = extras.getParcelable(Notification.EXTRA_PICTURE)
                        ?: extras.getParcelable(Notification.EXTRA_LARGE_ICON)
                }
            } catch (_: Exception) {}

            MediaPlaybackHelper.updateFromNotificationFallback(
                title = title,
                artist = text,
                albumArt = art,
                packageName = pkg,
                context = this
            )
        }
    }
}
