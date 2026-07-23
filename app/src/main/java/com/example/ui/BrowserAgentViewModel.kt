package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.ActionLog
import com.example.data.AgentMemory
import com.example.data.AgentRepository
import com.example.data.AppDatabase
import com.example.data.ChatSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class ChatRole { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class BrowserTabState(
    val id: String,
    val title: String = "New Tab",
    val url: String = "https://example.com",
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

data class InteractiveNode(
    val selector: String,
    val label: String,
    val type: String
)

data class PageSnapshot(
    val tabId: String,
    val title: String,
    val url: String,
    val excerpt: String,
    val interactive: List<InteractiveNode> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class BrowserAction(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val tabId: String? = null,
    val url: String? = null,
    val selector: String? = null,
    val text: String? = null,
    val waitMs: Long? = null,
    val background: Boolean = false,
    val saveAs: String? = null,
    val stepNumber: Int = 0,
    val totalSteps: Int = 0
)

data class PlanPreview(
    val summary: String,
    val actions: List<BrowserAction>
)

data class PlannerResult(
    val status: String,
    val message: String,
    val actions: List<BrowserAction>,
    val memories: List<Pair<String, String>> = emptyList()
)

data class ThinkingEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val details: String,
    val status: String,
    val createdAt: Long = System.currentTimeMillis()
)

class BrowserAgentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AgentRepository(AppDatabase.getInstance(application).agentDao())
    private val prefs = application.getSharedPreferences("browser_agent_prefs", Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _tabs = MutableStateFlow(
        listOf(
            BrowserTabState(
                id = "tab-1",
                title = "Start",
                url = "https://example.com"
            )
        )
    )
    val tabs: StateFlow<List<BrowserTabState>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow("tab-1")
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _pageSnapshots = MutableStateFlow<Map<String, PageSnapshot>>(emptyMap())
    val pageSnapshots: StateFlow<Map<String, PageSnapshot>> = _pageSnapshots.asStateFlow()

    private val _clipboard = MutableStateFlow<Map<String, String>>(emptyMap())
    val clipboard: StateFlow<Map<String, String>> = _clipboard.asStateFlow()

    private val _planPreview = MutableStateFlow<PlanPreview?>(null)
    val planPreview: StateFlow<PlanPreview?> = _planPreview.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val _browserCommands = MutableSharedFlow<BrowserAction>(extraBufferCapacity = 64)
    val browserCommands = _browserCommands.asSharedFlow()

    private val _thinkingEntries = MutableStateFlow<List<ThinkingEntry>>(emptyList())
    val thinkingEntries: StateFlow<List<ThinkingEntry>> = _thinkingEntries.asStateFlow()

    private val _aiApiKey = MutableStateFlow(loadStoredApiKey())
    val aiApiKey: StateFlow<String> = _aiApiKey.asStateFlow()

    private val _aiModel = MutableStateFlow(loadStoredModel())
    val aiModel: StateFlow<String> = _aiModel.asStateFlow()

    private val _aiBaseUrl = MutableStateFlow(loadStoredBaseUrl())
    val aiBaseUrl: StateFlow<String> = _aiBaseUrl.asStateFlow()

    private val _aiStatus = MutableStateFlow("AI ready.")
    val aiStatus: StateFlow<String> = _aiStatus.asStateFlow()

    private val _autoPreviewEnabled = MutableStateFlow(prefs.getBoolean("AUTO_PREVIEW", true))
    val autoPreviewEnabled: StateFlow<Boolean> = _autoPreviewEnabled.asStateFlow()

    private val _retryCount = MutableStateFlow(prefs.getInt("RETRY_COUNT", 3).coerceIn(1, 5))
    val retryCount: StateFlow<Int> = _retryCount.asStateFlow()

    private val _currentSessionId = MutableStateFlow(
        prefs.getString("CURRENT_SESSION_ID", null) ?: generateSessionId()
    )
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    val sessions: StateFlow<List<ChatSession>> = repository.observeSessions().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val chatMessages: StateFlow<List<ChatMessage>> = _currentSessionId.flatMapLatest { sessionId ->
        repository.observeChatRecords(sessionId).map { records ->
            records.map {
                ChatMessage(
                    id = it.id.toString(),
                    role = runCatching { ChatRole.valueOf(it.role) }.getOrDefault(ChatRole.SYSTEM),
                    text = it.text,
                    createdAt = it.createdAt
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val memories: StateFlow<List<AgentMemory>> = _currentSessionId.flatMapLatest { sessionId ->
        repository.observeMemories(sessionId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs: StateFlow<List<ActionLog>> = _currentSessionId.flatMapLatest { sessionId ->
        repository.observeLogs(sessionId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        bootstrapSession(_currentSessionId.value)
    }

    private fun generateSessionId(): String = "session-${System.currentTimeMillis()}"

    private fun defaultSessionTitle(): String {
        return "New Chat ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
    }

    private fun bootstrapSession(sessionId: String) {
        viewModelScope.launch {
            repository.ensureSession(sessionId, defaultSessionTitle())
            val welcomeKey = "WELCOME_$sessionId"
            if (!prefs.getBoolean(welcomeKey, false)) {
                repository.addChatRecord(
                    sessionId,
                    ChatRole.ASSISTANT.name,
                    "হাই। আমি ধাপে ধাপে কাজ করতে পারি, একাধিক টাস্ক সিরিয়ালি চালাতে পারি, ওয়েবপেজের লেখা পড়ে বাংলায় বুঝিয়ে বলতে পারি, আর যে কাজ করি সেটা আপনাকে চ্যাটে জানাই।"
                )
                prefs.edit().putBoolean(welcomeKey, true).apply()
            }
        }
    }

    fun createNewSession() {
        val sessionId = generateSessionId()
        prefs.edit().putString("CURRENT_SESSION_ID", sessionId).apply()
        _currentSessionId.value = sessionId
        _thinkingEntries.value = emptyList()
        _clipboard.value = emptyMap()
        bootstrapSession(sessionId)
    }

    fun switchSession(sessionId: String) {
        prefs.edit().putString("CURRENT_SESSION_ID", sessionId).apply()
        _currentSessionId.value = sessionId
        _thinkingEntries.value = emptyList()
        bootstrapSession(sessionId)
    }

    private fun loadStoredApiKey(): String {
        val stored = prefs.getString("AI_API_KEY", null).orEmpty().trim()
        if (stored.isNotBlank()) return stored
        return buildConfigApiKey().takeUnless { it.equals("CHANGE_ME", ignoreCase = true) }.orEmpty()
    }

    private fun loadStoredModel(): String {
        return prefs.getString("AI_MODEL", null)?.trim().takeUnless { it.isNullOrBlank() }
            ?: buildConfigModel().ifBlank { "mistral-small-latest" }
    }

    private fun loadStoredBaseUrl(): String {
        return prefs.getString("AI_BASE_URL", null)?.trim().takeUnless { it.isNullOrBlank() }
            ?: "https://api.mistral.ai/v1/chat/completions"
    }

    fun maskedApiKey(): String {
        val value = _aiApiKey.value.trim()
        if (value.isBlank()) return "Not configured"
        return if (value.length <= 8) "••••••••" else value.take(4) + "••••••" + value.takeLast(4)
    }

    fun saveAiConfig(apiKey: String, model: String, baseUrl: String) {
        val finalKey = apiKey.trim()
        val finalModel = model.trim().ifBlank { "mistral-small-latest" }
        val finalBaseUrl = normalizeBaseUrl(baseUrl)
        prefs.edit()
            .putString("AI_API_KEY", finalKey)
            .putString("AI_MODEL", finalModel)
            .putString("AI_BASE_URL", finalBaseUrl)
            .apply()
        _aiApiKey.value = finalKey
        _aiModel.value = finalModel
        _aiBaseUrl.value = finalBaseUrl
        _aiStatus.value = if (finalKey.isBlank()) "AI config saved without API key." else "AI config saved."
        addChat(ChatRole.SYSTEM, "AI settings updated for model $finalModel")
    }

    fun setAutoPreview(enabled: Boolean) {
        prefs.edit().putBoolean("AUTO_PREVIEW", enabled).apply()
        _autoPreviewEnabled.value = enabled
    }

    fun setRetryCount(value: Int) {
        val safeValue = value.coerceIn(1, 5)
        prefs.edit().putInt("RETRY_COUNT", safeValue).apply()
        _retryCount.value = safeValue
    }

    fun clearClipboard() {
        _clipboard.value = emptyMap()
        addChat(ChatRole.SYSTEM, "Clipboard aliases cleared.")
    }

    fun addClipboardItem(alias: String, value: String) {
        val cleanAlias = alias.trim()
        if (cleanAlias.isBlank() || value.isBlank()) return
        _clipboard.value = _clipboard.value + (cleanAlias to value)
    }

    fun clearThinking() {
        _thinkingEntries.value = emptyList()
    }

    fun testAiConfig() {
        viewModelScope.launch {
            _aiStatus.value = "Testing AI connection…"
            val result = withContext(Dispatchers.IO) { performAiHealthCheck() }
            _aiStatus.value = result
            addChat(ChatRole.SYSTEM, result)
        }
    }

    fun submitQuickTool(action: String) {
        when (action) {
            "new_tab" -> viewModelScope.launch { _browserCommands.emit(BrowserAction(kind = "new_tab", url = "https://example.com")) }
            "background_tab" -> viewModelScope.launch { _browserCommands.emit(BrowserAction(kind = "new_tab", url = "https://developer.android.com", background = true)) }
            "refresh" -> viewModelScope.launch { _browserCommands.emit(BrowserAction(kind = "refresh", tabId = _activeTabId.value)) }
            "google" -> viewModelScope.launch { _browserCommands.emit(BrowserAction(kind = "open_url", tabId = _activeTabId.value, url = "https://www.google.com")) }
            "docs" -> viewModelScope.launch { _browserCommands.emit(BrowserAction(kind = "new_tab", url = "https://docs.mistral.ai", background = false)) }
            "clear_clipboard" -> clearClipboard()
            "clear_history" -> clearHistory()
            "new_chat" -> createNewSession()
        }
    }

    fun submitPrompt(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty()) return

        viewModelScope.launch {
            addChatSuspend(ChatRole.USER, prompt)

            if (looksUnsafe(prompt)) {
                addChatSuspend(
                    ChatRole.ASSISTANT,
                    "আমি temporary email, OTP/verification scraping, account abuse, বা bypass workflow তৈরি করতে সাহায্য করতে পারি না। তবে generic browsing, reading, click, type, copy, extract, summary, history, memory এবং ধাপে ধাপে safe automation করতে পারি।"
                )
                return@launch
            }

            _isWorking.value = true
            try {
                handlePrompt(prompt)
            } finally {
                _isWorking.value = false
            }
        }
    }

    private suspend fun handlePrompt(prompt: String) {
        if (handlePageInsightPrompt(prompt)) return

        parseRememberCommand(prompt)?.let { (title, content) ->
            repository.addMemory(_currentSessionId.value, title, content)
            addChatSuspend(ChatRole.ASSISTANT, "মেমোরিতে সেভ করেছি: $title")
            return
        }

        val localActions = parseLocalActions(prompt)
        if (localActions.isNotEmpty()) {
            _planPreview.value = PlanPreview(
                summary = "ধাপে ধাপে লোকাল কমান্ড প্ল্যান তৈরি হয়েছে।",
                actions = localActions
            )
            queueActions(localActions)
            addChatSuspend(ChatRole.ASSISTANT, buildQueuedMessage(localActions))
            return
        }

        val apiKey = effectiveApiKey()
        if (apiKey.isBlank()) {
            _aiStatus.value = "Missing API key. Open Settings and save your AI config."
            addChatSuspend(
                ChatRole.ASSISTANT,
                "AI planner ব্যবহার করতে Settings ট্যাবে গিয়ে API key, model, base URL save করুন।"
            )
            return
        }

        _aiStatus.value = "Planning with ${effectiveModel()}…"
        val result = withContext(Dispatchers.IO) { planWithMistral(prompt, apiKey) }
        when (result.status.lowercase()) {
            "ok" -> {
                result.memories.forEach { (title, content) -> repository.addMemory(_currentSessionId.value, title, content) }
                _planPreview.value = PlanPreview(result.message, result.actions)
                queueActions(result.actions)
                _aiStatus.value = "AI plan ready with ${result.actions.size} action(s)."
                addChatSuspend(ChatRole.ASSISTANT, result.message.ifBlank { buildQueuedMessage(result.actions) })
            }
            "refuse", "needs_user" -> {
                _planPreview.value = PlanPreview(result.message, emptyList())
                _aiStatus.value = result.message
                addChatSuspend(ChatRole.ASSISTANT, result.message)
            }
            else -> {
                _aiStatus.value = result.message
                addChatSuspend(ChatRole.ASSISTANT, result.message.ifBlank { "Planner returned an unexpected response." })
            }
        }
    }

    private suspend fun handlePageInsightPrompt(prompt: String): Boolean {
        val lowered = prompt.lowercase()
        val summaryRequest = listOf(
            "details", "detail", "summarize", "summary", "what is on this page",
            "ডিটেলস", "ডিটেল", "সামারি", "সংক্ষেপ", "এই ওয়েবসাইটে", "এই ওয়েবসাইটে", "এই পেইজে"
        ).any { it in lowered }
        if (!summaryRequest) return false

        val snapshot = _pageSnapshots.value[_activeTabId.value]
        if (snapshot == null) {
            addChatSuspend(ChatRole.ASSISTANT, "এখনও কোন page preview পাইনি। আগে একটি ওয়েবসাইট খুলুন বা refresh করুন।")
            return true
        }

        val important = snapshot.interactive.take(6).joinToString("\n") {
            "• ${it.label.ifBlank { it.type }}"
        }
        val text = buildString {
            appendLine("এই ওয়েবসাইটের তথ্য:")
            appendLine("শিরোনাম: ${snapshot.title}")
            appendLine("লিংক: ${snapshot.url}")
            appendLine("সংক্ষেপ: ${snapshot.excerpt.take(700)}")
            if (important.isNotBlank()) {
                appendLine("দেখা যাওয়া গুরুত্বপূর্ণ আইটেম:")
                appendLine(important)
            }
        }.trim()
        addChatSuspend(ChatRole.ASSISTANT, text)
        addThinking("পৃষ্ঠা বিশ্লেষণ", text.take(500), "success")
        return true
    }

    private suspend fun queueActions(actions: List<BrowserAction>) {
        val queued = actions.mapIndexed { index, action ->
            action.copy(stepNumber = index + 1, totalSteps = actions.size)
        }
        queued.forEach { action -> _browserCommands.emit(withDefaultTab(action)) }
    }

    private fun withDefaultTab(action: BrowserAction): BrowserAction {
        if (action.kind == "new_tab") return action
        if (action.tabId != null) return action
        return action.copy(tabId = _activeTabId.value)
    }

    fun onBrowserActionStarted(action: BrowserAction) {
        val message = buildStepMessage(action)
        addThinking(
            title = message,
            details = actionDebug(action),
            status = "running"
        )
        addChat(ChatRole.SYSTEM, message)
    }

    fun createTab(url: String = "https://example.com", activate: Boolean = true): String {
        val newId = "tab-${System.currentTimeMillis()}"
        _tabs.value = _tabs.value + BrowserTabState(id = newId, title = "New Tab", url = url)
        if (activate) _activeTabId.value = newId
        return newId
    }

    fun setActiveTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
        }
    }

    fun closeTab(tabId: String) {
        val current = _tabs.value
        if (current.size <= 1) return
        _tabs.value = current.filterNot { it.id == tabId }
        _pageSnapshots.value = _pageSnapshots.value - tabId
        if (_activeTabId.value == tabId) {
            _activeTabId.value = _tabs.value.first().id
        }
    }

    fun updateTabState(
        tabId: String,
        title: String? = null,
        url: String? = null,
        isLoading: Boolean? = null,
        canGoBack: Boolean? = null,
        canGoForward: Boolean? = null
    ) {
        _tabs.value = _tabs.value.map {
            if (it.id != tabId) it else it.copy(
                title = title ?: it.title,
                url = url ?: it.url,
                isLoading = isLoading ?: it.isLoading,
                canGoBack = canGoBack ?: it.canGoBack,
                canGoForward = canGoForward ?: it.canGoForward
            )
        }
    }

    fun updateSnapshot(snapshot: PageSnapshot) {
        _pageSnapshots.value = _pageSnapshots.value + (snapshot.tabId to snapshot)
        updateTabState(snapshot.tabId, title = snapshot.title, url = snapshot.url, isLoading = false)
    }

    fun resolveTemplate(text: String?): String {
        if (text.isNullOrBlank()) return ""
        var resolved = text.orEmpty()
        _clipboard.value.forEach { (key, value) ->
            resolved = resolved.replace("{{$key}}", value)
        }
        return resolved
    }

    fun onBrowserActionResult(action: BrowserAction, ok: Boolean, message: String, extractedText: String? = null) {
        viewModelScope.launch {
            val tabId = action.tabId ?: _activeTabId.value
            repository.addLog(
                _currentSessionId.value,
                tabId,
                action.kind,
                if (ok) message else "Failed: $message"
            )

            if (!action.saveAs.isNullOrBlank() && !extractedText.isNullOrBlank()) {
                addClipboardItem(action.saveAs, extractedText)
                addChatSuspend(ChatRole.SYSTEM, "কপি করা ভ্যালু {{${action.saveAs}}} নামে সেভ হয়েছে।")
                addChatSuspend(ChatRole.ASSISTANT, "কপি করা লেখা:\n$extractedText")
            } else if (!extractedText.isNullOrBlank()) {
                addChatSuspend(ChatRole.ASSISTANT, "পাওয়া লেখা:\n$extractedText")
            }

            val finalMessage = if (ok) {
                "ধাপ ${action.stepNumber.takeIf { it > 0 } ?: 1} সফল: ${translateResultMessage(action, message)}"
            } else {
                "ধাপ ${action.stepNumber.takeIf { it > 0 } ?: 1} ব্যর্থ: ${translateResultMessage(action, message)}"
            }

            addThinking(
                title = finalMessage,
                details = if (!extractedText.isNullOrBlank()) extractedText.take(600) else message,
                status = if (ok) "success" else "error"
            )
            addChatSuspend(ChatRole.SYSTEM, finalMessage)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearLogs(_currentSessionId.value)
            addChatSuspend(ChatRole.SYSTEM, "এই চ্যাটের action history clear করা হয়েছে।")
        }
    }

    private fun addThinking(title: String, details: String, status: String) {
        val entry = ThinkingEntry(title = title, details = details, status = status)
        _thinkingEntries.value = (listOf(entry) + _thinkingEntries.value).take(60)
    }

    private fun addChat(role: ChatRole, text: String) {
        viewModelScope.launch { addChatSuspend(role, text) }
    }

    private suspend fun addChatSuspend(role: ChatRole, text: String) {
        val sessionId = _currentSessionId.value
        val existingTitle = sessions.value.firstOrNull { it.id == sessionId }?.title.orEmpty()
        if (role == ChatRole.USER && (existingTitle.isBlank() || existingTitle.startsWith("New Chat"))) {
            repository.touchSession(sessionId, text.take(30))
        } else {
            repository.touchSession(sessionId, existingTitle.ifBlank { defaultSessionTitle() })
        }
        repository.addChatRecord(sessionId, role.name, text)
    }

    private fun buildQueuedMessage(actions: List<BrowserAction>): String {
        if (actions.isEmpty()) return "কোন action queue হয়নি।"
        return buildString {
            append("আমি ধাপে ধাপে ")
            append(actions.size)
            append("টি কাজ করব।\n")
            actions.forEachIndexed { index, action ->
                append(index + 1)
                append(". ")
                append(actionLabelBangla(action))
                append('\n')
            }
        }.trim()
    }

    private fun parseRememberCommand(prompt: String): Pair<String, String>? {
        val lowered = prompt.lowercase()
        if (!lowered.startsWith("remember ") && !lowered.startsWith("save memory ") && !lowered.startsWith("মনে রাখো ")) return null
        val content = prompt.substringAfter(' ').substringAfter(' ').trim().ifBlank { return null }
        val title = content.take(40)
        return title to content
    }

    private fun parseLocalActions(prompt: String): List<BrowserAction> {
        val segments = splitPromptIntoSteps(prompt)
        if (segments.size > 1) {
            return segments.flatMap { parseSingleAction(it) }
        }
        return parseSingleAction(prompt)
    }

    private fun splitPromptIntoSteps(prompt: String): List<String> {
        return prompt
            .replace("\n", " then ")
            .split(Regex("(?i)\\bthen\\b|তারপর|এরপর|পরে"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun parseSingleAction(prompt: String): List<BrowserAction> {
        val lowered = prompt.trim().lowercase()
        val actions = mutableListOf<BrowserAction>()

        if (lowered.startsWith("open background ") || lowered.startsWith("background tab ")) {
            val url = extractUrl(prompt)?.let(::normalizeUrl)
            actions += BrowserAction(kind = "new_tab", url = url ?: "https://example.com", background = true)
            return actions
        }

        extractUrl(prompt)?.let { url ->
            if (
                lowered.startsWith("open ") ||
                lowered.startsWith("visit ") ||
                lowered.startsWith("go to ") ||
                lowered.startsWith("ওপেন ") ||
                lowered == url.lowercase()
            ) {
                actions += BrowserAction(kind = "open_url", url = normalizeUrl(url))
                return actions
            }
        }

        if (lowered.startsWith("search ") || lowered.startsWith("খুঁজো ")) {
            val query = prompt.substringAfter(' ').trim()
            if (query.isNotBlank()) {
                actions += BrowserAction(
                    kind = "open_url",
                    url = "https://www.google.com/search?q=" + query.replace(" ", "+")
                )
                return actions
            }
        }

        if (lowered == "back" || lowered.contains("go back") || lowered.contains("পেছনে")) {
            actions += BrowserAction(kind = "back")
        }
        if (lowered == "forward" || lowered.contains("go forward") || lowered.contains("সামনে যাও")) {
            actions += BrowserAction(kind = "forward")
        }
        if (lowered == "refresh" || lowered.contains("reload") || lowered.contains("রিফ্রেশ")) {
            actions += BrowserAction(kind = "refresh")
        }
        if (lowered.startsWith("new tab") || lowered.startsWith("নতুন ট্যাব")) {
            val url = extractUrl(prompt)?.let(::normalizeUrl)
            actions += BrowserAction(kind = "new_tab", url = url)
        }
        if (lowered.startsWith("close tab") || lowered.startsWith("ট্যাব বন্ধ")) {
            actions += BrowserAction(kind = "close_tab")
        }
        if (lowered.startsWith("switch tab ") || lowered.startsWith("ট্যাব বদলাও ")) {
            val raw = lowered.substringAfterLast(' ').trim().toIntOrNull()
            val target = raw?.minus(1)?.let { i -> _tabs.value.getOrNull(i)?.id }
            if (target != null) actions += BrowserAction(kind = "switch_tab", tabId = target)
        }
        if (lowered.startsWith("extract page") || lowered.startsWith("copy page") || lowered.startsWith("পেইজ কপি")) {
            val alias = prompt.substringAfter(" as ", "page_text").trim().ifBlank { "page_text" }
            actions += BrowserAction(kind = "extract_page_text", saveAs = alias)
        } else if (lowered.startsWith("extract ") || lowered.startsWith("copy ")) {
            val selector = prompt.substringAfter(' ').substringBefore(" as ").trim()
            val alias = prompt.substringAfter(" as ", "").trim().ifBlank { null }
            if (selector.isNotBlank()) actions += BrowserAction(kind = "extract_text", selector = selector, saveAs = alias)
        }
        if (lowered.startsWith("click ") || lowered.startsWith("ক্লিক ")) {
            val selector = prompt.substringAfter(' ').trim()
            if (selector.isNotBlank()) actions += BrowserAction(kind = "click", selector = selector)
        }
        if (lowered.startsWith("type ") || lowered.startsWith("লিখো ")) {
            val body = prompt.substringAfter(' ')
            val pieces = body.split(" into ", limit = 2)
            if (pieces.size == 2) {
                actions += BrowserAction(kind = "type", text = pieces[0].trim(), selector = pieces[1].trim())
            }
        }
        if (lowered.startsWith("scroll down") || lowered.contains("নিচে স্ক্রল")) {
            actions += BrowserAction(kind = "scroll", text = "down")
        }
        if (lowered.startsWith("scroll up") || lowered.contains("উপরে স্ক্রল")) {
            actions += BrowserAction(kind = "scroll", text = "up")
        }
        if (lowered.startsWith("scroll top")) {
            actions += BrowserAction(kind = "scroll", text = "top")
        }
        if (lowered.startsWith("scroll bottom")) {
            actions += BrowserAction(kind = "scroll", text = "bottom")
        }
        if (lowered.startsWith("wait ") || lowered.startsWith("অপেক্ষা ")) {
            val seconds = lowered.substringAfter(' ').substringBefore(' ').trim().toLongOrNull()
            if (seconds != null) actions += BrowserAction(kind = "wait", waitMs = seconds * 1000L)
        }

        return actions
    }

    private fun looksUnsafe(prompt: String): Boolean {
        val text = prompt.lowercase()
        val blockedHints = listOf(
            "temp-mail",
            "temp mail",
            "verification code",
            "otp",
            "sign up",
            "signup",
            "create account",
            "bypass",
            "up.cts6.com",
            "obtain code",
            "email login"
        )
        return blockedHints.any { it in text }
    }

    private fun normalizeUrl(url: String): String {
        return if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    }

    private fun extractUrl(text: String): String? {
        val regex = Regex("""((https?://)?[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}[^\s]*)""")
        return regex.find(text)?.groupValues?.get(1)
    }

    private fun buildConfigApiKey(): String = try {
        BuildConfig.MISTRAL_API_KEY.orEmpty().trim()
    } catch (_: Throwable) {
        ""
    }

    private fun buildConfigModel(): String = try {
        BuildConfig.MISTRAL_MODEL.orEmpty().trim()
    } catch (_: Throwable) {
        ""
    }

    private fun effectiveApiKey(): String {
        return _aiApiKey.value.trim().takeUnless { it.isBlank() || it.equals("CHANGE_ME", ignoreCase = true) }
            ?: buildConfigApiKey().takeUnless { it.isBlank() || it.equals("CHANGE_ME", ignoreCase = true) }
            ?: ""
    }

    private fun effectiveModel(): String {
        return _aiModel.value.trim().ifBlank { buildConfigModel().ifBlank { "mistral-small-latest" } }
    }

    private fun effectiveBaseUrl(): String {
        return normalizeBaseUrl(_aiBaseUrl.value)
    }

    private fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.isBlank() -> "https://api.mistral.ai/v1/chat/completions"
            trimmed.endsWith("/v1/chat/completions") -> trimmed
            trimmed.endsWith("/") -> trimmed + "v1/chat/completions"
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/v1/chat/completions"
        }
    }

    private fun performAiHealthCheck(): String {
        val apiKey = effectiveApiKey()
        if (apiKey.isBlank()) return "AI health check failed: missing API key."
        return try {
            val requestJson = JSONObject().apply {
                put("model", effectiveModel())
                put("temperature", 0.0)
                put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", "Return exactly: OK"))
                        .put(JSONObject().put("role", "user").put("content", "Ping"))
                )
                put("max_tokens", 8)
            }
            val request = Request.Builder()
                .url(effectiveBaseUrl())
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return "AI health check failed: HTTP ${response.code} ${extractApiError(body)}"
                }
                val content = extractAssistantContent(body)
                if (content.isBlank()) "AI health check failed: empty response." else "AI connection successful: ${content.take(80)}"
            }
        } catch (t: Throwable) {
            "AI health check failed: ${t.message ?: "unknown error"}"
        }
    }

    private fun planWithMistral(prompt: String, apiKey: String): PlannerResult {
        return try {
            val activeTab = _tabs.value.firstOrNull { it.id == _activeTabId.value }
            val snapshot = _pageSnapshots.value[_activeTabId.value]
            val recentMemories = memories.value.take(6)
            val recentLogs = logs.value.take(12)

            val systemPrompt = """
                You are a browser action planner for an Android app with multi-tab WebView support.
                Return JSON only.
                Never plan account creation, temporary email usage, OTP or verification harvesting, bypassing restrictions, or abuse of third-party services.
                If the request is unsafe or unclear, return:
                {"status":"refuse","message":"short explanation","actions":[]}

                Supported actions:
                - open_url {"kind":"open_url","url":"https://example.com","tabId":"tab-1"}
                - new_tab {"kind":"new_tab","url":"https://example.com","background":true}
                - switch_tab {"kind":"switch_tab","tabId":"tab-1"}
                - click {"kind":"click","selector":"button.primary","tabId":"tab-1"}
                - type {"kind":"type","selector":"input[name='q']","text":"hello","tabId":"tab-1"}
                - extract_text {"kind":"extract_text","selector":"h1","saveAs":"headline","tabId":"tab-1"}
                - extract_page_text {"kind":"extract_page_text","saveAs":"page_text","tabId":"tab-1"}
                - scroll {"kind":"scroll","text":"down","tabId":"tab-1"}
                - back / forward / refresh / close_tab / wait

                Use CSS selectors only.
                Use {{alias}} placeholders inside type.text if an earlier extract_text saved a value.
                Prefer short plans, and use wait when a page needs time before interaction.
                Keep plans concrete, safe, and resilient.
                When the user asks for multiple steps, return them in the right order.

                Response shape:
                {
                  "status":"ok",
                  "message":"brief summary in Bengali or simple English",
                  "actions":[...],
                  "memories":[{"title":"optional","content":"optional"}]
                }
            """.trimIndent()

            val userContext = buildString {
                appendLine("User request:")
                appendLine(prompt)
                appendLine()
                appendLine("Current active tab:")
                appendLine(activeTab?.let { "${it.id} | ${it.title} | ${it.url}" } ?: "none")
                appendLine()
                appendLine("Current page snapshot:")
                appendLine(snapshot?.let { formatSnapshot(it) } ?: "No snapshot yet")
                appendLine()
                appendLine("Available tabs:")
                _tabs.value.forEach { appendLine("- ${it.id}: ${it.title} | ${it.url}") }
                appendLine()
                appendLine("Recent memories:")
                recentMemories.forEach { appendLine("- ${it.title}: ${it.content}") }
                appendLine()
                appendLine("Recent action log:")
                recentLogs.forEach { appendLine("- [${it.tabId}] ${it.actionType}: ${it.summary}") }
            }

            val requestJson = JSONObject().apply {
                put("model", effectiveModel())
                put("temperature", 0.1)
                put("response_format", JSONObject().put("type", "json_object"))
                put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(JSONObject().put("role", "user").put("content", userContext))
                )
            }

            val request = Request.Builder()
                .url(effectiveBaseUrl())
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return PlannerResult(
                        status = "error",
                        message = "AI response error: HTTP ${response.code} ${extractApiError(body)}",
                        actions = emptyList()
                    )
                }

                val content = extractAssistantContent(body)
                if (content.isBlank()) {
                    return PlannerResult(
                        status = "error",
                        message = "AI response error: empty assistant content.",
                        actions = emptyList()
                    )
                }
                parsePlannerJson(content)
            }
        } catch (t: Throwable) {
            PlannerResult(
                status = "error",
                message = "Planner failed: ${t.message ?: "unknown error"}",
                actions = emptyList()
            )
        }
    }

    private fun parsePlannerJson(content: String): PlannerResult {
        return try {
            val cleaned = extractJsonObject(content)
            val json = JSONObject(cleaned)
            val actionsArray = json.optJSONArray("actions") ?: JSONArray()
            val actions = buildList {
                for (i in 0 until actionsArray.length()) {
                    val item = actionsArray.optJSONObject(i) ?: continue
                    add(
                        BrowserAction(
                            kind = item.optString("kind"),
                            tabId = item.optString("tabId").ifBlank { null },
                            url = item.optString("url").ifBlank { null },
                            selector = item.optString("selector").ifBlank { null },
                            text = item.optString("text").ifBlank { null },
                            waitMs = item.optLong("waitMs").takeIf { it > 0 },
                            background = item.optBoolean("background", false),
                            saveAs = item.optString("saveAs").ifBlank { null }
                        )
                    )
                }
            }

            val memoriesArray = json.optJSONArray("memories") ?: JSONArray()
            val memories = buildList {
                for (i in 0 until memoriesArray.length()) {
                    val item = memoriesArray.optJSONObject(i) ?: continue
                    val title = item.optString("title")
                    val body = item.optString("content")
                    if (title.isNotBlank() && body.isNotBlank()) add(title to body)
                }
            }

            PlannerResult(
                status = json.optString("status", "ok"),
                message = json.optString("message", "Plan ready."),
                actions = actions,
                memories = memories
            )
        } catch (t: Throwable) {
            PlannerResult(
                status = "error",
                message = "AI response parse error: ${t.message ?: "invalid JSON"}",
                actions = emptyList()
            )
        }
    }

    private fun extractAssistantContent(body: String): String {
        val root = JSONObject(body)
        val message = root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?: return ""
        val rawContent = message.opt("content")
        return when (rawContent) {
            is String -> rawContent
            is JSONArray -> buildString {
                for (i in 0 until rawContent.length()) {
                    val item = rawContent.opt(i)
                    when (item) {
                        is JSONObject -> append(item.optString("text", item.toString()))
                        is String -> append(item)
                    }
                }
            }
            else -> rawContent?.toString().orEmpty()
        }
    }

    private fun extractJsonObject(content: String): String {
        val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) return cleaned
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start >= 0 && end > start) return cleaned.substring(start, end + 1)
        return cleaned
    }

    private fun extractApiError(body: String): String {
        return runCatching {
            val root = JSONObject(body)
            root.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: root.optString("message").takeIf { it.isNotBlank() }
                ?: body.take(140)
        }.getOrDefault(body.take(140))
    }

    private fun formatSnapshot(snapshot: PageSnapshot): String {
        val nodes = snapshot.interactive.take(20).joinToString("\n") {
            "- ${it.type}: ${it.label} => ${it.selector}"
        }
        return """
            Title: ${snapshot.title}
            Url: ${snapshot.url}
            Excerpt: ${snapshot.excerpt}
            Interactive elements:
            $nodes
        """.trimIndent()
    }

    private fun buildStepMessage(action: BrowserAction): String {
        val prefix = if (action.totalSteps > 0) "ধাপ ${action.stepNumber}/${action.totalSteps}" else "ধাপ"
        return "$prefix: ${actionLabelBangla(action)}"
    }

    private fun actionLabelBangla(action: BrowserAction): String {
        return when (action.kind) {
            "open_url" -> "ওয়েবসাইট খুলছি ${action.url.orEmpty()}"
            "new_tab" -> if (action.background) "background tab খুলছি ${action.url.orEmpty()}" else "নতুন tab খুলছি ${action.url.orEmpty()}"
            "switch_tab" -> "অন্য tab-এ যাচ্ছি"
            "close_tab" -> "বর্তমান tab বন্ধ করছি"
            "back" -> "পেছনের পেইজে ফিরছি"
            "forward" -> "পরের পেইজে যাচ্ছি"
            "refresh" -> "পেইজ refresh করছি"
            "click" -> "selector click করছি: ${action.selector.orEmpty()}"
            "type" -> "লেখা বসাচ্ছি: ${action.selector.orEmpty()}"
            "extract_text" -> "নির্দিষ্ট লেখা কপি করছি: ${action.selector.orEmpty()}"
            "extract_page_text" -> "পেইজের লেখা কপি করছি"
            "scroll" -> "স্ক্রল করছি ${action.text.orEmpty()}"
            "wait" -> "অপেক্ষা করছি ${(action.waitMs ?: 0L) / 1000} সেকেন্ড"
            else -> action.kind
        }
    }

    private fun actionDebug(action: BrowserAction): String {
        return when (action.kind) {
            "click" -> "document.querySelector('${action.selector.orEmpty()}')?.click()"
            "type" -> "document.querySelector('${action.selector.orEmpty()}').value = '${action.text.orEmpty()}'"
            "extract_text" -> "document.querySelector('${action.selector.orEmpty()}').innerText"
            "extract_page_text" -> "document.body.innerText"
            "scroll" -> "window.scrollBy(...) // ${action.text.orEmpty()}"
            "open_url" -> "webView.loadUrl('${action.url.orEmpty()}')"
            else -> action.kind
        }
    }

    private fun translateResultMessage(action: BrowserAction, message: String): String {
        return when (action.kind) {
            "extract_text", "extract_page_text" -> "লেখা সংগ্রহ করা হয়েছে।"
            "click" -> "ক্লিক সম্পন্ন হয়েছে।"
            "type" -> "লেখা বসানো হয়েছে।"
            "scroll" -> "স্ক্রল সম্পন্ন হয়েছে।"
            "open_url" -> "ওয়েবসাইট লোড করা হয়েছে।"
            else -> message
        }
    }
}
