package com.guide.app.planning

import com.guide.app.models.ActionToken
import com.guide.app.models.Detection

object Planner {

    // Navigation zones: LEFT (0-33%), CENTER (33-67%), RIGHT (67-100%)
    private const val LEFT_ZONE_END = 0.33f
    private const val RIGHT_ZONE_START = 0.67f

    // Height-based thresholds (relative to frame height = 320)
    private const val CRITICAL_HEIGHT_RATIO = 0.40f  // STOP if object height > 40% of frame
    private const val WARNING_HEIGHT_RATIO = 0.25f   // VEER if object height > 25% of frame

    private const val FRAME_HEIGHT = 320f

    // Navigation-relevant obstacle classes from Python implementation
    private val NAVIGATION_CLASSES = setOf(
        "person", "bicycle", "car", "motorcycle", "bus", "truck", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "dog", "cat",
        "chair", "couch", "potted plant", "dining table"
    )

    fun decide(detections: List<Detection>): ActionToken {
        if (detections.isEmpty()) {
            return ActionToken.CLEAR
        }

        // Filter for navigation-relevant obstacles
        val relevantDetections = detections.filter { det ->
            NAVIGATION_CLASSES.contains(det.label)
        }

        if (relevantDetections.isEmpty()) {
            return ActionToken.CLEAR
        }

        // Find the most critical obstacle
        for (det in relevantDetections) {
            val zone = getZone(det)
            val heightRatio = det.h / FRAME_HEIGHT

            // Critical height: STOP
            if (heightRatio > CRITICAL_HEIGHT_RATIO) {
                if (zone == "CENTER") {
                    return ActionToken.STOP
                }
                // If critical obstacle on left/right, still stop
                return ActionToken.STOP
            }

            // Warning height: VEER
            if (heightRatio > WARNING_HEIGHT_RATIO) {
                when (zone) {
                    "CENTER" -> {
                        // Obstacle in center, veer based on which side has more space
                        return if (det.cx < FRAME_HEIGHT / 2) {
                            ActionToken.SLIGHT_RIGHT
                        } else {
                            ActionToken.SLIGHT_LEFT
                        }
                    }
                    "LEFT" -> return ActionToken.SLIGHT_RIGHT
                    "RIGHT" -> return ActionToken.SLIGHT_LEFT
                }
            }

            // If obstacle present but not critical, return caution
            if (zone == "CENTER") {
                return ActionToken.CAUTION
            }
        }

        return ActionToken.CLEAR
    }

    private fun getZone(det: Detection): String {
        // Calculate object center in normalized coordinates [0, 320]
        val cx_norm = det.cx / FRAME_HEIGHT

        return when {
            cx_norm < LEFT_ZONE_END -> "LEFT"
            cx_norm > RIGHT_ZONE_START -> "RIGHT"
            else -> "CENTER"
        }
    }
}
