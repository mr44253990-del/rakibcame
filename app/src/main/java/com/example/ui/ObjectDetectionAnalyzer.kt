package com.example.ui

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

class ObjectDetectionAnalyzer(
    private val onObjectsDetected: (List<DetectedObjectData>) -> Unit
) : ImageAnalysis.Analyzer {

    // Object Detector with classification enabled
    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableMultipleObjects()
        .enableClassification()  // Optional
        .build()

    private val objectDetector = ObjectDetection.getClient(options)
    
    // Also use Image Labeling for better broad category detection (Trees, Plants, etc)
    private val labelerOptions = ImageLabelerOptions.DEFAULT_OPTIONS
    private val labeler = ImageLabeling.getClient(labelerOptions)

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            // We run object detection and image labeling in parallel or sequence
            // For simplicity and specific labels like "Trees", labeler is often better for background
            
            objectDetector.process(image)
                .addOnSuccessListener { detectedObjects ->
                    val results = detectedObjects.map { obj ->
                        DetectedObjectData(
                            label = obj.labels.firstOrNull()?.text ?: "Object",
                            bounds = obj.boundingBox,
                            confidence = obj.labels.firstOrNull()?.confidence ?: 0f
                        )
                    }
                    
                    // If object detector has no labels, try broad image labeling
                    if (results.all { it.label == "Object" }) {
                        labeler.process(image)
                            .addOnSuccessListener { labels ->
                                val labelResults = labels.map { label ->
                                    DetectedObjectData(
                                        label = label.text,
                                        bounds = android.graphics.Rect(), // No bounds for entire image labels
                                        confidence = label.confidence
                                    )
                                }
                                onObjectsDetected(labelResults.take(3))
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        onObjectsDetected(results)
                        imageProxy.close()
                    }
                }
                .addOnFailureListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
