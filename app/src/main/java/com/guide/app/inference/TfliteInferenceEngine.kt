package com.guide.app.inference

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.guide.app.models.Detection
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TfliteInferenceEngine(private val context: Context) : InferenceEngine {

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var loadFailed = false
    private var loggedOnce = false

    private val inputSize = 320

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val modelPath = "models/detector.tflite"
            val labelsPath = "models/labels.txt"

            val modelBuffer = context.assets.open(modelPath).use { inputStream ->
                val buffer = ByteBuffer.allocateDirect(inputStream.available())
                buffer.order(ByteOrder.nativeOrder())
                val bytes = inputStream.readBytes()
                buffer.put(bytes)
                buffer
            }

            labels = context.assets.open(labelsPath).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readLines()
            }

            val options = Interpreter.Options()
            try {
                val nnApiDelegate = NnApiDelegate()
                options.addDelegate(nnApiDelegate)
            } catch (e: Exception) {
                Log.w(TAG, "NNAPI delegate not available, using CPU", e)
            }

            interpreter = Interpreter(modelBuffer, options)
            Log.i(TAG, "TFLite model loaded successfully")
        } catch (e: Exception) {
            loadFailed = true
            if (!loggedOnce) {
                Log.w(TAG, "Model loading failed (missing assets or error). Returning empty detections.", e)
                loggedOnce = true
            }
        }
    }

    override fun infer(image: ImageProxy): List<Detection> {
        if (loadFailed || interpreter == null) {
            return emptyList()
        }

        return try {
            val bitmap = imageProxyToBitmap(image)
            val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = bitmapToByteBuffer(resized)

            val outputShape = interpreter!!.getOutputTensor(0).shape()
            val numDetections = outputShape[1]
            val outputBuffer = Array(1) { Array(numDetections) { FloatArray(6) } }

            interpreter!!.run(inputBuffer, outputBuffer)

            // TFLite output format: [1, 300, 6] where 6 = [x1, y1, x2, y2, confidence, class_id]
            // Coordinates are NORMALIZED [0, 1] and NMS is already applied by the model
            val detections = mutableListOf<Detection>()
            for (i in 0 until numDetections) {
                val x1_norm = outputBuffer[0][i][0]
                val y1_norm = outputBuffer[0][i][1]
                val x2_norm = outputBuffer[0][i][2]
                val y2_norm = outputBuffer[0][i][3]
                val conf = outputBuffer[0][i][4]
                val classId = outputBuffer[0][i][5].toInt()

                // Confidence threshold matching Python implementation
                if (conf < 0.45f) continue

                // Scale normalized coordinates to image dimensions
                // Note: Using inputSize (320x320) as the reference since that's what model expects
                val x1 = x1_norm * inputSize
                val y1 = y1_norm * inputSize
                val x2 = x2_norm * inputSize
                val y2 = y2_norm * inputSize

                // Convert from [x1, y1, x2, y2] to [cx, cy, w, h] format for Detection model
                val w = x2 - x1
                val h = y2 - y1
                val cx = x1 + w / 2
                val cy = y1 + h / 2

                val label = if (classId >= 0 && classId < labels.size) {
                    labels[classId]
                } else {
                    "unclassified"
                }

                detections.add(Detection(label, conf, cx, cy, w, h))
            }

            // NMS is already applied by the TFLite model, so we return detections directly
            detections
        } catch (e: Exception) {
            Log.e(TAG, "Inference error, returning empty list", e)
            emptyList()
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            image.width,
            image.height,
            null
        )
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = intValues[pixel++]
                byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f)
                byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)
                byteBuffer.putFloat((value and 0xFF) / 255.0f)
            }
        }
        return byteBuffer
    }

    private fun performNMS(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        val sorted = detections.sortedByDescending { it.conf }
        val selected = mutableListOf<Detection>()

        for (det in sorted) {
            var shouldKeep = true
            for (kept in selected) {
                if (iou(det, kept) > iouThreshold) {
                    shouldKeep = false
                    break
                }
            }
            if (shouldKeep) {
                selected.add(det)
            }
        }
        return selected
    }

    private fun iou(a: Detection, b: Detection): Float {
        val x1 = maxOf(a.cx - a.w / 2, b.cx - b.w / 2)
        val y1 = maxOf(a.cy - a.h / 2, b.cy - b.h / 2)
        val x2 = minOf(a.cx + a.w / 2, b.cx + b.w / 2)
        val y2 = minOf(a.cy + a.h / 2, b.cy + b.h / 2)

        val inter = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val areaA = a.w * a.h
        val areaB = b.w * b.h
        val union = areaA + areaB - inter

        return if (union > 0) inter / union else 0f
    }

    companion object {
        private const val TAG = "TfliteInferenceEngine"
    }
}
