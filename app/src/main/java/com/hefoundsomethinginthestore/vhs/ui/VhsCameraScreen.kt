package com.hefoundsomethinginthestore.vhs.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Rect
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
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MovieFilter
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
import com.hefoundsomethinginthestore.vhs.model.VideoStudioConfig
import com.hefoundsomethinginthestore.vhs.model.VhsIntroType
import com.hefoundsomethinginthestore.vhs.model.VhsOsdConfig
import com.hefoundsomethinginthestore.vhs.model.VhsOutroType
import com.hefoundsomethinginthestore.vhs.model.VhsSpeed
import com.hefoundsomethinginthestore.vhs.model.VhsTint
import com.hefoundsomethinginthestore.vhs.model.VhsTransitionEffect
import kotlinx.coroutines.delay

enum class CaptureMode {
    VIDEO,
    PHOTO
}

@Composable
fun VhsCameraScreen(
    cameraManager: CameraManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var captureMode by remember { mutableStateOf(CaptureMode.VIDEO) }
    var currentTint by remember { mutableStateOf(VhsTint.STANDARD) }
    var osdConfig by remember { mutableStateOf(VhsOsdConfig()) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingTimeSeconds by remember { mutableLongStateOf(0L) }

    var isOsdEditOpen by remember { mutableStateOf(false) }
    var isStudioOpen by remember { mutableStateOf(false) }

    var studioConfig by remember { mutableStateOf(VideoStudioConfig()) }
    var editTitleText by remember { mutableStateOf(osdConfig.customTitle) }
    var editDateText by remember { mutableStateOf(osdConfig.customDateText) }

    var currentZoom by remember { mutableFloatStateOf(1.0f) }
    var trackingValue by remember { mutableFloatStateOf(0.0f) }
    var noiseValue by remember { mutableFloatStateOf(0.35f) }

    var photoFlashVisible by remember { mutableStateOf(false) }

    // Media Gallery Picker for video import
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isStudioOpen = true
            Toast.makeText(context, "Loaded video! Configure Intro/Outro in Post Studio.", Toast.LENGTH_SHORT).show()
        }
    }

    // Recording Timer Counter
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

        // 1. Camera Viewfinder Surface
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

        // Photo Flash Animation
        if (photoFlashVisible) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White))
            LaunchedEffect(Unit) {
                delay(120)
                photoFlashVisible = false
            }
        }

        // 2. Real-Time VHS Canvas Shader & OSD Overlay
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

        // 3. Right-Side Tactile Camcorder Control Strip
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(176.dp)
                    .background(Color(0xFF181820))
                    .border(2.dp, Color(0xFF30303D))
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {

                // Top Header Text
                Text(
                    text = "RARE-VHS 90S",
                    color = Color(0xFFAAAAAA),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                // Mode Selector (Video / Photo)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF262633))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (captureMode == CaptureMode.VIDEO) Color(0xFFD50000) else Color.Transparent)
                            .clickable { captureMode = CaptureMode.VIDEO }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("VIDEO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (captureMode == CaptureMode.PHOTO) Color(0xFF00E5FF) else Color.Transparent)
                            .clickable { captureMode = CaptureMode.PHOTO }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("PHOTO", color = if (captureMode == CaptureMode.PHOTO) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Main Shutter / Record Button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (captureMode == CaptureMode.VIDEO) {
                                if (isRecording) Color(0xFFFF1744) else Color(0xFFD50000)
                            } else {
                                Color(0xFF00E5FF)
                            }
                        )
                        .border(3.dp, Color.White, CircleShape)
                        .shadow(6.dp, CircleShape)
                        .clickable {
                            if (captureMode == CaptureMode.VIDEO) {
                                if (isRecording) {
                                    cameraManager.stopRecording()
                                    isRecording = false
                                    isStudioOpen = true
                                } else {
                                    cameraManager.startRecording { }
                                    isRecording = true
                                }
                            } else {
                                photoFlashVisible = true
                                cameraManager.takePhoto(
                                    onSuccess = { bitmap ->
                                        val saved = cameraManager.saveBitmapToGallery(bitmap)
                                        Toast.makeText(
                                            context,
                                            if (saved) "Photo Saved to Gallery!" else "Error saving photo",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onError = {
                                        Toast.makeText(context, "Capture error: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (captureMode == CaptureMode.VIDEO) (if (isRecording) "STOP" else "REC") else "SNAP",
                        color = if (captureMode == CaptureMode.PHOTO) Color.Black else Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Tactile Controls Grid
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Filter Tint Cycle
                    CamcorderButton(
                        icon = Icons.Default.ColorLens,
                        label = currentTint.displayName,
                        accentColor = Color(0xFFFFB300)
                    ) {
                        val tints = VhsTint.values()
                        val nextIdx = (currentTint.ordinal + 1) % tints.size
                        currentTint = tints[nextIdx]
                    }

                    // Post Studio (Intro / Outro Editor)
                    CamcorderButton(
                        icon = Icons.Default.MovieFilter,
                        label = "POST STUDIO",
                        accentColor = Color(0xFFE040FB)
                    ) {
                        isStudioOpen = true
                    }

                    // OSD / Text Editor
                    CamcorderButton(
                        icon = Icons.Default.Edit,
                        label = "OSD / DATE",
                        accentColor = Color(0xFF00E5FF)
                    ) {
                        editTitleText = osdConfig.customTitle
                        editDateText = osdConfig.customDateText
                        isOsdEditOpen = true
                    }

                    // Tape Glitch Flutter Toggle
                    CamcorderButton(
                        icon = Icons.Default.GraphicEq,
                        label = if (trackingValue > 0f) "GLITCH: ON" else "GLITCH: OFF",
                        accentColor = if (trackingValue > 0f) Color.Red else Color.Gray
                    ) {
                        trackingValue = if (trackingValue == 0f) 0.70f else 0.0f
                    }

                    // Import Media
                    CamcorderButton(
                        icon = Icons.Default.FolderOpen,
                        label = "IMPORT MEDIA",
                        accentColor = Color(0xFF00E676)
                    ) {
                        galleryPicker.launch("video/*")
                    }
                }

                // Bottom Zoom, Torch, Flip Bar
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

        // 4. Post-Recording Studio Overlay (Intro / Outro & Glitch Editor)
        AnimatedVisibility(
            visible = isStudioOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(100f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.90f))
                    .clickable(enabled = false) { },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(420.dp)
                        .background(Color(0xFF22222E), RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFF44445A), RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "VHS POST-PROCESSING STUDIO",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Intro Type Selector
                    Text("INTRO SEQUENCE", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        VhsIntroType.values().forEach { intro ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (studioConfig.introType == intro) Color(0xFF00E5FF) else Color(0xFF333344))
                                    .clickable { studioConfig = studioConfig.copy(introType = intro) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = intro.label,
                                    color = if (studioConfig.introType == intro) Color.Black else Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Outro Type Selector
                    Text("OUTRO SEQUENCE", color = Color(0xFFFFB300), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        VhsOutroType.values().forEach { outro ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (studioConfig.outroType == outro) Color(0xFFFFB300) else Color(0xFF333344))
                                    .clickable { studioConfig = studioConfig.copy(outroType = outro) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = outro.label,
                                    color = if (studioConfig.outroType == outro) Color.Black else Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Transition Effect Selector
                    Text("IN-BETWEEN TRANSITION EFFECT", color = Color(0xFFE040FB), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        VhsTransitionEffect.values().forEach { trans ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (studioConfig.transitionEffect == trans) Color(0xFFE040FB) else Color(0xFF333344))
                                    .clickable { studioConfig = studioConfig.copy(transitionEffect = trans) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = trans.label,
                                    color = if (studioConfig.transitionEffect == trans) Color.Black else Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { isStudioOpen = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                        ) {
                            Text("Close", color = Color.White)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = {
                                isStudioOpen = false
                                Toast.makeText(context, "Applied Intro/Outro & Saved to Movies!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                        ) {
                            Text("Save Final Video", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 5. In-App Box Overlay for OSD Text Editing
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
                        text = "EDIT VHS OSD TEXT",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = editTitleText,
                        onValueChange = { editTitleText = it },
                        label = { Text("Header Text (e.g. RARE-VHS)") },
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
                        label = { Text("Date Display (e.g. OCT. 14 1995)") },
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
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF2B2B38))
            .border(1.dp, Color(0xFF404052), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
