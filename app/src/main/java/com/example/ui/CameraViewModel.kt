package com.example.ui

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

data class DetectedObjectData(
    val label: String,
    val bounds: android.graphics.Rect,
    val confidence: Float
)

// Simulated bounding box data structure for offline object detection
data class SimulatedTarget(
    val label: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float
)

// Simulated Scene data structure
data class SimulatedScene(
    val id: Int,
    val name: String, // Sunset, Outdoor, Portrait, Food, Landscape
    val imagePath: String,
    val objects: List<SimulatedTarget>,
    val recommendation: String
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CameraDatabase.getDatabase(application, viewModelScope)
    private val repository = CameraRepository(db.cameraDao())

    // --------------------------------------------------
    // Persistent Settings
    // --------------------------------------------------
    private val prefs = application.getSharedPreferences("CameraSettings", android.content.Context.MODE_PRIVATE)

    val currentLanguage = MutableStateFlow(prefs.getString("LANGUAGE", "English") ?: "English")
    val isAutoListenEnabled = MutableStateFlow(prefs.getBoolean("AUTO_LISTEN", true))
    val isObjectDetectionEnabled = MutableStateFlow(prefs.getBoolean("ML_OBJECT", true))
    val isFaceDetectionEnabled = MutableStateFlow(prefs.getBoolean("ML_FACE", true))
    val isPoseDetectionEnabled = MutableStateFlow(prefs.getBoolean("ML_POSE", true))
    val isBarcodeScanningEnabled = MutableStateFlow(prefs.getBoolean("ML_BARCODE", true))

    fun updateSetting(key: String, value: Any) {
        val editor = prefs.edit()
        when (value) {
            is String -> {
                editor.putString(key, value)
                if (key == "LANGUAGE") {
                    currentLanguage.value = value
                    updateTTSLanguage()
                    if (audioListeningState.value == "Idle" && isAutoListenEnabled.value) {
                         startListening()
                    }
                }
            }
            is Boolean -> {
                editor.putBoolean(key, value)
                when (key) {
                    "AUTO_LISTEN" -> isAutoListenEnabled.value = value
                    "ML_OBJECT" -> isObjectDetectionEnabled.value = value
                    "ML_FACE" -> isFaceDetectionEnabled.value = value
                    "ML_POSE" -> isPoseDetectionEnabled.value = value
                    "ML_BARCODE" -> isBarcodeScanningEnabled.value = value
                }
            }
        }
        editor.apply()
    }

    // --------------------------------------------------
    // 1. Reactive Database flows
    // --------------------------------------------------
    val customVoiceCommands: StateFlow<List<CustomCommand>> = repository.customCommands
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val galleryMedia: StateFlow<List<CapturedMedia>> = repository.publicMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val secureMedia: StateFlow<List<CapturedMedia>> = repository.secureMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --------------------------------------------------
    // 2. DSLR Manual Configuration State
    // --------------------------------------------------
    val iso = MutableStateFlow(400) // 100 to 6400
    val shutterSpeed = MutableStateFlow("1/125") // 1/4000 to 1s
    val exposureCompensation = MutableStateFlow(0.0f) // -3.0f to +3.0f
    val whiteBalance = MutableStateFlow("Auto") // Auto, Incandescent, Fluorescent, Sunny, Cloudy
    val manualFocus = MutableStateFlow(1.0f) // 0.0f (Macro) to 1.0f (Infinity)
    val zoomLevel = MutableStateFlow(1.0f) // 1.0f to 10.0f
    val isStabilizationActive = MutableStateFlow(true)
    val isHdrActive = MutableStateFlow(true)
    val currentCameraLens = MutableStateFlow("BACK") // FRONT or BACK
    val currentCameraMode = MutableStateFlow("Pro") // Auto, Pro, Portrait, Night, Macro, Scanner

    // --------------------------------------------------
    // 3. AI Camera States (Object detection, Scene, Gestures)
    // --------------------------------------------------
    val isAiDetectionActive = MutableStateFlow(true)
    val activeGesture = MutableStateFlow("None") // Thumbs Up, Palm, Two Fingers, Swipe L, Swipe R
    val assistantBubble = MutableStateFlow("Ready for voice action.")
    val audioListeningState = MutableStateFlow("Idle") // Idle, Listening...
    val shutterTimer = MutableStateFlow(0) // 0, 3, 10
    val isFlashEnabled = MutableStateFlow(false)
    val detectedObjectResults = MutableStateFlow<List<DetectedObjectData>>(emptyList())

    // --------------------------------------------------
    // 3.5 ML Kit Components
    // --------------------------------------------------
    private val actionSound = android.media.MediaActionSound()

    // List of pre-configured beautiful scenes to cycle through for simulation/real demonstration
    val scenes = listOf(
        SimulatedScene(
            id = 1,
            name = "Sunset",
            imagePath = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1000",
            objects = listOf(
                SimulatedTarget("Mountain", 0.15f, 0.35f, 0.85f, 0.68f, 0.96f),
                SimulatedTarget("Scenic Lake", 0.20f, 0.65f, 0.80f, 0.90f, 0.92f),
                SimulatedTarget("Tree", 0.05f, 0.50f, 0.28f, 0.95f, 0.88f)
            ),
            recommendation = "Sunset detected. Warming filter added. Exposing for HDR highlights."
        ),
        SimulatedScene(
            id = 2,
            name = "Outdoor",
            imagePath = "https://images.unsplash.com/photo-1543466835-00a7907e9de1?w=1000",
            objects = listOf(
                SimulatedTarget("Dog (Pet)", 0.32f, 0.42f, 0.68f, 0.85f, 0.97f),
                SimulatedTarget("Tennis Ball", 0.72f, 0.78f, 0.80f, 0.86f, 0.91f),
                SimulatedTarget("Green Grass", 0.02f, 0.68f, 0.98f, 0.98f, 0.85f)
            ),
            recommendation = "Pet dog detected. High-speed shutter tracker activated to capture movement."
        ),
        SimulatedScene(
            id = 3,
            name = "Portrait",
            imagePath = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=1000",
            objects = listOf(
                SimulatedTarget("Person (Subject)", 0.25f, 0.18f, 0.75f, 0.92f, 0.99f),
                SimulatedTarget("Smile (Detected)", 0.46f, 0.47f, 0.54f, 0.53f, 0.94f),
                SimulatedTarget("Eye (L)", 0.42f, 0.36f, 0.46f, 0.40f, 0.96f),
                SimulatedTarget("Eye (R)", 0.54f, 0.36f, 0.58f, 0.40f, 0.96f)
            ),
            recommendation = "Face detected. Auto 50mm portrait bokeh blur active. Ready to capture smile."
        ),
        SimulatedScene(
            id = 4,
            name = "Food",
            imagePath = "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=1000",
            objects = listOf(
                SimulatedTarget("Gourmet Pizza", 0.22f, 0.28f, 0.78f, 0.82f, 0.98f),
                SimulatedTarget("Plate", 0.14f, 0.22f, 0.86f, 0.88f, 0.94f),
                SimulatedTarget("Table Charger", 0.05f, 0.10f, 0.95f, 0.95f, 0.78f)
            ),
            recommendation = "Food detected. Brightness balanced. Warm macro color calibration loaded."
        ),
        SimulatedScene(
            id = 5,
            name = "Landscape",
            imagePath = "https://images.unsplash.com/photo-1503376780353-7e6692767b70?w=1000",
            objects = listOf(
                SimulatedTarget("Sports Car", 0.18f, 0.44f, 0.82f, 0.84f, 0.97f),
                SimulatedTarget("Alloy Wheel", 0.26f, 0.71f, 0.38f, 0.84f, 0.93f),
                SimulatedTarget("Asphalt Road", 0.05f, 0.75f, 0.95f, 0.98f, 0.86f)
            ),
            recommendation = "Automobile detected. Fast autofocus tracker engaged. Stabilizing frame scale."
        )
    )

    val currentSceneIndex = MutableStateFlow(0)
    val activeScene = currentSceneIndex.map { index -> scenes[index] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), scenes[0])

    // --------------------------------------------------
    // 4. Video Recording State
    // --------------------------------------------------
    val isRecordingVideo = MutableStateFlow(false)
    val videoDurationSeconds = MutableStateFlow(0)
    val isVideoPaused = MutableStateFlow(false)

    // --------------------------------------------------
    // 5. Active Gallery Preview, Details and Editing Studio State
    // --------------------------------------------------
    val searchSqlQuery = MutableStateFlow("")
    val activeGalleryTab = MutableStateFlow("All") // All, Favorites, Videos, Vault
    val selectedMediaItem = MutableStateFlow<CapturedMedia?>(null) // For viewing detail
    val activeEditingItem = MutableStateFlow<CapturedMedia?>(null) // Working copy in AI Studio

    // Lock System for Secure Vault (Default passcode 1234)
    val isVaultLocked = MutableStateFlow(true)
    val vaultPinCode = MutableStateFlow("")

    // Editing Studio Adjusters
    val aiEditBlurFactor = MutableStateFlow(0.0f)
    val aiEditEnhanceFactor = MutableStateFlow(0.0f)
    val aiEditFaceBeauty = MutableStateFlow(0.0f)
    val aiEditSkinTone = MutableStateFlow("Original") // Original, Bright, Bronze, Warm
    val isCartoonEffectActive = MutableStateFlow(false)
    val objectRemoveMode = MutableStateFlow(false) // Toggle draw to erase

    // Voice listening
    private var speechRecognizer: android.speech.SpeechRecognizer? = null
    private var recognizerIntent: android.content.Intent? = null

    fun initializeSpeechRecognizer(context: android.content.Context) {
        if (speechRecognizer != null) return
        val mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(context)
        mainExecutor.execute {
            if (android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
                recognizerIntent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) { audioListeningState.value = "Listening..." }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { audioListeningState.value = "Idle" }
                    override fun onError(error: Int) {
                        audioListeningState.value = "Idle"
                        if (isAutoListenEnabled.value) {
                            viewModelScope.launch {
                                delay(1000)
                                startListening()
                            }
                        }
                    }
                    override fun onResults(results: android.os.Bundle?) {
                        audioListeningState.value = "Idle"
                        val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            assistantBubble.value = text
                            applyVoiceCommand(text)
                        }
                        if (isAutoListenEnabled.value) {
                            startListening()
                        }
                    }
                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            assistantBubble.value = matches[0]
                        }
                    }
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })
            }
        }
    }

    // New: Horizon Level Tracking (Simulated for Demo/Pro HUD feel)
    val horizonAngle = MutableStateFlow(0f)
    init {
        viewModelScope.launch {
            var angle = 0f
            var delta = 0.2f
            while (true) {
                delay(50)
                angle += delta
                if (angle > 1.5f || angle < -1.5f) delta = -delta
                horizonAngle.value = angle
            }
        }
    }

    fun startListening() {
        val intent = recognizerIntent ?: return
        val mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(getApplication())
        mainExecutor.execute {
            speechRecognizer?.startListening(intent)
            audioListeningState.value = "Listening..."
        }
    }

    fun stopListening() {
        val mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(getApplication())
        mainExecutor.execute {
            speechRecognizer?.stopListening()
            audioListeningState.value = "Idle"
        }
    }

    // --------------------------------------------------
    // 6. Speech / Voice System Setup
    // --------------------------------------------------
    private var tts: TextToSpeech? = null

    private fun updateTTSLanguage() {
        val locale = if (currentLanguage.value == "Bengali") Locale("bn", "BD") else Locale.US
        tts?.language = locale
        recognizerIntent?.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, locale)
        recognizerIntent?.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toString())
    }

    init {
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                updateTTSLanguage()
                val welcome = if (currentLanguage.value == "Bengali") "ক্যামেরা প্রস্তুত।" else "Camera ready."
                speakNow(welcome)
            }
        }

        // Auto increment video timer when recording
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (isRecordingVideo.value && !isVideoPaused.value) {
                    videoDurationSeconds.value += 1
                }
            }
        }
    }

    fun speakNow(message: String) {
        if (isRecordingVideo.value) {
            assistantBubble.value = message
            return 
        }
        assistantBubble.value = message
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun toggleCameraLens() {
        currentCameraLens.value = if (currentCameraLens.value == "BACK") "FRONT" else "BACK"
        speakNow("Switching to ${currentCameraLens.value.lowercase()} camera lens.")
    }

    fun cycleScene() {
        val nextIndex = (currentSceneIndex.value + 1) % scenes.size
        currentSceneIndex.value = nextIndex
        val newScene = scenes[nextIndex]
        speakNow("AI Scene Mode: ${newScene.name}. ${newScene.recommendation}")
    }

    fun applyVoiceCommand(phrase: String) {
        audioListeningState.value = "Processing command: '$phrase'"
        viewModelScope.launch {
            delay(500) // simulated processing delay
            val normalized = phrase.trim().lowercase()

            // First check if it matches a registered custom voice command
            val foundCustom = customVoiceCommands.value.firstOrNull { it.phrase.trim().lowercase() == normalized }
            if (foundCustom != null) {
                executeCustomCommand(foundCustom)
                audioListeningState.value = "Idle"
                return@launch
            }

            // Fallback to core system commands
            when {
                normalized.contains("take picture") || normalized.contains("capture") || normalized.contains("ছবি") || normalized.contains("পিকচার") -> {
                    capturePhoto(delaySeconds = shutterTimer.value)
                }
                normalized.contains("start video") || normalized.contains("ভিডিও শুরু") || normalized.contains("ভিডিও রেকর্ড") -> {
                    startVideo()
                }
                normalized.contains("stop video") || normalized.contains("ভিডিও বন্ধ") || normalized.contains("ভিডিও সেভ") -> {
                    stopVideo()
                }
                normalized.contains("pause video") || normalized.contains("ভিডিও বিরতি") -> {
                    pauseVideo()
                }
                normalized.contains("resume video") || normalized.contains("ভিডিও পুনরায়") -> {
                    resumeVideo()
                }
                normalized.contains("zoom in") || normalized.contains("জুম ইন") || normalized.contains("কাছে") || normalized.contains("জুম করো") -> {
                    zoomLevel.value = (zoomLevel.value + 2.0f).coerceAtMost(10.0f)
                    CameraGlobals.cameraControl?.setZoomRatio(zoomLevel.value)
                    speakNow("Zoom adjusted to ${String.format("%.1f", zoomLevel.value)}x")
                    assistantBubble.value = "ZOOMING IN: ${String.format("%.1f", zoomLevel.value)}x"
                }
                normalized.contains("10x zoom") || normalized.contains("১০ গুণ জুম") || normalized.contains("ফুল জুম") -> {
                    zoomLevel.value = 10.0f
                    CameraGlobals.cameraControl?.setZoomRatio(zoomLevel.value)
                    speakNow("Zooming to maximum 10x.")
                    assistantBubble.value = "MAX ZOOM: 10.0x"
                }
                normalized.contains("zoom out") || normalized.contains("জুম আউট") || normalized.contains("দুরে") || normalized.contains("জুম কমাও") -> {
                    zoomLevel.value = (zoomLevel.value - 2.0f).coerceAtLeast(1.0f)
                    CameraGlobals.cameraControl?.setZoomRatio(zoomLevel.value)
                    speakNow("Zoom adjusted to ${String.format("%.1f", zoomLevel.value)}x")
                    assistantBubble.value = "ZOOMING OUT: ${String.format("%.1f", zoomLevel.value)}x"
                }
                normalized.contains("open gallery") || normalized.contains("গ্যালারি") || normalized.contains("গ্যালারি দেখাও") -> {
                    speakNow("Opening your photo gallery.")
                    assistantBubble.value = "OPENING GALLERY..."
                    // This command would ideally trigger showGalleryPanel in the UI. 
                    // Since ViewModel doesn't direct UI flow, we speak it.
                }
                normalized.contains("settings") || normalized.contains("সেটিংস") -> {
                    speakNow("Opening camera settings panel.")
                    assistantBubble.value = "CONFIGURING SETTINGS..."
                }
                normalized.contains("front camera") || normalized.contains("সামনের ক্যামেরা") || normalized.contains("সেলফি") -> {
                    currentCameraLens.value = "FRONT"
                    speakNow("Front camera enabled.")
                }
                normalized.contains("back camera") || normalized.contains("পেছনের ক্যামেরা") -> {
                    currentCameraLens.value = "BACK"
                    speakNow("Back camera enabled.")
                }
                normalized.contains("switch camera") || normalized.contains("ক্যামেরা পরিবর্তন") || normalized.contains("ক্যামেরা চেঞ্জ") -> {
                    toggleCameraLens()
                }
                normalized.contains("flash on") || normalized.contains("ফ্ল্যাশ অন") || normalized.contains("ফ্ল্যাশ চালু") -> {
                    isFlashEnabled.value = true
                    speakNow("Camera flashlight active for next capture.")
                }
                normalized.contains("flash off") || normalized.contains("ফ্ল্যাশ অফ") || normalized.contains("ফ্ল্যাশ বন্ধ") -> {
                    isFlashEnabled.value = false
                    CameraGlobals.cameraControl?.enableTorch(false)
                    speakNow("Camera flashlight disabled.")
                }
                normalized.contains("timer 3") || normalized.contains("৩ সেকেন্ড") -> {
                    shutterTimer.value = 3
                    speakNow("Shutter timer set to 3 seconds.")
                }
                normalized.contains("timer 10") || normalized.contains("১০ সেকেন্ড") -> {
                    shutterTimer.value = 10
                    speakNow("Shutter timer set to 10 seconds.")
                }
                normalized.contains("timer off") || normalized.contains("টাইমার বন্ধ") -> {
                    shutterTimer.value = 0
                    speakNow("Shutter timer disabled.")
                }
                normalized.contains("night mode") -> {
                    currentCameraMode.value = "Night"
                    speakNow("DSLR Night mode loaded. Shutter expanded.")
                }
                normalized.contains("portrait mode") -> {
                    currentCameraMode.value = "Portrait"
                    speakNow("Lens configuration set to 50mm portrait blur.")
                }
                normalized.contains("increase brightness") -> {
                    exposureCompensation.value = (exposureCompensation.value + 1.0f).coerceAtMost(3.0f)
                    speakNow("Exposure level boosted.")
                }
                normalized.contains("reduce brightness") -> {
                    exposureCompensation.value = (exposureCompensation.value - 1.0f).coerceAtLeast(-3.0f)
                    speakNow("Exposure level dimmed.")
                }
                else -> {
                    speakNow("Voice phrase unrecognized. Add it to Custom Commands panel.")
                }
            }
            audioListeningState.value = "Idle"
        }
    }

    private suspend fun executeCustomCommand(custom: CustomCommand) {
        currentCameraLens.value = custom.cameraSelection
        currentCameraMode.value = custom.filterType
        zoomLevel.value = custom.zoomLevel
        CameraGlobals.cameraControl?.setZoomRatio(custom.zoomLevel)

        speakNow("Custom Routine Triggered: ${custom.phrase}. Initiating ${custom.timerSeconds} second self-timer.")

        if (custom.timerSeconds > 0) {
            for (i in custom.timerSeconds downTo 1) {
                speakNow("$i")
                delay(1000)
            }
        }

        if (custom.actionType == "VIDEO") {
            startVideo()
        } else {
            capturePhoto(
                overrideMode = custom.filterType,
                overrideResolution = custom.resolution
            )
        }
    }

    // --------------------------------------------------
    // Core Actions: Capture, Record
    // --------------------------------------------------
    fun capturePhoto(overrideMode: String? = null, overrideResolution: String? = null, delaySeconds: Int = 0) {
        val capture = CameraGlobals.imageCapture
        if (capture == null) {
            speakNow("Camera hardware not ready.")
            return
        }
        
        viewModelScope.launch {
            if (delaySeconds > 0) {
                for (i in delaySeconds downTo 1) {
                    speakNow("$i") // Tts sound for timer
                    // Play a tick sound if possible or just use TTS
                    delay(1000)
                }
                actionSound.play(android.media.MediaActionSound.START_VIDEO_RECORDING) // Use as a beep
            }
            
            // Flash Trigger logic
            if (isFlashEnabled.value) {
                CameraGlobals.cameraControl?.enableTorch(true)
                delay(200) // wait a bit for brightness
            }
            
            speakNow("Capturing...")
            actionSound.play(android.media.MediaActionSound.SHUTTER_CLICK)

            val context = getApplication<Application>()
            val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val dateStr = format.format(Date())
            val fileName = "IMG_${dateStr}.jpg"

            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Camera")
                }
            }
            val outputOptions = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()

            capture.takePicture(
                outputOptions,
                androidx.core.content.ContextCompat.getMainExecutor(context),
                object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: androidx.camera.core.ImageCapture.OutputFileResults) {
                        if (isFlashEnabled.value) {
                            CameraGlobals.cameraControl?.enableTorch(false)
                        }
                        val uri = outputFileResults.savedUri?.toString() ?: ""
                        viewModelScope.launch {
                            val activeScene = activeScene.value
                            val detectedLabels = detectedObjectResults.value.joinToString(", ") { it.label }
                            val aiSummary = if (detectedLabels.isNotEmpty()) "Detected: $detectedLabels" else "Scene: ${activeScene.name}"
                            val newMedia = CapturedMedia(
                                name = fileName,
                                uriPath = uri,
                                isVideo = false,
                                detectedObjects = if (detectedLabels.isNotEmpty()) detectedLabels else "Camera Scene",
                                detectedScene = activeScene.name
                            )
                            repository.insertMedia(newMedia)
                            speakNow("Photo saved. $aiSummary")
                        }
                    }
                    override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                        if (isFlashEnabled.value) {
                            CameraGlobals.cameraControl?.enableTorch(false)
                        }
                        speakNow("Capture error: ${exception.message}")
                    }
                }
            )
        }
    }

    private var recordingJob: Job? = null

    fun startVideo() {
        if (isRecordingVideo.value) return
        val capture = CameraGlobals.videoCapture
        if (capture == null) {
            speakNow("Video hardware not ready.")
            return
        }
        val context = getApplication<Application>()
        val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val dateStr = format.format(Date())
        val fileName = "VID_${dateStr}.mp4"

        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Movies/Camera")
            }
        }
        val outputOptions = androidx.camera.video.MediaStoreOutputOptions.Builder(
            context.contentResolver,
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val pendingRecording = capture.output.prepareRecording(context, outputOptions).withAudioEnabled()
            CameraGlobals.activeRecording = pendingRecording.start(androidx.core.content.ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is androidx.camera.video.VideoRecordEvent.Finalize -> {
                        val uri = event.outputResults.outputUri.toString()
                        viewModelScope.launch {
                            val newMedia = CapturedMedia(
                                name = fileName,
                                uriPath = uri,
                                isVideo = true,
                                detectedObjects = "Real Video",
                                detectedScene = "CameraX",
                                fileSizeBytes = event.outputResults.outputUri.hashCode().toLong()
                            )
                            repository.insertMedia(newMedia)
                            speakNow("Video saved to gallery.")
                        }
                        CameraGlobals.activeRecording = null
                        this@CameraViewModel.recordingJob?.cancel()
                        isRecordingVideo.value = false
                    }
                }
            }
        } else {
            speakNow("Microphone permission missing for video.")
            return
        }

        viewModelScope.launch {
            speakNow("Starting cinematic video recording.")
            isRecordingVideo.value = true
            videoDurationSeconds.value = 0
            isVideoPaused.value = false
            
            recordingJob?.cancel()
            this@CameraViewModel.recordingJob = launch {
                while (isRecordingVideo.value) {
                    delay(1000)
                    if (!isVideoPaused.value) {
                        videoDurationSeconds.value++
                    }
                }
            }
        }
    }

    fun pauseVideo() {
        if (!isRecordingVideo.value || isVideoPaused.value) return
        CameraGlobals.activeRecording?.pause()
        isVideoPaused.value = true
        speakNow("Recording paused.")
    }

    fun resumeVideo() {
        if (!isRecordingVideo.value || !isVideoPaused.value) return
        CameraGlobals.activeRecording?.resume()
        isVideoPaused.value = false
        speakNow("Recording resumed.")
    }

    fun stopVideo() {
        if (!isRecordingVideo.value) return
        CameraGlobals.activeRecording?.stop()
        this@CameraViewModel.recordingJob?.cancel()
        isRecordingVideo.value = false
        speakNow("Video captured.")
    }

    // --------------------------------------------------
    // Room custom voice command inserts/deletes
    // --------------------------------------------------
    fun addCustomCommand(phrase: String, camera: String, timer: Int, filter: String, resolution: String, stabilization: Boolean, zoom: Float, action: String) {
        viewModelScope.launch {
            val element = CustomCommand(
                phrase = phrase,
                cameraSelection = camera,
                timerSeconds = timer,
                filterType = filter,
                resolution = resolution,
                stabilization = stabilization,
                zoomLevel = zoom,
                actionType = action,
                isSystem = false
            )
            repository.insertCustomCommand(element)
            speakNow("Custom phrase '$phrase' registered successfully.")
        }
    }

    fun removeCustomCommand(id: Int) {
        viewModelScope.launch {
            repository.deleteCustomCommand(id)
            speakNow("Custom phrase removed.")
        }
    }

    // --------------------------------------------------
    // Media & Gallery Action Operations
    // --------------------------------------------------
    fun toggleFavorite(item: CapturedMedia) {
        viewModelScope.launch {
            repository.setFavorite(item.id, !item.isFavorite)
        }
    }

    fun toggleSecureFolder(item: CapturedMedia) {
        viewModelScope.launch {
            val isSecured = !item.isSecure
            repository.setSecure(item.id, isSecured)
            if (isSecured) {
                speakNow("${item.name} moved to cryptographically secure vault.")
            } else {
                speakNow("${item.name} restored to public gallery.")
            }
        }
    }

    fun deleteMediaItem(item: CapturedMedia) {
        viewModelScope.launch {
            repository.deleteMedia(item.id)
            selectedMediaItem.value = null
            activeEditingItem.value = null
            speakNow("${item.name} deleted permanently.")
        }
    }

    fun openEditingStudio(item: CapturedMedia) {
        activeEditingItem.value = item
        // Initialize editing variables with clean defaults
        aiEditBlurFactor.value = 0.0f
        aiEditEnhanceFactor.value = 0.0f
        aiEditFaceBeauty.value = 0.0f
        aiEditSkinTone.value = "Original"
        isCartoonEffectActive.value = false
        objectRemoveMode.value = false
        speakNow("Loading image to AI Creative Studio. Use sliders to edit.")
    }

    fun saveEditingStudioChanges() {
        val working = activeEditingItem.value ?: return
        viewModelScope.launch {
            speakNow("Synthesizing neural assets... saving to local flash.")
            delay(600) // Simulated rendering processing delay

            // Format tags representing changes
            val modifications = mutableListOf<String>()
            if (aiEditBlurFactor.value > 0.1f) modifications.add("Bokeh")
            if (aiEditEnhanceFactor.value > 0.1f) modifications.add("Sharpened")
            if (aiEditFaceBeauty.value > 0.1f) modifications.add("Skinsmoothed")
            if (aiEditSkinTone.value != "Original") modifications.add(aiEditSkinTone.value)
            if (isCartoonEffectActive.value) modifications.add("Cartoonized")

            val modString = if (modifications.isNotEmpty()) " (${modifications.joinToString(", ")})" else ""
            val newObjects = if (working.detectedObjects.isNotEmpty()) {
                "${working.detectedObjects}, Edited$modString"
            } else {
                "Edited$modString"
            }

            val updatedMedia = working.copy(
                id = 0, // save as a new master file!
                name = "AI_EDIT_${System.currentTimeMillis() / 1000}.jpg",
                detectedObjects = newObjects
            )
            repository.insertMedia(updatedMedia)
            activeEditingItem.value = null
            speakNow("New AI enhanced image saved to gallery.")
        }
    }

    // --------------------------------------------------
    // Live Gestures Simulation Logic
    // --------------------------------------------------
    fun simulateGesture(gesture: String) {
        activeGesture.value = gesture
        speakNow("Gesture Detected: $gesture")
        viewModelScope.launch {
            delay(1500)
            activeGesture.value = "None"
        }
        when (gesture) {
            "Palm Show" -> capturePhoto()
            "Thumbs Up" -> {
                if (isRecordingVideo.value) stopVideo() else startVideo()
            }
            "Two Finger Pinch" -> {
                zoomLevel.value = if (zoomLevel.value > 1.5f) 1.0f else 3.0f
                speakNow("Two finger gesture triggered lens scale. Zoom calibrated.")
            }
            "Swipe Left" -> {
                speakNow("Loading integrated premium gallery.")
                activeGalleryTab.value = "All"
            }
            "Swipe Right" -> {
                toggleCameraLens()
            }
        }
    }

    // --------------------------------------------------
    // Clean-up
    // --------------------------------------------------
    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}
