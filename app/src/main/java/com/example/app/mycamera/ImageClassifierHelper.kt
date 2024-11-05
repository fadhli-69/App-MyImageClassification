package com.example.app.mycamera

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Classifications
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier

class ImageClassifierHelper(
    private val threshold: Float = 0.1f,
    private val maxResults: Int = 3,
    private val modelName: String = "mobilenet_v1.tflite",
    private val runningMode: RunningMode = RunningMode.LIVE_STREAM,
    val context: Context,
    val classifierListener: ClassifierListener?
) {
    private var imageClassifier: ImageClassifier? = null

    init {
        setupImageClassifier()
    }

    private fun setupImageClassifier() {
        val optionsBuilder = ImageClassifier.ImageClassifierOptions.builder()
            .setScoreThreshold(threshold)
            .setMaxResults(maxResults)
            .setRunningMode(runningMode)

        if (runningMode == RunningMode.LIVE_STREAM) {
            optionsBuilder.setResultListener { result, _ ->
                val finishTimeMs = SystemClock.uptimeMillis()
                val inferenceTime = finishTimeMs - result.timestampMs()
                classifierListener?.onResults(
                    result.classificationResult().classifications(),
                    inferenceTime
                )
            }.setErrorListener { error ->
                classifierListener?.onError(error.message.toString())
            }
        }

        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath(modelName)
        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        try {
            imageClassifier = ImageClassifier.createFromOptions(
                context,
                optionsBuilder.build()
            )
        } catch (e: IllegalStateException) {
            classifierListener?.onError(context.getString(R.string.image_classifier_failed))
            Log.e(TAG, e.message.toString())
        }
    }

    fun classifyImage(image: ImageProxy) {
        try {
            if (imageClassifier == null) {
                setupImageClassifier()
            }

            val bitmap = toBitmap(image)
            val mpImage = BitmapImageBuilder(bitmap).build()

            val imageProcessingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(image.imageInfo.rotationDegrees)
                .build()

            val inferenceTime = SystemClock.uptimeMillis()
            imageClassifier?.classifyAsync(mpImage, imageProcessingOptions, inferenceTime)
        } catch (e: Exception) {
            classifierListener?.onError("Error during classification: ${e.message}")
            Log.e(TAG, "Error during classification", e)
        }
    }

    private fun toBitmap(image: ImageProxy): Bitmap {
        val bitmapBuffer = Bitmap.createBitmap(
            image.width,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }
        image.close()
        return bitmapBuffer
    }

    interface ClassifierListener {
        fun onError(error: String)
        fun onResults(
            results: List<Classifications>?,
            inferenceTime: Long
        )
    }

    companion object {
        private const val TAG = "ImageClassifierHelper"
    }
}