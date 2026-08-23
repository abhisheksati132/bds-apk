package com.example.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorderHelper(private val context: Context) {
    private val TAG = "AudioRecorderHelper"
    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var isRecording = false

    fun startRecording(onStarted: (File) -> Unit = {}): Boolean {
        if (isRecording) return false

        try {
            val audioDir = File(context.cacheDir, "voice_notes").apply { if (!exists()) mkdirs() }
            val outputFile = File(audioDir, "rec_${System.currentTimeMillis()}.m4a")
            currentOutputFile = outputFile

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            onStarted(outputFile)
            Log.d(TAG, "Recording started -> ${outputFile.absolutePath}")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start MediaRecorder: ${e.message}")
            stopRecording()
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Audio recording unexpected exception: ${e.message}")
            stopRecording()
            return false
        }
    }

    fun getMaxAmplitude(): Int {
        return if (isRecording) {
            try {
                recorder?.maxAmplitude ?: 0
            } catch (e: Exception) {
                0
            }
        } else 0
    }

    fun stopRecording(): File? {
        if (!isRecording) return null
        return try {
            recorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.w(TAG, "MediaRecorder stop failed (too short audio?): ${e.message}")
                }
                release()
            }
            recorder = null
            isRecording = false
            val file = currentOutputFile
            Log.d(TAG, "Recording stopped. File size: ${file?.length() ?: 0} bytes")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder: ${e.message}")
            recorder = null
            isRecording = false
            null
        }
    }

    fun cancelRecording() {
        stopRecording()
        currentOutputFile?.let {
            if (it.exists()) it.delete()
        }
        currentOutputFile = null
    }

    fun isCurrentlyRecording(): Boolean = isRecording
}
