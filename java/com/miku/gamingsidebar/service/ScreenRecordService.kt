package com.miku.gamingsidebar.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.miku.gamingsidebar.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

class ScreenRecordService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var internalAudioRecorder: InternalAudioRecorder? = null
    private var currentOutputFile: File? = null
    private var tempVideoFile: File? = null
    private var tempAudioFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            if (resultCode != 0 && resultData != null) {
                startScreenRecording(resultCode, resultData)
            } else {
                stopSelf()
            }
        } else if (action == ACTION_STOP) {
            stopScreenRecording()
        }
        return START_NOT_STICKY
    }

    private fun startScreenRecording(resultCode: Int, resultData: Intent) {
        startForegroundNotification()

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val targetShortDim = 1080
        val rawWidth = metrics.widthPixels
        val rawHeight = metrics.heightPixels
        val ratio = rawWidth.toFloat() / rawHeight.toFloat()

        var width: Int
        var height: Int
        if (rawWidth > rawHeight) {
            height = targetShortDim
            width = (targetShortDim * ratio).toInt()
        } else {
            width = targetShortDim
            height = (targetShortDim / ratio).toInt()
        }
        if (width % 2 != 0) width -= 1
        if (height % 2 != 0) height -= 1
        val dpi = metrics.densityDpi

        val moviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "MikuHub")
        if (!moviesDir.exists()) {
            moviesDir.mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val finalOutputFile = File(moviesDir, "ScreenRecord_$timestamp.mp4")
        currentOutputFile = finalOutputFile

        val tempVideoFile = File(cacheDir, "temp_video_$timestamp.mp4")
        val tempAudioFile = File(cacheDir, "temp_audio_$timestamp.m4a")
        this.tempVideoFile = tempVideoFile
        this.tempAudioFile = tempAudioFile

        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(tempVideoFile.absolutePath)
                setVideoSize(width, height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(12 * 1000 * 1000) // 12 Mbps
                setVideoFrameRate(120) // 120 FPS
                prepare()
            }

            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = projectionManager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("MediaProjection no inicializado")
            mediaProjection = proj

            proj.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopScreenRecording()
                }
            }, Handler(Looper.getMainLooper()))

            virtualDisplay = proj.createVirtualDisplay(
                "MikuScreenRecorder",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface,
                null,
                null
            )

            // Start Pure Digital Internal Audio Recording (Android 10+ / API 29+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                internalAudioRecorder = InternalAudioRecorder(proj, tempAudioFile).apply {
                    start()
                }
            }

            mediaRecorder?.start()
            _isRecordingFlow.value = true
        } catch (e: Exception) {
            e.printStackTrace()
            stopScreenRecording()
        }
    }

    private fun stopScreenRecording() {
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // Ignored
        }
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            // Ignored
        }
        mediaRecorder = null

        try {
            internalAudioRecorder?.stop()
        } catch (e: Exception) {
            // Ignored
        }
        internalAudioRecorder = null

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null

        _isRecordingFlow.value = false

        // Mux clean video + internal digital audio into final MP4
        val tempVid = tempVideoFile
        val tempAud = tempAudioFile
        val finalOut = currentOutputFile

        if (tempVid != null && finalOut != null) {
            val success = AudioVideoMuxer.mux(tempVid, tempAud, finalOut)
            if (success && finalOut.exists()) {
                MediaScannerConnection.scanFile(this, arrayOf(finalOut.absolutePath), arrayOf("video/mp4"), null)
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundNotification() {
        val channelId = "miku_screen_recorder"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Grabación de Pantalla",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Miku Hub Grabadora")
            .setContentText("Grabando pantalla en juego...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1002, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1002, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRecordingFlow.value = false
    }

    companion object {
        const val ACTION_START = "com.miku.gamingsidebar.action.START_RECORD"
        const val ACTION_STOP = "com.miku.gamingsidebar.action.STOP_RECORD"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private val _isRecordingFlow = MutableStateFlow(false)
        val isRecordingFlow: StateFlow<Boolean> = _isRecordingFlow

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
