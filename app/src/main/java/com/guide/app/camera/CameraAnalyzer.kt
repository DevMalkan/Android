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
            if (frameCount % 15 != 0L) {
                return
            }

            val detections = engine!!.infer(imageProxy)
            val token = Planner.decide(detections)

            lastToken = token

            if (token != ActionToken.CLEAR) {
                val now = System.currentTimeMillis()
                if (now - lastCueTimeMs >= CUE_RATE_LIMIT_MS || token == ActionToken.STOP) {
                    lastCueTimeMs = now
                    CueManager.emit(context, tts, token, detections)

                    when (token) {
                        ActionToken.STOP -> stopCount++
                        ActionToken.SLIGHT_LEFT, ActionToken.SLIGHT_RIGHT -> veerCount++
                        else -> {}
                    }

                    AgentClient.enqueue(token, detections)
                }
            }
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
