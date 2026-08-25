package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioPlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val activeMediaUrl: String? = null,
    val playbackSpeed: Float = 1.0f
)

class AudioPlayerHelper(private val context: Context) {
    private val TAG = "AudioPlayerHelper"
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _playbackState = MutableStateFlow(AudioPlaybackState())
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    fun playAudio(urlOrPath: String, onCompletion: () -> Unit = {}) {
        if (urlOrPath.isBlank()) return

        if (_playbackState.value.activeMediaUrl == urlOrPath && mediaPlayer != null) {
            if (_playbackState.value.isPlaying) {
                pauseAudio()
            } else {
                resumeAudio()
            }
            return
        }

        stopAudio()

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
                    setDataSource(context, Uri.parse(urlOrPath))
                } else {
                    setDataSource(urlOrPath)
                }

                setOnPreparedListener { mp ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        try {
                            val params = mp.playbackParams
                            params.speed = _playbackState.value.playbackSpeed
                            mp.playbackParams = params
                        } catch (_: Exception) {}
                    }
                    mp.start()
                    val dur = mp.duration
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = true,
                        currentPositionMs = 0,
                        durationMs = if (dur > 0) dur else 1000,
                        activeMediaUrl = urlOrPath
                    )
                    startProgressTracker()
                }

                setOnCompletionListener {
                    stopProgressTracker()
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionMs = 0,
                        durationMs = it.duration,
                        activeMediaUrl = urlOrPath
                    )
                    onCompletion()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    stopAudio()
                    true
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPlayer for $urlOrPath: ${e.message}")
            stopAudio()
        }
    }

    fun pauseAudio() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    stopProgressTracker()
                    _playbackState.value = _playbackState.value.copy(isPlaying = false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing audio: ${e.message}")
        }
    }

    fun resumeAudio() {
        try {
            mediaPlayer?.let {
                it.start()
                startProgressTracker()
                _playbackState.value = _playbackState.value.copy(isPlaying = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming audio: ${e.message}")
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
            _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking audio: ${e.message}")
        }
    }

    fun setSpeed(speed: Float) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                mediaPlayer?.let { mp ->
                    val params = mp.playbackParams
                    params.speed = speed
                    mp.playbackParams = params
                }
            }
            _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting speed: ${e.message}")
        }
    }

    fun cyclePlaybackSpeed(): Float {
        val current = _playbackState.value.playbackSpeed
        val nextSpeed = when {
            current < 1.25f -> 1.5f
            current < 1.75f -> 2.0f
            else -> 1.0f
        }
        setSpeed(nextSpeed)
        return nextSpeed
    }

    fun stopAudio() {
        stopProgressTracker()
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
            _playbackState.value = AudioPlaybackState()
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        val cur = mp.currentPosition
                        val dur = mp.duration
                        _playbackState.value = _playbackState.value.copy(
                            currentPositionMs = cur,
                            durationMs = dur
                        )
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopAudio()
        scope.cancel()
    }
}
