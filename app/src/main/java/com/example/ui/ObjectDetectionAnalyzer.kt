package com.example.ui

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

class ObjectDetectionAnalyzer(
    private val viewModel: CameraViewModel,
    private val onObjectsDetected: (List<DetectedObjectData>) -> Unit
) : ImageAnalysis.Analyzer {

    private val objectDetector = ObjectDetection.getClient(ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableMultipleObjects().enableClassification().build())
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private val poseDetector = PoseDetection.getClient(PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
    private val barcodeScanner = BarcodeScanning.getClient()
    private val faceDetector = FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).build())

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return imageProxy.close()
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        
        val tasks = mutableListOf<Task<*>>()
        val results = mutableListOf<DetectedObjectData>()

        if (viewModel.isObjectDetectionEnabled.value) {
            val t1 = objectDetector.process(image).addOnSuccessListener { detected ->
                results.addAll(detected.map { obj ->
                    DetectedObjectData(obj.labels.firstOrNull()?.text ?: "Object", obj.boundingBox, obj.labels.firstOrNull()?.confidence ?: 0f)
                })
            }
            val t2 = labeler.process(image).addOnSuccessListener { labels ->
                results.addAll(labels.take(2).map { label ->
                    DetectedObjectData(label.text, android.graphics.Rect(), label.confidence)
                })
            }
            tasks.add(t1)
            tasks.add(t2)
        }

        if (viewModel.isFaceDetectionEnabled.value) {
            val t3 = faceDetector.process(image).addOnSuccessListener { faces ->
                results.addAll(faces.map { face ->
                    val smileProb = face.smilingProbability ?: 0f
                    
                    if (viewModel.isFaceGestureExposureEnabled.value) {
                        try {
                            if (smileProb > 0.65f) {
                                // Smiling increases exposure/brightness
                                val newVal = (viewModel.exposureCompensation.value + 0.2f).coerceAtMost(3.0f)
                                viewModel.exposureCompensation.value = newVal
                                CameraGlobals.cameraControl?.setExposureCompensationIndex((newVal * 2).toInt())
                            } else if (face.leftEyeOpenProbability != null && face.rightEyeOpenProbability != null) {
                                if (face.leftEyeOpenProbability!! < 0.3f && face.rightEyeOpenProbability!! < 0.3f) {
                                    // Closing/squinting eyes dims exposure/brightness
                                    val newVal = (viewModel.exposureCompensation.value - 0.2f).coerceAtLeast(-3.0f)
                                    viewModel.exposureCompensation.value = newVal
                                    CameraGlobals.cameraControl?.setExposureCompensationIndex((newVal * 2).toInt())
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore exceptions if preview instance is temporarily rebuilding
                        }
                    }
                    
                    val smileInfo = if (smileProb > 0.5f) " (Smiling)" else ""
                    DetectedObjectData("Face$smileInfo", face.boundingBox, smileProb)
                })
            }
            tasks.add(t3)
        }

        if (viewModel.isPoseDetectionEnabled.value) {
            val t4 = poseDetector.process(image).addOnSuccessListener { pose ->
                // Basic check if a whole pose is found
                if (pose.allPoseLandmarks.isNotEmpty()) {
                    var isStanding = false
                    // Simple logic for standing vs sitting could check hips vs ankles, but just saying "Pose" is enough for visualizer
                    val leftShoulder = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_SHOULDER)
                    var labelText = "Pose Detected"
                    if (leftShoulder != null) {
                        // Pose recommendation logic
                        viewModel.activeGesture.value = "Tip: Keep arms relaxed."
                    }
                    // For bounding box, we could enclose landmarks
                    results.add(DetectedObjectData(labelText, android.graphics.Rect(), 1.0f))
                }
            }
            tasks.add(t4)
        }

        if (viewModel.isBarcodeScanningEnabled.value) {
            val t5 = barcodeScanner.process(image).addOnSuccessListener { barcodes ->
                results.addAll(barcodes.map { barcode ->
                    DetectedObjectData("Barcode: ${barcode.rawValue}", barcode.boundingBox ?: android.graphics.Rect(), 1.0f)
                })
            }
            tasks.add(t5)
        }

        if (tasks.isEmpty()) {
            imageProxy.close()
            onObjectsDetected(emptyList())
        } else {
            Tasks.whenAllComplete(tasks).addOnCompleteListener {
                onObjectsDetected(results)
                imageProxy.close()
            }
        }
    }
}
