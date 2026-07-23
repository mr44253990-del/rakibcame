package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AgentRepository
import com.example.data.AppDatabase
import com.example.data.ButtonMapping
import com.example.data.ControllerProfile
import com.example.service.ControllerMapperService
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ControllerDeviceInfo(
    val id: Int,
    val name: String,
    val descriptor: String,
    val vendorId: Int,
    val productId: Int
)

data class SourceInput(
    val code: String,
    val label: String,
    val value: Float = 1f
)

data class TraceEntry(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val details: String,
    val level: String,
    val createdAt: Long = System.currentTimeMillis()
)

class ControllerMapperViewModel(application: Application) : AndroidViewModel(application), InputManager.InputDeviceListener {
    private val repository = AgentRepository(AppDatabase.getInstance(application).agentDao())
    private val prefs = application.getSharedPreferences("virtual_xbox_mapper_prefs", Context.MODE_PRIVATE)
    private val inputManager = application.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val activeSourceTargets = linkedMapOf<String, String>()

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            _shizukuPermissionGranted.value = granted
            _shizukuBackendStatus.value = if (granted) backendLabel() else "Shizuku permission denied."
            addTrace("Shizuku permission", if (granted) "Permission granted" else "Permission denied", if (granted) "success" else "error")
        }
    }

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshShizukuState()
        addTrace("Shizuku connected", backendLabel(), "success")
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        _shizukuAvailable.value = false
        _shizukuPermissionGranted.value = false
        _shizukuBackendStatus.value = "Shizuku binder disconnected."
        addTrace("Shizuku disconnected", "Binder dead", "error")
    }

    private val _serviceEnabled = MutableStateFlow(prefs.getBoolean("SERVICE_ENABLED", false))
    val serviceEnabled: StateFlow<Boolean> = _serviceEnabled.asStateFlow()

    private val _rootModeEnabled = MutableStateFlow(prefs.getBoolean("ROOT_MODE_ENABLED", false))
    val rootModeEnabled: StateFlow<Boolean> = _rootModeEnabled.asStateFlow()

    private val _rootStatus = MutableStateFlow("Root / Sui backend not checked yet.")
    val rootStatus: StateFlow<String> = _rootStatus.asStateFlow()

    private val _shizukuAvailable = MutableStateFlow(false)
    val shizukuAvailable: StateFlow<Boolean> = _shizukuAvailable.asStateFlow()

    private val _shizukuPermissionGranted = MutableStateFlow(false)
    val shizukuPermissionGranted: StateFlow<Boolean> = _shizukuPermissionGranted.asStateFlow()

    private val _shizukuBackendStatus = MutableStateFlow("Shizuku backend not connected.")
    val shizukuBackendStatus: StateFlow<String> = _shizukuBackendStatus.asStateFlow()

    private val _status = MutableStateFlow("Compatibility mode ready.")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<ControllerDeviceInfo>>(emptyList())
    val connectedDevices: StateFlow<List<ControllerDeviceInfo>> = _connectedDevices.asStateFlow()

    private val _lastInput = MutableStateFlow<SourceInput?>(null)
    val lastInput: StateFlow<SourceInput?> = _lastInput.asStateFlow()

    private val _pressedTargets = MutableStateFlow<Set<String>>(emptySet())
    val pressedTargets: StateFlow<Set<String>> = _pressedTargets.asStateFlow()

    private val _traceEntries = MutableStateFlow<List<TraceEntry>>(emptyList())
    val traceEntries: StateFlow<List<TraceEntry>> = _traceEntries.asStateFlow()

    private val _activeProfileId = MutableStateFlow(prefs.getLong("ACTIVE_PROFILE_ID", -1L))
    val activeProfileId: StateFlow<Long> = _activeProfileId.asStateFlow()

    val profiles: StateFlow<List<ControllerProfile>> = repository.observeProfiles().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val mappings: StateFlow<List<ButtonMapping>> = _activeProfileId.flatMapLatest { profileId ->
        if (profileId <= 0L) flowOf(emptyList()) else repository.observeMappings(profileId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        inputManager.registerInputDeviceListener(this, null)
        initializeShizuku()
        refreshConnectedDevices()
        viewModelScope.launch {
            ensureDefaultProfile()
        }
        addTrace(
            title = "App started",
            details = "USB compatibility mapper চালু হয়েছে। Stock Android non-root mode এ real virtual Xbox HID output সীমিত। Root/Sui backend later required for broader game compatibility.",
            level = "info"
        )
    }

    private fun initializeShizuku() {
        runCatching {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
            Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        }
        refreshShizukuState()
    }

    fun refreshShizukuState() {
        val available = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        _shizukuAvailable.value = available
        if (!available) {
            _shizukuPermissionGranted.value = false
            _shizukuBackendStatus.value = "Shizuku not running. Start Shizuku or Sui first."
            return
        }
        val granted = runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        _shizukuPermissionGranted.value = granted
        _shizukuBackendStatus.value = if (granted) backendLabel() else "Shizuku connected, permission not granted yet."
    }

    fun requestShizukuPermission() {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            _shizukuBackendStatus.value = "Shizuku is not running. Open Shizuku app first."
            addTrace("Shizuku", "Shizuku binder not available", "error")
            return
        }
        if (runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)) {
            _shizukuPermissionGranted.value = true
            _shizukuBackendStatus.value = backendLabel()
            addTrace("Shizuku", "Permission already granted", "success")
            return
        }
        val deniedPermanently = runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false)
        if (deniedPermanently) {
            _shizukuBackendStatus.value = "Shizuku permission was denied. Grant it from Shizuku manager."
            addTrace("Shizuku", "Permission denied previously", "error")
            return
        }
        runCatching { Shizuku.requestPermission(SHIZUKU_REQUEST_CODE) }
            .onSuccess {
                _shizukuBackendStatus.value = "Permission request sent to Shizuku."
                addTrace("Shizuku", "Permission request sent", "info")
            }
            .onFailure {
                _shizukuBackendStatus.value = "Shizuku request failed: ${it.message ?: "unknown error"}"
                addTrace("Shizuku", _shizukuBackendStatus.value, "error")
            }
    }

    private fun backendLabel(): String {
        val uid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
        return when (uid) {
            0 -> "Shizuku / Sui backend: ROOT (uid=0)"
            2000 -> "Shizuku backend: ADB shell (uid=2000)"
            else -> "Shizuku backend connected (uid=$uid)"
        }
    }

    private suspend fun ensureDefaultProfile() {
        if (repository.getProfileCount() == 0) {
            val id = repository.createProfile("Default Xbox Layout")
            setActiveProfile(id)
        } else if (_activeProfileId.value <= 0L) {
            repository.getMostRecentProfileId()?.let { setActiveProfile(it) }
        }
    }

    fun createProfile(name: String) {
        viewModelScope.launch {
            val clean = name.trim().ifBlank {
                "Profile ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
            }
            val id = repository.createProfile(clean)
            setActiveProfile(id)
            addTrace("Profile created", "নতুন profile: $clean", "success")
        }
    }

    fun setActiveProfile(profileId: Long) {
        prefs.edit().putLong("ACTIVE_PROFILE_ID", profileId).apply()
        _activeProfileId.value = profileId
        _status.value = "Active profile switched."
    }

    fun removeAllRegisteredMappings() {
        val profileId = _activeProfileId.value
        if (profileId <= 0L) return
        viewModelScope.launch {
            repository.clearMappings(profileId)
            activeSourceTargets.clear()
            _pressedTargets.value = emptySet()
            _status.value = "সব registered mapping remove করা হয়েছে।"
            addTrace("Mappings cleared", "বর্তমান profile-এর সব mapping মুছে ফেলা হয়েছে।", "success")
        }
    }

    fun removeMapping(mappingId: Long) {
        viewModelScope.launch {
            repository.deleteMapping(mappingId)
            _status.value = "Mapping removed."
        }
    }

    fun assignLastInput(targetButton: String) {
        val profileId = _activeProfileId.value
        val input = _lastInput.value ?: run {
            _status.value = "আগে controller-এ একটি button বা stick move করুন।"
            return
        }
        if (profileId <= 0L) return
        viewModelScope.launch {
            repository.upsertMapping(profileId, input.code, input.label, targetButton)
            _status.value = "${input.label} এখন $targetButton এ map হয়েছে।"
            addTrace(
                title = "Mapping saved",
                details = "${input.label} → $targetButton",
                level = "success"
            )
        }
    }

    fun setServiceEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        prefs.edit().putBoolean("SERVICE_ENABLED", enabled).apply()
        _serviceEnabled.value = enabled
        val intent = Intent(context, ControllerMapperService::class.java).apply {
            action = if (enabled) ControllerMapperService.ACTION_START else ControllerMapperService.ACTION_STOP
            putExtra("root_mode", _rootModeEnabled.value)
        }
        if (enabled) {
            ContextCompat.startForegroundService(context, intent)
            _status.value = if (_rootModeEnabled.value) {
                "Service started with root-ready mode requested."
            } else {
                "Service started. Keep this app active for live input capture in non-root mode."
            }
            addTrace("Service ON", "Foreground compatibility service চালু হয়েছে।", "info")
        } else {
            context.startService(intent)
            _status.value = "Service stopped."
            addTrace("Service OFF", "Foreground service বন্ধ করা হয়েছে।", "info")
        }
    }

    fun refreshConnectedDevices() {
        val devices = InputDevice.getDeviceIds()
            .toList()
            .mapNotNull { id -> InputDevice.getDevice(id) }
            .filter { isGameController(it) }
            .map {
                ControllerDeviceInfo(
                    id = it.id,
                    name = it.name ?: "Unknown Controller",
                    descriptor = it.descriptor ?: "N/A",
                    vendorId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) it.vendorId else 0,
                    productId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) it.productId else 0
                )
            }
        _connectedDevices.value = devices
    }

    fun setRootModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("ROOT_MODE_ENABLED", enabled).apply()
        _rootModeEnabled.value = enabled
        _status.value = if (enabled) "Root-ready mode enabled. Grant superuser permission when prompted." else "Root-ready mode disabled."
        addTrace("Root mode", if (enabled) "Root-ready mode enabled" else "Root-ready mode disabled", "info")
    }

    fun requestRootAccess() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runRootCommand("id")
            }
            _rootStatus.value = result
            addTrace("Root check", result, if (result.contains("uid=0")) "success" else "error")
        }
    }

    fun checkUinputAccess() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runRootCommand("if [ -w /dev/uinput ]; then echo UINPUT_WRITABLE; else echo UINPUT_BLOCKED; fi")
            }
            _rootStatus.value = result
            addTrace("uinput check", result, if (result.contains("UINPUT_WRITABLE")) "success" else "error")
        }
    }

    fun onControllerKeyEvent(event: KeyEvent): Boolean {
        val device = event.device ?: return false
        if (!isGameController(device)) return false

        val code = "KEY_${event.keyCode}"
        val label = KeyEvent.keyCodeToString(event.keyCode)
        val input = SourceInput(code = code, label = label)
        _lastInput.value = input

        val mappedTarget = mappings.value.firstOrNull { it.sourceCode == code }?.targetButton
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (!event.isLongPress) {
                    if (mappedTarget != null) markPressed(code, mappedTarget) else markReleased(code)
                    addTrace("Input detected", "$label চাপা হয়েছে", "info")
                    _status.value = "Detected: $label"
                }
            }
            KeyEvent.ACTION_UP -> {
                markReleased(code)
            }
        }
        return true
    }

    fun onControllerMotionEvent(event: MotionEvent): Boolean {
        val device = event.device ?: return false
        if (!isGameController(device)) return false

        val activeInputs = mutableListOf<SourceInput>()
        collectAxis(event, MotionEvent.AXIS_HAT_X, "DPAD_LEFT", "D-Pad Left", negative = true, activeInputs = activeInputs)
        collectAxis(event, MotionEvent.AXIS_HAT_X, "DPAD_RIGHT", "D-Pad Right", negative = false, activeInputs = activeInputs)
        collectAxis(event, MotionEvent.AXIS_HAT_Y, "DPAD_UP", "D-Pad Up", negative = true, activeInputs = activeInputs)
        collectAxis(event, MotionEvent.AXIS_HAT_Y, "DPAD_DOWN", "D-Pad Down", negative = false, activeInputs = activeInputs)
        collectAxis(event, MotionEvent.AXIS_X, "L_STICK_LEFT", "Left Stick Left", negative = true, activeInputs = activeInputs)
        collectAxis(event, MotionEvent.AXIS_X, "L_STICK_RIGHT", "Left Stick Right", negative = false, activeInputs = activeInputs)
        collectAxis(event, MotionEvent.AXIS_Y, "L_STICK_UP", "Left Stick Up", negative = true, activeInputs = activeInputs)
        collectAxis(event, MotionEvent.AXIS_Y, "L_STICK_DOWN", "Left Stick Down", negative = false, activeInputs = activeInputs)
        collectAxis(event, MotionEvent.AXIS_Z, "R_STICK_LEFT", "Right Stick Left", negative = true, activeInputs = activeInputs)
        collectAxis(event, MotionEvent.AXIS_Z, "R_STICK_RIGHT", "Right Stick Right", negative = false, activeInputs = activeInputs)
        collectAxis(event, MotionEvent.AXIS_RZ, "R_STICK_UP", "Right Stick Up", negative = true, activeInputs = activeInputs)
        collectAxis(event, MotionEvent.AXIS_RZ, "R_STICK_DOWN", "Right Stick Down", negative = false, activeInputs = activeInputs)
        collectTrigger(event, MotionEvent.AXIS_LTRIGGER, "LT_AXIS", "Left Trigger", activeInputs)
        collectTrigger(event, MotionEvent.AXIS_RTRIGGER, "RT_AXIS", "Right Trigger", activeInputs)

        val allKnownSources = listOf(
            "DPAD_LEFT", "DPAD_RIGHT", "DPAD_UP", "DPAD_DOWN",
            "L_STICK_LEFT", "L_STICK_RIGHT", "L_STICK_UP", "L_STICK_DOWN",
            "R_STICK_LEFT", "R_STICK_RIGHT", "R_STICK_UP", "R_STICK_DOWN",
            "LT_AXIS", "RT_AXIS"
        )

        allKnownSources.forEach { sourceCode ->
            val active = activeInputs.any { it.code == sourceCode }
            val mappedTarget = mappings.value.firstOrNull { it.sourceCode == sourceCode }?.targetButton
            if (active && mappedTarget != null) markPressed(sourceCode, mappedTarget) else if (!active) markReleased(sourceCode)
        }

        activeInputs.firstOrNull()?.let {
            _lastInput.value = it
            _status.value = "Detected: ${it.label}"
        }
        return activeInputs.isNotEmpty()
    }

    private fun runRootCommand(command: String): String {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exit = process.waitFor()
            if (exit == 0) output.ifBlank { "Root command succeeded." } else "Root command failed (exit=$exit): ${output.ifBlank { "no output" }}"
        } catch (t: Throwable) {
            "Root request failed: ${t.message ?: "unknown error"}"
        }
    }

    private fun collectAxis(
        event: MotionEvent,
        axis: Int,
        code: String,
        label: String,
        negative: Boolean,
        activeInputs: MutableList<SourceInput>
    ) {
        val value = event.getAxisValue(axis)
        val active = if (negative) value <= -0.55f else value >= 0.55f
        if (active) activeInputs += SourceInput(code, label, kotlin.math.abs(value))
    }

    private fun collectTrigger(
        event: MotionEvent,
        axis: Int,
        code: String,
        label: String,
        activeInputs: MutableList<SourceInput>
    ) {
        val value = event.getAxisValue(axis)
        if (value >= 0.4f) activeInputs += SourceInput(code, label, value)
    }

    private fun markPressed(sourceCode: String, targetButton: String) {
        activeSourceTargets[sourceCode] = targetButton
        _pressedTargets.value = activeSourceTargets.values.toSet()
    }

    private fun markReleased(sourceCode: String) {
        activeSourceTargets.remove(sourceCode)
        _pressedTargets.value = activeSourceTargets.values.toSet()
    }

    private fun addTrace(title: String, details: String, level: String) {
        val entry = TraceEntry(title = title, details = details, level = level)
        _traceEntries.value = (listOf(entry) + _traceEntries.value).take(80)
    }

    private fun isGameController(device: InputDevice?): Boolean {
        val sources = device?.sources ?: return false
        return (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
            (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        refreshConnectedDevices()
        addTrace("Controller connected", "Device id: $deviceId", "success")
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        refreshConnectedDevices()
        addTrace("Controller removed", "Device id: $deviceId", "error")
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        refreshConnectedDevices()
    }

    override fun onCleared() {
        inputManager.unregisterInputDeviceListener(this)
        runCatching {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
            Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        }
        super.onCleared()
    }

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 1107
    }
}
