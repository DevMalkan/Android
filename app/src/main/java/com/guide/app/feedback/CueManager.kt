package com.guide.app.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import com.guide.app.models.ActionToken
import com.guide.app.models.Detection

object CueManager {

    private var lastToken: ActionToken? = null
    private var lastDetections: List<Detection>? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    fun emit(context: Context, tts: TextToSpeech?, token: ActionToken, detections: List<Detection> = emptyList()) {
        if (token == ActionToken.CLEAR) return

        lastToken = token
        lastDetections = detections

        vibrate(context, token)
        speak(context, tts, token, detections)
    }

    fun repeatLastCue(context: Context, tts: TextToSpeech?) {
        lastToken?.let { emit(context, tts, it, lastDetections ?: emptyList()) }
    }

    private fun vibrate(context: Context, token: ActionToken) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let {
                val pattern = when (token) {
                    ActionToken.STOP -> longArrayOf(0, 600)
                    ActionToken.SLIGHT_LEFT, ActionToken.SLIGHT_RIGHT -> longArrayOf(0, 120, 80, 120)
                    ActionToken.CAUTION -> longArrayOf(0, 200)
                    else -> return
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(pattern, -1)
                    it.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    private fun speak(context: Context, tts: TextToSpeech?, token: ActionToken, detections: List<Detection>) {
        if (tts == null) return

        try {
            if (audioManager == null) {
                audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            }

            // Format: "[class_name] ahead, stop/move left/right"
            val objectName = if (detections.isNotEmpty()) {
                detections.first().label
            } else {
                "obstacle"
            }

            val message = when (token) {
                ActionToken.STOP -> "$objectName ahead, stop"
                ActionToken.SLIGHT_LEFT -> "$objectName ahead, move left"
                ActionToken.SLIGHT_RIGHT -> "$objectName ahead, move right"
                ActionToken.CAUTION -> "$objectName ahead, caution"
                else -> return
            }

            requestAudioFocus(context)

            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, token.name)
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak failed", e)
        }
    }

    private fun requestAudioFocus(context: Context) {
        try {
            audioManager?.let { am ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (audioFocusRequest == null) {
                        val audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()

                        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAudioAttributes(audioAttributes)
                            .build()
                    }
                    audioFocusRequest?.let { am.requestAudioFocus(it) }
                } else {
                    @Suppress("DEPRECATION")
                    am.requestAudioFocus(
                        null,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio focus request failed", e)
        }
    }

    private const val TAG = "CueManager"
}
