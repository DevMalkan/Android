package com.guide.app.planning

import android.util.Log
import com.guide.app.models.ActionToken
import com.guide.app.models.Detection

object Planner {

    private const val TAG = "Planner"

    // Frame dimensions (matching preprocessed video: 640x480)
    // Model input is 320x320, but detections are scaled back to original aspect ratio
    private const val FRAME_WIDTH = 640f
    private const val FRAME_HEIGHT = 480f

    // Navigation zones: LEFT (0-33%), CENTER (33-67%), RIGHT (67-100%)
    private const val LEFT_ZONE_END = 0.33f
    private const val RIGHT_ZONE_START = 0.67f

    // Height-based thresholds (relative to frame height)
    private const val CRITICAL_HEIGHT_RATIO = 0.40f  // STOP if object height > 40% of frame
    private const val WARNING_HEIGHT_RATIO = 0.25f   // VEER if object height > 25% of frame

    // Navigation-relevant obstacle classes from Python implementation
    private val NAVIGATION_CLASSES = setOf(
        "person", "bicycle", "car", "motorcycle", "bus", "truck", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "dog", "cat",
        "chair", "couch", "potted plant", "dining table", "backpack", "handbag",
        "suitcase", "umbrella", "train", "bed"
    )

    // Critical obstacles that should trigger immediate STOP (higher priority)
    private val CRITICAL_OBSTACLES = setOf(
        "person", "car", "bicycle", "motorcycle", "bus", "truck", "dog"
    )

    fun decide(detections: List<Detection>): ActionToken {
        Log.d(TAG, "════════════════════════════════════════════════════════")
        Log.d(TAG, "NAVIGATION DECISION - Input: ${detections.size} detections")

        if (detections.isEmpty()) {
            Log.d(TAG, "Decision: CLEAR (no detections)")
            Log.d(TAG, "════════════════════════════════════════════════════════")
            return ActionToken.CLEAR
        }

        // Filter for navigation-relevant obstacles
        val relevantDetections = detections.filter { det ->
            NAVIGATION_CLASSES.contains(det.label)
        }

        Log.d(TAG, "Navigation-relevant: ${relevantDetections.size}/${detections.size}")

        if (relevantDetections.isEmpty()) {
            Log.d(TAG, "Decision: CLEAR (no navigation-relevant obstacles)")
            Log.d(TAG, "════════════════════════════════════════════════════════")
            return ActionToken.CLEAR
        }

        // Log all detections with zones
        Log.d(TAG, "─── ALL DETECTIONS WITH ZONES ───")
        relevantDetections.forEachIndexed { idx, det ->
            val zone = getZone(det)
            val heightRatio = det.h / FRAME_HEIGHT
            val isCritical = CRITICAL_OBSTACLES.contains(det.label)
            Log.d(TAG, String.format(
                "[%d] %-15s zone=%-6s cx=%.1f cy=%.1f w=%.1f h=%.1f h%%=%.2f critical=%s",
                idx + 1, det.label, zone, det.cx, det.cy, det.w, det.h, heightRatio * 100, isCritical
            ))
        }

        // Separate detections by zone
        val centerObstacles = relevantDetections.filter { getZone(it) == "CENTER" }
        val leftObstacles = relevantDetections.filter { getZone(it) == "LEFT" }
        val rightObstacles = relevantDetections.filter { getZone(it) == "RIGHT" }

        Log.d(TAG, "By zone: CENTER=${centerObstacles.size}, LEFT=${leftObstacles.size}, RIGHT=${rightObstacles.size}")

        // Priority 1: Check center zone obstacles
        if (centerObstacles.isNotEmpty()) {
            // Find obstacle with highest confidence score
            val mostConfident = centerObstacles.maxByOrNull { it.conf } ?: return ActionToken.CLEAR

            val heightRatio = mostConfident.h / FRAME_HEIGHT

            Log.d(TAG, "─── CENTER ZONE ANALYSIS ───")
            Log.d(TAG, String.format(
                "Most confident: %s | conf=%.3f | h=%.1f (%.1f%%) | cx=%.1f | Thresholds: STOP>%.0f%%, VEER>%.0f%%",
                mostConfident.label, mostConfident.conf, mostConfident.h, heightRatio * 100, mostConfident.cx,
                CRITICAL_HEIGHT_RATIO * 100, WARNING_HEIGHT_RATIO * 100
            ))

            // Critical height: STOP
            if (heightRatio > CRITICAL_HEIGHT_RATIO) {
                Log.d(TAG, "Decision: STOP (height ratio ${String.format("%.1f%%", heightRatio * 100)} > ${CRITICAL_HEIGHT_RATIO * 100}%)")
                Log.d(TAG, "════════════════════════════════════════════════════════")
                return ActionToken.STOP
            }

            // Warning height: VEER
            if (heightRatio > WARNING_HEIGHT_RATIO) {
                // Decide direction based on obstacle position within center zone
                val decision = if (mostConfident.cx < FRAME_WIDTH / 2) {
                    // Obstacle on left side of center, veer right
                    ActionToken.SLIGHT_RIGHT
                } else {
                    // Obstacle on right side of center, veer left
                    ActionToken.SLIGHT_LEFT
                }
                Log.d(TAG, "Decision: $decision (height ratio ${String.format("%.1f%%", heightRatio * 100)} > ${WARNING_HEIGHT_RATIO * 100}%, cx=${mostConfident.cx} ${if (mostConfident.cx < FRAME_WIDTH/2) "<" else ">"} ${FRAME_WIDTH/2})")
                Log.d(TAG, "════════════════════════════════════════════════════════")
                return decision
            }

            // Small obstacle in center - CAUTION
            Log.d(TAG, "Decision: CAUTION (small obstacle in center, height ratio ${String.format("%.1f%%", heightRatio * 100)})")
            Log.d(TAG, "════════════════════════════════════════════════════════")
            return ActionToken.CAUTION
        }

        // Priority 2: Check side zones for large obstacles
        val sideObstacles = leftObstacles + rightObstacles
        if (sideObstacles.isNotEmpty()) {
            // Find obstacle with highest confidence score
            val mostConfidentSide = sideObstacles.maxByOrNull { it.conf } ?: return ActionToken.CLEAR

            val heightRatio = mostConfidentSide.h / FRAME_HEIGHT
            val sideZone = getZone(mostConfidentSide)

            Log.d(TAG, "─── SIDE ZONE ANALYSIS ───")
            Log.d(TAG, String.format(
                "Most confident: %s | conf=%.3f | zone=%s | h=%.1f (%.1f%%) | cx=%.1f",
                mostConfidentSide.label, mostConfidentSide.conf, sideZone, mostConfidentSide.h, heightRatio * 100, mostConfidentSide.cx
            ))

            // Large side obstacle: guide away from it
            if (heightRatio > WARNING_HEIGHT_RATIO) {
                val decision = if (mostConfidentSide.cx < FRAME_WIDTH / 2) {
                    // Large obstacle on left, move right
                    ActionToken.SLIGHT_RIGHT
                } else {
                    // Large obstacle on right, move left
                    ActionToken.SLIGHT_LEFT
                }
                Log.d(TAG, "Decision: $decision (large side obstacle, height ratio ${String.format("%.1f%%", heightRatio * 100)} > ${WARNING_HEIGHT_RATIO * 100}%)")
                Log.d(TAG, "════════════════════════════════════════════════════════")
                return decision
            }

            // Small side obstacle: caution
            if (heightRatio > 0.15f) {  // 15% threshold for side caution
                Log.d(TAG, "Decision: CAUTION (side obstacle, height ratio ${String.format("%.1f%%", heightRatio * 100)} > 15%)")
                Log.d(TAG, "════════════════════════════════════════════════════════")
                return ActionToken.CAUTION
            }
        }

        // No significant obstacles detected
        Log.d(TAG, "Decision: CLEAR (obstacles too small or in safe zones)")
        Log.d(TAG, "════════════════════════════════════════════════════════")
        return ActionToken.CLEAR
    }

    private fun getZone(det: Detection): String {
        // Calculate object center in normalized coordinates [0, 1]
        // Detections use model input size (320x320), so normalize by FRAME_WIDTH
        val cx_norm = det.cx / FRAME_WIDTH

        return when {
            cx_norm < LEFT_ZONE_END -> "LEFT"
            cx_norm > RIGHT_ZONE_START -> "RIGHT"
            else -> "CENTER"
        }
    }
}
