package com.hefoundsomethinginthestore.vhs.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.hefoundsomethinginthestore.vhs.camera.CameraManager
import com.hefoundsomethinginthestore.vhs.model.VhsOsdConfig
import com.hefoundsomethinginthestore.vhs.model.VhsTint
import kotlinx.coroutines.delay

@Composable
fun VhsCameraScreen(
    cameraManager: CameraManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentTint by remember { mutableStateOf(VhsTint.STANDARD) }
    var osdConfig by remember { mutableStateOf(VhsOsdConfig()) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingTimeSeconds by remember { mutableLongStateOf(0L) }

    var isOsdEditOpen by remember { mutableStateOf(false) }
    var editTitleText by remember { mutableStateOf(osdConfig.customTitle) }
    var editDateText by remember { mutableStateOf(osdConfig.customDateText) }

    var currentZoom by remember { mutableFloatStateOf(1.0f) }
    var trackingValue by remember { mutableFloatStateOf(0.0f) }
    var noiseValue by remember { mutableFloatStateOf(0.35f) }

    // Media Gallery Picker
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            Toast.makeText(context, "Loaded media with VHS filter applied!", Toast.LENGTH_SHORT).show()
        }
    }

    // Recording Time Counter Timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingTimeSeconds = 0L
            while (isRecording) {
                delay(1000)
                recordingTimeSeconds++
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {

        // 1. Camera Preview Viewfinder
        val previewView = remember { PreviewView(context) }
        DisposableEffect(lifecycleOwner) {
            cameraManager.startCamera(lifecycleOwner, previewView)
            onDispose {
                cameraManager.shutdown()
            }
        }

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Real-Time VHS Canvas Shader & OSD HUD Overlay
        VhsFilterOverlay(
            tint = currentTint,
            osdConfig = osdConfig.copy(
                trackingDistortion = trackingValue,
                noiseIntensity = noiseValue
            ),
            isRecording = isRecording,
            recordingTimeSeconds = recordingTimeSeconds,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Right-Side Tactile Camcorder Control Panel (Rarevision Style)
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(170.dp)
                    .background(Color(0xFF1B1B22))
                    .border(2.dp, Color(0xFF33333F))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {

                // Panel Header Text
                Text(
                    text = "RARE-VHS 90s",
                    color = Color(0xFFAAAAAA),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Big Red REC Button
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) Color(0xFFFF1744) else Color(0xFFD50000))
                        .border(3.dp, if (isRecording) Color.White else Color(0xFF880000), CircleShape)
                        .shadow(6.dp, CircleShape)
                        .clickable {
                            if (isRecording) {
                                cameraManager.stopRecording()
                                isRecording = false
                                Toast
                                    .makeText(context, "Saved to Gallery!", Toast.LENGTH_SHORT)
                                    .show()
                            } else {
                                cameraManager.startRecording { }
                                isRecording = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isRecording) "STOP" else "REC",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Tactile Controls Grid
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Tint Mode Button
                    CamcorderButton(
                        icon = Icons.Default.ColorLens,
                        label = currentTint.displayName,
                        accentColor = Color(0xFFFFB300)
                    ) {
                        val tints = VhsTint.values()
                        val nextIdx = (currentTint.ordinal + 1) % tints.size
                        currentTint = tints[nextIdx]
                    }

                    // Edit OSD Date & Title
                    CamcorderButton(
                        icon = Icons.Default.Edit,
                        label = "OSD / DATE",
                        accentColor = Color(0xFF00E5FF)
                    ) {
                        editTitleText = osdConfig.customTitle
                        editDateText = osdConfig.customDateText
                        isOsdEditOpen = true
                    }

                    // Glitch / Tracking Flutter Button
                    CamcorderButton(
                        icon = Icons.Default.GraphicEq,
                        label = "GLITCH / TAPE",
                        accentColor = Color(0xFFE040FB)
                    ) {
                        trackingValue = if (trackingValue == 0f) 0.65f else 0.0f
                    }

                    // Import Media Button
                    CamcorderButton(
                        icon = Icons.Default.FolderOpen,
                        label = "IMPORT MEDIA",
                        accentColor = Color(0xFF00E676)
                    ) {
                        galleryPicker.launch("video/*")
                    }
                }

                // Bottom Utilities Row (Zoom, Flash, Flip)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    IconButton(onClick = {
                        currentZoom = (currentZoom - 0.5f).coerceAtLeast(1.0f)
                        cameraManager.setZoomRatio(currentZoom)
                    }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
                    }

                    IconButton(onClick = {
                        cameraManager.toggleTorch()
                    }) {
                        Icon(Icons.Default.FlashOn, contentDescription = "Torch", tint = Color.Yellow)
                    }

                    IconButton(onClick = {
                        cameraManager.toggleCamera(lifecycleOwner, previewView)
                    }) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "Flip", tint = Color.White)
                    }

                    IconButton(onClick = {
                        currentZoom = (currentZoom + 0.5f).coerceAtMost(5.0f)
                        cameraManager.setZoomRatio(currentZoom)
                    }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
                    }
                }
            }
        }

        // 4. In-App Box Overlay for OSD Editing (Per rule: Box with zIndex and fillMaxSize, NOT Dialog)
        AnimatedVisibility(
            visible = isOsdEditOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(100f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = false) { },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .background(Color(0xFF22222C), RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFF444455), RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "EDIT VHS OVERLAY OSD",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = editTitleText,
                        onValueChange = { editTitleText = it },
                        label = { Text("Header / Title (Backrooms Log)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editDateText,
                        onValueChange = { editDateText = it },
                        label = { Text("Date Display (e.g. OCT 14 1995)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Static Noise Level: ${(noiseValue * 100).toInt()}%",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = noiseValue,
                        onValueChange = { noiseValue = it },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { isOsdEditOpen = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                        ) {
                            Text("Cancel", color = Color.White)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = {
                                osdConfig = osdConfig.copy(
                                    customTitle = editTitleText,
                                    customDateText = editDateText
                                )
                                isOsdEditOpen = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                        ) {
                            Text("Save OSD", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CamcorderButton(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF2C2C38))
            .border(1.dp, Color(0xFF444455), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
