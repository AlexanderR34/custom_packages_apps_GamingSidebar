package com.miku.gamingsidebar.service

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AudioMixerHelper {

    private val _gameVolume = MutableStateFlow(0.8f)
    val gameVolume: StateFlow<Float> = _gameVolume.asStateFlow()

    private val _voiceVolume = MutableStateFlow(0.8f)
    val voiceVolume: StateFlow<Float> = _voiceVolume.asStateFlow()

    private val _isGameMuted = MutableStateFlow(false)
    val isGameMuted: StateFlow<Boolean> = _isGameMuted.asStateFlow()

    private val _isVoiceMuted = MutableStateFlow(false)
    val isVoiceMuted: StateFlow<Boolean> = _isVoiceMuted.asStateFlow()

    private var previousGameVolume = 0.8f
    private var previousVoiceVolume = 0.8f

    @Suppress("DEPRECATION")
    private fun isBluetoothA2dpActive(am: AudioManager): Boolean {
        return try {
            am.isBluetoothA2dpOn
        } catch (e: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun isBluetoothScoActive(am: AudioManager): Boolean {
        return try {
            am.isBluetoothScoOn
        } catch (e: Exception) {
            false
        }
    }

    private fun getVoiceStream(am: AudioManager): Int {
        // If in an actual telephony phone call, use STREAM_VOICE_CALL
        if (am.mode == AudioManager.MODE_IN_CALL) {
            return AudioManager.STREAM_VOICE_CALL
        }
        // If Bluetooth A2DP is connected without active SCO (e.g. bluetooth speakers or headsets in game),
        // routing voice volume 0 or stream 0 to A2DP device (0x80) on MediaTek chipsets causes
        // AudioMTKGainController to lock AudioDspStreamManager into "btOutWriteAction [1] muted".
        // Decouple voice stream to STREAM_NOTIFICATION so A2DP music is not killed.
        if (isBluetoothA2dpActive(am) && !isBluetoothScoActive(am)) {
            return AudioManager.STREAM_NOTIFICATION
        }
        return if (am.mode == AudioManager.MODE_IN_COMMUNICATION) {
            AudioManager.STREAM_VOICE_CALL
        } else {
            AudioManager.STREAM_NOTIFICATION
        }
    }

    /**
     * Sincroniza SOLO LEYENDO los volúmenes del sistema.
     * NUNCA llama a setStreamVolume para evitar reconfiguraciones en el stack Bluetooth.
     */
    fun syncCurrentVolumes(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        
        val maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curMusic = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val musicNorm = (curMusic.toFloat() / maxMusic).coerceIn(0f, 1f)
        _gameVolume.value = musicNorm
        _isGameMuted.value = (curMusic == 0)

        val voiceStream = getVoiceStream(am)
        val maxVoice = am.getStreamMaxVolume(voiceStream).coerceAtLeast(1)
        val curVoice = am.getStreamVolume(voiceStream)
        val voiceNorm = (curVoice.toFloat() / maxVoice).coerceIn(0f, 1f)
        _voiceVolume.value = voiceNorm
        _isVoiceMuted.value = (curVoice == 0)
    }

    fun setGameVolume(context: Context, volumeFraction: Float) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val targetStep = (volumeFraction * max).roundToInt().coerceIn(0, max)
        val steppedFraction = targetStep.toFloat() / max
        _gameVolume.value = steppedFraction
        _isGameMuted.value = (targetStep == 0)

        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, targetStep, 0)
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun toggleGameMute(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val currentlyMuted = _isGameMuted.value
        if (currentlyMuted) {
            val restore = if (previousGameVolume > 0.05f) previousGameVolume else 0.5f
            setGameVolume(context, restore)
            return false
        } else {
            previousGameVolume = _gameVolume.value
            setGameVolume(context, 0f)
            return true
        }
    }

    fun setVoiceVolume(context: Context, volumeFraction: Float) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val voiceStream = getVoiceStream(am)
        val max = am.getStreamMaxVolume(voiceStream).coerceAtLeast(1)
        val targetStep = (volumeFraction * max).roundToInt().coerceIn(0, max)
        val steppedFraction = targetStep.toFloat() / max
        _voiceVolume.value = steppedFraction
        _isVoiceMuted.value = (targetStep == 0)

        try {
            am.setStreamVolume(voiceStream, targetStep, 0)
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun toggleVoiceMute(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val currentlyMuted = _isVoiceMuted.value
        if (currentlyMuted) {
            val restore = if (previousVoiceVolume > 0.05f) previousVoiceVolume else 0.7f
            setVoiceVolume(context, restore)
            return false
        } else {
            previousVoiceVolume = _voiceVolume.value
            setVoiceVolume(context, 0f)
            return true
        }
    }
}
