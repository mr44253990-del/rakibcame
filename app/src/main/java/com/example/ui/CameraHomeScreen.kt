package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage
import com.example.data.CapturedMedia
import com.example.data.CustomCommand
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

// Themes and Styles for DSLR Dashboard (High Density Design Theme)
val CarbonDark = Color(0xFF0A0A0A)
val CarbonMedium = Color(0xFF121212)
val CharcoalGlass = Color(0x99000000) // Black/60 translucent glass
val GoldMuted = Color(0xFFF97316) // Vibrant High Density Orange-500 Accent
val LedGreen = Color(0xFF4CAF50)
val LedRed = Color(0xFFF44336)
val HUDTransparent = Color(0x7F101010)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class)
@Composable
fun CameraHomeScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1. App Startup Flow State & Permission Checking
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    val audioPermissionState = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)

    val hasPermissions = cameraPermissionState.status.isGranted && audioPermissionState.status.isGranted

    // Navigation and panels overlay state
    var showGalleryPanel by remember { mutableStateOf(false) }
    var showCustomCommandsPanel by remember { mutableStateOf(false) }
    var showAllGesturesHelp by remember { mutableStateOf(false) }
    var showVoiceHelp by remember { mutableStateOf(false) }
    var showSettingsPanel by remember { mutableStateOf(false) }

    // Quick Selectors in Left DSLR Drawer
    var activeManualModeSelector by remember { mutableStateOf("") } // "ISO", "SHUTTER", "WB", "FOCUS", ""

    // Voice control integration for UI panels
    val assistantBubble by viewModel.assistantBubble.collectAsState()
    LaunchedEffect(assistantBubble) {
        if (assistantBubble == "OPENING GALLERY...") {
            showGalleryPanel = true
        } else if (assistantBubble == "CONFIGURING SETTINGS...") {
            showSettingsPanel = true
            viewModel.speakNow(if (viewModel.currentLanguage.value == "Bengali") "সেটিংস খোলা হয়েছে।" else "Settings opened.")
        }
    }

    if (!hasPermissions) {
        // Startup splash permissions flow view
        StartupStartupScreen(
            cameraGranted = cameraPermissionState.status.isGranted,
            audioGranted = audioPermissionState.status.isGranted,
            onRequestCamera = { cameraPermissionState.launchPermissionRequest() },
            onRequestAudio = { audioPermissionState.launchPermissionRequest() },
            onStartEngine = {
                if (cameraPermissionState.status.isGranted && audioPermissionState.status.isGranted) {
                    viewModel.speakNow("camera initialization complete. voice recognition systems loaded.")
                } else {
                    Toast.makeText(context, "Please grant Camera & Microphone permissions first.", Toast.LENGTH_LONG).show()
                }
            }
        )
    } else {
        // Main Immersive DSLR Landscape HUD Core
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // BACK KEY handling to dismiss open panels first
            BackHandler(enabled = showGalleryPanel || showCustomCommandsPanel || showSettingsPanel || viewModel.selectedMediaItem.value != null || viewModel.activeEditingItem.value != null) {
                when {
                    viewModel.activeEditingItem.value != null -> viewModel.activeEditingItem.value = null
                    viewModel.selectedMediaItem.value != null -> viewModel.selectedMediaItem.value = null
                    showGalleryPanel -> showGalleryPanel = false
                    showCustomCommandsPanel -> showCustomCommandsPanel = false
                    showSettingsPanel -> showSettingsPanel = false
                }
            }

            // -----------------------------------------------------------------------------------------
            // LAYOUT LAYER 1: Dual-Preview Viewfinder (CameraX Preview + Simulated Scenery Overlays)
            // -----------------------------------------------------------------------------------------
            CameraViewfinder(viewModel = viewModel)

            // -----------------------------------------------------------------------------------------
            // LAYOUT LAYER 2: Drawing real-time bounding box tags, smile trackers, eye outlines
            // -----------------------------------------------------------------------------------------
            LiveAiTrackerOverlay(viewModel = viewModel)

            // -----------------------------------------------------------------------------------------
            // LAYOUT LAYER 3: Translucent Heads-Up Display (HUD Controls + Bars)
            // -----------------------------------------------------------------------------------------
            DslrHudTopBar(viewModel = viewModel)

            // Video Recording Timer (Flashy indicator)
            val isRecording by viewModel.isRecordingVideo.collectAsState()
            val recordDuration by viewModel.videoDurationSeconds.collectAsState()
            
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(LedRed)
                        )
                        Spacer(Modifier.width(8.dp))
                        val minutes = recordDuration / 60
                        val seconds = recordDuration % 60
                        Text(
                            text = String.format("%02d:%02d", minutes, seconds),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // DSLR Dial & Adjustments Column (Left Side)
            LeftDslrControlDrawer(
                viewModel = viewModel,
                activeSelector = activeManualModeSelector,
                onSelectorToggled = { activeManualModeSelector = if (activeManualModeSelector == it) "" else it }
            )

            // DSLR Quick Action Trigger Controls (Right Column)
            RightSideQuickButtons(
                viewModel = viewModel,
                modifier = Modifier.align(Alignment.CenterEnd),
                onToggleGallery = { showGalleryPanel = !showGalleryPanel },
                onToggleCustomCommands = { showCustomCommandsPanel = !showCustomCommandsPanel },
                onToggleSettings = { showSettingsPanel = !showSettingsPanel },
                onShowGesturesHelp = { showAllGesturesHelp = true },
                onShowVoiceHelp = { showVoiceHelp = true }
            )

            // -----------------------------------------------------------------------------------------
            // LAYOUT LAYER 4: Speech Companion Talking bubble and Listening prompts
            // -----------------------------------------------------------------------------------------
            BottomNotificationCenter(viewModel = viewModel)

            // -----------------------------------------------------------------------------------------
            // MODAL SHEET OVERLAYS: Gallery, Custom command boards, AI studio editors, Secure Vault pin
            // -----------------------------------------------------------------------------------------

            // 1. Sliding integrated Photo Gallery Panel
            AnimatedVisibility(
                visible = showGalleryPanel,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                modifier = Modifier.fillMaxHeight().width(520.dp).align(Alignment.CenterStart)
            ) {
                AppGalleryPanel(
                    viewModel = viewModel,
                    onDismiss = { showGalleryPanel = false }
                )
            }

            // 2. Custom Commands Designer & Tracker Panel
            AnimatedVisibility(
                visible = showCustomCommandsPanel,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.fillMaxWidth().height(320.dp).align(Alignment.TopCenter)
            ) {
                CustomCommandsPanel(
                    viewModel = viewModel,
                    onDismiss = { showCustomCommandsPanel = false }
                )
            }

            // 3. Settings Panel
            AnimatedVisibility(
                visible = showSettingsPanel,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(), // From Right
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.fillMaxHeight().width(320.dp).align(Alignment.CenterEnd)
            ) {
                AppSettingsPanel(
                    viewModel = viewModel,
                    onDismiss = { showSettingsPanel = false }
                )
            }

            // 4. AI Photo Details Modal Dialog
            viewModel.selectedMediaItem.value?.let { selectedItem ->
                PhotoDetailOverlay(
                    media = selectedItem,
                    viewModel = viewModel,
                    onDismiss = { viewModel.selectedMediaItem.value = null }
                )
            }

            // 4. AI Editing Studio Overlay
            viewModel.activeEditingItem.value?.let { activeItem ->
                AiEditingStudioOverlay(
                    media = activeItem,
                    viewModel = viewModel,
                    onDismiss = { viewModel.activeEditingItem.value = null }
                )
            }

            // 5. Visual Gestures Selector Menu
            if (showAllGesturesHelp) {
                SimpleGesturesHelpDialog(
                    onGestureSelected = {
                        viewModel.simulateGesture(it)
                        showAllGesturesHelp = false
                    },
                    onDismiss = { showAllGesturesHelp = false }
                )
            }

            // 6. Voice commands list help selector
            if (showVoiceHelp) {
                VoiceHelpGridDialog(
                    onCommandSelected = {
                        viewModel.applyVoiceCommand(it)
                        showVoiceHelp = false
                    },
                    onDismiss = { showVoiceHelp = false }
                )
            }

            // Shutter capture visual white flash indicator
            var triggerFlashAnim by remember { mutableStateOf(false) }
            val flashAlpha by animateFloatAsState(
                targetValue = if (triggerFlashAnim) 0f else 1f,
                animationSpec = tween(durationMillis = 300, easing = LinearEasing),
                label = "flash"
            )

            LaunchedFlows(viewModel, onCaptureAnimTrigger = {
                scope.launch {
                    triggerFlashAnim = true
                    delay(50)
                    triggerFlashAnim = false
                }
            })

            if (flashAlpha > 0.05f && triggerFlashAnim) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = flashAlpha))
                )
            }

            // LAST MEDIA THUMBNAIL PREVIEW
            val galleryMedia by viewModel.galleryMedia.collectAsState()
            val lastMedia = galleryMedia.lastOrNull()

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 32.dp, end = 32.dp)
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                    .clickable { showGalleryPanel = true },
                contentAlignment = Alignment.Center
            ) {
                if (lastMedia != null) {
                    androidx.compose.foundation.Image(
                        painter = coil.compose.rememberAsyncImagePainter(lastMedia.uriPath),
                        contentDescription = "Last captured",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = "Gallery",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

// LaunchedFlows observer helper
@Composable
private fun LaunchedFlows(viewModel: CameraViewModel, onCaptureAnimTrigger: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.galleryMedia.collect {
            // Whenever gallery updates, if it is a new item captured, blink screen
            if (it.isNotEmpty()) {
                onCaptureAnimTrigger()
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Component 1: App Permissions Splash screen
// -----------------------------------------------------------------------------
@Composable
fun StartupStartupScreen(
    cameraGranted: Boolean,
    audioGranted: Boolean,
    onRequestCamera: () -> Unit,
    onRequestAudio: () -> Unit,
    onStartEngine: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF18191A), Color(0xFF0C0D0E))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Aesthetic circuit mesh graphic
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stepX = size.width / 8
            val stepY = size.height / 6
            for (i in 1..7) {
                drawLine(
                    color = Color(0x0AFFFFFF),
                    start = Offset(i * stepX, 0f),
                    end = Offset(i * stepX, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            for (i in 1..5) {
                drawLine(
                    color = Color(0x0AFFFFFF),
                    start = Offset(0f, i * stepY),
                    end = Offset(size.width, i * stepY),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        Card(
            modifier = Modifier
                .width(480.dp)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonMedium.copy(alpha = 0.9f)),
            border = BorderStroke(1.dp, Color(0x33FFFFFF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "rakibcame logo",
                        tint = GoldMuted,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "rakibcame",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                }

                Text(
                    text = "Offline AI Voice & Gesture Controlled DSLR Engine",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Divider(color = Color(0x1AFFFFFF), thickness = 1.dp)

                // Permissions List Checked status indicators
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PermissionStatusRow(
                        title = "Camera Viewfinder Permission",
                        description = "Required to initialize CameraX lens hardware.",
                        isGranted = cameraGranted,
                        onGrantedRequest = onRequestCamera
                    )

                    PermissionStatusRow(
                        title = "Offline Audio / Mic Capture",
                        description = "Required for Continuous Listening Wake Words.",
                        isGranted = audioGranted,
                        onGrantedRequest = onRequestAudio
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onStartEngine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("initialize_camera_engine_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (cameraGranted && audioGranted) GoldMuted else Color.DarkGray
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "Start", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Launch Offline AI DSLR Hud",
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantedRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF141516))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Color.LightGray, fontSize = 10.sp)
        }
        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = LedGreen,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Button(
                onClick = onGrantedRequest,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33A9A9A9)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("Allow", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Component 2: Camera view finder (CameraX hardware + fallbacks)
// -----------------------------------------------------------------------------
@Composable
fun CameraViewfinder(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe DSLR adjusters to apply real-time filter overlays on simulated or camera feeds
    val focusValue by viewModel.manualFocus.collectAsState()
    val zoomValue by viewModel.zoomLevel.collectAsState()
    val isoValue by viewModel.iso.collectAsState()
    val wbValue by viewModel.whiteBalance.collectAsState()
    val exposureValue by viewModel.exposureCompensation.collectAsState()
    val activeSceneItem by viewModel.activeScene.collectAsState()
    val isLensFront by viewModel.currentCameraLens.collectAsState()
    val shutterTimerValue by viewModel.shutterTimer.collectAsState()
    
    // Auto-start persistent voice assistant
    LaunchedEffect(Unit) {
        viewModel.initializeSpeechRecognizer(context)
        viewModel.startListening()
    }

    // Volume key listener for shutter
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        val listener = android.view.View.OnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                    viewModel.capturePhoto(delaySeconds = shutterTimerValue)
                    return@OnKeyListener true
                }
            }
            false
        }
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener(listener)
        onDispose {
            view.setOnKeyListener(null)
        }
    }

    // Control zoom via CameraControl instead of rebinding
    LaunchedEffect(zoomValue) {
        CameraGlobals.cameraControl?.setZoomRatio(zoomValue)
    }

    // Base Blur configuration based on Manual Focus selector
    val visualBlurAmount = if (focusValue < 0.9f) {
        ((1.0f - focusValue) * 12).toInt().coerceAtLeast(1)
    } else 0

    // White balance color temperature multiplier matrix
    val tintFilterMatrix = remember(wbValue, exposureValue) {
        val brightnessShift = 1.0f + (exposureValue * 0.15f)
        val matrix = floatArrayOf(
            brightnessShift, 0f, 0f, 0f, 0f,
            0f, brightnessShift, 0f, 0f, 0f,
            0f, 0f, brightnessShift, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        // Adjust Temperature color values
        when (wbValue) {
            "Sunny" -> { // Warm Gold tint
                matrix[0] = matrix[0] * 1.15f // Red
                matrix[1] = matrix[1] * 1.05f // Green
                matrix[2] = matrix[2] * 0.85f // Blue
            }
            "Cloudy" -> { // Heavy warmth
                matrix[0] = matrix[0] * 1.25f // Red
                matrix[2] = matrix[2] * 0.75f // Blue
            }
            "Fluorescent" -> { // Cool Blue green
                matrix[0] = matrix[0] * 0.85f // Red
                matrix[1] = matrix[1] * 1.12f // Green
                matrix[2] = matrix[2] * 1.25f // Blue
            }
            "Incandescent" -> { // Heavy cooling blue
                matrix[0] = matrix[0] * 0.70f // Red
                matrix[2] = matrix[2] * 1.35f // Blue
            }
        }
        ColorMatrix(matrix)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    val newZoom = (zoomValue * zoom).coerceIn(1.0f, 10.0f)
                    viewModel.zoomLevel.value = newZoom
                }
            }
            .pointerInput(Unit) {
                // Handle dynamic screen touch focus clicks
                detectTapGestures { offset ->
                    viewModel.speakNow("Manual focus lock targeted at point - X ${offset.x.toInt()} Y ${offset.y.toInt()}%")
                }
            }
    ) {
        // ALWAYS LOAD THE HIGH-RESOLUTION IMAGE FOR EMULATOR PREVIEW STABILITY & ACCURATE OBJECT RANGING VISUALS
        // Also supports horizontal flip if lens is Front!
        val scaleTransition by animateFloatAsState(targetValue = zoomValue, animationSpec = tween(300), label = "zoomScale")

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (visualBlurAmount > 0) Modifier.blur(visualBlurAmount.dp) else Modifier)
        ) {
            key(isLensFront) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        previewView
                    },
                    update = { previewView ->
                        // Rebind only when lens changes
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                
                                val selector = if (isLensFront == "FRONT") {
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                } else {
                                    CameraSelector.DEFAULT_BACK_CAMERA
                                }
                                
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                
                                val imageCapture = androidx.camera.core.ImageCapture.Builder().build()
                                
                                // NEW: Object Detection Analyzer
                                val analyzer = ObjectDetectionAnalyzer(viewModel) { results ->
                                    viewModel.detectedObjectResults.value = results
                                }
                                val imageAnalyzer = androidx.camera.core.ImageAnalysis.Builder()
                                    .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also {
                                        it.setAnalyzer(ContextCompat.getMainExecutor(previewView.context), analyzer)
                                    }

                                val recorder = androidx.camera.video.Recorder.Builder()
                                    .setQualitySelector(androidx.camera.video.QualitySelector.from(androidx.camera.video.Quality.HIGHEST, androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(androidx.camera.video.Quality.SD)))
                                    .build()
                                val videoCapture = androidx.camera.video.VideoCapture.withOutput(recorder)

                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture, imageAnalyzer, videoCapture)
                                
                                CameraGlobals.cameraControl = camera.cameraControl
                                CameraGlobals.imageCapture = imageCapture
                                CameraGlobals.videoCapture = videoCapture
                                
                                // Re-apply states
                                CameraGlobals.cameraControl?.setZoomRatio(zoomValue)
                            } catch (e: Throwable) {
                                // ignore
                            }
                        }, ContextCompat.getMainExecutor(previewView.context))
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            // Drawing professional alignment grid lines
                            val verticalThree = size.width / 3
                            val horizontalThree = size.height / 3
                            drawLine(
                                color = Color(0x3FF6F6F6),
                                start = Offset(verticalThree, 0f),
                                end = Offset(verticalThree, size.height),
                                strokeWidth = 0.5.dp.toPx()
                            )
                            drawLine(
                                color = Color(0x3FF6F6F6),
                                start = Offset(verticalThree * 2, 0f),
                                end = Offset(verticalThree * 2, size.height),
                                strokeWidth = 0.5.dp.toPx()
                            )
                            drawLine(
                                color = Color(0x3FF6F6F6),
                                start = Offset(0f, horizontalThree),
                                end = Offset(size.width, horizontalThree),
                                strokeWidth = 0.5.dp.toPx()
                            )
                            drawLine(
                                color = Color(0x3FF6F6F6),
                                start = Offset(0f, horizontalThree * 2),
                                end = Offset(size.width, horizontalThree * 2),
                                strokeWidth = 0.5.dp.toPx()
                            )
                        }
                )
            }
        }

        // Portrait Mode Depth-of-Field Simulation
        val activeMode by viewModel.currentCameraMode.collectAsState()
        if (activeMode == "Portrait") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                            radius = 600f
                        )
                    )
            )
            // Center focus circle
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .border(1.dp, LedGreen.copy(alpha = 0.4f), CircleShape)
            )
        }

        // Smart Horizon Level Indicator
        val horizonAngle by viewModel.horizonAngle.collectAsState()
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(200.dp, 2.dp)
                .graphicsLayer { rotationZ = horizonAngle }
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, LedGreen.copy(alpha = 0.8f), Color.Transparent)
                    )
                )
        )
        // Center mark
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(10.dp)
                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
        )

        // Camera mode visual filters and overlays
        if (activeMode == "Night") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x2E1A237E)) // subtle navy tint to represent high sensor noise
            )
        }

        // Simulated Camera Lens View (High Density Vignette)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                        radius = 800f
                    )
                )
        )
    }
}

// -----------------------------------------------------------------------------
// Component 3: Live AI overlay trackers (drawing real-time target markers)
// -----------------------------------------------------------------------------
@Composable
fun LiveAiTrackerOverlay(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val isAiActive by viewModel.isAiDetectionActive.collectAsState()
    val activeSceneItem by viewModel.activeScene.collectAsState()
    val detectedObjects by viewModel.detectedObjectResults.collectAsState()

    if (!isAiActive) return
    
    // UI drawing for both simulated targets and REAL ML Kit detections
    Box(modifier = modifier.fillMaxSize()) {
        // 1. Real Detections from ML Kit
        Canvas(modifier = Modifier.fillMaxSize()) {
            detectedObjects.forEach { obj ->
                // Note: Coordinates might need scaling if preview != image size
                // For simplicity, we just draw what we get
                drawRect(
                    color = LedGreen.copy(alpha = 0.3f),
                    topLeft = Offset(obj.bounds.left.toFloat(), obj.bounds.top.toFloat()),
                    size = Size(obj.bounds.width().toFloat(), obj.bounds.height().toFloat()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        
        // 2. Labels for Real Detections
        detectedObjects.forEach { obj ->
            Box(
                modifier = Modifier
                    .offset(x = obj.bounds.left.dp, y = obj.bounds.top.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CharcoalGlass)
                    .padding(4.dp)
            ) {
                Text(
                    text = "${obj.label} ${(obj.confidence * 100).toInt()}%",
                    color = LedGreen,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Component 4: Heads-Up Display Top Bar
// -----------------------------------------------------------------------------
@Composable
fun DslrHudTopBar(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val listeningState by viewModel.audioListeningState.collectAsState()
    val stabilizationOn by viewModel.isStabilizationActive.collectAsState()
    val hdrOn by viewModel.isHdrActive.collectAsState()
    val cameraMode by viewModel.currentCameraMode.collectAsState()

    val listeningPulseAnimation = rememberInfiniteTransition(label = "listening")
    val audioGlowValue by listeningPulseAnimation.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speak"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(CharcoalGlass)
                .border(BorderStroke(0.5.dp, Color(0x1BFFFFFF)), RoundedCornerShape(30.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // HUD Status Identifiers (Left Segment)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Audio Mic Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (listeningState.contains("Processing")) Icons.Default.VolumeUp else Icons.Default.Mic,
                        contentDescription = "Mic",
                        tint = if (listeningState != "Idle") LedRed.copy(alpha = audioGlowValue) else Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (listeningState != "Idle") "DICTATING" else "AUTO-LISTEN ON",
                        color = if (listeningState != "Idle") LedRed else LedGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // AI Neural Heartbeat
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val transition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by transition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                        label = "pulse"
                    )
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Neural Heartbeat",
                        tint = LedRed,
                        modifier = Modifier.size(12.dp).graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                    )
                    Text(
                        text = "SYNC-OK",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Shutter Sound & Flash Segment
                val isFlashOn by viewModel.isFlashEnabled.collectAsState()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.isFlashEnabled.value = !isFlashOn },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            tint = if (isFlashOn) GoldMuted else Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = if (isFlashOn) "FLASH AUTO" else "FLASH OFF",
                        color = if (isFlashOn) GoldMuted else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // AI Network mode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = "Cloud",
                        tint = GoldMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "LOCAL-NET",
                        color = GoldMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Divider(
                    color = Color(0x33FFFFFF),
                    modifier = Modifier
                        .height(14.dp)
                        .width(1.dp)
                )

                // Battery Meter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = "Battery",
                        tint = LedGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text("84%", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }

                // Memory Info
                Text(
                    text = "MEM: 124 GB FREE",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Specs status values (Right Segment)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Stability Sign
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { viewModel.isStabilizationActive.value = !stabilizationOn }
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = "Stabilizer",
                        tint = if (stabilizationOn) LedGreen else Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "OIS",
                        color = if (stabilizationOn) LedGreen else Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // HDR Sign
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { viewModel.isHdrActive.value = !hdrOn }
                ) {
                    Icon(
                        imageVector = Icons.Default.HdrOn,
                        contentDescription = "HDR",
                        tint = if (hdrOn) GoldMuted else Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "HDR",
                        color = if (hdrOn) GoldMuted else Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Video FPS / Resolution Spec
                Text(
                    text = "60 FPS  |  4K UHD",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                // Current Camera Mode Pill Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(GoldMuted)
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = cameraMode.uppercase(),
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Component 5: Left DSLR control drawer (ISO, Shutter, Focus wheel dials)
// -----------------------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LeftDslrControlDrawer(
    viewModel: CameraViewModel,
    activeSelector: String,
    onSelectorToggled: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shutterTimerValue by viewModel.shutterTimer.collectAsState()
    val selectedWb by viewModel.whiteBalance.collectAsState()
    val manualBlurFocus by viewModel.manualFocus.collectAsState()
    val isoRatio by viewModel.iso.collectAsState()
    val shutter by viewModel.shutterSpeed.collectAsState()
    val sceneSelected by viewModel.activeScene.collectAsState()

    Row(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 16.dp, top = 60.dp, bottom = 60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Rotary Wheel Tab triggers List Column
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(CharcoalGlass)
                .border(BorderStroke(0.5.dp, Color(0x2EFFFFFF)), RoundedCornerShape(30.dp))
                .padding(vertical = 12.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ISO Selector button
            DslrDialTriggerButton(
                label = "ISO",
                activeValue = isoRatio.toString(),
                isSelected = activeSelector == "ISO",
                onClick = { onSelectorToggled("ISO") }
            )

            // Shutter Trigger button
            DslrDialTriggerButton(
                label = "SHT",
                activeValue = shutter,
                isSelected = activeSelector == "SHUTTER",
                onClick = { onSelectorToggled("SHUTTER") }
            )

            // White Balance Trigger button
            DslrDialTriggerButton(
                label = "W/B",
                activeValue = selectedWb,
                isSelected = activeSelector == "WB",
                onClick = { onSelectorToggled("WB") }
            )

            // Manual Focus Trigger button
            DslrDialTriggerButton(
                label = "FCS",
                activeValue = if (manualBlurFocus >= 0.95f) "INF" else "${(manualBlurFocus * 10).toInt()}",
                isSelected = activeSelector == "FOCUS",
                onClick = { onSelectorToggled("FOCUS") }
            )

            // Timer Trigger button
            DslrDialTriggerButton(
                label = "TMR",
                activeValue = if (shutterTimerValue == 0) "OFF" else "${shutterTimerValue}s",
                isSelected = activeSelector == "TIMER",
                onClick = { onSelectorToggled("TIMER") }
            )

            Divider(color = Color(0x33FFFFFF), modifier = Modifier.width(36.dp), thickness = 0.5.dp)

            // Scene Swap trigger (cycles beautiful mock live scenery feeds)
            IconButton(
                onClick = { viewModel.cycleScene() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF263238))
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterVintage,
                    contentDescription = "Cycle mock scenario views",
                    tint = GoldMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = sceneSelected.name.uppercase(),
                color = GoldMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // Horizontal Slider Adjuster Shelf pop-up based on active trigger selections
        AnimatedVisibility(
            visible = activeSelector.isNotEmpty(),
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CharcoalGlass)
                    .border(BorderStroke(0.5.dp, GoldMuted.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MANUAL ${activeSelector.uppercase()}",
                        color = GoldMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    when (activeSelector) {
                        "ISO" -> {
                            val isoValues = listOf(100, 200, 400, 800, 1600, 3200, 6400)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                isoValues.forEach { item ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isoRatio == item) GoldMuted else Color(0x1BFFFFFF))
                                            .clickable { viewModel.iso.value = item }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = item.toString(),
                                            color = if (isoRatio == item) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        "SHUTTER" -> {
                            val shutterSpeeds = listOf("1/4000", "1/2000", "1/1000", "1/500", "1/250", "1/125", "1/60", "1/30", "1/15", "1/8", "1s")
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                shutterSpeeds.forEach { item ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (shutter == item) GoldMuted else Color(0x1BFFFFFF))
                                            .clickable { viewModel.shutterSpeed.value = item }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = item,
                                            color = if (shutter == item) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        "WB" -> {
                            val wbOptions = listOf("Auto", "Sunny", "Cloudy", "Fluorescent", "Incandescent")
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(wbOptions) { item ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (selectedWb == item) GoldMuted else Color(0x1BFFFFFF))
                                            .clickable { viewModel.whiteBalance.value = item }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = item,
                                            color = if (selectedWb == item) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        "FOCUS" -> {
                            Column {
                                Slider(
                                    value = manualBlurFocus,
                                    onValueChange = { viewModel.manualFocus.value = it },
                                    colors = SliderDefaults.colors(
                                        thumbColor = GoldMuted,
                                        activeTrackColor = GoldMuted,
                                        inactiveTrackColor = Color.LightGray
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Macro (Blur)", color = Color.LightGray, fontSize = 9.sp)
                                    Text("Infinity", color = Color.LightGray, fontSize = 9.sp)
                                }
                            }
                        }
                        "TIMER" -> {
                            val timerOptions = listOf(0, 3, 10)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                timerOptions.forEach { t ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (shutterTimerValue == t) GoldMuted else Color(0x1BFFFFFF))
                                            .clickable { viewModel.shutterTimer.value = t }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (t == 0) "OFF" else "${t}s",
                                            color = if (shutterTimerValue == t) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DslrDialTriggerButton(
    label: String,
    activeValue: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .size(width = 46.dp, height = 46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) GoldMuted else Color(0x0EFFFFFF))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else Color.Gray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = activeValue,
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -----------------------------------------------------------------------------
// Component 6: DSLR Quick action control triggers (Right Side Column)
// -----------------------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RightSideQuickButtons(
    viewModel: CameraViewModel,
    onToggleGallery: () -> Unit,
    onToggleCustomCommands: () -> Unit,
    onToggleSettings: () -> Unit,
    onShowGesturesHelp: () -> Unit,
    onShowVoiceHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shutterTimerValue by viewModel.shutterTimer.collectAsState()
    val isRecording by viewModel.isRecordingVideo.collectAsState()
    val isPauseRec by viewModel.isVideoPaused.collectAsState()
    val recordDuration by viewModel.videoDurationSeconds.collectAsState()
    val isAiActive by viewModel.isAiDetectionActive.collectAsState()

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = 16.dp, top = 60.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP HALF: Quick Feature Switchers
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(CharcoalGlass)
                .padding(vertical = 12.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Settings Menu Toggle
            IconButton(
                onClick = onToggleSettings,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x1BFFFFFF))
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // AI Overlay on/off switch
            IconButton(
                onClick = { viewModel.isAiDetectionActive.value = !isAiActive },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isAiActive) GoldMuted else Color(0x1BFFFFFF))
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Mode Toggle",
                    tint = if (isAiActive) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Lens Swappper (Front/Back)
            IconButton(
                onClick = { viewModel.toggleCameraLens() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x1BFFFFFF))
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Flip Lens",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Custom Voice command setup board trigger
            IconButton(
                onClick = onToggleCustomCommands,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x1BFFFFFF))
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddModerator,
                    contentDescription = "Custom Script Builder",
                    tint = GoldMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Gestures HUD Trigger
            IconButton(
                onClick = onShowGesturesHelp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x1BFFFFFF))
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SignLanguage,
                    contentDescription = "Show AI Hand Gestures list",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Speech HUD trigger helper
            val context = LocalContext.current
            IconButton(
                onClick = { 
                    viewModel.initializeSpeechRecognizer(context)
                    viewModel.startListening()
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(GoldMuted.copy(alpha = 0.2f))
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardVoice,
                    contentDescription = "Simulate voice scripts",
                    tint = GoldMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // BOTTOM HALF: Capture Action Group
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Mode choice vertical spinner
            ModeWheelChoiceSpinner(viewModel = viewModel)

            // Dynamic Shutter/Record buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Video Rec Shutter
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) LedRed else Color(0x33F44336))
                        .border(BorderStroke(2.dp, Color.White), CircleShape)
                        .clickable {
                            if (isRecording) {
                                viewModel.stopVideo()
                            } else {
                                viewModel.startVideo()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        AnimatedRecordingTick(recordDuration = recordDuration)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(LedRed)
                        )
                    }
                }

                // Primary PHOTO capture shutter
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = { 
                                viewModel.capturePhoto(delaySeconds = shutterTimerValue) 
                            },
                            onLongClick = {
                                if (!isRecording) {
                                    viewModel.startVideo()
                                }
                            }
                        )
                        .background(Color.White)
                        .border(BorderStroke(4.dp, Color.DarkGray), CircleShape)
                        .testTag("photo_capture_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) LedRed else Color.White)
                            .border(BorderStroke(1.5.dp, Color.Black), CircleShape)
                    )
                }

                // Quick photo gallery opener thumbnail
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CarbonMedium)
                        .border(BorderStroke(1.5.dp, Color.White), RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleGallery),
                    contentAlignment = Alignment.Center
                ) {
                    val mediaList by viewModel.galleryMedia.collectAsState()
                    if (mediaList.isNotEmpty()) {
                        AsyncImage(
                            model = mediaList.first().uriPath,
                            contentDescription = "Last Shot Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = Color.LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModeWheelChoiceSpinner(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val modes = listOf("Auto", "Pro", "Portrait", "Night", "Macro", "Scanner")
    val selectedMode by viewModel.currentCameraMode.collectAsState()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CharcoalGlass)
            .border(BorderStroke(0.5.dp, Color(0x33FFFFFF)), RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.take(3).forEach { mode ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedMode == mode) GoldMuted else Color.Transparent)
                    .clickable { viewModel.currentCameraMode.value = mode }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = mode.uppercase(),
                    color = if (selectedMode == mode) Color.Black else Color.White.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun AnimatedRecordingTick(recordDuration: Int) {
    val secondsStr = remember(recordDuration) {
        val mins = recordDuration / 60
        val secs = recordDuration % 60
        String.format("%02d:%02d", mins, secs)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White)
        )
        Text(
            text = secondsStr,
            color = Color.White,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// -----------------------------------------------------------------------------
// Component 7: Bottom voice notification bubble panel
// -----------------------------------------------------------------------------
@Composable
fun BottomNotificationCenter(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val assistantPhrase by viewModel.assistantBubble.collectAsState()
    val gesturePhrase by viewModel.activeGesture.collectAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // High density animated pulsing waveform
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val transition = rememberInfiniteTransition(label = "wave")
                
                val height1 by transition.animateFloat(
                    initialValue = 6f,
                    targetValue = 18f,
                    animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "h1"
                )
                val height2 by transition.animateFloat(
                    initialValue = 12f,
                    targetValue = 24f,
                    animationSpec = infiniteRepeatable(tween(350, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
                    label = "h2"
                )
                val height3 by transition.animateFloat(
                    initialValue = 8f,
                    targetValue = 20f,
                    animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "h3"
                )
                val height4 by transition.animateFloat(
                    initialValue = 14f,
                    targetValue = 8f,
                    animationSpec = infiniteRepeatable(tween(400, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
                    label = "h4"
                )

                Box(modifier = Modifier.size(2.dp, height1.dp).background(Color(0xFF60A5FA), RoundedCornerShape(1.dp)))
                Box(modifier = Modifier.size(2.dp, height2.dp).background(Color(0xFF60A5FA), RoundedCornerShape(1.dp)))
                Box(modifier = Modifier.size(2.dp, height3.dp).background(Color(0xFF60A5FA), RoundedCornerShape(1.dp)))
                Box(modifier = Modifier.size(2.dp, height4.dp).background(Color(0xFF60A5FA), RoundedCornerShape(1.dp)))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Smart talking bubble description card
                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalGlass.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, GoldMuted.copy(alpha = 0.4f)),
                    modifier = Modifier.widthIn(max = 400.dp)
                ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(GoldMuted.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "Voice Guide Speak Bubble",
                            tint = GoldMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = assistantPhrase,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // Gesture feedback indicator alert
            AnimatedVisibility(
                visible = gesturePhrase != "None",
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = LedGreen),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gesture,
                            contentDescription = "Gesture",
                            tint = Color.Black,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = gesturePhrase.uppercase(),
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
}

// -----------------------------------------------------------------------------
// Component 8: Gallery listing drawer sheet
// -----------------------------------------------------------------------------
@Composable
fun AppGalleryPanel(
    viewModel: CameraViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchStr by viewModel.searchSqlQuery.collectAsState()
    val activeTab by viewModel.activeGalleryTab.collectAsState()
    val isLocked by viewModel.isVaultLocked.collectAsState()
    val pinValue by viewModel.vaultPinCode.collectAsState()

    val scope = rememberCoroutineScope()

    // Query database with search context
    val publicMediaList by viewModel.galleryMedia.collectAsState()
    val secureMediaList by viewModel.secureMedia.collectAsState()

    // Search query locally for maximum filtering speed
    val filteredList = remember(publicMediaList, secureMediaList, searchStr, activeTab, isLocked) {
        val src = if (activeTab == "Vault") {
            if (isLocked) emptyList() else secureMediaList
        } else {
            publicMediaList
        }

        src.filter { item ->
            val matchSearchAndObjects = if (searchStr.isNotEmpty()) {
                item.name.contains(searchStr, ignoreCase = true) ||
                        item.detectedObjects.contains(searchStr, ignoreCase = true) ||
                        item.detectedScene.contains(searchStr, ignoreCase = true)
            } else true

            val matchCategory = when (activeTab) {
                "Videos" -> item.isVideo
                "Favorites" -> item.isFavorite
                else -> true
            }

            matchSearchAndObjects && matchCategory
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonDark.copy(alpha = 0.95f))
            .border(BorderStroke(1.dp, Color(0x1BFFFFFF)), RoundedCornerShape(0.dp))
            .padding(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Gallery",
                        tint = GoldMuted
                    )
                    Text(
                        text = " rakibcame Preview Gallery",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchStr,
                onValueChange = { viewModel.searchSqlQuery.value = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search offline: 'dog', 'sunset', 'pizza'...", fontSize = 12.sp, color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = GoldMuted,
                    unfocusedBorderColor = Color.DarkGray
                ),
                shape = RoundedCornerShape(8.dp)
            )

            // Category Tab Chips row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = listOf("All", "Favorites", "Videos", "Vault")
                tabs.forEach { tab ->
                    val isTabActive = activeTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isTabActive) GoldMuted else Color(0x0EFFFFFF))
                            .border(BorderStroke(0.5.dp, if (isTabActive) GoldMuted else Color.DarkGray), RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.activeGalleryTab.value = tab
                                if (tab == "Vault") {
                                    viewModel.vaultPinCode.value = ""
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (tab == "Vault") {
                                Icon(
                                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = "Secure lock icon",
                                    tint = if (isTabActive) Color.Black else Color.Gray,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text(
                                text = tab,
                                color = if (isTabActive) Color.Black else Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Secure Folders passcode barrier view
            if (activeTab == "Vault" && isLocked) {
                SecureVaultLockOverlay(
                    pinString = pinValue,
                    onPinPressed = { char ->
                        if (pinValue.length < 4) {
                            viewModel.vaultPinCode.value = pinValue + char
                            if (viewModel.vaultPinCode.value == "1234") {
                                viewModel.isVaultLocked.value = false
                                viewModel.speakNow("Identity verified. Decrypting secure ledger assets.")
                            }
                        }
                    },
                    onClearPin = { viewModel.vaultPinCode.value = "" }
                )
            } else {
                // Main Grid of media thumbnail objects
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "None", tint = Color.DarkGray, modifier = Modifier.size(48.dp))
                            Text("No captured assets match filter.", color = Color.Gray, fontSize = 12.sp)
                            Text("Try spelling 'Sunset', 'Dog', 'Pizza' or 'Car'", color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredList, key = { it.id }) { media ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CarbonMedium)
                                    .border(BorderStroke(0.5.dp, Color(0x1BFFFFFF)))
                                    .clickable { viewModel.selectedMediaItem.value = media }
                            ) {
                                AsyncImage(
                                    model = media.uriPath,
                                    contentDescription = media.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // Indicators tags: video badge, favorite heart
                                if (media.isVideo) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black.copy(alpha = 0.7f))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(10.dp))
                                            Text("VIDEO", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (media.isFavorite) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = "Favorite Core",
                                        tint = LedRed,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecureVaultLockOverlay(
    pinString: String,
    onPinPressed: (String) -> Unit,
    onClearPin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.Lock, contentDescription = "Secured Vault", tint = GoldMuted, modifier = Modifier.size(40.dp))
        Text(
            text = "FINGERPRINT / PASSCODE BARRIER",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Enter safe ledger passcode to unpack encrypted media. Note: Demo PIN is 1234",
            color = Color.LightGray,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )

        // PIN placeholder dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (i in 1..4) {
                val filled = pinString.length >= i
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (filled) GoldMuted else Color.DarkGray)
                        .border(BorderStroke(1.dp, Color.LightGray), CircleShape)
                )
            }
        }

        // Mechanical Numpad keys grid
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.width(180.dp)
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "OK")
            )
            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { k ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.5f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(CarbonMedium)
                                .clickable {
                                    if (k == "C") {
                                        onClearPin()
                                    } else if (k != "OK") {
                                        onPinPressed(k)
                                    }
                                }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(k, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Component 9: Custom commands setup board drawer
// -----------------------------------------------------------------------------
@Composable
fun CustomCommandsPanel(
    viewModel: CameraViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val registeredScripts by viewModel.customVoiceCommands.collectAsState()

    var textPhrase by remember { mutableStateOf("") }
    var selectedLens by remember { mutableStateOf("FRONT") }
    var selectTimer by remember { mutableStateOf(3) }
    var selectedFilter by remember { mutableStateOf("BEAUTY") }
    var selectedRes by remember { mutableStateOf("1080P") }
    var selectedAction by remember { mutableStateOf("PHOTO") }
    var selectedZoom by remember { mutableStateOf(1.0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonDark)
            .border(BorderStroke(1.dp, Color(0x22FFFFFF)), RoundedCornerShape(0.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Column: Register New Auto Routine form
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Register Voice Routine Script", color = GoldMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                OutlinedTextField(
                    value = textPhrase,
                    onValueChange = { textPhrase = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Trig Voice Phrase: e.g. 'rakib selfie'", fontSize = 11.sp, color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GoldMuted,
                        unfocusedBorderColor = Color.DarkGray
                    )
                )

                // Action Type & Zoom Selector Choice Grid
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Action toggle
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ACTION TYPE", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("PHOTO", "VIDEO").forEach { action ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (selectedAction == action) GoldMuted else Color(0x0EFFFFFF))
                                        .clickable { selectedAction = action }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(action, color = if (selectedAction == action) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Zoom level
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ZOOM LEVEL: ${String.format("%.1f", selectedZoom)}x", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Slider(
                            value = selectedZoom,
                            onValueChange = { selectedZoom = it },
                            valueRange = 1.0f..10.0f,
                            colors = SliderDefaults.colors(thumbColor = GoldMuted, activeTrackColor = GoldMuted)
                        )
                    }
                }

                // Lens & Timer Choice Grid
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Lens toggle
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CAMERA LENS", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("FRONT", "BACK").forEach { lens ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (selectedLens == lens) GoldMuted else Color(0x0EFFFFFF))
                                        .clickable { selectedLens = lens }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(lens, color = if (selectedLens == lens) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Timer chooser
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SELF-TIMER", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(0, 3, 5).forEach { sec ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (selectTimer == sec) GoldMuted else Color(0x0EFFFFFF))
                                        .clickable { selectTimer = sec }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${sec}s", color = if (selectTimer == sec) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Filter & Resolution Choice Grid
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Filter selection
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CREATIVE STUDIO PRESETS", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("BEAUTY", "CINEMATIC", "HDR").forEach { filter ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (selectedFilter == filter) GoldMuted else Color(0x0EFFFFFF))
                                        .clickable { selectedFilter = filter }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(filter, color = if (selectedFilter == filter) Color.Black else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Resolution trigger
                    Column(modifier = Modifier.weight(1f)) {
                        Text("RESOLUTION TARGETS", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("1080P", "4K").forEach { res ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (selectedRes == res) GoldMuted else Color(0x0EFFFFFF))
                                        .clickable { selectedRes = res }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(res, color = if (selectedRes == res) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Submit Button
                Button(
                    onClick = {
                        if (textPhrase.isNotEmpty()) {
                            viewModel.addCustomCommand(
                                phrase = textPhrase,
                                camera = selectedLens,
                                timer = selectTimer,
                                filter = selectedFilter,
                                resolution = selectedRes,
                                stabilization = true,
                                zoom = selectedZoom,
                                action = selectedAction
                            )
                            textPhrase = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldMuted),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Register Neural Phrase", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Vertical divider
            Divider(color = Color(0x1BFFFFFF), modifier = Modifier.fillMaxHeight().width(1.dp))

            // Right Column: Active Voice Commands List Tracker
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Live Neural Routines", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(registeredScripts) { script ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF161718))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Adjust, contentDescription = "Script", tint = GoldMuted, modifier = Modifier.size(12.dp))
                                    Text("\"${script.phrase}\"", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = "Preset: ${script.cameraSelection} Lens + ${script.timerSeconds}s Custom Timer + Mode: ${script.filterType}",
                                    color = Color.LightGray,
                                    fontSize = 9.sp
                                )
                            }

                            if (!script.isSystem) {
                                IconButton(
                                    onClick = { viewModel.removeCustomCommand(script.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LedRed, modifier = Modifier.size(16.dp))
                                }
                            } else {
                                Icon(Icons.Default.Verified, contentDescription = "Built-in System command", tint = LedGreen, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Component 10: Photo Detail Dialog Sheet
// -----------------------------------------------------------------------------
@Composable
fun PhotoDetailOverlay(
    media: CapturedMedia,
    viewModel: CameraViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(560.dp)
                .wrapContentHeight()
                .clickable(enabled = false) {}, // prevent click-through dismissal
            colors = CardDefaults.cardColors(containerColor = CarbonDark),
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                // High Quality Render Image (Left Segment)
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .aspectRatio(0.82f)
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = media.uriPath,
                        contentDescription = media.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    if (media.isVideo) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleFilled,
                            contentDescription = "Play Video",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp).align(Alignment.Center)
                        )
                    }
                }

                // AI Stats & Actions Menu (Right Segment)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SPEC DETAILS", color = GoldMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                        }
                    }

                    Text(media.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)

                    Divider(color = Color(0x1BFFFFFF))

                    // Simulated EXIF and AI Object tags data
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExifSpecValue(label = "Format", value = if (media.isVideo) "H.264 MP4 Cinematic" else "JPEG Pro RAW")
                        ExifSpecValue(label = "Resolved Scene", value = media.detectedScene)
                        ExifSpecValue(label = "Neural Tags", value = media.detectedObjects.ifEmpty { "Unlabeled" })
                        val date = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(media.timestamp))
                        ExifSpecValue(label = "Exif Time", value = date)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Buttons list
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Favorite toggle
                        IconButton(
                            onClick = { viewModel.toggleFavorite(media); onDismiss() },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x0EFFFFFF))
                                .weight(1f)
                        ) {
                            Icon(
                                imageVector = if (media.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Fav",
                                tint = if (media.isFavorite) LedRed else Color.White
                            )
                        }

                        // Encryption Safe lock toggle
                        IconButton(
                            onClick = { viewModel.toggleSecureFolder(media); onDismiss() },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x0EFFFFFF))
                                .weight(1f)
                        ) {
                            Icon(
                                imageVector = if (media.isSecure) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = "Vault",
                                tint = if (media.isSecure) GoldMuted else Color.White
                            )
                        }

                        // Delete
                        IconButton(
                            onClick = { viewModel.deleteMediaItem(media); onDismiss() },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1BF44336))
                                .weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LedRed)
                        }
                    }

                    // Open AI Creative Studio editor
                    if (!media.isVideo) {
                        Button(
                            onClick = {
                                viewModel.openEditingStudio(media)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldMuted),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = "Magic", modifier = Modifier.size(16.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Boot AI Creative Studio", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExifSpecValue(label: String, value: String) {
    Column {
        Text(label.uppercase(), color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// -----------------------------------------------------------------------------
// Component 11: AI Editing Studio Sheet (sliders, blur, obj removal)
// -----------------------------------------------------------------------------
@Composable
fun AiEditingStudioOverlay(
    media: CapturedMedia,
    viewModel: CameraViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Sliders states
    val blurVal by viewModel.aiEditBlurFactor.collectAsState()
    val enhanceVal by viewModel.aiEditEnhanceFactor.collectAsState()
    val beautyVal by viewModel.aiEditFaceBeauty.collectAsState()
    val toneSelected by viewModel.aiEditSkinTone.collectAsState()
    val cartoonVal by viewModel.isCartoonEffectActive.collectAsState()
    val eraserActive by viewModel.objectRemoveMode.collectAsState()

    var simulateEraseDrawPoints = remember { mutableStateListOf<Offset>() }

    // Dynamic blur rendering inside Edit dialog!
    val localBlurPx = if (blurVal > 0.05f) {
        (blurVal * 15).toInt().coerceAtLeast(1)
    } else 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.98f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // Screen preview panel (Left)
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
                    .background(Color(0xFF070707))
                    .pointerInput(eraserActive) {
                        detectDragGestures { change, dragAmount ->
                            if (eraserActive) {
                                simulateEraseDrawPoints.add(change.position)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Interactive background image loaded with local custom drawing filters
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .then(if (localBlurPx > 0) Modifier.blur(localBlurPx.dp) else Modifier)
                ) {
                    AsyncImage(
                        model = media.uriPath,
                        contentDescription = "Editing Viewfinder",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Eraser healing dots trace
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (pt in simulateEraseDrawPoints) {
                        drawCircle(
                            color = LedRed.copy(alpha = 0.4f),
                            radius = 24f,
                            center = pt
                        )
                    }
                }

                if (eraserActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(20.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(LedRed.copy(alpha = 0.8f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Touch & Draw over object to heal", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Divider
            Divider(color = Color(0x1BFFFFFF), modifier = Modifier.fillMaxHeight().width(1.dp))

            // Adjusters tools panel (Right half)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "Studio", tint = GoldMuted)
                        Text("AI Creative Studio v1.2", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Divider(color = Color(0x1BFFFFFF))

                // Object Remover Tool Toggle
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (eraserActive) LedRed.copy(alpha = 0.2f) else CarbonMedium),
                    border = BorderStroke(1.dp, if (eraserActive) LedRed else Color.Transparent)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.objectRemoveMode.value = !eraserActive
                                if (!eraserActive) {
                                    viewModel.speakNow("Object remover active. Tap and drag over items to vanish them.")
                                } else {
                                    simulateEraseDrawPoints.clear()
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Healing, contentDescription = "Heal", tint = if (eraserActive) LedRed else GoldMuted)
                            Column {
                                Text("Eraser (Heal Object)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("neural content-aware fill brush", color = Color.Gray, fontSize = 9.sp)
                            }
                        }
                        Switch(
                            checked = eraserActive,
                            onCheckedChange = {
                                viewModel.objectRemoveMode.value = it
                                if (it) {
                                    viewModel.speakNow("Brushing enabled.")
                                } else {
                                    simulateEraseDrawPoints.clear()
                                }
                            }
                        )
                    }
                }

                // Slider 1: Blur
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("50mm Portrait Bokeh", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${(blurVal * 100).toInt()}%", color = GoldMuted, fontSize = 11.sp)
                    }
                    Slider(
                        value = blurVal,
                        onValueChange = { viewModel.aiEditBlurFactor.value = it },
                        colors = SliderDefaults.colors(thumbColor = GoldMuted, activeTrackColor = GoldMuted)
                    )
                }

                // Slider 2: Enhance
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Neural Detail Sharpener", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${(enhanceVal * 100).toInt()}%", color = GoldMuted, fontSize = 11.sp)
                    }
                    Slider(
                        value = enhanceVal,
                        onValueChange = { viewModel.aiEditEnhanceFactor.value = it },
                        colors = SliderDefaults.colors(thumbColor = GoldMuted, activeTrackColor = GoldMuted)
                    )
                }

                // Slider 3: Beauty
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pro Skin Beauty Filter", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${(beautyVal * 100).toInt()}%", color = GoldMuted, fontSize = 11.sp)
                    }
                    Slider(
                        value = beautyVal,
                        onValueChange = { viewModel.aiEditFaceBeauty.value = it },
                        colors = SliderDefaults.colors(thumbColor = GoldMuted, activeTrackColor = GoldMuted)
                    )
                }

                // Button: Skin Tone options
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Auto Skin Tone Calibration", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Original", "Bright", "Bronze", "Warm").forEach { tone ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (toneSelected == tone) GoldMuted else Color(0x1BFFFFFF))
                                    .clickable { viewModel.aiEditSkinTone.value = tone }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(tone, color = if (toneSelected == tone) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Toggle: Cartoon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("2D Cell Cartoonizer Neural Effect", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Switch(checked = cartoonVal, onCheckedChange = { viewModel.isCartoonEffectActive.value = it })
                }

                Spacer(modifier = Modifier.weight(1f))

                // Bottom Save/Close Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text("Reset", color = Color.White, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            viewModel.saveEditingStudioChanges()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1.5f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldMuted)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Save", modifier = Modifier.size(16.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Synthesize Asset", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Component 12: Dialog helper menus (Gestures help)
// -----------------------------------------------------------------------------
@Composable
fun SimpleGesturesHelpDialog(
    onGestureSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Simulate AI Hand Gesture Trigger", color = GoldMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Select an offline hand dynamic gesture, and the app will simulate the MediaPipe tracking outlines instantly:", color = Color.LightGray, fontSize = 11.sp)

                val gestures = listOf(
                    "Palm Show" to "Instant photo capture (Selfie trigger)",
                    "Thumbs Up" to "Video record Toggle (Start/Stop)",
                    "Two Finger Pinch" to "Adjust focal slider scale (Zoom)",
                    "Swipe Left" to "Pull out integrated preview gallery",
                    "Swipe Right" to "Rotate digital lens camera hardware (Flip)"
                )

                gestures.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF222324))
                            .clickable { onGestureSelected(entry.first) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Gesture, contentDescription = "Gen", tint = GoldMuted, modifier = Modifier.size(14.dp))
                                Text(entry.first, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(entry.second, color = Color.LightGray, fontSize = 9.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = GoldMuted)
            }
        },
        containerColor = CarbonDark,
        shape = RoundedCornerShape(12.dp)
    )
}

// -----------------------------------------------------------------------------
// Component 13: Dialog helper menus (Voice commands help)
// -----------------------------------------------------------------------------
@Composable
fun VoiceHelpGridDialog(
    onCommandSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val commands = listOf(
        "Take picture" to "ছবি তুলবে",
        "Capture" to "ছবি তুলবে",
        "Start video" to "ভিডিও রেকর্ড শুরু",
        "Stop video" to "ভিডিও রেকর্ড বন্ধ",
        "Zoom in" to "জুম বাড়াবে",
        "Zoom out" to "জুম কমাবে",
        "Front camera" to "সামনের ক্যামেরা",
        "Back camera" to "পেছনের ক্যামেরা",
        "Flash on" to "ফ্ল্যাশ চালু",
        "Flash off" to "ফ্ল্যাশ বন্ধ",
        "Nature Mode" to "সিনিক ল্যান্ডস্কেপ ফিল্টার",
        "Portrait Mode" to "পোর্ট্রেট ব্লার",
        "Night Mode" to "নাইট স্পেক্ট্রাম",
        "Open Gallery" to "গ্যালারি ওপেন",
        "Increase brightness" to "এক্সপোজার বাড়াবে",
        "Reduce brightness" to "এক্সপোজার কমাবে"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Simulate Offline Speech Dictation", color = GoldMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Tap any English/Bengali voice command statement below to feed it directly to the offline speech parser engine:", color = Color.LightGray, fontSize = 11.sp)

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(commands) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF222324))
                                .clickable { onCommandSelected(item.first) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.SettingsVoice, contentDescription = "Voice", tint = GoldMuted, modifier = Modifier.size(14.dp))
                                Text(item.first, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(item.second, color = Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.SansSerif)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = GoldMuted)
            }
        },
        containerColor = CarbonDark,
        shape = RoundedCornerShape(12.dp)
    )
}
