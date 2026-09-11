package com.miku.gamingsidebar.service

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object AudioVideoMuxer {

    fun mux(videoFile: File, audioFile: File?, outputFile: File): Boolean {
        if (!videoFile.exists() || videoFile.length() == 0L) return false

        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
            return videoFile.renameTo(outputFile)
        }

        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Video Track
            var videoTrackIndex = -1
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i)
                    videoTrackIndex = muxer.addTrack(format)
                    break
                }
            }

            // Audio Track
            var audioTrackIndex = -1
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i)
                    audioTrackIndex = muxer.addTrack(format)
                    break
                }
            }

            if (videoTrackIndex < 0) {
                return false
            }

            muxer.start()

            val bufferSize = 1024 * 1024
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // Copy Video Frames
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // Copy Audio Frames
            if (audioTrackIndex >= 0) {
                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) break

                    bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    bufferInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                    audioExtractor.advance()
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                videoFile.copyTo(outputFile, overwrite = true)
                return true
            } catch (ex: Exception) {
                return false
            }
        } finally {
            try {
                videoExtractor.release()
            } catch (e: Exception) { }
            try {
                audioExtractor.release()
            } catch (e: Exception) { }
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) { }

            try { videoFile.delete() } catch (e: Exception) { }
            try { audioFile.delete() } catch (e: Exception) { }
        }
    }
}
