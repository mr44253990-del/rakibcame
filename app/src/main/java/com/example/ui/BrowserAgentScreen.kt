package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.ActionLog
import com.example.data.AgentMemory
import com.example.data.ChatSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserAgentScreen(viewModel: BrowserAgentViewModel) {
    val context = LocalContext.current
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val snapshots by viewModel.pageSnapshots.collectAsState()
    val chat by viewModel.chatMessages.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val plan by viewModel.planPreview.collectAsState()
    val isWorking by viewModel.isWorking.collectAsState()
    val thinkingEntries by viewModel.thinkingEntries.collectAsState()
    val aiApiKey by viewModel.aiApiKey.collectAsState()
    val aiModel by viewModel.aiModel.collectAsState()
    val aiBaseUrl by viewModel.aiBaseUrl.collectAsState()
    val aiStatus by viewModel.aiStatus.collectAsState()
    val autoPreviewEnabled by viewModel.autoPreviewEnabled.collectAsState()
    val retryCount by viewModel.retryCount.collectAsState()

    var input by rememberSaveable { mutableStateOf("") }
    var sidePanelIndex by rememberSaveable { mutableStateOf(0) }
    var settingsApiKey by rememberSaveable(aiApiKey) { mutableStateOf(aiApiKey) }
    var settingsModel by rememberSaveable(aiModel) { mutableStateOf(aiModel) }
    var settingsBaseUrl by rememberSaveable(aiBaseUrl) { mutableStateOf(aiBaseUrl) }

    val runtime = remember {
        BrowserRuntime(
            context = context,
            onStateChanged = { tabId, title, url, isLoading, canGoBack, canGoForward ->
                viewModel.updateTabState(tabId, title, url, isLoading, canGoBack, canGoForward)
            },
            onSnapshot = viewModel::updateSnapshot
        )
    }

    DisposableEffect(runtime) {
        onDispose { runtime.destroyAll() }
    }

    LaunchedEffect(tabs) {
        runtime.syncTabs(tabs)
    }

    LaunchedEffect(Unit) {
        viewModel.browserCommands.collect { action ->
            viewModel.onBrowserActionStarted(action)
            val result = runtime.execute(action, viewModel)
            viewModel.onBrowserActionResult(action, result.ok, result.message, result.extractedText)
        }
    }

    LaunchedEffect(activeTabId, tabs.size, autoPreviewEnabled) {
        if (!autoPreviewEnabled) return@LaunchedEffect
        while (true) {
            delay(2500)
            runtime.refreshSnapshot(activeTabId)
        }
    }

    val activeSnapshot = snapshots[activeTabId]
    val panelTabs = listOf("Chat", "Chats", "Memory", "History", "Plan", "Settings", "Tools")

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0F14))) {
        TopAppBar(
            title = {
                Column {
                    Text("Arena Browser Agent", color = Color.White)
                    Text(
                        aiStatus,
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            actions = {
                if (isWorking) {
                    Text("Working…", color = Color(0xFF38BDF8), modifier = Modifier.padding(end = 12.dp))
                }
                IconButton(onClick = { sidePanelIndex = 5 }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
                IconButton(onClick = {
                    val newId = viewModel.createTab("https://example.com")
                    runtime.ensureTab(newId, "https://example.com")
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add tab", tint = Color.White)
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                Surface(
                    modifier = Modifier.widthIn(min = 160.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = if (tab.id == activeTabId) Color(0xFF1D4ED8) else Color(0xFF111827),
                    tonalElevation = if (tab.id == activeTabId) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { viewModel.setActiveTab(tab.id) }
                            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "${index + 1}. ${tab.title}",
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = tab.url,
                                color = Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (tabs.size > 1) {
                            IconButton(onClick = {
                                runtime.destroyTab(tab.id)
                                viewModel.closeTab(tab.id)
                            }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close tab", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Card(
                modifier = Modifier.width(360.dp).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
            ) {
                ScrollableTabRow(selectedTabIndex = sidePanelIndex, edgePadding = 8.dp) {
                    panelTabs.forEachIndexed { index, title ->
                        Tab(
                            selected = sidePanelIndex == index,
                            onClick = { sidePanelIndex = index },
                            text = { Text(title) },
                            icon = {
                                when (title) {
                                    "Chat" -> Icon(Icons.Default.SmartToy, contentDescription = null)
                                    "Chats" -> Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                                    "Memory" -> Icon(Icons.Default.Memory, contentDescription = null)
                                    "History" -> Icon(Icons.Default.History, contentDescription = null)
                                    "Plan" -> Icon(Icons.Default.Psychology, contentDescription = null)
                                    "Settings" -> Icon(Icons.Default.Settings, contentDescription = null)
                                    else -> Icon(Icons.Default.Tune, contentDescription = null)
                                }
                            }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).padding(12.dp)) {
                    when (sidePanelIndex) {
                        0 -> ChatPanel(chatMessages = chat)
                        1 -> SessionsPanel(
                            sessions = sessions,
                            currentSessionId = currentSessionId,
                            onNewSession = viewModel::createNewSession,
                            onSwitchSession = viewModel::switchSession
                        )
                        2 -> MemoryPanel(memories = memories, clipboard = clipboard)
                        3 -> HistoryPanel(logs = logs, onClear = viewModel::clearHistory)
                        4 -> PlanPanel(plan = plan)
                        5 -> SettingsPanel(
                            apiKey = settingsApiKey,
                            model = settingsModel,
                            baseUrl = settingsBaseUrl,
                            maskedApiKey = viewModel.maskedApiKey(),
                            aiStatus = aiStatus,
                            autoPreviewEnabled = autoPreviewEnabled,
                            retryCount = retryCount,
                            onApiKeyChange = { settingsApiKey = it },
                            onModelChange = { settingsModel = it },
                            onBaseUrlChange = { settingsBaseUrl = it },
                            onSave = { viewModel.saveAiConfig(settingsApiKey, settingsModel, settingsBaseUrl) },
                            onTest = viewModel::testAiConfig,
                            onAutoPreviewChange = viewModel::setAutoPreview,
                            onRetryCountChange = viewModel::setRetryCount
                        )
                        else -> ToolsPanel(
                            onTool = viewModel::submitQuickTool,
                            onPrompt = { input = it; sidePanelIndex = 0 },
                            clipboard = clipboard
                        )
                    }
                }

                Divider(color = Color(0xFF1F2937))

                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Type a safe browser instruction") },
                        placeholder = { Text("Open https://example.com or click button.primary") },
                        maxLines = 4
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = {
                                input = ""
                            }
                        ) { Text("Clear") }
                        IconButton(onClick = {
                            val prompt = input
                            input = ""
                            viewModel.submitPrompt(prompt)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF020617))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    BrowserStatusHeader(
                        snapshot = activeSnapshot,
                        tab = tabs.firstOrNull { it.id == activeTabId },
                        clipboard = clipboard
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx -> FrameLayout(ctx) },
                            update = { container ->
                                runtime.attach(container, activeTabId)
                            }
                        )
                    }

                    Divider(color = Color(0xFF1E293B), modifier = Modifier.padding(top = 12.dp))

                    PreviewPanel(snapshot = activeSnapshot, thinkingEntries = thinkingEntries)
                }
            }
        }
    }
}

@Composable
private fun ChatPanel(chatMessages: List<ChatMessage>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(chatMessages) { message ->
            val bg = when (message.role) {
                ChatRole.USER -> Color(0xFF1D4ED8)
                ChatRole.ASSISTANT -> Color(0xFF0F172A)
                ChatRole.SYSTEM -> Color(0xFF14532D)
            }
            Card(colors = CardDefaults.cardColors(containerColor = bg)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = message.role.name,
                        color = Color(0xFFCBD5E1),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer {
                        Text(text = message.text, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionsPanel(
    sessions: List<ChatSession>,
    currentSessionId: String,
    onNewSession: () -> Unit,
    onSwitchSession: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Chat sessions", color = Color.White, fontWeight = FontWeight.Bold)
            Button(onClick = onNewSession) { Text("New chat") }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(sessions) { session ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (session.id == currentSessionId) Color(0xFF1D4ED8) else Color(0xFF0F172A)
                    ),
                    modifier = Modifier.fillMaxWidth().clickable { onSwitchSession(session.id) }
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(session.title, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(session.id, color = Color(0xFFCBD5E1), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryPanel(memories: List<AgentMemory>, clipboard: Map<String, String>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("Clipboard aliases", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (clipboard.isEmpty()) {
                Text("No copied values yet.", color = Color(0xFF94A3B8))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    clipboard.forEach { (key, value) ->
                        AssistChip(
                            onClick = {},
                            label = { Text("{{$key}} = ${value.take(40)}") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                        )
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text("Saved memories", color = Color.White, fontWeight = FontWeight.Bold)
        }
        if (memories.isEmpty()) {
            item { Text("No memory saved yet. Use: remember your note here", color = Color(0xFF94A3B8)) }
        } else {
            items(memories) { memory ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(memory.title, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(memory.content, color = Color(0xFFCBD5E1))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryPanel(logs: List<ActionLog>, onClear: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Action history", color = Color.White, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClear) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear history", tint = Color.White)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            if (logs.isEmpty()) {
                item { Text("No action history yet.", color = Color(0xFF94A3B8)) }
            } else {
                items(logs) { log ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("[${log.tabId}] ${log.actionType}", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(log.summary, color = Color(0xFFCBD5E1))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanPanel(plan: PlanPreview?) {
    if (plan == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No plan yet.", color = Color(0xFF94A3B8))
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("Planner summary", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(plan.summary, color = Color(0xFFCBD5E1))
            Spacer(Modifier.height(12.dp))
            Text("Queued actions", color = Color.White, fontWeight = FontWeight.Bold)
        }
        if (plan.actions.isEmpty()) {
            item { Text("No action was queued.", color = Color(0xFF94A3B8)) }
        } else {
            items(plan.actions) { action ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(action.kind, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(action.url, action.selector, action.text, action.secondaryText?.let { "••••" }, action.saveAs).joinToString(" | ").ifBlank { "tab=${action.tabId}" },
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    apiKey: String,
    model: String,
    baseUrl: String,
    maskedApiKey: String,
    aiStatus: String,
    autoPreviewEnabled: Boolean,
    retryCount: Int,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onAutoPreviewChange: (Boolean) -> Unit,
    onRetryCountChange: (Int) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("AI configuration", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Saved key: $maskedApiKey", color = Color(0xFF94A3B8))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                placeholder = { Text("Paste your Mistral API key") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                placeholder = { Text("mistral-small-latest") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                placeholder = { Text("https://api.mistral.ai") },
                maxLines = 2
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) { Text("Save") }
                Button(onClick = onTest) { Text("Test") }
            }
            Spacer(Modifier.height(8.dp))
            Text(aiStatus, color = Color(0xFF38BDF8))
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Runtime options", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto preview refresh", color = Color.White)
                    Text("Continuously update DOM preview while browsing", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = autoPreviewEnabled, onCheckedChange = onAutoPreviewChange)
            }
            Spacer(Modifier.height(10.dp))
            Text("Retry count", color = Color.White)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { count ->
                    FilterChip(
                        selected = retryCount == count,
                        onClick = { onRetryCountChange(count) },
                        label = { Text(count.toString()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolsPanel(
    onTool: (String) -> Unit,
    onPrompt: (String) -> Unit,
    clipboard: Map<String, String>
) {
    val quickPrompts = listOf(
        "open https://example.com",
        "search android webview compose",
        "new tab https://developer.android.com",
        "extract h1 as headline",
        "copy page as page_text",
        "scroll down then copy page as page_text",
        "login your@email.com password your-password"
    )

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("Quick tools", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onTool("new_tab") }) { Text("New tab") }
                Button(onClick = { onTool("background_tab") }) { Text("BG tab") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onTool("refresh") }) { Text("Refresh") }
                Button(onClick = { onTool("google") }) { Text("Google") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onTool("docs") }) { Text("Mistral docs") }
                Button(onClick = { onTool("clear_history") }) { Text("Clear history") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onTool("clear_clipboard") }) { Text("Clear clipboard") }
                Button(onClick = { onTool("new_chat") }) { Text("New chat") }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Prompt presets", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quickPrompts.forEach { prompt ->
                    AssistChip(onClick = { onPrompt(prompt) }, label = { Text(prompt) })
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Clipboard aliases", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (clipboard.isEmpty()) {
                Text("No aliases saved.", color = Color(0xFF94A3B8))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    clipboard.forEach { (key, value) ->
                        AssistChip(onClick = {}, label = { Text("{{$key}} = ${value.take(28)}") })
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserStatusHeader(
    snapshot: PageSnapshot?,
    tab: BrowserTabState?,
    clipboard: Map<String, String>
) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color.White)
            Text(tab?.title ?: "Browser", color = Color.White, fontWeight = FontWeight.Bold)
            if (tab?.isLoading == true) {
                Text("Loading…", color = Color(0xFF38BDF8))
            }
        }
        Text(snapshot?.url ?: tab?.url ?: "No page loaded", color = Color(0xFF94A3B8))
        Text(
            "Back: ${tab?.canGoBack == true}  •  Forward: ${tab?.canGoForward == true}  •  Live preview: ${snapshot != null}",
            color = Color(0xFF64748B),
            style = MaterialTheme.typography.labelSmall
        )
        if (clipboard.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                clipboard.keys.take(4).forEach { alias ->
                    AssistChip(
                        onClick = {},
                        label = { Text("{{$alias}}") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewPanel(snapshot: PageSnapshot?, thinkingEntries: List<ThinkingEntry>) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
            Text("Preview / readable DOM summary", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        if (snapshot == null) {
            Text("No preview yet. Open a page first.", color = Color(0xFF94A3B8))
        } else {
            Text(snapshot.title, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Updated: ${formatTimestamp(snapshot.updatedAt)}  •  Elements: ${snapshot.interactive.size}",
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(snapshot.excerpt.ifBlank { "No visible text found." }, color = Color(0xFFCBD5E1), maxLines = 6, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Text("Interactive elements", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().height(140.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(snapshot.interactive.take(12)) { node ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(node.label.ifBlank { node.type }, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(node.selector, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Thinking / execution trace", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().height(150.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(thinkingEntries.take(8)) { item ->
                val accent = when (item.status) {
                    "success" -> Color(0xFF16A34A)
                    "error" -> Color(0xFFDC2626)
                    "running" -> Color(0xFF38BDF8)
                    else -> Color(0xFF64748B)
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1220))) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                        Text(item.title, color = accent, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(item.details, color = Color(0xFFCBD5E1), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private data class RuntimeResult(
    val ok: Boolean,
    val message: String,
    val extractedText: String? = null
)

private fun formatTimestamp(value: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(value))
}

private class BrowserRuntime(
    context: Context,
    private val onStateChanged: (String, String?, String?, Boolean?, Boolean?, Boolean?) -> Unit,
    private val onSnapshot: (PageSnapshot) -> Unit
) {
    private val appContext = context.applicationContext
    private val webViews = linkedMapOf<String, WebView>()

    fun syncTabs(tabs: List<BrowserTabState>) {
        tabs.forEach { ensureTab(it.id, it.url) }
        val validIds = tabs.map { it.id }.toSet()
        val toRemove = webViews.keys.filterNot { it in validIds }
        toRemove.forEach(::destroyTab)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun ensureTab(tabId: String, initialUrl: String = "https://example.com"): WebView {
        webViews[tabId]?.let { return it }

        val webView = WebView(appContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    onStateChanged(tabId, title, view?.url, view?.progress != 100, view?.canGoBack(), view?.canGoForward())
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    onStateChanged(tabId, view?.title, url, true, view?.canGoBack(), view?.canGoForward())
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    onStateChanged(tabId, view?.title, url, false, view?.canGoBack(), view?.canGoForward())
                    captureSnapshot(tabId)
                }
            }
            loadUrl(initialUrl)
        }

        webViews[tabId] = webView
        return webView
    }

    fun attach(container: FrameLayout, tabId: String) {
        val webView = ensureTab(tabId)
        if (webView.parent != container) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            container.removeAllViews()
            container.addView(webView)
        }
    }

    fun destroyTab(tabId: String) {
        val webView = webViews.remove(tabId) ?: return
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
    }

    fun destroyAll() {
        webViews.keys.toList().forEach(::destroyTab)
    }

    fun refreshSnapshot(tabId: String) {
        captureSnapshot(tabId)
    }

    suspend fun execute(action: BrowserAction, viewModel: BrowserAgentViewModel): RuntimeResult {
        return when (action.kind) {
            "new_tab" -> {
                val newId = viewModel.createTab(action.url ?: "https://example.com", activate = !action.background)
                val webView = ensureTab(newId, action.url ?: "https://example.com")
                waitForPageStable(webView)
                refreshSnapshot(newId)
                RuntimeResult(true, "Opened new tab $newId")
            }
            "switch_tab" -> {
                val tabId = action.tabId ?: return RuntimeResult(false, "No tab id provided.")
                val webView = ensureTab(tabId)
                viewModel.setActiveTab(tabId)
                waitForPageStable(webView)
                refreshSnapshot(tabId)
                RuntimeResult(true, "Switched to $tabId")
            }
            "close_tab" -> {
                val tabId = action.tabId ?: return RuntimeResult(false, "No tab id provided.")
                if (viewModel.tabs.value.size <= 1) {
                    RuntimeResult(false, "At least one tab must stay open.")
                } else {
                    destroyTab(tabId)
                    viewModel.closeTab(tabId)
                    RuntimeResult(true, "Closed $tabId")
                }
            }
            "open_url" -> {
                val tabId = action.tabId ?: return RuntimeResult(false, "No tab selected.")
                val webView = ensureTab(tabId)
                val url = action.url ?: return RuntimeResult(false, "No URL provided.")
                webView.post { webView.loadUrl(url) }
                waitForPageStable(webView)
                refreshSnapshot(tabId)
                RuntimeResult(true, "Opening $url")
            }
            "back" -> navigate(action.tabId, "Went back") { if (canGoBack()) { goBack(); true } else false }
            "forward" -> navigate(action.tabId, "Went forward") { if (canGoForward()) { goForward(); true } else false }
            "refresh" -> navigate(action.tabId, "Page refreshed") { reload(); true }
            "wait" -> {
                delay(action.waitMs ?: 1000L)
                RuntimeResult(true, "Waited ${action.waitMs ?: 1000L}ms")
            }
            "click" -> {
                val webView = resolveTab(action.tabId)
                val selector = action.selector ?: return RuntimeResult(false, "No selector provided.")
                val payload = selectorActionWithRetry(webView, attempts = viewModel.retryCount.value) { jsCommand(webView, clickScript(selector)) }
                val ok = payload.optBoolean("ok", false)
                if (ok) waitForPageStable(webView)
                refreshSnapshot(action.tabId ?: webViews.keys.first())
                RuntimeResult(ok, payload.optString("message", if (ok) "Clicked." else "Click failed."))
            }
            "type" -> {
                val webView = resolveTab(action.tabId)
                val selector = action.selector ?: return RuntimeResult(false, "No selector provided.")
                val text = viewModel.resolveTemplate(action.text)
                val payload = selectorActionWithRetry(webView, attempts = viewModel.retryCount.value) { jsCommand(webView, typeScript(selector, text)) }
                val ok = payload.optBoolean("ok", false)
                waitForDomCalm(webView)
                refreshSnapshot(action.tabId ?: webViews.keys.first())
                RuntimeResult(ok, payload.optString("message", if (ok) "Typed text." else "Typing failed."))
            }
            "extract_text" -> {
                val webView = resolveTab(action.tabId)
                val selector = action.selector ?: return RuntimeResult(false, "No selector provided.")
                val payload = selectorActionWithRetry(webView, attempts = viewModel.retryCount.value) { jsCommand(webView, extractScript(selector)) }
                val ok = payload.optBoolean("ok", false)
                refreshSnapshot(action.tabId ?: webViews.keys.first())
                RuntimeResult(ok, payload.optString("message", "Extracted text."), payload.optString("text"))
            }
            "extract_page_text" -> {
                val webView = resolveTab(action.tabId)
                val payload = selectorActionWithRetry(webView, attempts = viewModel.retryCount.value) { jsCommand(webView, extractPageScript()) }
                val ok = payload.optBoolean("ok", false)
                refreshSnapshot(action.tabId ?: webViews.keys.first())
                RuntimeResult(ok, payload.optString("message", "Extracted page text."), payload.optString("text"))
            }
            "smart_login" -> {
                val webView = resolveTab(action.tabId)
                val username = viewModel.resolveTemplate(action.text)
                val password = viewModel.resolveTemplate(action.secondaryText)
                val payload = selectorActionWithRetry(webView, attempts = viewModel.retryCount.value) { jsCommand(webView, smartLoginScript(username, password)) }
                val ok = payload.optBoolean("ok", false)
                if (ok) waitForPageStable(webView, timeoutMs = 20000L)
                refreshSnapshot(action.tabId ?: webViews.keys.first())
                RuntimeResult(ok, payload.optString("message", "Login attempt finished."))
            }
            "scroll" -> {
                val webView = resolveTab(action.tabId)
                val payload = jsCommand(webView, scrollScript(action.text.orEmpty()))
                val ok = payload.optBoolean("ok", false)
                refreshSnapshot(action.tabId ?: webViews.keys.first())
                RuntimeResult(ok, payload.optString("message", "Scrolled."))
            }
            else -> RuntimeResult(false, "Unsupported action: ${action.kind}")
        }
    }

    private suspend fun selectorActionWithRetry(
        webView: WebView,
        attempts: Int = 3,
        block: suspend () -> JSONObject
    ): JSONObject {
        var last = JSONObject("{\"ok\":false,\"message\":\"Action failed.\"}")
        repeat(attempts) { index ->
            last = block()
            if (last.optBoolean("ok", false)) return last
            delay((index + 1) * 500L)
        }
        return last
    }

    private suspend fun navigate(tabId: String?, successMessage: String, action: WebView.() -> Boolean): RuntimeResult {
        val webView = resolveTab(tabId)
        val ok = suspendCancellableCoroutine<Boolean> { continuation ->
            webView.post { continuation.resume(webView.action()) }
        }
        if (ok) waitForPageStable(webView)
        refreshSnapshot(tabId ?: webViews.keys.first())
        return if (ok) RuntimeResult(true, successMessage) else RuntimeResult(false, "Navigation not available.")
    }

    private suspend fun waitForDomCalm(webView: WebView) {
        delay(300)
        repeat(5) {
            val payload = jsCommand(webView, readyStateScript())
            if (payload.optBoolean("ok", false) && payload.optBoolean("ready", false)) {
                delay(200)
                return
            }
            delay(250)
        }
    }

    private suspend fun waitForPageStable(webView: WebView, timeoutMs: Long = 15000L) {
        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < timeoutMs) {
            val progressReady = suspendCancellableCoroutine<Boolean> { continuation ->
                webView.post { continuation.resume(webView.progress >= 95) }
            }
            val payload = jsCommand(webView, readyStateScript())
            if (progressReady && payload.optBoolean("ok", false) && payload.optBoolean("ready", false)) {
                delay(400)
                return
            }
            delay(350)
        }
    }

    private fun resolveTab(tabId: String?): WebView {
        val targetId = tabId ?: webViews.keys.first()
        return ensureTab(targetId)
    }

    private fun captureSnapshot(tabId: String) {
        val webView = webViews[tabId] ?: return
        webView.post {
            webView.evaluateJavascript(snapshotScript()) { raw ->
                runCatching {
                    val decoded = decodeJsString(raw)
                    val json = JSONObject(decoded)
                    val nodesJson = json.optJSONArray("interactive") ?: JSONArray()
                    val nodes = buildList {
                        for (i in 0 until nodesJson.length()) {
                            val item = nodesJson.optJSONObject(i) ?: continue
                            add(
                                InteractiveNode(
                                    selector = item.optString("selector"),
                                    label = item.optString("label"),
                                    type = item.optString("type")
                                )
                            )
                        }
                    }
                    onSnapshot(
                        PageSnapshot(
                            tabId = tabId,
                            title = json.optString("title"),
                            url = json.optString("url"),
                            excerpt = json.optString("excerpt"),
                            interactive = nodes
                        )
                    )
                }
            }
        }
    }

    private suspend fun jsCommand(webView: WebView, script: String): JSONObject {
        val raw = suspendCancellableCoroutine<String> { continuation ->
            webView.post {
                webView.evaluateJavascript(script) { value ->
                    continuation.resume(value ?: "null")
                }
            }
        }
        val decoded = decodeJsString(raw)
        return JSONObject(decoded)
    }

    private fun decodeJsString(raw: String): String {
        return try {
            JSONArray("[$raw]").getString(0)
        } catch (_: Throwable) {
            raw.trim('"')
        }
    }

    private fun jsEscape(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    private fun clickScript(selector: String) = """
        (() => {
          const el = document.querySelector('${jsEscape(selector)}');
          if (!el) return JSON.stringify({ok:false, message:'Selector not found: ${jsEscape(selector)}'});
          el.click();
          return JSON.stringify({ok:true, message:'Clicked ${jsEscape(selector)}'});
        })();
    """.trimIndent()

    private fun typeScript(selector: String, text: String) = """
        (() => {
          const el = document.querySelector('${jsEscape(selector)}');
          if (!el) return JSON.stringify({ok:false, message:'Selector not found: ${jsEscape(selector)}'});
          el.focus();
          if ('value' in el) {
            el.value = '${jsEscape(text)}';
          } else {
            el.innerText = '${jsEscape(text)}';
          }
          el.dispatchEvent(new Event('input', { bubbles: true }));
          el.dispatchEvent(new Event('change', { bubbles: true }));
          return JSON.stringify({ok:true, message:'Typed into ${jsEscape(selector)}'});
        })();
    """.trimIndent()

    private fun extractScript(selector: String) = """
        (() => {
          const el = document.querySelector('${jsEscape(selector)}');
          if (!el) return JSON.stringify({ok:false, message:'Selector not found: ${jsEscape(selector)}'});
          const text = ('value' in el && el.value) ? el.value : ((el.innerText || el.textContent || '').trim());
          return JSON.stringify({ok:true, message:'Extracted text from ${jsEscape(selector)}', text});
        })();
    """.trimIndent()

    private fun extractPageScript() = """
        (() => {
          const text = (document.body?.innerText || '').trim();
          return JSON.stringify({ok:true, message:'Extracted page text', text});
        })();
    """.trimIndent()

    private fun readyStateScript() = """
        (() => JSON.stringify({ok:true, ready: document.readyState === 'complete'}))();
    """.trimIndent()

    private fun smartLoginScript(username: String, password: String) = """
        (() => {
          const textLike = Array.from(document.querySelectorAll("input[type='email'], input[type='text'], input:not([type]), input[name*='user' i], input[name*='email' i], input[id*='user' i], input[id*='email' i]"));
          const passwordField = document.querySelector("input[type='password']");
          if (!passwordField) return JSON.stringify({ok:false, message:'Password field not found'});
          const userField = textLike.find(el => el.offsetParent !== null) || textLike[0];
          if (!userField) return JSON.stringify({ok:false, message:'Username/email field not found'});
          userField.focus();
          userField.value = '${jsEscape(username)}';
          userField.dispatchEvent(new Event('input', { bubbles: true }));
          userField.dispatchEvent(new Event('change', { bubbles: true }));
          passwordField.focus();
          passwordField.value = '${jsEscape(password)}';
          passwordField.dispatchEvent(new Event('input', { bubbles: true }));
          passwordField.dispatchEvent(new Event('change', { bubbles: true }));
          const form = passwordField.form || userField.form;
          const submit = form?.querySelector("button[type='submit'], input[type='submit'], button") || document.querySelector("button[type='submit'], input[type='submit'], button");
          if (submit) {
            submit.click();
            return JSON.stringify({ok:true, message:'Login form submitted'});
          }
          if (form) {
            form.submit();
            return JSON.stringify({ok:true, message:'Login form submitted'});
          }
          return JSON.stringify({ok:false, message:'Submit button/form not found'});
        })();
    """.trimIndent()

    private fun scrollScript(direction: String) = """
        (() => {
          const dir = '${jsEscape(direction)}';
          if (dir === 'up') window.scrollBy({ top: -window.innerHeight * 0.8, behavior: 'smooth' });
          else if (dir === 'top') window.scrollTo({ top: 0, behavior: 'smooth' });
          else if (dir === 'bottom') window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
          else window.scrollBy({ top: window.innerHeight * 0.8, behavior: 'smooth' });
          return JSON.stringify({ok:true, message:'Scrolled ' + dir});
        })();
    """.trimIndent()

    private fun snapshotScript() = """
        (() => {
          const safe = (value) => (value || '').toString().replace(/\s+/g, ' ').trim();
          const esc = (value) => (typeof CSS !== 'undefined' && CSS.escape) ? CSS.escape(value) : value;
          const selectorFor = (el) => {
            if (!el) return '';
            if (el.id) return '#' + esc(el.id);
            const name = el.getAttribute('name');
            if (name) return el.tagName.toLowerCase() + '[name="' + name + '"]';
            const aria = el.getAttribute('aria-label');
            if (aria) return el.tagName.toLowerCase() + '[aria-label="' + aria + '"]';
            const type = el.getAttribute('type');
            if (type) return el.tagName.toLowerCase() + '[type="' + type + '"]';
            let path = el.tagName.toLowerCase();
            let parent = el.parentElement;
            let current = el;
            let depth = 0;
            while (parent && depth < 3) {
              const siblings = Array.from(parent.children).filter(node => node.tagName === current.tagName);
              const index = siblings.indexOf(current) + 1;
              path = current.tagName.toLowerCase() + ':nth-of-type(' + index + ') > ' + path;
              current = parent;
              parent = parent.parentElement;
              depth += 1;
            }
            return path;
          };
          const interactive = Array.from(document.querySelectorAll('a,button,input,textarea,select,[role="button"]'))
            .slice(0, 40)
            .map(el => ({
              selector: selectorFor(el),
              label: safe(el.innerText || el.value || el.getAttribute('aria-label') || el.getAttribute('placeholder') || el.tagName),
              type: (el.tagName || 'node').toLowerCase()
            }));
          return JSON.stringify({
            title: safe(document.title),
            url: safe(location.href),
            excerpt: safe(document.body ? document.body.innerText.slice(0, 1600) : ''),
            interactive
          });
        })();
    """.trimIndent()
}
