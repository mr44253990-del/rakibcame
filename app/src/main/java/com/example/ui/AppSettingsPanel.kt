package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
    
    val isFaceGestureExposure by viewModel.isFaceGestureExposureEnabled.collectAsState()
    val currentFps by viewModel.currentFps.collectAsState()
    val currentResolution by viewModel.currentResolution.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(Color(0xF20B0B0C)) // Translucent ultra premium dark background
            .padding(16.dp)
    ) {
        // App settings Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Config",
                    tint = GoldMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "CORE SETTINGS",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x1BFFFFFF))
                    .size(28.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close Settings", tint = Color.LightGray, modifier = Modifier.size(16.dp))
            }
        }

        Divider(color = Color(0x1EFFFFFF), thickness = 1.dp)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Voice & Language Assistance Options
            Column {
                SettingsCategoryTitle("VOICE & LANGUAGE", Icons.Default.VolumeUp)
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Toggle Language Panel Selection option
                Text("Assistant Dialog Language", color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x0EFFFFFF))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("English", "Bengali").forEach { lang ->
                        val isSelected = currentLanguage == lang
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) GoldMuted else Color.Transparent)
                                .clickable { viewModel.updateSetting("LANGUAGE", lang) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (lang == "Bengali") "বাংলা (BD)" else "English (US)",
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                // Auto Voice Listen settings toggle
                SettingToggleRow(
                    label = "Auto Voice Wake-up",
                    info = "Listen automatically on application entering",
                    checked = isAutoListen
                ) {
                    viewModel.updateSetting("AUTO_LISTEN", it)
                }
            }

            // Section 2: Camera Capture Specs
            Column {
                SettingsCategoryTitle("CAMERA METRICS", Icons.Default.Videocam)
                Spacer(modifier = Modifier.height(10.dp))
                
                // FPS Control options
                Text("Video Target Frame Rate (FPS)", color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x0EFFFFFF))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(24, 30, 60).forEach { fps ->
                        val isSelected = currentFps == fps
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) GoldMuted else Color.Transparent)
                                .clickable { viewModel.updateSetting("CAMERA_FPS", fps) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$fps FPS",
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Resolution Selection
                Text("Output Format Quality", color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x0EFFFFFF))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("4K", "1080P", "720P").forEach { res ->
                        val isSelected = currentResolution == res
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) GoldMuted else Color.Transparent)
                                .clickable { viewModel.updateSetting("CAMERA_RESOLUTION", res) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = res,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Section 3: Smart Visual Gesturing Controls ( মুখের মাধ্যমে আলো )
            Column {
                SettingsCategoryTitle("SMART GESTURES & OVERLAYS", Icons.Default.Speed)
                Spacer(modifier = Modifier.height(10.dp))

                SettingToggleRow(
                    label = "Mouth-Controlled Brightness",
                    info = "Open mouth or smile to automatically boost/dim exposure",
                    checked = isFaceGestureExposure
                ) {
                    viewModel.updateSetting("FACE_GESTURE_EXPOSURE", it)
                }
            }

            // Section 4: ML Kit Diagnostics & Engines ( অবজেক্ট ডিটেকশন চালু/বন্ধ )
            Column {
                SettingsCategoryTitle("ML KIT COMPUTER VISION", Icons.Default.Info)
                Spacer(modifier = Modifier.height(10.dp))
                
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x09FFFFFF))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BoxedToggleSettingRow(
                        label = "Object & Scene Detection",
                        checked = isMlObject
                    ) { viewModel.updateSetting("ML_OBJECT", it) }

                    BoxedToggleSettingRow(
                        label = "Human Face & Emotion",
                        checked = isMlFace
                    ) { viewModel.updateSetting("ML_FACE", it) }

                    BoxedToggleSettingRow(
                        label = "Pose & Skeletal Tracking",
                        checked = isMlPose
                    ) { viewModel.updateSetting("ML_POSE", it) }

                    BoxedToggleSettingRow(
                        label = "Barcode & QR Scanning",
                        checked = isMlBarcode
                    ) { viewModel.updateSetting("ML_BARCODE", it) }
                }
            }
        }
    }
}

@Composable
fun SettingsCategoryTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = Color.LightGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun SettingToggleRow(
    label: String,
    info: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (info.isNotEmpty()) {
                Text(info, color = Color.Gray, fontSize = 10.sp)
            }
        }
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

@Composable
fun BoxedToggleSettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.LightGray, fontSize = 12.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.85f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = GoldMuted,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}
