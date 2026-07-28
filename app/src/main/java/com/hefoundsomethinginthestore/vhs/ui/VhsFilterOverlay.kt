package com.hefoundsomethinginthestore.vhs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import kotlin.math.sqrt
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

    LaunchedEffect(Unit) {
        while (true) {
            frameTick++
            delay(33L) // ~30fps
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Strong colour tint overlay (much more visible than before)
        if (tint != VhsTint.BLANK) {
            drawTintOverlay(tint, w, h)
        }

        // 2. Heavy CRT scanlines — visible dark bands every 4px
        drawScanlines(w, h, frameTick)

        // 3. VHS grain & tracking distortion
        val noiseLevel = if (tint == VhsTint.GLITCH_MAX) 1.0f else osdConfig.noiseIntensity
        val trackLevel = if (tint == VhsTint.GLITCH_MAX) 0.95f else osdConfig.trackingDistortion
        drawGrainAndTracking(w, h, frameTick, noiseLevel, trackLevel)

        // 4. Vignette — dark corners like a real VHS camcorder lens
        drawVignette(w, h)

        // 5. OSD elements
        drawOsd(w, h, tint, osdConfig, isRecording, recordingTimeSeconds, frameTick, textMeasurer)
    }
}

private fun DrawScope.drawTintOverlay(tint: VhsTint, w: Float, h: Float) {
    val sz = Size(w, h)
    when (tint) {
        VhsTint.BLANK -> Unit
        VhsTint.STANDARD -> {
            // Warm amber VHS tape look – strong enough to clearly see
            drawRect(color = Color(0x44CC8800), size = sz)          // amber
            drawRect(color = Color(0x22000000), size = sz)          // slight darkening
        }
        VhsTint.NIGHT_VISION -> {
            drawRect(color = Color(0xCC003300), size = sz)          // heavy dark green base
            drawRect(color = Color(0x6600FF44), size = sz)          // bright green overlay
        }
        VhsTint.GLITCH_MAX -> {
            drawRect(color = Color(0x55FF0055), size = sz)
            drawRect(color = Color(0x33000000), size = sz)
        }
        VhsTint.WARM_SEPIA -> {
            drawRect(color = Color(0x668B6020), size = sz)
            drawRect(color = Color(0x22AA7733), size = sz)
        }
        VhsTint.MONO_BW -> {
            // Greyscale effect: darken and desaturate
            drawRect(color = Color(0x88222222), size = sz)
        }
        VhsTint.CYBER_BLUE -> {
            drawRect(color = Color(0x550044CC), size = sz)
            drawRect(color = Color(0x22000033), size = sz)
        }
        VhsTint.LIMINAL_YELLOW -> {
            drawRect(color = Color(0x66AAAA00), size = sz)
            drawRect(color = Color(0x22555500), size = sz)
        }
    }
}

private fun DrawScope.drawScanlines(w: Float, h: Float, tick: Long) {
    // Dark horizontal bands every 4px — very visible, signature VHS look
    val offset = if (tick % 2L == 0L) 0f else 2f
    var y = offset
    while (y < h) {
        drawLine(
            color = Color.Black.copy(alpha = 0.30f),
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 3f
        )
        y += 4f
    }
}

private fun DrawScope.drawGrainAndTracking(
    w: Float, h: Float,
    tick: Long,
    noiseIntensity: Float,
    trackingDistortion: Float
) {
    val rng = Random(tick)

    // Dense grain — 1200 particles at full noise
    val particleCount = (1200 * noiseIntensity).toInt()
    repeat(particleCount) {
        val px = rng.nextFloat() * w
        val py = rng.nextFloat() * h
        val alpha = rng.nextFloat() * 0.65f
        val white = rng.nextBoolean()
        drawCircle(
            color = (if (white) Color.White else Color.Black).copy(alpha = alpha),
            radius = rng.nextFloat() * 2.5f,
            center = Offset(px, py)
        )
    }

    // Occasional horizontal pixel dropout lines
    if (rng.nextFloat() < noiseIntensity * 0.4f) {
        val lineY = rng.nextFloat() * h
        val lineW = rng.nextFloat() * w * 0.6f + w * 0.1f
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(rng.nextFloat() * (w - lineW), lineY),
            end = Offset(rng.nextFloat() * (w - lineW) + lineW, lineY),
            strokeWidth = 1.5f
        )
    }

    // VHS tracking bar — triggered randomly or by trackingDistortion slider
    val effectiveTracking = (trackingDistortion + (if (rng.nextFloat() < 0.06f) 0.55f else 0f)).coerceIn(0f, 1f)
    if (effectiveTracking > 0.05f) {
        val barH = h * 0.10f * effectiveTracking
        val barY = (tick * 18f + rng.nextFloat() * h) % (h + barH) - barH

        drawRect(
            color = Color.White.copy(alpha = 0.45f * effectiveTracking),
            topLeft = Offset(0f, barY),
            size = Size(w, barH)
        )
        // RGB chroma split lines within the tracking bar
        drawLine(color = Color.Red.copy(alpha = 0.8f * effectiveTracking),
            start = Offset(-25f, barY + 6f), end = Offset(w - 25f, barY + 6f), strokeWidth = 5f)
        drawLine(color = Color.Cyan.copy(alpha = 0.8f * effectiveTracking),
            start = Offset(25f, barY + 16f), end = Offset(w + 25f, barY + 16f), strokeWidth = 5f)
    }
}

private fun DrawScope.drawVignette(w: Float, h: Float) {
    // Radial dark vignette at corners — authentic camcorder lens look
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.50f)),
            center = Offset(w / 2f, h / 2f),
            radius = (w.coerceAtLeast(h)) * 0.72f
        ),
        size = Size(w, h)
    )
}

private fun DrawScope.drawOsd(
    w: Float, h: Float,
    tint: VhsTint,
    osdConfig: VhsOsdConfig,
    isRecording: Boolean,
    recordingTimeSecs: Long,
    tick: Long,
    measurer: androidx.compose.ui.text.TextMeasurer
) {
    val fontColor = if (tint == VhsTint.NIGHT_VISION) Color(0xFF00FF66) else Color.White
    val osdStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = fontColor
    )
    val shadowStyle = osdStyle.copy(color = Color.Black.copy(alpha = 0.9f))

    fun drawOsdText(text: String, x: Float, y: Float) {
        drawText(measurer, text, Offset(x + 2f, y + 2f), shadowStyle)
        drawText(measurer, text, Offset(x, y), osdStyle)
    }

    // Top-left: PLAY ▶  or  ● REC (blinking dot)
    val recBlink = tick % 18L < 9L
    val topLeft = when {
        isRecording && recBlink -> "● REC"
        isRecording -> "  REC"
        else -> "▶  PLAY"
    }
    drawOsdText(topLeft, 28f, 28f)

    // Top-right: speed mode + battery glyph
    val batteryText = "${osdConfig.speed.label}  ▐███▌"
    val battMeasured = measurer.measure(batteryText, osdStyle)
    drawOsdText(batteryText, w - battMeasured.size.width - 28f, 28f)

    // Bottom-left: custom title + date
    val title = osdConfig.customTitle.ifEmpty { "RARE-VHS 90S" }
    drawOsdText(title, 28f, h - 100f)
    val date = osdConfig.customDateText.ifEmpty { "DEC. 31  '99" }
    drawOsdText(date, 28f, h - 64f)

    // Bottom-right: real-time timecode
    if (osdConfig.showTimestamp) {
        val h2 = recordingTimeSecs / 3600
        val m2 = (recordingTimeSecs % 3600) / 60
        val s2 = recordingTimeSecs % 60
        val tc = "%d:%02d:%02d".format(h2, m2, s2)
        val tcMeasured = measurer.measure(tc, osdStyle)
        drawOsdText(tc, w - tcMeasured.size.width - 28f, h - 64f)
    }
}
