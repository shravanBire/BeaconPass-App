package com.example.beaconpass.features.auth.presentation

import android.graphics.Rect
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beaconpass.core.vision.CameraViewfinder
import com.example.beaconpass.core.vision.FaceEmbeddingHelper
import com.example.beaconpass.features.auth.data.AuthRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun FaceEnrollmentScreen(
    userId: String,
    onEnrollmentComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepository = remember { AuthRepository() }
    val embeddingHelper = remember { FaceEmbeddingHelper(context) }

    var statusText by remember { mutableStateOf("Position your face within the frame") }
    var isProcessing by remember { mutableStateOf(false) }
    var isComplete by remember { mutableStateOf(false) }

    val detector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(options)
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val faceAnalyzer = remember {
        ImageAnalysis.Analyzer { imageProxy ->
            if (isProcessing || isComplete) {
                imageProxy.close()
                return@Analyzer
            }

            @OptIn(ExperimentalGetImage::class)
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

                detector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.size == 1) {
                            val face = faces[0]

                            // Face size check
                            if (face.boundingBox.width() > 150 && face.boundingBox.height() > 150) {
                                statusText = "Face Detected. Processing vector..."
                                isProcessing = true

                                // 1. Convert ImageProxy to Bitmap
                                val rawBitmap = imageProxy.toBitmap()

                                // 2. Rotate Bitmap to match ML Kit's coordinate frame
                                val matrix = android.graphics.Matrix().apply {
                                    postRotate(rotationDegrees.toFloat())
                                }
                                val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                                    rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                                )

                                // 3. Extract 192-d embedding
                                val embedding = embeddingHelper.extractEmbedding(rotatedBitmap, face.boundingBox)

                                if (embedding != null) {
                                    scope.launch(Dispatchers.Main) {
                                        statusText = "Saving to secure database..."
                                        val saveResult = authRepository.saveFaceEmbedding(userId, embedding)
                                        saveResult.onSuccess {
                                            isComplete = true
                                            statusText = "Biometric Enrollment Successful!"
                                            Toast.makeText(context, "Face Registered Successfully", Toast.LENGTH_SHORT).show()
                                            onEnrollmentComplete()
                                        }.onFailure { err ->
                                            isProcessing = false
                                            statusText = "Upload error: ${err.localizedMessage}. Try again."
                                        }
                                    }
                                } else {
                                    isProcessing = false
                                    statusText = "Could not extract embedding. Look directly into camera."
                                }
                            } else {
                                statusText = "Move closer to the camera"
                            }
                        } else if (faces.size > 1) {
                            statusText = "Multiple faces detected. Ensure you are alone."
                        } else {
                            statusText = "Position your face within the frame"
                        }
                    }
                    .addOnFailureListener {
                        statusText = "Face detection error"
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            detector.close()
            embeddingHelper.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Biometric Registration",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = "Register your 1:1 face embedding to prevent proxy check-ins",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Oval Camera Viewport
            Box(
                modifier = Modifier
                    .size(260.dp, 340.dp)
                    .clip(RoundedCornerShape(130.dp))
                    .border(3.dp, if (isComplete) Color(0xFF10B981) else Color(0xFF2563EB), RoundedCornerShape(130.dp)),
                contentAlignment = Alignment.Center
            ) {
                CameraViewfinder(
                    modifier = Modifier.fillMaxSize(),
                    analyzer = faceAnalyzer
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF2563EB))
                    } else if (isComplete) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                    } else {
                        Icon(Icons.Default.Face, contentDescription = null, tint = Color(0xFF2563EB))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = statusText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E293B)
                    )
                }
            }
        }

        Text(
            text = "Your facial vector (192 floats) is mathematically secured and used strictly for on-device comparison.",
            fontSize = 11.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
}