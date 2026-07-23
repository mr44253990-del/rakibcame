package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ButtonMapping
import com.example.data.ControllerProfile

@Composable
fun ControllerMapperScreen(viewModel: ControllerMapperViewModel) {
    val serviceEnabled by viewModel.serviceEnabled.collectAsState()
    val status by viewModel.status.collectAsState()
    val devices by viewModel.connectedDevices.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val lastInput by viewModel.lastInput.collectAsState()
    val mappings by viewModel.mappings.collectAsState()
    val pressedTargets by viewModel.pressedTargets.collectAsState()
    val traces by viewModel.traceEntries.collectAsState()

    var newProfileName by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08111F))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HeaderCard(status = status)
        }
        item {
            ServiceCard(
                enabled = serviceEnabled,
                onToggle = viewModel::setServiceEnabled
            )
        }
        item {
            CompatibilityCard()
        }
        item {
            DevicesCard(devices = devices, onRefresh = viewModel::refreshConnectedDevices)
        }
        item {
            ProfilesCard(
                profiles = profiles,
                activeProfileId = activeProfileId,
                newProfileName = newProfileName,
                onProfileNameChange = { newProfileName = it },
                onCreateProfile = {
                    viewModel.createProfile(newProfileName)
                    newProfileName = ""
                },
                onSelectProfile = viewModel::setActiveProfile
            )
        }
        item {
            DetectionCard(
                lastInput = lastInput,
                onAssign = viewModel::assignLastInput,
                onClearRegistered = viewModel::removeAllRegisteredMappings
            )
        }
        item {
            XboxPreviewCard(pressedTargets = pressedTargets)
        }
        item {
            MappingListCard(mappings = mappings, onDelete = viewModel::removeMapping)
        }
        item {
            TraceCard(traces = traces)
        }
    }
}

@Composable
private fun HeaderCard(status: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0F172A), Color(0xFF172554), Color(0xFF1D4ED8))
                    )
                )
                .padding(20.dp)
        ) {
            Text(
                "Virtual Xbox Mapper",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Generic / USB controller input detect করে Xbox-style layout mapping profile তৈরি করে।",
                color = Color(0xFFE2E8F0)
            )
            Spacer(Modifier.height(10.dp))
            SelectionContainer {
                Text(status, color = Color(0xFF93C5FD), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ServiceCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Compatibility Service", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "Service ON করলে profile active থাকবে। Non-root mode-এ live capture-এর জন্য app foreground-এ রাখা best.",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun CompatibilityCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Important note", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Stock Android non-root mode-এ এই app mapping, preview, input monitoring, profile service, এবং common compatibility workflow দেয়। Real system-wide virtual Xbox HID output সাধারণত root/uinput ছাড়া সীমিত।",
                color = Color(0xFFCBD5E1)
            )
        }
    }
}

@Composable
private fun DevicesCard(devices: List<ControllerDeviceInfo>, onRefresh: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1220))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Connected controllers", color = Color.White, fontWeight = FontWeight.Bold)
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (devices.isEmpty()) {
                Text("কোন USB/Bluetooth game controller detect হয়নি।", color = Color(0xFF94A3B8))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    devices.forEach { device ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(device.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("id=${device.id}  vendor=${device.vendorId}  product=${device.productId}", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
                                Text(device.descriptor, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilesCard(
    profiles: List<ControllerProfile>,
    activeProfileId: Long,
    newProfileName: String,
    onProfileNameChange: (String) -> Unit,
    onCreateProfile: () -> Unit,
    onSelectProfile: (Long) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Profiles", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = onProfileNameChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("New profile name") },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onCreateProfile) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (profiles.isEmpty()) {
                Text("Profile load হচ্ছে...", color = Color(0xFF94A3B8))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.take(6).forEach { profile ->
                        AssistChip(
                            onClick = { onSelectProfile(profile.id) },
                            label = { Text(profile.name) },
                            enabled = true,
                            leadingIcon = {
                                if (profile.id == activeProfileId) {
                                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF22C55E), CircleShape))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectionCard(
    lastInput: SourceInput?,
    onAssign: (String) -> Unit,
    onClearRegistered: () -> Unit
) {
    val targetRows = listOf(
        listOf("A", "B", "X", "Y"),
        listOf("LB", "RB", "LT", "RT"),
        listOf("LS", "RS", "BACK", "START"),
        listOf("DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT")
    )

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1220))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Live input detection", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                lastInput?.let { "Detected: ${it.label} (${it.code})" } ?: "এখনও কোন input detect হয়নি।",
                color = Color(0xFF93C5FD),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            targetRows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { target ->
                        Button(onClick = { onAssign(target) }, modifier = Modifier.weight(1f)) {
                            Text(target, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = onClearRegistered, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("All registered remove")
            }
        }
    }
}

@Composable
private fun XboxPreviewCard(pressedTargets: Set<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Xbox layout preview", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(Color(0xFF09101C), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
            ) {
                Pill("LB", pressedTargets.contains("LB"), Modifier.align(Alignment.TopStart).padding(24.dp))
                Pill("RB", pressedTargets.contains("RB"), Modifier.align(Alignment.TopEnd).padding(24.dp))
                Pill("LT", pressedTargets.contains("LT"), Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 64.dp))
                Pill("RT", pressedTargets.contains("RT"), Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 64.dp))

                Stick("LS", pressedTargets.contains("LS"), Modifier.align(Alignment.CenterStart).padding(start = 48.dp))
                Stick("RS", pressedTargets.contains("RS"), Modifier.align(Alignment.CenterEnd).padding(end = 48.dp))

                DPadCluster(pressedTargets = pressedTargets, modifier = Modifier.align(Alignment.BottomStart).padding(start = 36.dp, bottom = 36.dp))
                FaceButtons(pressedTargets = pressedTargets, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 36.dp))

                Pill("BACK", pressedTargets.contains("BACK"), Modifier.align(Alignment.Center).padding(end = 70.dp, bottom = 30.dp))
                Pill("START", pressedTargets.contains("START"), Modifier.align(Alignment.Center).padding(start = 70.dp, bottom = 30.dp))
            }
        }
    }
}

@Composable
private fun MappingListCard(mappings: List<ButtonMapping>, onDelete: (Long) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1220))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Saved mappings", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (mappings.isEmpty()) {
                Text("কোন mapping save করা হয়নি।", color = Color(0xFF94A3B8))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    mappings.forEach { mapping ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(mapping.targetButton, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(mapping.sourceLabel, color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
                                    SelectionContainer {
                                        Text(mapping.sourceCode, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Button(onClick = { onDelete(mapping.id) }) {
                                    Text("Remove")
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
private fun TraceCard(traces: List<TraceEntry>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Live trace", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (traces.isEmpty()) {
                Text("এখনও কোন trace নেই।", color = Color(0xFF94A3B8))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    traces.take(20).forEach { trace ->
                        val accent = when (trace.level) {
                            "success" -> Color(0xFF22C55E)
                            "error" -> Color(0xFFEF4444)
                            else -> Color(0xFF38BDF8)
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(trace.title, color = accent, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                SelectionContainer {
                                    Text(trace.details, color = Color(0xFFE2E8F0), maxLines = 4, overflow = TextOverflow.Ellipsis)
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
private fun FaceButtons(pressedTargets: Set<String>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(120.dp)) {
        CircleButton("Y", pressedTargets.contains("Y"), Color(0xFFEAB308), Modifier.align(Alignment.TopCenter))
        CircleButton("X", pressedTargets.contains("X"), Color(0xFF3B82F6), Modifier.align(Alignment.CenterStart))
        CircleButton("B", pressedTargets.contains("B"), Color(0xFFEF4444), Modifier.align(Alignment.CenterEnd))
        CircleButton("A", pressedTargets.contains("A"), Color(0xFF22C55E), Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun DPadCluster(pressedTargets: Set<String>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(120.dp)) {
        Pill("UP", pressedTargets.contains("DPAD_UP"), Modifier.align(Alignment.TopCenter))
        Pill("LEFT", pressedTargets.contains("DPAD_LEFT"), Modifier.align(Alignment.CenterStart))
        Pill("RIGHT", pressedTargets.contains("DPAD_RIGHT"), Modifier.align(Alignment.CenterEnd))
        Pill("DOWN", pressedTargets.contains("DPAD_DOWN"), Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Stick(label: String, active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(78.dp)
            .background(if (active) Color(0xFF38BDF8) else Color(0xFF1E293B), CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Pill(label: String, active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .wrapContentHeight()
            .background(if (active) Color(0xFF38BDF8) else Color(0xFF1E293B), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CircleButton(label: String, active: Boolean, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(42.dp)
            .background(if (active) color else color.copy(alpha = 0.35f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Black)
    }
}
