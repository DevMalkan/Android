package com.guide.app.telemetry

import android.util.Log
import com.guide.app.BuildConfig
import com.guide.app.models.ActionToken
import com.guide.app.models.Detection
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object AgentClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val executor = Executors.newSingleThreadExecutor()
    private val eventQueue = mutableListOf<EventData>()
    private val sessionId = UUID.randomUUID().toString()

    private const val MAX_QUEUE_SIZE = 200
    private const val FLUSH_INTERVAL_MS = 5000L
    private var retryBackoffMs = 1000L

    private data class EventData(
        val token: ActionToken,
        val detections: List<Detection>,
        val timestampSeconds: Long
    )

    init {
        startFlushTimer()
    }

    fun enqueue(token: ActionToken, detections: List<Detection>) {
        executor.execute {
            synchronized(eventQueue) {
                if (eventQueue.size >= MAX_QUEUE_SIZE) {
                    eventQueue.removeAt(0)
                }
                eventQueue.add(EventData(token, detections, System.currentTimeMillis() / 1000))
            }
        }
    }

    private fun startFlushTimer() {
        executor.execute {
            while (true) {
                try {
                    Thread.sleep(FLUSH_INTERVAL_MS)
                    flushEvents()
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Flush timer error", e)
                }
            }
        }
    }

    private fun flushEvents() {
        val base = BuildConfig.AGENT_BASE
        if (base.isBlank()) {
            return
        }

        val eventsToSend = synchronized(eventQueue) {
            if (eventQueue.isEmpty()) return
            eventQueue.toList().also { eventQueue.clear() }
        }

        try {
            val jsonArray = JSONArray()
            for (event in eventsToSend) {
                val obj = JSONObject()
                obj.put("client_id", "placeholder")
                obj.put("session_id", sessionId)
                obj.put("t_client", event.timestampSeconds)

                // Determine zone and event based on token
                val (zone, action) = when (event.token) {
                    ActionToken.STOP -> Pair("obstacle_center", "stop")
                    ActionToken.SLIGHT_LEFT -> Pair("obstacle_center", "veer_left")
                    ActionToken.SLIGHT_RIGHT -> Pair("obstacle_center", "veer_right")
                    ActionToken.CAUTION -> Pair("obstacle_detected", "caution")
                    ActionToken.CLEAR -> continue
                }
                obj.put("events", JSONArray(listOf(zone, action)))

                if (event.detections.isNotEmpty()) {
                    val firstDet = event.detections.first()
                    obj.put("classes", JSONArray(event.detections.map { it.label }))
                    obj.put("confidence", firstDet.conf)
                    firstDet.distanceM?.let { obj.put("free_ahead_m", it) }
                }

                obj.put("app", "android-1.0.0")
                jsonArray.put(obj)
            }

            val body = jsonArray.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$base/ingest_event")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    retryBackoffMs = 1000L
                    Log.d(TAG, "Flushed ${eventsToSend.size} events")
                } else {
                    handleFailure(eventsToSend)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Flush failed, requeueing events", e)
            handleFailure(eventsToSend)
        }
    }

    private fun handleFailure(events: List<EventData>) {
        synchronized(eventQueue) {
            eventQueue.addAll(0, events.takeLast(MAX_QUEUE_SIZE - eventQueue.size))
        }

        retryBackoffMs = minOf(retryBackoffMs * 2, 60000L)
        try {
            Thread.sleep(retryBackoffMs)
        } catch (e: InterruptedException) {
            Log.w(TAG, "Backoff interrupted")
        }
    }

    private const val TAG = "AgentClient"
}
