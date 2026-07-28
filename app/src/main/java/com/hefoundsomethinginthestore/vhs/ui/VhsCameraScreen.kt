package com.hefoundsomethinginthestore.vhs.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.hefoundsomethinginthestore.vhs.camera.CameraManager
import com.hefoundsomethinginthestore.vhs.model.VideoStudioConfig
import com.hefoundsomethinginthestore.vhs.model.VhsIntroType
import com.hefoundsomethinginthestore.vhs.model.VhsOsdConfig
import com.hefoundsomethinginthestore.vhs.model.VhsOutroType
import com.hefoundsomethinginthestore.vhs.model.VhsTransitionEffect
import com.hefoundsomethinginthestore.vhs.model.VhsTint
import kotlinx.coroutines.delay

enum class CaptureMode { VIDEO, PHOTO }

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
    var isTorchOn by remember { mutableStateOf(false) }

    var isOsdEditOpen by remember { mutableStateOf(false) }
    var isStudioOpen by remember { mutableStateOf(false) }
    var studioConfig by remember { mutableStateOf(VideoStudioConfig()) }
    var editTitleText by remember { mutableStateOf(osdConfig.customTitle) }
    var editDateText by remember { mutableStateOf(osdConfig.customDateText) }

    var currentZoom by remember { mutableFloatStateOf(1.0f) }
    var noiseValue by remember { mutableFloatStateOf(0.35f) }
    var trackingValue by remember { mutableFloatStateOf(0.0f) }

    var photoFlashVisible by remember { mutableStateOf(false) }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { isStudioOpen = true }
    }

    // REC timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingTimeSeconds = 0L
            while (isRecording) {
                delay(1000)
                recordingTimeSeconds++
            }
        }
    }

    // Blinking REC dot animation
    val infiniteTransition = rememberInfiniteTransition(label = "rec")
    val recAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "recBlink"
    )

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {

        // 1. Camera Viewfinder — full screen
        val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
        DisposableEffect(lifecycleOwner) {
            cameraManager.startCamera(lifecycleOwner, previewView)
            onDispose { cameraManager.shutdown() }
        }

        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        currentZoom = (currentZoom * zoom).coerceIn(1.0f, 8.0f)
                        cameraManager.setZoomRatio(currentZoom)
                    }
                }
        )

        // Photo shutter flash
        if (photoFlashVisible) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.8f)))
        }

        // 2. VHS Canvas shader + OSD
        VhsFilterOverlay(
            tint = currentTint,
            osdConfig = osdConfig.copy(trackingDistortion = trackingValue, noiseIntensity = noiseValue),
            isRecording = isRecording,
            recordingTimeSeconds = recordingTimeSeconds,
            modifier = Modifier.fillMaxSize()
        )

        // 3. TOP BAR — mode switch + torch + flip (Rarevision style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Torch
            IconButton(
                onClick = { isTorchOn = cameraManager.toggleTorch() },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(
                    if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Torch",
                    tint = if (isTorchOn) Color(0xFFFFD600) else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Center: VIDEO / PHOTO toggle (Rarevision style pill)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(3.dp)
            ) {
                CaptureModeTab("VIDEO", captureMode == CaptureMode.VIDEO, Icons.Default.Videocam) {
                    captureMode = CaptureMode.VIDEO
                }
                Spacer(modifier = Modifier.width(2.dp))
                CaptureModeTab("PHOTO", captureMode == CaptureMode.PHOTO, Icons.Default.Photo) {
                    captureMode = CaptureMode.PHOTO
                }
            }

            // Flip camera
            IconButton(
                onClick = { cameraManager.toggleCamera(lifecycleOwner, previewView) },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(
                    Icons.Default.Cameraswitch,
                    contentDescription = "Flip",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 4. BOTTOM CONTROLS — full Rarevision-style layout
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Filter carousel (like Rarevision filter row above the controls)
            FilterCarousel(
                currentTint = currentTint,
                onTintSelected = { currentTint = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom action row: Gallery | REC | OSD edit
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Import / Post Studio
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(72.dp)
                ) {
                    IconButton(
                        onClick = { galleryPicker.launch("video/*") },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1C1C1C))
                            .border(1.dp, Color(0xFF444444), CircleShape)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Import", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("IMPORT", color = Color(0xFFAAAAAA), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }

                // Center: Main REC / SNAP shutter — big, like Rarevision
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .background(Color.White)
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
                                        Toast.makeText(context, if (saved) "Photo saved!" else "Error saving", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show() }
                                )
                                // Dismiss flash after short delay
                                // (handled with LaunchedEffect below)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Inner circle — red when recording, grey outline for photo
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(if (captureMode == CaptureMode.VIDEO && isRecording) RoundedCornerShape(10.dp) else CircleShape)
                            .background(
                                when {
                                    captureMode == CaptureMode.VIDEO && isRecording -> Color(0xFFFF1744).copy(alpha = recAlpha.coerceIn(0.6f, 1f))
                                    captureMode == CaptureMode.VIDEO -> Color(0xFFFF1744)
                                    else -> Color(0xFFEEEEEE)
                                }
                            )
                    )
                }

                // Right: OSD edit + Post studio
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(72.dp)
                ) {
                    IconButton(
                        onClick = { isStudioOpen = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1C1C1C))
                            .border(1.dp, Color(0xFF444444), CircleShape)
                    ) {
                        Icon(Icons.Default.MovieFilter, contentDescription = "Studio", tint = Color(0xFFE040FB), modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("STUDIO", color = Color(0xFFAAAAAA), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Photo flash dismiss
        LaunchedEffect(photoFlashVisible) {
            if (photoFlashVisible) {
                delay(100)
                photoFlashVisible = false
            }
        }

        // 5. OSD Edit Overlay
        AnimatedVisibility(visible = isOsdEditOpen, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize().zIndex(100f)) {
            OsdEditOverlay(
                titleText = editTitleText,
                dateText = editDateText,
                noiseValue = noiseValue,
                onTitleChange = { editTitleText = it },
                onDateChange = { editDateText = it },
                onNoiseChange = { noiseValue = it },
                onCancel = { isOsdEditOpen = false },
                onSave = {
                    osdConfig = osdConfig.copy(customTitle = editTitleText, customDateText = editDateText)
                    isOsdEditOpen = false
                }
            )
        }

        // 6. Post-Studio Overlay
        AnimatedVisibility(visible = isStudioOpen, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize().zIndex(100f)) {
            StudioOverlay(
                studioConfig = studioConfig,
                onConfigChange = { studioConfig = it },
                onClose = { isStudioOpen = false },
                onSave = {
                    isStudioOpen = false
                    Toast.makeText(context, "Post-processing applied & saved!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 7. OSD Edit button — top-right corner shortcut icon
        IconButton(
            onClick = {
                editTitleText = osdConfig.customTitle
                editDateText = osdConfig.customDateText
                isOsdEditOpen = true
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 64.dp, end = 16.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(Icons.Default.Edit, contentDescription = "Edit OSD", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CaptureModeTab(
    label: String,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = label,
            tint = if (isSelected) Color.Black else Color.White,
            modifier = Modifier.size(14.dp))
        Text(label,
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun FilterCarousel(
    currentTint: VhsTint,
    onTintSelected: (VhsTint) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VhsTint.values().forEach { tint ->
            val isSelected = tint == currentTint
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color(0xFF222222) else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) Color.White else Color(0xFF444444),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onTintSelected(tint) }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                // Color dot preview
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(tintPreviewColor(tint))
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tint.displayName.uppercase(),
                    color = if (isSelected) Color.White else Color(0xFFAAAAAA),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(56.dp)
                )
            }
        }
    }
}

private fun tintPreviewColor(tint: VhsTint): Color = when (tint) {
    VhsTint.BLANK -> Color(0xFF282828)
    VhsTint.STANDARD -> Color(0xFF8B7355)
    VhsTint.NIGHT_VISION -> Color(0xFF00CC44)
    VhsTint.GLITCH_MAX -> Color(0xFFCC0055)
    VhsTint.WARM_SEPIA -> Color(0xFFC8783C)
    VhsTint.MONO_BW -> Color(0xFF888888)
    VhsTint.CYBER_BLUE -> Color(0xFF0077CC)
    VhsTint.LIMINAL_YELLOW -> Color(0xFFAAAA22)
}

@Composable
private fun OsdEditOverlay(
    titleText: String,
    dateText: String,
    noiseValue: Float,
    onTitleChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onNoiseChange: (Float) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(Color(0xFF1A1A22), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF3A3A4A), RoundedCornerShape(14.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("OSD OVERLAY", color = Color.White, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = titleText, onValueChange = onTitleChange,
                label = { Text("Header / Title Text") },
                colors = osdFieldColors(), singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = dateText, onValueChange = onDateChange,
                label = { Text("Date (e.g. OCT. 14 1995)") },
                colors = osdFieldColors(), singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Grain / Noise: ${(noiseValue * 100).toInt()}%",
                color = Color.LightGray, fontSize = 11.sp)
            Slider(value = noiseValue, onValueChange = onNoiseChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF)))

            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C))) {
                    Text("Cancel", color = Color.White)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StudioOverlay(
    studioConfig: VideoStudioConfig,
    onConfigChange: (VideoStudioConfig) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.90f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .background(Color(0xFF1A1A22), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF3A3A4A), RoundedCornerShape(14.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("POST-PROCESSING STUDIO", color = Color.White, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(14.dp))

            // INTRO
            SectionLabel("INTRO SEQUENCE", Color(0xFF00E5FF))
            SelectorRow(VhsIntroType.values().toList(), studioConfig.introType,
                label = { it.label }, activeColor = Color(0xFF00E5FF)) {
                onConfigChange(studioConfig.copy(introType = it))
            }
            Spacer(modifier = Modifier.height(12.dp))

            // OUTRO
            SectionLabel("OUTRO SEQUENCE", Color(0xFFFFB300))
            SelectorRow(VhsOutroType.values().toList(), studioConfig.outroType,
                label = { it.label }, activeColor = Color(0xFFFFB300)) {
                onConfigChange(studioConfig.copy(outroType = it))
            }
            Spacer(modifier = Modifier.height(12.dp))

            // TRANSITIONS
            SectionLabel("GLITCH TRANSITION", Color(0xFFE040FB))
            SelectorRow(VhsTransitionEffect.values().toList(), studioConfig.transitionEffect,
                label = { it.label }, activeColor = Color(0xFFE040FB)) {
                onConfigChange(studioConfig.copy(transitionEffect = it))
            }
            Spacer(modifier = Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C))) {
                    Text("Close", color = Color.White)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))) {
                    Text("Save & Apply", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
}

@Composable
private fun <T> SelectorRow(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    activeColor: Color,
    onSelect: (T) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            val isSelected = item == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) activeColor else Color(0xFF262632))
                    .border(1.dp, if (isSelected) activeColor else Color(0xFF3A3A4A), RoundedCornerShape(6.dp))
                    .clickable { onSelect(item) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label(item), color = if (isSelected) Color.Black else Color(0xFFCCCCCC),
                    fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun osdFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF00E5FF),
    unfocusedBorderColor = Color(0xFF444455),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Color(0xFF00E5FF),
    unfocusedLabelColor = Color.Gray
)
