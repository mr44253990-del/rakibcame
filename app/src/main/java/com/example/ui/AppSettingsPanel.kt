package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppSettingsPanel(
    viewModel: CameraViewModel,
    onDismiss: () -> Unit
) {
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val isAutoListen by viewModel.isAutoListenEnabled.collectAsState()
    val isMlObject by viewModel.isObjectDetectionEnabled.collectAsState()
    val isMlFace by viewModel.isFaceDetectionEnabled.collectAsState()
    val isMlPose by viewModel.isPoseDetectionEnabled.collectAsState()
    val isMlBarcode by viewModel.isBarcodeScanningEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(Color(0xE6111111))
            .padding(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "CAMERA SETTINGS",
                color = GoldMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close Settings", tint = Color.LightGray)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Language Setting
        Text("VOICE LANGUAGE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(CarbonMedium),
            horizontalArrangement = Arrangement.Center
        ) {
            listOf("English", "Bengali").forEach { lang ->
                Row(
                    modifier = Modifier.weight(1f).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentLanguage == lang,
                        onClick = { viewModel.updateSetting("LANGUAGE", lang) },
                        colors = RadioButtonDefaults.colors(selectedColor = GoldMuted, unselectedColor = Color.Gray)
                    )
                    Text(lang, color = Color.White, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // System Settings
        Text("SYSTEM SETTINGS", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        SettingToggleRow("Auto Voice Listen", isAutoListen) { viewModel.updateSetting("AUTO_LISTEN", it) }
        
        Spacer(modifier = Modifier.height(24.dp))

        // ML Kit Toggles
        Text("ML KIT ENGINE CONFIGURATION", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        Column(
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(CarbonMedium).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingToggleRow("Object & Scene Detection", isMlObject) { viewModel.updateSetting("ML_OBJECT", it) }
            SettingToggleRow("Face Detection & Emotion", isMlFace) { viewModel.updateSetting("ML_FACE", it) }
            SettingToggleRow("Pose Tracking & Advice", isMlPose) { viewModel.updateSetting("ML_POSE", it) }
            SettingToggleRow("Barcode Scanner", isMlBarcode) { viewModel.updateSetting("ML_BARCODE", it) }
        }
    }
}

@Composable
fun SettingToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.LightGray, fontSize = 13.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = GoldMuted,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}
