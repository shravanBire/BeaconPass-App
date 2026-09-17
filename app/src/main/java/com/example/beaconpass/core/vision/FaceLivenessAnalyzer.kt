package com.example.beaconpass.core.vision

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceLivenessAnalyzer(
    private val onLivenessPassed: () -> Unit,
    private val onFaceStatusUpdate: (String) -> Unit
) : ImageAnalysis.Analyzer {

    // ML Kit Configuration: Enable eye-open probability classification
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    private var eyesWereClosed = false
    private var isVerified = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isVerified) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isEmpty()) {
                        onFaceStatusUpdate("Align your face inside the oval")
                    } else {
                        val face = faces.first()
                        val leftOpen = face.leftEyeOpenProbability ?: -1.0f
                        val rightOpen = face.rightEyeOpenProbability ?: -1.0f

                        if (leftOpen in 0.0f..1.0f && rightOpen in 0.0f..1.0f) {
                            // Phase 1: Detect eyes closing (< 0.20)
                            if (leftOpen < 0.20f && rightOpen < 0.20f) {
                                eyesWereClosed = true
                                onFaceStatusUpdate("Eyes closed. Now open your eyes!")
                            }
                            // Phase 2: Detect eyes reopening (> 0.75) -> Liveness Validated
                            else if (eyesWereClosed && leftOpen > 0.75f && rightOpen > 0.75f) {
                                isVerified = true
                                onFaceStatusUpdate("Liveness Verified!")
                                onLivenessPassed()
                            } else if (!eyesWereClosed) {
                                onFaceStatusUpdate("Action Required: Blink both eyes slowly[cite: 1, 2]")
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    onFaceStatusUpdate("Face analyzer error")
                }
                .addOnCompleteListener {
                    imageProxy.close() // Mandatory: Release frame buffer for CameraX
                }
        } else {
            imageProxy.close()
        }
    }
}