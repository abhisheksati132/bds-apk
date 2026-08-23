package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

object HapticHelper {

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            Log.w("HapticHelper", "Error getting vibrator: ${e.message}")
            null
        }
    }

    /**
     * Physical haptic confirmation when a message is sent.
     */
    fun playMessageSentHaptic(context: Context) {
        val vibrator = getVibrator(context) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(35)
            }
        } catch (e: Exception) {
            Log.d("HapticHelper", "Message sent haptic: ${e.message}")
        }
    }

    /**
     * Subtle physical feedback when toggling emoji reactions.
     */
    fun playReactionHaptic(context: Context) {
        val vibrator = getVibrator(context) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(25)
            }
        } catch (e: Exception) {
            Log.d("HapticHelper", "Reaction haptic: ${e.message}")
        }
    }

    /**
     * Physical vibration confirmation when clearing a chat thread.
     */
    fun playClearChatHaptic(context: Context) {
        val vibrator = getVibrator(context) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 40, 80), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 50, 40, 80), -1)
            }
        } catch (e: Exception) {
            Log.d("HapticHelper", "Clear chat haptic: ${e.message}")
        }
    }

    /**
     * Custom vibration pattern player for incoming messages and pattern testing.
     */
    fun triggerVibrationPattern(context: Context, patternName: String) {
        val vibrator = getVibrator(context) ?: return
        if (patternName.equals("OFF", ignoreCase = true) || patternName.equals("SILENT", ignoreCase = true)) {
            return
        }

        try {
            when (patternName.uppercase()) {
                "SHORT" -> {
                    // Quick double pulse
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 50, 40), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0, 40, 50, 40), -1)
                    }
                }
                "LONG" -> {
                    // Strong extended pulse
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(220, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(220)
                    }
                }
                "HEARTBEAT" -> {
                    // Rhythmic double thud heartbeat pattern
                    val timings = longArrayOf(0, 70, 80, 140, 200)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(timings, -1)
                    }
                }
                else -> {
                    // "DEFAULT" standard vibration
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(80)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("HapticHelper", "Vibration pattern error: ${e.message}")
        }
    }

    /**
     * Play notification sound if enabled.
     */
    fun playNotificationSound(context: Context) {
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notificationUri)
            ringtone?.play()
        } catch (e: Exception) {
            Log.d("HapticHelper", "Notification sound error: ${e.message}")
        }
    }
}
