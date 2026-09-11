package com.miku.gamingsidebar.service

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RequiresApi(Build.VERSION_CODES.Q)
class InternalAudioRecorder(
    private val mediaProjection: MediaProjection,
    private val outputFile: File
) {
    private var audioRecord: AudioRecord? = null
    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var audioTrackIndex = -1
    private var isMuxerStarted = false
    private val isRecording = AtomicBoolean(false)
    private var recordingThread: Thread? = null

    private val sampleRate = 48000
    private val channelCount = 2
    private val bitRate = 192000

    @SuppressLint("MissingPermission")
    fun start() {
        try {
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBufferSize * 2).coerceAtLeast(8192)

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()

            audioRecord = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()

            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
            }

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val rec = audioRecord
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                stop()
                return
            }

            rec.startRecording()
            isRecording.set(true)

            recordingThread = Thread { encodeLoop(bufferSize) }.apply { start() }
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    private fun encodeLoop(bufferSize: Int) {
        val record = audioRecord ?: return
        val codec = mediaCodec ?: return
        val muxer = mediaMuxer ?: return

        val pcmBuffer = ByteArray(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        while (isRecording.get()) {
            val readBytes = record.read(pcmBuffer, 0, pcmBuffer.size)
            if (readBytes > 0) {
                val inputIndex = codec.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    inputBuffer?.clear()
                    inputBuffer?.put(pcmBuffer, 0, readBytes)
                    val presentationTimeUs = System.nanoTime() / 1000
                    codec.queueInputBuffer(inputIndex, 0, readBytes, presentationTimeUs, 0)
                }
            }

            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            while (outputIndex >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size > 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && isMuxerStarted && audioTrackIndex >= 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                    }
                }

                codec.releaseOutputBuffer(outputIndex, false)
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }

            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec.outputFormat
                audioTrackIndex = muxer.addTrack(newFormat)
                muxer.start()
                isMuxerStarted = true
            }
        }

        try {
            val inputIndex = codec.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                codec.queueInputBuffer(inputIndex, 0, 0, System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            while (outputIndex >= 0) {
                if (bufferInfo.size > 0 && isMuxerStarted && audioTrackIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun stop() {
        isRecording.set(false)
        try {
            recordingThread?.join(1000)
        } catch (e: Exception) {
            // Ignored
        }

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignored
        }
        audioRecord = null

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) {
            // Ignored
        }
        mediaCodec = null

        try {
            if (isMuxerStarted) {
                mediaMuxer?.stop()
            }
            mediaMuxer?.release()
        } catch (e: Exception) {
            // Ignored
        }
        mediaMuxer = null
        isMuxerStarted = false
    }
}
