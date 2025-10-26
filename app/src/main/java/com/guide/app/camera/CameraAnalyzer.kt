package com.guide.app.camera

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.camera.core.ImageProxy
import com.guide.app.feedback.CueManager
import com.guide.app.inference.InferenceEngine
import com.guide.app.inference.TfliteInferenceEngine
import com.guide.app.models.ActionToken
import com.guide.app.planning.Planner
import com.guide.app.telemetry.AgentClient

object CameraAnalyzer {

    private var engine: InferenceEngine? = null
    private var lastCueTimeMs = 0L
    private const val CUE_RATE_LIMIT_MS = 1000L

    @Volatile
    var frameCount = 0L
        private set

    @Volatile
    var stopCount = 0
        private set

    @Volatile
    var veerCount = 0
        private set

    @Volatile
    var lastToken: ActionToken = ActionToken.CLEAR
        private set

    fun handleFrame(context: Context, imageProxy: ImageProxy, tts: TextToSpeech?) {
        try {
            if (engine == null) {
                engine = TfliteInferenceEngine(context)
            }

            frameCount++

            // Process only 1 frame every 15 frames to avoid chaotic audio output
            // This gives ~1 FPS processing rate from 15 FPS camera feed
            if (frameCount % 45 != 0L) {
                return
            }

            Log.d(TAG, "")
            Log.d(TAG, "████████████████████████████████████████████████████████")
            Log.d(TAG, "FRAME #$frameCount (Processing frame ${frameCount / 45})")
            Log.d(TAG, "████████████████████████████████████████████████████████")

            val detections = engine!!.infer(imageProxy)
            val token = Planner.decide(detections)

            lastToken = token

            Log.d(TAG, "────────────────────────────────────────────────────────")
            Log.d(TAG, "FINAL ACTION: $token")
            Log.d(TAG, "────────────────────────────────────────────────────────")

            if (token != ActionToken.CLEAR) {
                val now = System.currentTimeMillis()
                if (now - lastCueTimeMs >= CUE_RATE_LIMIT_MS || token == ActionToken.STOP) {
                    lastCueTimeMs = now
                    Log.d(TAG, "Audio output: ENABLED (rate limit passed)")
                    CueManager.emit(context, tts, token, detections)

                    when (token) {
                        ActionToken.STOP -> stopCount++
                        ActionToken.SLIGHT_LEFT, ActionToken.SLIGHT_RIGHT -> veerCount++
                        else -> {}
                    }

                    AgentClient.enqueue(token, detections)
                } else {
                    Log.d(TAG, "Audio output: SUPPRESSED (rate limit: ${CUE_RATE_LIMIT_MS}ms)")
                }
            } else {
                Log.d(TAG, "Audio output: NONE (path clear)")
            }

            Log.d(TAG, "████████████████████████████████████████████████████████")
            Log.d(TAG, "")
        } catch (e: Exception) {
            Log.e(TAG, "Frame handling error, ignoring", e)
        } finally {
            imageProxy.close()
        }
    }

    fun resetStats() {
        frameCount = 0
        stopCount = 0
        veerCount = 0
        lastToken = ActionToken.CLEAR
    }

    private const val TAG = "CameraAnalyzer"
}
