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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.hefoundsomethinginthestore.vhs.processing.VideoProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Which shutter action is active
private enum class CaptureMode { VIDEO, PHOTO }

@Composable
fun VhsCameraScreen(
    cameraManager: CameraManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val processor = remember { VideoProcessor(context) }

    // ── State ───────────────────────────────────────────────────────────────
    var captureMode by remember { mutableStateOf(CaptureMode.VIDEO) }
    var currentTint by remember { mutableStateOf(VhsTint.STANDARD) }
    val tints = VhsTint.values()
    var tintIndex by remember { mutableStateOf(1) } // STANDARD by default

    var osdConfig by remember { mutableStateOf(VhsOsdConfig()) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSecs by remember { mutableLongStateOf(0L) }
    var lastRecordedUri by remember { mutableStateOf<android.net.Uri?>(null) }

    var isTorchOn by remember { mutableStateOf(false) }
    var showOsdEdit by remember { mutableStateOf(false) }
    var showStudio by remember { mutableStateOf(false) }
    var studioConfig by remember { mutableStateOf(VideoStudioConfig()) }

    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableFloatStateOf(0f) }
    var photoFlash by remember { mutableStateOf(false) }

    // ── REC timer ───────────────────────────────────────────────────────────
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSecs = 0L
            while (isRecording) { delay(1000); recordingSecs++ }
        }
    }

    val infiniteT = rememberInfiniteTransition(label = "rec")
    val recBlink by infiniteT.animateFloat(
        1f, 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "blink"
    )

    // ── Camera preview ───────────────────────────────────────────────────────
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(lifecycleOwner) {
        cameraManager.startCamera(lifecycleOwner, previewView)
        onDispose { cameraManager.shutdown() }
    }

    // ────────────────────────────────────────────────────────────────────────
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {

        // ① Camera viewfinder – fills the whole screen
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val next = (cameraManager.currentZoomRatio * zoom).coerceIn(1f, 8f)
                        cameraManager.setZoomRatio(next)
                    }
                }
        )

        // ② VHS canvas effects rendered over the viewfinder
        VhsFilterOverlay(
            tint = currentTint,
            osdConfig = osdConfig,
            isRecording = isRecording,
            recordingTimeSeconds = recordingSecs,
            modifier = Modifier.fillMaxSize()
        )

        // ③ Photo shutter flash
        if (photoFlash) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.75f)))
            LaunchedEffect(Unit) { delay(80); photoFlash = false }
        }

        // ④ BOTTOM PANEL — Rarevision VHS layout
        //    Filter row  +  5-button control row, floating over the viewfinder
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            // ── Filter name row: ← TINT NAME →
            FilterNameRow(
                name = tints[tintIndex].displayName.uppercase(),
                onPrev = {
                    tintIndex = (tintIndex - 1 + tints.size) % tints.size
                    currentTint = tints[tintIndex]
                },
                onNext = {
                    tintIndex = (tintIndex + 1) % tints.size
                    currentTint = tints[tintIndex]
                }
            )

            // ── Main 5-button controls row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xE6080808))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT PAIR
                SideButton(
                    icon = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    tint = if (isTorchOn) Color(0xFFFFD600) else Color.White,
                    label = "TORCH"
                ) { isTorchOn = cameraManager.toggleTorch() }

                SideButton(
                    icon = Icons.Default.Tune,
                    tint = Color.White,
                    label = "OSD"
                ) { showOsdEdit = true }

                // CENTER — Big REC / SNAP shutter (matches Rarevision's white ring + coloured inner)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .border(4.dp, Color.White, CircleShape)
                        .clickable {
                            if (captureMode == CaptureMode.VIDEO) {
                                if (isRecording) {
                                    cameraManager.stopRecording()
                                } else {
                                    isRecording = true
                                    cameraManager.startRecording { uri ->
                                        isRecording = false
                                        lastRecordedUri = uri
                                        if (uri != null) showStudio = true
                                    }
                                }
                            } else {
                                photoFlash = true
                                cameraManager.takePhoto(
                                    onSuccess = { bmp ->
                                        val ok = cameraManager.saveBitmapToGallery(bmp)
                                        Toast.makeText(context, if (ok) "Photo saved!" else "Save failed", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { Toast.makeText(context, "Capture error", Toast.LENGTH_SHORT).show() }
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Inner fill: red square when recording (Rarevision stops icon), red circle otherwise
                    val isVideoMode = captureMode == CaptureMode.VIDEO
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(
                                if (isVideoMode && isRecording) RoundedCornerShape(8.dp)
                                else if (isVideoMode) CircleShape
                                else RoundedCornerShape(4.dp)
                            )
                            .background(
                                when {
                                    isVideoMode && isRecording ->
                                        Color(0xFFFF1744).copy(alpha = recBlink.coerceIn(0.55f, 1f))
                                    isVideoMode -> Color(0xFFFF1744)
                                    else -> Color(0xFFE0E0E0)
                                }
                            )
                    )
                }

                // RIGHT PAIR
                SideButton(
                    icon = Icons.Default.Cached,
                    tint = Color.White,
                    label = "FLIP"
                ) { cameraManager.toggleCamera(lifecycleOwner, previewView) }

                SideButton(
                    icon = Icons.Default.Movie,
                    tint = Color(0xFFE040FB),
                    label = "STUDIO"
                ) { if (lastRecordedUri != null) showStudio = true else Toast.makeText(context, "Record a video first", Toast.LENGTH_SHORT).show() }
            }

            // ── Mode indicator: VIDEO | PHOTO tabs at very bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xF0050505))
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeTab("VIDEO", Icons.Default.Movie, captureMode == CaptureMode.VIDEO) { captureMode = CaptureMode.VIDEO }
                Spacer(Modifier.width(32.dp))
                ModeTab("PHOTO", Icons.Default.CameraAlt, captureMode == CaptureMode.PHOTO) { captureMode = CaptureMode.PHOTO }
            }
        }

        // ⑤ Post-processing progress overlay (full screen, highest z)
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .zIndex(200f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 3.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Processing video…", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { processingProgress },
                        color = Color(0xFF00E5FF),
                        trackColor = Color(0xFF222222),
                        modifier = Modifier.fillMaxWidth(0.7f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${(processingProgress * 100).toInt()}%", color = Color(0xFF00E5FF),
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
        }

        // ⑥ OSD edit overlay
        AnimatedVisibility(
            visible = showOsdEdit,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.fillMaxSize().zIndex(100f)
        ) {
            OsdEditOverlay(
                config = osdConfig,
                onUpdate = { osdConfig = it },
                onClose = { showOsdEdit = false }
            )
        }

        // ⑦ Post-studio overlay
        AnimatedVisibility(
            visible = showStudio && !isProcessing,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.fillMaxSize().zIndex(100f)
        ) {
            StudioOverlay(
                config = studioConfig,
                onConfigChange = { studioConfig = it },
                onClose = { showStudio = false },
                onProcess = {
                    val uri = lastRecordedUri ?: return@StudioOverlay
                    showStudio = false
                    isProcessing = true
                    processingProgress = 0f
                    scope.launch {
                        val result = processor.process(uri, studioConfig, currentTint) { p ->
                            processingProgress = p
                        }
                        isProcessing = false
                        Toast.makeText(
                            context,
                            if (result != null) "✓ Video saved to gallery!" else "Processing failed",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FilterNameRow(name: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xD9000000))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "◀",
            color = Color(0xFFCCCCCC),
            fontSize = 18.sp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onPrev)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Text(
            name,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 2.sp
        )
        Text(
            "▶",
            color = Color(0xFFCCCCCC),
            fontSize = 18.sp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onNext)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SideButton(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(58.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A))
                .border(1.dp, Color(0xFF383838), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color(0xFF888888), fontSize = 8.sp, fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ModeTab(label: String, icon: ImageVector, isActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isActive) Color(0xFF1E1E1E) else Color.Transparent)
            .border(1.dp, if (isActive) Color(0xFF505050) else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = label,
            tint = if (isActive) Color.White else Color(0xFF666666),
            modifier = Modifier.size(14.dp))
        Text(label,
            color = if (isActive) Color.White else Color(0xFF666666),
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OSD Edit Overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OsdEditOverlay(
    config: VhsOsdConfig,
    onUpdate: (VhsOsdConfig) -> Unit,
    onClose: () -> Unit
) {
    var title by remember(config) { mutableStateOf(config.customTitle) }
    var date by remember(config) { mutableStateOf(config.customDateText) }
    var noise by remember(config) { mutableFloatStateOf(config.noiseIntensity) }
    var tracking by remember(config) { mutableFloatStateOf(config.trackingDistortion) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.87f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color(0xFF141420), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF2A2A3A), RoundedCornerShape(16.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("OSD OVERLAY", color = Color.White, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(18.dp))

            OutlinedTextField(value = title, onValueChange = { title = it },
                label = { Text("Title / Label") }, singleLine = true,
                colors = sheetFieldColors(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = date, onValueChange = { date = it },
                label = { Text("Date (e.g. OCT. 14 1995)") }, singleLine = true,
                colors = sheetFieldColors(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))

            SliderRow("Grain Noise", noise, Color(0xFF00E5FF)) { noise = it }
            Spacer(Modifier.height(8.dp))
            SliderRow("Tracking Glitch", tracking, Color(0xFFE040FB)) { tracking = it }

            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252535))) {
                    Text("Cancel", color = Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Button(onClick = {
                    onUpdate(config.copy(customTitle = title, customDateText = date,
                        noiseIntensity = noise, trackingDistortion = tracking))
                    onClose()
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, colour: Color, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ${(value * 100).toInt()}%",
            color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.width(160.dp))
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = colour, activeTrackColor = colour))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Post-Processing Studio Overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StudioOverlay(
    config: VideoStudioConfig,
    onConfigChange: (VideoStudioConfig) -> Unit,
    onClose: () -> Unit,
    onProcess: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .background(Color(0xFF141420), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF2A2A3A), RoundedCornerShape(16.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("POST-PROCESSING STUDIO", color = Color.White, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(4.dp))
            Text("Effects are baked into your video", color = Color(0xFF666666),
                fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(18.dp))

            // INTRO
            StudioSectionLabel("INTRO SEQUENCE", Color(0xFF00E5FF))
            SelectorGrid(VhsIntroType.values().toList(), config.introType, { it.label }, Color(0xFF00E5FF)) {
                onConfigChange(config.copy(introType = it))
            }
            Spacer(Modifier.height(12.dp))

            // OUTRO
            StudioSectionLabel("OUTRO SEQUENCE", Color(0xFFFFB300))
            SelectorGrid(VhsOutroType.values().toList(), config.outroType, { it.label }, Color(0xFFFFB300)) {
                onConfigChange(config.copy(outroType = it))
            }
            Spacer(Modifier.height(12.dp))

            // TRANSITION
            StudioSectionLabel("GLITCH TRANSITION", Color(0xFFE040FB))
            SelectorGrid(VhsTransitionEffect.values().toList(), config.transitionEffect, { it.label }, Color(0xFFE040FB)) {
                onConfigChange(config.copy(transitionEffect = it))
            }

            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252535))) {
                    Text("Cancel", color = Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Button(onClick = onProcess,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))) {
                    Text("Process & Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StudioSectionLabel(text: String, colour: Color) {
    Text(text, color = colour, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace, letterSpacing = 1.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
}

@Composable
private fun <T> SelectorGrid(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    activeColour: Color,
    onSelect: (T) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            val isSelected = item == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) activeColour else Color(0xFF1E1E2E))
                    .border(1.dp, if (isSelected) activeColour else Color(0xFF303040), RoundedCornerShape(8.dp))
                    .clickable { onSelect(item) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label(item),
                    color = if (isSelected) Color.Black else Color(0xFFAAAAAA),
                    fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}

@Composable
private fun sheetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF00E5FF),
    unfocusedBorderColor = Color(0xFF3A3A5A),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Color(0xFF00E5FF),
    unfocusedLabelColor = Color(0xFF888888)
)
