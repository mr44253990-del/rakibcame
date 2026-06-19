package com.example.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PanoramaHorizontal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Panorama360CaptureScreen(
    viewModel: CameraViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isCapturing by remember { mutableStateOf(false) }
    var captureProgress by remember { mutableStateOf(0f) }
    var yawRotation by remember { mutableStateOf(0f) }
    
    // Gyroscope tracking logic for realistic sweep
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        var lastTimestamp = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (isCapturing) {
                    if (lastTimestamp != 0L) {
                        val dt = (event.timestamp - lastTimestamp) * (1.0f / 1000000000.0f)
                        val axisY = event.values[1] // Rotation around Y axis
                        
                        // Accumulate yaw rotation roughly mapped to 360 sweep
                        yawRotation += (axisY * dt) * (180f / Math.PI.toFloat())
                        
                        // Update progress (Map ~360 degree rotation to 1.0 progress)
                        // Or if we just pan linearly
                        val targetDegrees = 120f // 120 deg panoramic sweep
                        captureProgress = (Math.abs(yawRotation) / targetDegrees).coerceIn(0f, 1f)
                    }
                    lastTimestamp = event.timestamp
                } else {
                    lastTimestamp = 0L
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
    
    // Auto complete tracking
    LaunchedEffect(captureProgress) {
        if (captureProgress >= 1f && isCapturing) {
            isCapturing = false
            viewModel.speakNow("Panorama capture stitched and secured.")
            
            // Save panorama image
            scope.launch {
                val db = com.example.data.CameraDatabase.getDatabase(context, scope).cameraDao()
                db.insertMedia(
                    com.example.data.CapturedMedia(
                        name = "PANORAMA_360_${System.currentTimeMillis()}.jpg",
                        uriPath = "https://images.unsplash.com/photo-1557971370-e728d13e8e81?ixlib=rb-4.0.3&auto=format&fit=crop&w=3000&q=80", // Simulated equirectangular spherical panorama
                        isVideo = false,
                        isPanorama = true, // KEY: It acts as a 360/pano rendering toggle
                        detectedObjects = "Spherical Space, Architecture, VR",
                        detectedScene = "VR 360 Sphere"
                    )
                )
            }
            delay(1000)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Underlay: Live Camera View
        CameraViewfinder(viewModel = viewModel)
        
        // Panning Overlays
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isCapturing) {
                Text(
                    text = "PAN DEVICE STEADILY ALONG HORIZON",
                    color = GoldMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Box(
                    modifier = Modifier.width(300.dp).height(4.dp).background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(captureProgress)
                            .background(LedGreen)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${(captureProgress * 100).toInt()}% Stitch Progress",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PanoramaHorizontal, contentDescription = null, tint = Color.LightGray)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "360° PANORAMA MODE",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        
        // Close Button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .padding(32.dp)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(Color(0x44000000))
        ) {
            Icon(Icons.Default.Close, contentDescription = "Exit 360", tint = Color.White)
        }
        
        // Shutter Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
        ) {
            Button(
                onClick = {
                    if (!isCapturing) {
                        yawRotation = 0f
                        captureProgress = 0f
                        isCapturing = true
                        viewModel.speakNow("Sweep the environment. Recording engine active.")
                    } else {
                        isCapturing = false
                        viewModel.speakNow("Capture aborted.")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isCapturing) LedRed else Color.White),
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                if (!isCapturing) {
                    Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.Transparent))
                } else {
                    Box(modifier = Modifier.size(24.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(Color.White))
                }
            }
            
            // Decorative outer ring
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    color = Color.White,
                    radius = size.minDimension / 2 + 10f,
                    style = Stroke(width = 4f)
                )
            }
        }
    }
}
