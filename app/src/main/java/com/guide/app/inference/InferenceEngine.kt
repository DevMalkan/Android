package com.guide.app.inference

import androidx.camera.core.ImageProxy
import com.guide.app.models.Detection

interface InferenceEngine {
    fun infer(image: ImageProxy): List<Detection>
}
