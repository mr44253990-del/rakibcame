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
    val gyroX by viewModel.stabilizerOffsetX.collectAsState()
    val gyroY by viewModel.stabilizerOffsetY.collectAsState()
    val gyroRoll by viewModel.stabilizerRollDegrees.collectAsState()

    var settingsOpen by remember { mutableStateOf(false) }
    var delayedPreview by remember { mutableStateOf(true) }
    var delayedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var bindError by remember { mutableStateOf<String?>(null) }
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

    BackHandler(enabled = settingsOpen) { settingsOpen = false }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        val cropScale by animateFloatAsState(
            targetValue = if (stabilizationOn) cropScaleFor(stabilizationMode, stabilizationLevel) else 1f,
            label = "cropScale"
        )
        val smoothPreviewModifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = cropScale * zoom.coerceIn(1f, 10f)
                scaleY = cropScale * zoom.coerceIn(1f, 10f)
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
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    Text("Buffering stable preview...", color = Color.White)
                }
            }
        }

        StabilizerTopBar(
            isRecording = isRecording,
            isPaused = isPaused,
            seconds = seconds,
            mode = stabilizationMode,
            level = stabilizationLevel,
            delayMs = if (delayedPreview) delayMs else 0,
            nativeEis = nativeEis,
            bindError = bindError
        )

        ZoomPill(zoom = zoom, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))

        BottomControls(
            isRecording = isRecording,
            isPaused = isPaused,
            onRecord = {
                if (!isRecording) viewModel.startVideo() else viewModel.stopVideo()
            },
            onPauseResume = {
                if (isPaused) viewModel.resumeVideo() else viewModel.pauseVideo()
            },
            onSwitchLens = {
                viewModel.currentCameraLens.value = if (lens == "BACK") "FRONT" else "BACK"
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
                    delayedPreview = delayedPreview,
                    onDelayedPreview = { delayedPreview = it },
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
    nativeEis: Boolean,
    bindError: String?
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("RakibCame", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                text = bindError ?: "${mode} • ${level} • ${delayMs}ms buffer • ${if (nativeEis) "HW EIS" else "Gyro EIS"}",
                color = if (bindError == null) Color(0xFFB8C1CC) else Warn,
                fontSize = 11.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isRecording) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (isPaused) Warn else Danger))
                Text(if (isPaused) "PAUSED" else "REC", color = if (isPaused) Warn else Danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text(formatTime(seconds), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun BottomControls(
    isRecording: Boolean,
    isPaused: Boolean,
    onRecord: () -> Unit,
    onPauseResume: () -> Unit,
    onSwitchLens: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(18.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Panel)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(28.dp)) }
        IconButton(onClick = onSwitchLens) { Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera", tint = Color.White, modifier = Modifier.size(30.dp)) }

        Box(
            Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(if (isRecording) Danger else Color.White)
                .border(5.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                .clickable(onClick = onRecord),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (isRecording) Icons.Default.Stop else Icons.Default.RadioButtonChecked, contentDescription = "Record", tint = if (isRecording) Color.White else Danger, modifier = Modifier.size(42.dp))
        }

        IconButton(enabled = isRecording, onClick = onPauseResume) {
            Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = "Pause or resume", tint = if (isRecording) Color.White else Color.Gray, modifier = Modifier.size(34.dp))
        }
        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Accent, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun ZoomPill(zoom: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("${String.format(Locale.US, "%.1f", zoom)}×", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StabilizerSettingsSheet(
    viewModel: CameraViewModel,
    delayedPreview: Boolean,
    onDelayedPreview: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val stabilizationOn by viewModel.isStabilizationActive.collectAsState()
    val mode by viewModel.stabilizationMode.collectAsState()
    val level by viewModel.stabilizationLevel.collectAsState()
    val zoom by viewModel.zoomLevel.collectAsState()
    val fps by viewModel.currentFps.collectAsState()
    val resolution by viewModel.currentResolution.collectAsState()

    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Smart Tools", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
        }

        SettingSwitch("Stabilization", "CameraX hardware EIS + gyro crop transform", stabilizationOn) {
            viewModel.updateSetting("STABILIZATION_ACTIVE", it)
        }
        SettingSwitch("1-2s delayed preview", "Frames are buffered before showing stable view", delayedPreview, onDelayedPreview)

        Text("Mode", color = Color(0xFFB8C1CC), fontWeight = FontWeight.Bold)
        SegmentedRow(listOf("AI Stabilized", "Action Mode", "Cinematic Mode", "Extreme Stabilizer"), mode) {
            viewModel.updateSetting("STABILIZATION_MODE", it)
            viewModel.updateSetting("STABILIZATION_ACTIVE", true)
        }

        Text("Level", color = Color(0xFFB8C1CC), fontWeight = FontWeight.Bold)
        SegmentedRow(listOf("Low", "Medium", "High", "Ultra"), level) { viewModel.updateSetting("STABILIZATION_LEVEL", it) }

        Text("Quality", color = Color(0xFFB8C1CC), fontWeight = FontWeight.Bold)
        SegmentedRow(listOf("720P", "1080P", "4K"), resolution) { viewModel.updateSetting("CAMERA_RESOLUTION", it) }
        SegmentedRow(listOf("24", "30", "60"), fps.toString()) { viewModel.updateSetting("CAMERA_FPS", it.toInt()) }

        Text("Zoom wheel", color = Color.White, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ZoomWheel(zoom = zoom, onZoom = { value ->
                viewModel.zoomLevel.value = value
                CameraGlobals.cameraControl?.setZoomRatio(value)
            })
            Column(Modifier.weight(1f)) {
                Text("${String.format(Locale.US, "%.1f", zoom)}×", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Slider(value = zoom, onValueChange = {
                    viewModel.zoomLevel.value = it
                    CameraGlobals.cameraControl?.setZoomRatio(it)
                }, valueRange = 1f..10f)
            }
        }
        Spacer(Modifier.height(24.dp))
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
private fun SegmentedRow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val active = item == selected
            Surface(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelect(item) },
                color = if (active) Accent else Color(0xFF1B212A),
                contentColor = if (active) Color.Black else Color.White
            ) {
                Box(Modifier.padding(PaddingValues(horizontal = 6.dp, vertical = 10.dp)), contentAlignment = Alignment.Center) {
                    Text(item, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ZoomWheel(zoom: Float, onZoom: (Float) -> Unit) {
    Box(
        Modifier
            .size(132.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF293241), Color(0xFF0B0F14))))
            .border(2.dp, Accent.copy(alpha = 0.6f), CircleShape)
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
            drawArc(Accent, -215f, sweep, false, style = Stroke(stroke, cap = StrokeCap.Round))
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

            val preview = Preview.Builder()
                .setTargetFrameRate(Range(fps, fps))
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
                .setTargetResolution(Size(960, 540))
                .build()
                .also { analysis ->
                    if (enableDelayedAnalyzer) {
                        val analyzer = DelayedPreviewAnalyzer(delayMs, onFrame)
                        analysis.setAnalyzer(analyzerExecutor, analyzer)
                    }
                }

            provider.unbindAll()
            val camera = if (enableDelayedAnalyzer) {
                provider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture, imageAnalysis)
            } else {
                provider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
            }
            CameraGlobals.cameraControl = camera.cameraControl
            CameraGlobals.videoCapture = videoCapture
            CameraGlobals.cameraControl?.setZoomRatio(viewModel.zoomLevel.value.coerceIn(1f, 10f))
            onError(null)
        } catch (t: Throwable) {
            onError("Camera error: ${t.message ?: t.javaClass.simpleName}")
        }
    }, ContextCompat.getMainExecutor(context))
}

private class DelayedPreviewAnalyzer(
    private val delayMs: Int,
    private val onFrame: (Bitmap) -> Unit
) : ImageAnalysis.Analyzer {
    private data class Frame(val time: Long, val bitmap: Bitmap)
    private val queue = ArrayDeque<Frame>()
    private val main = Handler(Looper.getMainLooper())

    override fun analyze(image: ImageProxy) {
        try {
            val bitmap = imageProxyToBitmap(image)
            if (bitmap != null) {
                val now = System.currentTimeMillis()
                queue.addLast(Frame(now, bitmap))
                while (queue.size > 90) queue.removeFirst().bitmap.recycle()
                var selected: Frame? = null
                while (queue.size > 1 && now - queue.first().time >= delayMs) selected = queue.removeFirst()
                selected?.let { frame -> main.post { onFrame(frame.bitmap) } }
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
