package com.example.beaconpass.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class FaceEmbeddingHelper(private val context: Context) {

    private var interpreter: Interpreter? = null

    companion object {
        private const val MODEL_NAME = "mobile_facenet.tflite"
        private const val INPUT_SIZE = 112
        private const val EMBEDDING_DIM = 192
        // MobileFaceNet calibrated verification threshold
        const val SIMILARITY_THRESHOLD = 0.80f
    }

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor = context.assets.openFd(MODEL_NAME)
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options().apply {
                setNumThreads(4) // Multithreading for fast on-device inference (<25ms)
            }
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Camera bitmap aur ML Kit bounding box se face embedding vector dynamically extract karta hai.
     */
    fun extractEmbedding(bitmap: Bitmap, boundingBox: Rect): FloatArray? {
        val tflite = interpreter
        if (tflite == null) {
            android.util.Log.e("FACE_EMBED", "CRITICAL: TFLite Interpreter is NULL!")
            return null
        }

        // 1. Safe cropping inside image boundaries
        val left = max(0, boundingBox.left)
        val top = max(0, boundingBox.top)
        val width = min(bitmap.width - left, boundingBox.width())
        val height = min(bitmap.height - top, boundingBox.height())

        if (width <= 0 || height <= 0) {
            android.util.Log.e("FACE_EMBED", "Invalid Crop: width=$width, height=$height")
            return null
        }

        val croppedFace = Bitmap.createBitmap(bitmap, left, top, width, height)
        val scaledFace = Bitmap.createScaledBitmap(croppedFace, INPUT_SIZE, INPUT_SIZE, true)

        // 2. Pre-process to direct ByteBuffer
        val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaledFace.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        inputBuffer.rewind()
        for (pixelValue in intValues) {
            val r = ((pixelValue shr 16) and 0xFF)
            val g = ((pixelValue shr 8) and 0xFF)
            val b = (pixelValue and 0xFF)

            // Normalize pixels to [-1.0, 1.0]
            inputBuffer.putFloat((r - 127.5f) / 128.0f)
            inputBuffer.putFloat((g - 127.5f) / 128.0f)
            inputBuffer.putFloat((b - 127.5f) / 128.0f)
        }

        // 3. Dynamically adapt to model's exact tensor output shape (e.g. [2, 128])
        val outputTensor = tflite.getOutputTensor(0)
        val outputShape = outputTensor.shape() // [2, 128]
        val dim0 = if (outputShape.isNotEmpty()) outputShape[0] else 1
        val dim1 = if (outputShape.size > 1) outputShape[1] else 128

        val outputArray = Array(dim0) { FloatArray(dim1) }

        // 4. Run inference
        tflite.run(inputBuffer, outputArray)

        android.util.Log.d("FACE_EMBED", "Successfully extracted embedding! Vector size: ${outputArray[0].size}")
        return outputArray[0]
    }
    /**
     * Do embedding vectors ke beech Cosine Similarity calculate karta hai (Range: -1.0 to 1.0).
     */
    fun calculateCosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in v1.indices) {
            dotProduct += (v1[i] * v2[i])
            normA += (v1[i] * v1[i])
            normB += (v2[i] * v2[i])
        }

        if (normA == 0.0 || normB == 0.0) return 0f

        return (dotProduct / (sqrt(normA) * sqrt(normB))).toFloat()
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}