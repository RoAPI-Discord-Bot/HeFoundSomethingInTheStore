package com.hefoundsomethinginthestore.vhs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.hefoundsomethinginthestore.vhs.model.VhsOsdConfig
import com.hefoundsomethinginthestore.vhs.model.VhsTint
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun VhsFilterOverlay(
    tint: VhsTint,
    osdConfig: VhsOsdConfig,
    isRecording: Boolean,
    recordingTimeSeconds: Long,
    modifier: Modifier = Modifier
) {
    var frameTick by remember { mutableLongStateOf(0L) }
    val textMeasurer = rememberTextMeasurer()

    // Continuous tick for animatable VHS noise, CRT scanline jitter, and glitch lines
    LaunchedEffect(Unit) {
        while (true) {
            frameTick++
            delay(40) // ~25 fps VHS frame rate simulation
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 1. Draw Color Tint Matrix Overlay
        drawTintOverlay(tint, width, height)

        // 2. Draw CRT Scanlines
        drawCrtScanlines(width, height, frameTick)

        // 3. Draw Tape Tracking & Noise Distortions
        drawVhsNoiseAndTracking(width, height, frameTick, osdConfig.noiseIntensity, osdConfig.trackingDistortion)

        // 4. Draw OSD Camcorder Vintage HUD (Text & Icons)
        drawVhsHud(
            width = width,
            height = height,
            tint = tint,
            osdConfig = osdConfig,
            isRecording = isRecording,
            recordingTimeSeconds = recordingTimeSeconds,
            frameTick = frameTick,
            textMeasurer = textMeasurer
        )
    }
}

private fun DrawScope.drawTintOverlay(tint: VhsTint, width: Float, height: Float) {
    when (tint) {
        VhsTint.STANDARD -> {
            // Subtle warm VHS amber glow
            drawRect(color = Color(0x1AEE9900), size = Size(width, height))
        }
        VhsTint.NIGHT_VISION -> {
            // Night vision green CRT phosphor fill + brightness tint
            drawRect(color = Color(0x8800FF44), size = Size(width, height))
            drawRect(color = Color(0x33003300), size = Size(width, height))
        }
        VhsTint.WARM_SEPIA -> {
            // Nostalgic golden sepia tone
            drawRect(color = Color(0x558B5A2B), size = Size(width, height))
        }
        VhsTint.MONO_BW -> {
            // Desaturated monochrome dark contrast tint
            drawRect(color = Color(0x33101010), size = Size(width, height))
        }
        VhsTint.CYBER_BLUE -> {
            // Cool low-light blue cam tint
            drawRect(color = Color(0x440099FF), size = Size(width, height))
        }
        VhsTint.BACKROOMS -> {
            // Liminal space fluorescent yellow-green tint
            drawRect(color = Color(0x55CCCC44), size = Size(width, height))
        }
    }
}

private fun DrawScope.drawCrtScanlines(width: Float, height: Float, frameTick: Long) {
    val scanlineStep = 6.0f
    val scanlineAlpha = 0.12f
    var y = 0.0f

    // Alternate scanline offset slightly for interlaced feel
    val offset = if (frameTick % 2L == 0L) 0.0f else 3.0f

    while (y < height) {
        drawLine(
            color = Color.Black.copy(alpha = scanlineAlpha),
            start = Offset(0f, y + offset),
            end = Offset(width, y + offset),
            strokeWidth = 2.0f
        )
        y += scanlineStep
    }
}

private fun DrawScope.drawVhsNoiseAndTracking(
    width: Float,
    height: Float,
    frameTick: Long,
    noiseIntensity: Float,
    trackingDistortion: Float
) {
    val rng = Random(frameTick)

    // Random Static Grain Particles
    val numParticles = (300 * noiseIntensity).toInt()
    for (i in 0 until numParticles) {
        val px = rng.nextFloat() * width
        val py = rng.nextFloat() * height
        val pAlpha = rng.nextFloat() * 0.4f
        drawCircle(
            color = if (rng.nextBoolean()) Color.White.copy(alpha = pAlpha) else Color.Black.copy(alpha = pAlpha),
            radius = rng.nextFloat() * 1.8f,
            center = Offset(px, py)
        )
    }

    // Tracking Glitch Band
    val effectiveTracking = (trackingDistortion + (if (rng.nextFloat() < 0.08f) 0.4f else 0.0f)).coerceIn(0f, 1f)
    if (effectiveTracking > 0.05f) {
        val glitchHeight = (height * 0.08f * effectiveTracking)
        val glitchY = (frameTick * 12.0f + rng.nextFloat() * height) % height

        // Horizontal noise bar
        drawRect(
            color = Color.White.copy(alpha = 0.35f * effectiveTracking),
            topLeft = Offset(0f, glitchY),
            size = Size(width, glitchHeight)
        )

        // RGB Chroma Shift Lines inside glitch band
        drawLine(
            color = Color.Red.copy(alpha = 0.6f * effectiveTracking),
            start = Offset(-15f, glitchY + 5f),
            end = Offset(width - 15f, glitchY + 5f),
            strokeWidth = 3f
        )
        drawLine(
            color = Color.Cyan.copy(alpha = 0.6f * effectiveTracking),
            start = Offset(15f, glitchY + 12f),
            end = Offset(width + 15f, glitchY + 12f),
            strokeWidth = 3f
        )
    }
}

private fun DrawScope.drawVhsHud(
    width: Float,
    height: Float,
    tint: VhsTint,
    osdConfig: VhsOsdConfig,
    isRecording: Boolean,
    recordingTimeSeconds: Long,
    frameTick: Long,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val fontColor = if (tint == VhsTint.NIGHT_VISION) Color(0xFF00FF66) else Color.White
    val textStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = fontColor
    )

    val shadowStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = Color.Black.copy(alpha = 0.8f)
    )

    // Helper for OSD text with retro black drop shadow
    fun drawOsdText(text: String, x: Float, y: Float) {
        val layoutResult = textMeasurer.measure(text, textStyle)
        // Shadow
        drawText(textMeasurer, text, Offset(x + 2f, y + 2f), shadowStyle)
        // Main Text
        drawText(textMeasurer, text, Offset(x, y), textStyle)
    }

    // Top-Left: Play / Record Status Indicator
    if (osdConfig.showPlayIndicator) {
        val statusText = if (isRecording) {
            val blink = (frameTick % 20L < 10L)
            if (blink) "REC ●" else "REC  "
        } else {
            "PLAY  ▶"
        }
        drawOsdText(statusText, 32f, 32f)
    }

    // Top-Right: SP / SLP Mode & Battery Level
    val modeText = "${osdConfig.speed.label}   [||||]"
    val modeWidth = textMeasurer.measure(modeText, textStyle).size.width
    drawOsdText(modeText, width - modeWidth - 32f, 32f)

    // Bottom-Left: Custom Date & Title Text (Backrooms Reference)
    val customTitle = osdConfig.customTitle.ifEmpty { "HE FOUND SOMETHING" }
    drawOsdText(customTitle, 32f, height - 90f)

    val dateText = osdConfig.customDateText.ifEmpty { "OCT. 14 1995" }
    drawOsdText(dateText, 32f, height - 50f)

    // Bottom-Right: Timecode (0:00:00)
    if (osdConfig.showTimestamp) {
        val hours = recordingTimeSeconds / 3600
        val mins = (recordingTimeSeconds % 3600) / 60
        val secs = recordingTimeSeconds % 60
        val timecode = String.format("%d:%02d:%02d", hours, mins, secs)

        val timecodeWidth = textMeasurer.measure(timecode, textStyle).size.width
        drawOsdText(timecode, width - timecodeWidth - 32f, height - 50f)
    }
}
