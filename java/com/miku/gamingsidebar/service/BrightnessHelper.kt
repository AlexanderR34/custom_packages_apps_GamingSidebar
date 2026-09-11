package com.miku.gamingsidebar.service

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Accurately maps slider (0.0 .. 1.0) to hardware and system brightness using
 * Android's standard perceptual Gamma 2.0 curve (Stevens' Power Law).
 * Prevents screen brightness from jumping to 100% at halfway.
 */
object BrightnessHelper {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var pendingJob: Job? = null

    fun getBrightness(context: Context): Float {
        return try {
            val currentSysBrightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                58
            )
            // Perceptual inverse: slider = sqrt(raw / 255)
            val linearNorm = (currentSysBrightness.toFloat() / 255f).coerceIn(0.001f, 1.0f)
            sqrt(linearNorm).coerceIn(0.02f, 1.0f)
        } catch (e: Exception) {
            0.5f
        }
    }

    fun setBrightness(context: Context, sliderValue: Float) {
        val clamped = sliderValue.coerceIn(0.02f, 1.0f)
        
        // Perceptual curve: raw = slider^2 * 255
        val gammaFloat = clamped.toDouble().pow(2.0).toFloat().coerceIn(0.001f, 1.0f)
        val systemBrightness = (gammaFloat * 255f).roundToInt().coerceIn(1, 255)

        // 1. Instant hardware display brightness override on overlay window
        GamingOverlayService.activeInstance?.setOverlayWindowBrightness(gammaFloat)

        // 2. Debounced persistent system setting update
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(40)
            try {
                try {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    )
                } catch (_: Exception) {}

                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    systemBrightness
                )
            } catch (_: Exception) {}
        }
    }
}
