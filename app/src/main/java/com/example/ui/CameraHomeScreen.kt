package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Range
import android.util.Size
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val GoldMuted = Color(0xFFF97316)
val LedGreen = Color(0xFF22C55E)
val LedRed = Color(0xFFEF4444)

private val UiBlack = Color(0xFF050608)
private val Panel = Color(0xCC101318)
private val Accent = LedGreen
private val Danger = LedRed
private val Warn = Color(0xFFF59E0B)

@Composable
fun CameraHomeScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissions = remember { requiredRuntimePermissions() }
    var hasPermissions by remember { mutableStateOf(permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasPermissions = permissions.all { result[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) launcher.launch(permissions)
    }

    if (!hasPermissions) {
        PermissionScreen(onRequest = { launcher.launch(permissions) })
    } else {
        CleanStabilizerCamera(viewModel = viewModel, modifier = modifier)
    }
}

// Compatibility entry point used by the existing panorama screen.
@Composable
fun CameraViewfinder(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    CleanStabilizerCamera(viewModel = viewModel, modifier = modifier)
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(UiBlack).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.Videocam, contentDescription = null, tint = Accent, modifier = Modifier.size(58.dp))
            Text("RakibCame Stabilizer", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Camera, microphone, media/storage permission দিন। ভিডিও রেকর্ড, সেভ এবং স্ট্যাবল প্রিভিউ চালাতে এগুলো দরকার।", color = Color(0xFFB8C1CC), fontSize = 14.sp)
            ElevatedButton(onClick = onRequest, colors = ButtonDefaults.elevatedButtonColors(containerColor = Accent, contentColor = Color.Black)) {
                Text("Allow permissions", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanStabilizerCamera(viewModel: CameraViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val isRecording by viewModel.isRecordingVideo.collectAsState()
    val isPaused by viewModel.isVideoPaused.collectAsState()
    val seconds by viewModel.videoDurationSeconds.collectAsState()
    val lens by viewModel.currentCameraLens.collectAsState()
    val zoom by viewModel.zoomLevel.collectAsState()
    val fps by viewModel.currentFps.collectAsState()
    val resolution by viewModel.currentResolution.collectAsState()
    val stabilizationOn by viewModel.isStabilizationActive.collectAsState()
    val stabilizationMode by viewModel.stabilizationMode.collectAsState()
    val stabilizationLevel by viewModel.stabilizationLevel.collectAsState()
    val delayMs by viewModel.stabilizationPreviewDelayMs.collectAsState()
    val nativeEis by viewModel.isCameraXStabilizationSupported.collectAsState()
    val filter by viewModel.currentFilter.collectAsState()
    val theme by viewModel.currentTheme.collectAsState()
    val flashOn by viewModel.isFlashEnabled.collectAsState()
    val cinematicBars by viewModel.isCinematicBarsEnabled.collectAsState()
    val exposureValue by viewModel.exposureCompensation.collectAsState()
    val videoMessage by viewModel.videoSaveMessage.collectAsState()
    val isRendering by viewModel.isRenderingVideo.collectAsState()
    val renderProgress by viewModel.renderProgress.collectAsState()
    val renderEta by viewModel.renderEtaText.collectAsState()
    val gyroX by viewModel.stabilizerOffsetX.collectAsState()
    val gyroY by viewModel.stabilizerOffsetY.collectAsState()
    val gyroRoll by viewModel.stabilizerRollDegrees.collectAsState()

    var settingsOpen by remember { mutableStateOf(false) }
    var zoomMenuOpen by remember { mutableStateOf(false) }
    val delayedPreview = false
    var delayedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var bindError by remember { mutableStateOf<String?>(null) }
    var liveFps by remember { mutableStateOf(0) }
    var showExposureHud by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && viewModel.isRecordingVideo.value) {
                viewModel.stopVideo()
                Toast.makeText(context, "Recording auto-saved to Gallery", Toast.LENGTH_SHORT).show()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            executor.shutdown()
            if (viewModel.isRecordingVideo.value) viewModel.stopVideo()
        }
    }

    LaunchedEffect(exposureValue) {
        val state = CameraGlobals.cameraInfo?.exposureState
        val range = state?.exposureCompensationRange
        if (range != null) {
            val normalized = ((exposureValue + 3f) / 6f).coerceIn(0f, 1f)
            val index = (range.lower + ((range.upper - range.lower) * normalized)).roundToInt()
            CameraGlobals.cameraControl?.setExposureCompensationIndex(index)
        }
    }

    LaunchedEffect(showExposureHud, exposureValue) {
        if (showExposureHud) {
            kotlinx.coroutines.delay(1200)
            showExposureHud = false
        }
    }

    BackHandler(enabled = settingsOpen) { settingsOpen = false }

    Box(modifier.fillMaxSize()) {
        val cropScale by animateFloatAsState(
            targetValue = if (stabilizationOn) cropScaleFor(stabilizationMode, stabilizationLevel) else 1f,
            label = "cropScale"
        )
        val smoothPreviewModifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoomChange, _ ->
                    if (zoomChange != 1f) {
                        val next = (viewModel.zoomLevel.value * zoomChange).coerceIn(1f, maxSupportedZoom())
                        applyCameraZoom(viewModel, next)
                    }
                }
            }
            .graphicsLayer {
                // Only stabilization crop is applied here. Zoom is applied through CameraControl
                // so recorded video gets the same zoom as the preview.
                scaleX = cropScale
                scaleY = cropScale
                translationX = if (stabilizationOn) gyroX else 0f
                translationY = if (stabilizationOn) gyroY else 0f
                rotationZ = if (stabilizationOn && stabilizationLevel != "Low") gyroRoll else 0f
            }

        key(lens, fps, resolution, stabilizationOn, stabilizationMode, stabilizationLevel, delayedPreview) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                update = { previewView ->
                    bindCameraUseCases(
                        context = previewView.context,
                        lifecycleOwner = lifecycleOwner,
                        previewView = previewView,
                        viewModel = viewModel,
                        lens = lens,
                        fps = fps,
                        resolution = resolution,
                        stabilizationOn = stabilizationOn,
                        analyzerExecutor = executor,
                        enableDelayedAnalyzer = delayedPreview,
                        delayMs = delayMs.coerceIn(0, 2000),
                        onFrame = { delayedBitmap = it },
                        onFps = { liveFps = it },
                        onError = { bindError = it }
                    )
                },
                modifier = smoothPreviewModifier.alpha(if (delayedPreview) 0.01f else 1f)
            )
        }

        if (delayedPreview) {
            val frame = delayedBitmap
            if (frame != null) {
                AsyncImage(
                    model = frame,
                    contentDescription = "Delayed stabilized preview",
                    contentScale = ContentScale.Crop,
                    modifier = smoothPreviewModifier
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassChip("Buffering ${delayMs}ms")
                }
            }
        }

        FilterOverlay(filter = filter, cinematicBars = cinematicBars)

        StabilizerTopBar(
            isRecording = isRecording,
            isPaused = isPaused,
            seconds = seconds,
            mode = stabilizationMode,
            level = stabilizationLevel,
            delayMs = if (delayedPreview) delayMs else 0,
            liveFps = liveFps,
            nativeEis = nativeEis,
            bindError = bindError
        )

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        showExposureHud = true
                    })
                }
        )

        ExposureStrip(
            exposure = exposureValue,
            visible = showExposureHud,
            onChange = { value ->
                viewModel.exposureCompensation.value = value
                showExposureHud = true
            },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp)
        )

        SaveAndRenderOverlay(
            message = videoMessage,
            isRendering = isRendering,
            progress = renderProgress,
            eta = renderEta,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp)
        )

        Box(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), contentAlignment = Alignment.CenterEnd) {
            ZoomPill(zoom = zoom, modifier = Modifier.clickable { zoomMenuOpen = !zoomMenuOpen })
            AnimatedVisibility(visible = zoomMenuOpen) {
                ZoomPopupWheel(
                    zoom = zoom,
                    accent = themeAccent(theme),
                    onZoom = { value -> applyCameraZoom(viewModel, value) },
                    modifier = Modifier.padding(end = 54.dp)
                )
            }
        }

        BottomControls(
            isRecording = isRecording,
            isPaused = isPaused,
            onRecord = {
                if (!isRecording) viewModel.startVideo() else viewModel.stopVideo()
            },
            onPauseResume = {
                if (isPaused) viewModel.resumeVideo() else viewModel.pauseVideo()
            },
            flashOn = flashOn,
            accent = themeAccent(theme),
            onSwitchLens = {
                viewModel.currentCameraLens.value = if (lens == "BACK") "FRONT" else "BACK"
            },
            onFlash = {
                val next = !flashOn
                viewModel.updateSetting("FLASH", next)
                CameraGlobals.cameraControl?.enableTorch(next)
            },
            onSettings = { settingsOpen = true },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        AnimatedVisibility(visible = settingsOpen, modifier = Modifier.align(Alignment.BottomCenter)) {
            ModalBottomSheet(
                onDismissRequest = { settingsOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color(0xFF101318)
            ) {
                StabilizerSettingsSheet(
                    viewModel = viewModel,
                    onClose = { scope.launch { settingsOpen = false } }
                )
            }
        }
    }
}

@Composable
private fun StabilizerTopBar(
    isRecording: Boolean,
    isPaused: Boolean,
    seconds: Int,
    mode: String,
    level: String,
    delayMs: Int,
    liveFps: Int,
    nativeEis: Boolean,
    bindError: String?
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        GlassChip(
            text = bindError ?: "${mode.take(10)} • ${level} • ${if (nativeEis) "HW" else "Gyro"} • ${liveFps}fps",
            color = if (bindError == null) Color.White else Warn
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isRecording) GlassChip(if (isPaused) "PAUSE" else "REC", if (isPaused) Warn else Danger)
            GlassChip(formatTime(seconds), Color.White)
        }
    }
}

@Composable
private fun BottomControls(
    isRecording: Boolean,
    isPaused: Boolean,
    flashOn: Boolean,
    accent: Color,
    onRecord: () -> Unit,
    onPauseResume: () -> Unit,
    onSwitchLens: () -> Unit,
    onFlash: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MiniIconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(19.dp)) }
        MiniIconButton(onClick = onSwitchLens) { Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera", tint = Color.White, modifier = Modifier.size(20.dp)) }
        MiniIconButton(onClick = onFlash) { Icon(if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, contentDescription = "Flash", tint = if (flashOn) Warn else Color.White, modifier = Modifier.size(20.dp)) }

        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (isRecording) Danger else Color.White.copy(alpha = 0.96f))
                .border(3.dp, accent.copy(alpha = 0.45f), CircleShape)
                .clickable(onClick = onRecord),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (isRecording) Icons.Default.Stop else Icons.Default.RadioButtonChecked, contentDescription = "Record", tint = if (isRecording) Color.White else Danger, modifier = Modifier.size(30.dp))
        }

        MiniIconButton(enabled = isRecording, onClick = onPauseResume) {
            Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = "Pause or resume", tint = if (isRecording) Color.White else Color.Gray, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun MiniIconButton(enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = if (enabled) 0.26f else 0.12f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun GlassChip(text: String, color: Color = Color.White) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SaveAndRenderOverlay(message: String, isRendering: Boolean, progress: Int, eta: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = message.isNotBlank() || isRendering, modifier = modifier) {
        Column(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(if (isRendering) "Rendering smooth copy… $progress%" else message, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (isRendering) {
                Box(Modifier.width(160.dp).height(4.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.20f))) {
                    Box(Modifier.fillMaxWidth(progress / 100f).height(4.dp).clip(RoundedCornerShape(99.dp)).background(Accent))
                }
                Text(eta, color = Color(0xFFD8E1EA), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ExposureStrip(exposure: Float, visible: Boolean, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    if (!visible) return
    val percent = (((exposure + 3f) / 6f) * 100f).roundToInt().coerceIn(0, 100)
    Column(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = if (visible) 0.18f else 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Drag up = brighter, drag down = darker.
                    onChange((exposure - dragAmount.y / 180f).coerceIn(-3f, 3f))
                }
            }
            .padding(horizontal = 7.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("☀", color = Color.White, fontSize = 15.sp)
        if (visible) Text("$percent%", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.width(4.dp).height(72.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.20f))) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .width(4.dp)
                    .height((72 * percent / 100f).dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Warn)
            )
        }
    }
}

@Composable
private fun ZoomPill(zoom: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("${String.format(Locale.US, "%.1f", zoom)}×", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}

@Composable
private fun ZoomPopupWheel(zoom: Float, accent: Color, onZoom: (Float) -> Unit, modifier: Modifier = Modifier) {
    var workingZoom by remember { mutableFloatStateOf(zoom) }
    LaunchedEffect(zoom) { workingZoom = zoom }
    Box(
        modifier
            .size(156.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.86f))
            .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Premium-camera style: drag up = zoom in, drag down = zoom out.
                    val delta = (-dragAmount.y + dragAmount.x * 0.25f) / 150f
                    workingZoom = (workingZoom + delta).coerceIn(1f, 10f)
                    onZoom(workingZoom)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(11.dp)) {
            val stroke = 9.dp.toPx()
            drawArc(Color.Black.copy(alpha = 0.14f), -220f, 260f, false, style = Stroke(stroke, cap = StrokeCap.Round))
            val sweep = ((zoom - 1f) / 9f) * 260f
            drawArc(accent, -220f, sweep, false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ZOOM", color = Color.Black.copy(alpha = 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("${String.format(Locale.US, "%.1f", zoom)}×", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StabilizerSettingsSheet(
    viewModel: CameraViewModel,
    onClose: () -> Unit
) {
    val stabilizationOn by viewModel.isStabilizationActive.collectAsState()
    val mode by viewModel.stabilizationMode.collectAsState()
    val level by viewModel.stabilizationLevel.collectAsState()
    val zoom by viewModel.zoomLevel.collectAsState()
    val fps by viewModel.currentFps.collectAsState()
    val resolution by viewModel.currentResolution.collectAsState()
    val filter by viewModel.currentFilter.collectAsState()
    val theme by viewModel.currentTheme.collectAsState()
    val cinematicBars by viewModel.isCinematicBarsEnabled.collectAsState()
    val fpsBoost by viewModel.isFpsBoostEnabled.collectAsState()
    val flashOn by viewModel.isFlashEnabled.collectAsState()
    val smoothRender by viewModel.isSmoothRenderEnabled.collectAsState()
    val capabilities by viewModel.cameraCapabilitiesSummary.collectAsState()
    val supportedQualities by viewModel.supportedVideoQualities.collectAsState()
    val accent = themeAccent(theme)

    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f))))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Glass Smart Tools", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("সব সেটিংস সেভ থাকবে", color = Color(0xFFB8C1CC), fontSize = 11.sp)
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
        }

        GlassInfoCard(capabilities)

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactToggle("EIS", stabilizationOn, accent) { viewModel.updateSetting("STABILIZATION_ACTIVE", it) }
            CompactToggle("60FPS", fpsBoost, accent) {
                viewModel.updateSetting("FPS_BOOST", it)
                viewModel.updateSetting("CAMERA_FPS", if (it) 60 else 30)
            }
            CompactToggle("Bars", cinematicBars, accent) { viewModel.updateSetting("CINEMATIC_BARS", it) }
            CompactToggle("Flash", flashOn, accent) {
                viewModel.updateSetting("FLASH", it)
                CameraGlobals.cameraControl?.enableTorch(it)
            }
            CompactToggle("Render", smoothRender, accent) { viewModel.updateSetting("SMOOTH_RENDER", it) }
        }

        Text("Stabilizer", color = Color(0xFFB8C1CC), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        SegmentedRow(listOf("AI Stabilized", "Action Mode", "Cinematic Mode", "Extreme Stabilizer"), mode, accent) {
            viewModel.updateSetting("STABILIZATION_MODE", it)
            viewModel.updateSetting("STABILIZATION_ACTIVE", true)
        }
        SegmentedRow(listOf("Low", "Medium", "High", "Ultra"), level, accent) { viewModel.updateSetting("STABILIZATION_LEVEL", it) }

        Text("Video", color = Color(0xFFB8C1CC), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        SegmentedRow(supportedQualities.ifEmpty { listOf("720P", "1080P") }, resolution, accent) { viewModel.updateSetting("CAMERA_RESOLUTION", it) }
        SegmentedRow(listOf("30", "60"), fps.toString(), accent) {
            viewModel.updateSetting("CAMERA_FPS", it.toInt())
            viewModel.updateSetting("FPS_BOOST", it == "60")
        }

        Text("Filters", color = Color(0xFFB8C1CC), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        SegmentedRow(listOf("Natural", "Cinematic", "Warm", "Cool", "Noir"), filter, accent) { viewModel.updateSetting("CAMERA_FILTER", it) }

        Text("Glass Themes", color = Color(0xFFB8C1CC), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        SegmentedRow(listOf("Emerald", "Blue", "Purple", "Gold", "Red"), theme, accent) { viewModel.updateSetting("UI_THEME", it) }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ZoomWheel(zoom = zoom, accent = accent, onZoom = { value -> applyCameraZoom(viewModel, value) })
            Column(Modifier.weight(1f)) {
                Text("Zoom ${String.format(Locale.US, "%.1f", zoom)}×", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Slider(value = zoom.coerceIn(1f, maxSupportedZoom()), onValueChange = {
                    applyCameraZoom(viewModel, it)
                }, valueRange = 1f..maxSupportedZoom())
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun GlassInfoCard(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .padding(10.dp)
    ) {
        Text(text, color = Color(0xFFE5EEF8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CompactToggle(title: String, checked: Boolean, accent: Color, onChecked: (Boolean) -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked) accent.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .clickable { onChecked(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(title, color = if (checked) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFF8D98A5), fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SegmentedRow(items: List<String>, selected: String, accent: Color = Accent, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items.forEach { item ->
            val active = item == selected
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable { onSelect(item) },
                color = if (active) accent else Color.White.copy(alpha = 0.12f),
                contentColor = if (active) Color.Black else Color.White
            ) {
                Box(Modifier.padding(PaddingValues(horizontal = 12.dp, vertical = 8.dp)), contentAlignment = Alignment.Center) {
                    Text(item, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ZoomWheel(zoom: Float, accent: Color = Accent, onZoom: (Float) -> Unit) {
    Box(
        Modifier
            .size(132.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF293241), Color(0xFF0B0F14))))
            .border(2.dp, accent.copy(alpha = 0.6f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = (dragAmount.x - dragAmount.y) / 120f
                    onZoom((zoom + delta).coerceIn(1f, 10f))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val stroke = 10.dp.toPx()
            drawArc(Color.White.copy(alpha = 0.14f), -215f, 250f, false, style = Stroke(stroke, cap = StrokeCap.Round))
            val sweep = ((zoom - 1f) / 9f) * 250f
            drawArc(accent, -215f, sweep, false, style = Stroke(stroke, cap = StrokeCap.Round))
            val angle = Math.toRadians((-215f + sweep).toDouble())
            val radius = size.minDimension / 2f - stroke
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color.White, 7.dp.toPx(), Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ZOOM", color = Color(0xFFB8C1CC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("${String.format(Locale.US, "%.1f", zoom)}×", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun FilterOverlay(filter: String, cinematicBars: Boolean) {
    val overlay = when (filter) {
        "Cinematic" -> Color(0xFF0F2A4A).copy(alpha = 0.13f)
        "Warm" -> Color(0xFFFFA135).copy(alpha = 0.11f)
        "Cool" -> Color(0xFF38BDF8).copy(alpha = 0.10f)
        "Noir" -> Color.Black.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    if (overlay.alpha > 0f) Box(Modifier.fillMaxSize().background(overlay))
    if (cinematicBars) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.fillMaxWidth().height(34.dp).background(Color.Black.copy(alpha = 0.70f)))
            Box(Modifier.fillMaxWidth().height(34.dp).background(Color.Black.copy(alpha = 0.70f)))
        }
    }
}

private fun themeAccent(theme: String): Color = when (theme) {
    "Blue" -> Color(0xFF38BDF8)
    "Purple" -> Color(0xFFA78BFA)
    "Gold" -> Color(0xFFFBBF24)
    "Red" -> Color(0xFFFB7185)
    else -> Accent
}

private fun maxSupportedZoom(): Float {
    return try {
        CameraGlobals.cameraInfo?.zoomState?.value?.maxZoomRatio?.coerceAtLeast(1f) ?: 10f
    } catch (_: Throwable) { 10f }
}

private fun applyCameraZoom(viewModel: CameraViewModel, requested: Float) {
    val clamped = requested.coerceIn(1f, maxSupportedZoom())
    viewModel.updateSetting("ZOOM_LEVEL", clamped)
    val control = CameraGlobals.cameraControl ?: return
    try {
        control.setZoomRatio(clamped)
    } catch (_: Throwable) {
        val max = maxSupportedZoom().coerceAtLeast(1.01f)
        control.setLinearZoom(((clamped - 1f) / (max - 1f)).coerceIn(0f, 1f))
    }
}

private fun buildCameraCapabilityText(
    cameraInfo: androidx.camera.core.CameraInfo,
    qualities: List<String>,
    nativeStabilization: Boolean
): String {
    return try {
        val c2 = androidx.camera.camera2.interop.Camera2CameraInfo.from(cameraInfo)
        val size = c2.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val mp = if (size != null) (size.width.toLong() * size.height.toLong() / 1_000_000.0) else null
        val focal = c2.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.joinToString { String.format(Locale.US, "%.1fmm", it) } ?: "Auto lens"
        val mpText = mp?.let { String.format(Locale.US, "%.1fMP", it) } ?: "Camera"
        "$mpText • Video: ${qualities.joinToString()} • $focal • Stabilizer: ${if (nativeStabilization) "Hardware + Gyro" else "Gyro fallback"}"
    } catch (_: Throwable) {
        "Video: ${qualities.joinToString()} • Stabilizer: ${if (nativeStabilization) "Hardware + Gyro" else "Gyro fallback"}"
    }
}

private fun bindCameraUseCases(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    viewModel: CameraViewModel,
    lens: String,
    fps: Int,
    resolution: String,
    stabilizationOn: Boolean,
    analyzerExecutor: java.util.concurrent.Executor,
    enableDelayedAnalyzer: Boolean,
    delayMs: Int,
    onFrame: (Bitmap) -> Unit,
    onFps: (Int) -> Unit,
    onError: (String?) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        try {
            val provider = cameraProviderFuture.get()
            val selector = if (lens == "FRONT") CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val cameraInfo = provider.getCameraInfo(selector)
            val nativeStabilization = try { Preview.getPreviewCapabilities(cameraInfo).isStabilizationSupported } catch (_: Throwable) { false }
            viewModel.updateCameraXStabilizationSupport(nativeStabilization)
            val supportedQualityLabels = try {
                QualitySelector.getSupportedQualities(cameraInfo).mapNotNull { quality ->
                    when (quality) {
                        Quality.UHD -> "4K"
                        Quality.FHD -> "1080P"
                        Quality.HD -> "720P"
                        Quality.SD -> "480P"
                        else -> null
                    }
                }
            } catch (_: Throwable) { listOf("720P", "1080P") }
            val capabilityText = buildCameraCapabilityText(cameraInfo, supportedQualityLabels, nativeStabilization)
            viewModel.updateCameraCapabilities(capabilityText, supportedQualityLabels)

            val targetFps = fps.coerceIn(30, 60)
            val preview = Preview.Builder()
                .setTargetFrameRate(Range(30, targetFps))
                .apply { if (stabilizationOn && nativeStabilization) setPreviewStabilizationEnabled(true) }
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val quality = when (resolution) {
                "4K" -> Quality.UHD
                "720P" -> Quality.HD
                else -> Quality.FHD
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build()
            val videoCapture = VideoCapture.Builder(recorder)
                .apply { if (stabilizationOn) setVideoStabilizationEnabled(true) }
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(Size(640, 360))
                .build()
                .also { analysis ->
                    val analyzer = SmartPreviewAnalyzer(delayMs, enableDelayedAnalyzer, onFrame, onFps)
                    analysis.setAnalyzer(analyzerExecutor, analyzer)
                }

            provider.unbindAll()
            val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture, imageAnalysis)
            CameraGlobals.cameraControl = camera.cameraControl
            CameraGlobals.cameraInfo = camera.cameraInfo
            CameraGlobals.videoCapture = videoCapture
            applyCameraZoom(viewModel, viewModel.zoomLevel.value)
            CameraGlobals.cameraControl?.enableTorch(viewModel.isFlashEnabled.value)
            onError(null)
        } catch (t: Throwable) {
            onError("Camera error: ${t.message ?: t.javaClass.simpleName}")
        }
    }, ContextCompat.getMainExecutor(context))
}

private class SmartPreviewAnalyzer(
    private val delayMs: Int,
    private val delayedPreview: Boolean,
    private val onFrame: (Bitmap) -> Unit,
    private val onFps: (Int) -> Unit
) : ImageAnalysis.Analyzer {
    private data class Frame(val time: Long, val bitmap: Bitmap)
    private val queue = ArrayDeque<Frame>()
    private val main = Handler(Looper.getMainLooper())
    private var frameCounter = 0
    private var lastFpsTime = System.currentTimeMillis()

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            frameCounter++
            if (now - lastFpsTime >= 1000) {
                val fps = frameCounter
                frameCounter = 0
                lastFpsTime = now
                main.post { onFps(fps) }
            }
            if (delayedPreview) {
                val bitmap = imageProxyToBitmap(image)
                if (bitmap != null) {
                    queue.addLast(Frame(now, bitmap))
                    while (queue.size > 90) queue.removeFirst().bitmap.recycle()
                    var selected: Frame? = null
                    while (queue.size > 1 && now - queue.first().time >= delayMs) selected = queue.removeFirst()
                    selected?.let { frame -> main.post { onFrame(frame.bitmap) } }
                }
            }
        } finally {
            image.close()
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    if (image.format != ImageFormat.YUV_420_888) return null
    val nv21 = yuv420ToNv21(image)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 72, out)
    val raw = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size()) ?: return null
    val rotation = image.imageInfo.rotationDegrees
    return if (rotation == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true).also { if (it != raw) raw.recycle() }
}

private fun yuv420ToNv21(image: ImageProxy): ByteArray {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    val chromaHeight = image.height / 2
    val chromaWidth = image.width / 2
    var offset = ySize
    val uRowStride = image.planes[1].rowStride
    val vRowStride = image.planes[2].rowStride
    val uPixelStride = image.planes[1].pixelStride
    val vPixelStride = image.planes[2].pixelStride
    val uBytes = ByteArray(uBuffer.remaining()).also { uBuffer.get(it) }
    val vBytes = ByteArray(vBuffer.remaining()).also { vBuffer.get(it) }
    for (row in 0 until chromaHeight) {
        for (col in 0 until chromaWidth) {
            val vuIndex = row * vRowStride + col * vPixelStride
            val uuIndex = row * uRowStride + col * uPixelStride
            if (vuIndex < vBytes.size) nv21[offset++] = vBytes[vuIndex]
            if (uuIndex < uBytes.size) nv21[offset++] = uBytes[uuIndex]
        }
    }
    return nv21
}

private fun requiredRuntimePermissions(): Array<String> {
    val base = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= 33) {
        base += Manifest.permission.READ_MEDIA_VIDEO
        base += Manifest.permission.READ_MEDIA_IMAGES
    } else {
        base += Manifest.permission.READ_EXTERNAL_STORAGE
    }
    if (Build.VERSION.SDK_INT <= 28) base += Manifest.permission.WRITE_EXTERNAL_STORAGE
    return base.distinct().toTypedArray()
}

private fun cropScaleFor(mode: String, level: String): Float = when {
    mode == "Extreme Stabilizer" -> 1.22f
    mode == "Action Mode" -> 1.16f
    level == "Ultra" -> 1.18f
    level == "High" -> 1.12f
    level == "Medium" -> 1.07f
    else -> 1.03f
}

private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
