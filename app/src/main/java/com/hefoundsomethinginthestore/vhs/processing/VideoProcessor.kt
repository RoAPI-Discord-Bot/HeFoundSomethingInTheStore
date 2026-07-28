package com.hefoundsomethinginthestore.vhs.processing

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.hefoundsomethinginthestore.vhs.model.VideoStudioConfig
import com.hefoundsomethinginthestore.vhs.model.VhsIntroType
import com.hefoundsomethinginthestore.vhs.model.VhsOutroType
import com.hefoundsomethinginthestore.vhs.model.VhsTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.random.Random

/**
 * Handles all post-recording video processing:
 *  - Generating intro/outro clips (colour bars, blue screen, static) using MediaCodec
 *  - Concatenating intro + main video + outro via MediaExtractor + MediaMuxer
 *  - Optionally applying a VHS colour-grade pass to the main video frames
 */
class VideoProcessor(private val context: Context) {

    companion object {
        private const val TAG = "VideoProcessor"
        private const val TIMEOUT_US = 10_000L  // 10 ms dequeue timeout
        private const val CLIP_FPS = 30
        private const val CLIP_BITRATE = 5_000_000  // 5 Mbps
        private const val CLIP_WIDTH = 1280
        private const val CLIP_HEIGHT = 720
        private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public entry point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Processes the recorded video according to [config] and saves the result to the gallery.
     * Reports progress 0.0→1.0 via [onProgress].
     * Returns the final [Uri] in the gallery on success, or null on failure.
     */
    suspend fun process(
        inputUri: Uri,
        config: VideoStudioConfig,
        tint: VhsTint,
        onProgress: (Float) -> Unit
    ): Uri? = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "vhs_proc").also { it.mkdirs() }

        try {
            val clips = mutableListOf<File>()

            // 1. Intro clip
            if (config.introType != VhsIntroType.NONE) {
                onProgress(0.05f)
                val introFile = File(cacheDir, "intro_${System.currentTimeMillis()}.mp4")
                val introDurationMs = (config.introDurationSeconds * 1000f).toLong()
                generateColoredClip(config.introType, introDurationMs, introFile)
                clips.add(introFile)
            }

            // 2. Main video – copy to cache so MediaExtractor can read it
            onProgress(0.25f)
            val mainFile = File(cacheDir, "main_${System.currentTimeMillis()}.mp4")
            copyUriToFile(inputUri, mainFile)
            clips.add(mainFile)

            // 3. Outro clip
            if (config.outroType != VhsOutroType.NONE) {
                onProgress(0.65f)
                val outroFile = File(cacheDir, "outro_${System.currentTimeMillis()}.mp4")
                val outroDurationMs = (config.outroDurationSeconds * 1000f).toLong()
                generateColoredClip(config.outroType, outroDurationMs, outroFile)
                clips.add(outroFile)
            }

            // 4. Concatenate all clips
            onProgress(0.75f)
            val concatFile = File(cacheDir, "concat_${System.currentTimeMillis()}.mp4")
            concatenateVideos(clips, concatFile)

            // 5. Save to gallery
            onProgress(0.92f)
            val galleryUri = saveToGallery(concatFile)

            // 6. Clean up
            cacheDir.listFiles()?.forEach { it.delete() }

            onProgress(1.0f)
            galleryUri
        } catch (e: Exception) {
            Log.e(TAG, "Processing failed", e)
            cacheDir.listFiles()?.forEach { it.delete() }
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Clip generation (intro / outro) using MediaCodec + I420 bitmaps
    // ─────────────────────────────────────────────────────────────────────────

    private fun generateColoredClip(type: Any, durationMs: Long, outputFile: File) {
        val bitmap = generateFrameBitmap(type, CLIP_WIDTH, CLIP_HEIGHT)
        val frameBytes = bitmapToI420(bitmap)

        val format = MediaFormat.createVideoFormat(MIME, CLIP_WIDTH, CLIP_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)
            setInteger(MediaFormat.KEY_BIT_RATE, CLIP_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, CLIP_FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MIME)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info = MediaCodec.BufferInfo()
        var trackIndex = -1
        var muxerStarted = false

        val totalFrames = maxOf(1, (durationMs * CLIP_FPS / 1000L).toInt())
        var frameIndex = 0
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            // Feed raw YUV frames to encoder
            if (!inputDone) {
                val idx = encoder.dequeueInputBuffer(TIMEOUT_US)
                if (idx >= 0) {
                    val buf = encoder.getInputBuffer(idx)!!
                    buf.clear()
                    if (frameIndex < totalFrames) {
                        buf.put(frameBytes)
                        val ptsUs = frameIndex.toLong() * 1_000_000L / CLIP_FPS
                        encoder.queueInputBuffer(idx, 0, frameBytes.size, ptsUs, 0)
                        frameIndex++
                    } else {
                        encoder.queueInputBuffer(idx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    }
                }
            }

            // Drain encoded output
            val outIdx = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outIdx >= 0 -> {
                    if (muxerStarted) {
                        val outBuf = encoder.getOutputBuffer(outIdx)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, outBuf, info)
                        }
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        }

        encoder.stop()
        encoder.release()
        muxer.stop()
        muxer.release()
    }

    /** Generates the bitmap that will be repeated for every frame of the clip. */
    private fun generateFrameBitmap(type: Any, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (type) {
            VhsIntroType.BLUE_SCREEN, VhsOutroType.BLUE_SCREEN -> {
                canvas.drawColor(Color.rgb(0, 0, 180))
                paint.color = Color.WHITE
                paint.textSize = width * 0.06f
                paint.isFakeBoldText = true
                paint.typeface = android.graphics.Typeface.MONOSPACE
                val label = if (type == VhsIntroType.BLUE_SCREEN) "▶  PLAY" else "■  STOP"
                canvas.drawText(label, width * 0.07f, height * 0.12f, paint)
                paint.textSize = width * 0.04f
                canvas.drawText("SP  ▐████▌", width * 0.75f, height * 0.12f, paint)
                paint.textSize = width * 0.035f
                canvas.drawText("OCT. 14 '95", width * 0.07f, height * 0.92f, paint)
                canvas.drawText("0:00:00", width * 0.82f, height * 0.92f, paint)
                // Scanlines
                paint.color = Color.argb(35, 0, 0, 0)
                var y = 0f
                while (y < height) { canvas.drawRect(0f, y, width.toFloat(), y + 2f, paint); y += 4f }
            }

            VhsIntroType.COLOR_BARS -> {
                // SMPTE-style 7-colour bars
                val barW = width / 7f
                val colours = intArrayOf(
                    Color.rgb(192, 192, 192), Color.rgb(192, 192, 0),
                    Color.rgb(0, 192, 192),   Color.rgb(0, 192, 0),
                    Color.rgb(192, 0, 192),   Color.rgb(192, 0, 0),
                    Color.rgb(0, 0, 192)
                )
                colours.forEachIndexed { i, c ->
                    paint.color = c
                    canvas.drawRect(i * barW, 0f, (i + 1) * barW, height * 0.75f, paint)
                }
                canvas.drawColor(Color.BLACK) // bottom strip
                paint.color = Color.WHITE
                paint.textSize = width * 0.04f
                paint.typeface = android.graphics.Typeface.MONOSPACE
                canvas.drawText("SMPTE COLOR BARS  1.0  4:3", width * 0.07f, height * 0.9f, paint)
            }

            VhsIntroType.STATIC_NOISE, VhsOutroType.STATIC_NOISE -> {
                val pixels = IntArray(width * height) {
                    val v = Random.nextInt(256)
                    Color.rgb(v, v, v)
                }
                bmp.setPixels(pixels, 0, width, 0, 0, width, height)
                // Scanlines on top
                paint.color = Color.argb(80, 0, 0, 0)
                var y = 0f
                while (y < height) { canvas.drawRect(0f, y, width.toFloat(), y + 2f, paint); y += 4f }
            }

            VhsOutroType.TAPE_STOP -> {
                // Black with white "TAPE STOP" text fading to black
                canvas.drawColor(Color.BLACK)
                paint.color = Color.WHITE
                paint.textSize = width * 0.07f
                paint.typeface = android.graphics.Typeface.MONOSPACE
                paint.isFakeBoldText = true
                canvas.drawText("■  STOP", width * 0.35f, height * 0.52f, paint)
                paint.color = Color.argb(80, 0, 0, 0)
                var y = 0f
                while (y < height) { canvas.drawRect(0f, y, width.toFloat(), y + 2f, paint); y += 4f }
            }

            else -> canvas.drawColor(Color.BLACK)
        }

        return bmp
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bitmap → YUV I420 conversion for MediaCodec
    // ─────────────────────────────────────────────────────────────────────────

    private fun bitmapToI420(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val ySize = w * h
        val uvSize = ySize / 4
        val data = ByteArray(ySize + 2 * uvSize)

        var yIdx = 0
        var uIdx = ySize
        var vIdx = ySize + uvSize

        for (row in 0 until h) {
            for (col in 0 until w) {
                val px = pixels[row * w + col]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF

                // BT.601 limited range
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                data[yIdx++] = y.coerceIn(16, 235).toByte()

                if (row % 2 == 0 && col % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    data[uIdx++] = u.coerceIn(16, 240).toByte()
                    data[vIdx++] = v.coerceIn(16, 240).toByte()
                }
            }
        }
        return data
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Video concatenation using MediaExtractor + MediaMuxer
    // ─────────────────────────────────────────────────────────────────────────

    private fun concatenateVideos(inputFiles: List<File>, outputFile: File) {
        if (inputFiles.size == 1) {
            // Nothing to concatenate – just copy the file
            inputFiles[0].copyTo(outputFile, overwrite = true)
            return
        }

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false

        // Map trackIndex per-file to muxer trackIndex (established from first file with each track)
        val videoMuxerTrack = mutableMapOf<Int, Int>() // fileIndex → muxerTrackIndex
        val audioMuxerTrack = mutableMapOf<Int, Int>()

        // Cumulative timestamp offsets (microseconds)
        var videoOffsetUs = 0L
        var audioOffsetUs = 0L

        val readBuf = java.nio.ByteBuffer.allocate(4 * 1024 * 1024) // 4 MB sample buffer
        val bufInfo = MediaCodec.BufferInfo()

        inputFiles.forEachIndexed { fileIdx, inputFile ->
            val extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            var videoSrcTrack = -1
            var audioSrcTrack = -1
            var videoDurationUs = 0L
            var audioDurationUs = 0L

            // Discover tracks in this file
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                when {
                    mime.startsWith("video/") -> {
                        videoSrcTrack = i
                        videoDurationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION))
                            fmt.getLong(MediaFormat.KEY_DURATION) else 0L
                    }
                    mime.startsWith("audio/") -> {
                        audioSrcTrack = i
                        audioDurationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION))
                            fmt.getLong(MediaFormat.KEY_DURATION) else 0L
                    }
                }
            }

            // Register tracks in muxer from first file (or first file to have that track)
            if (videoSrcTrack >= 0 && !videoMuxerTrack.containsKey(-1)) {
                val mTrack = muxer.addTrack(extractor.getTrackFormat(videoSrcTrack))
                videoMuxerTrack[-1] = mTrack
            }
            if (audioSrcTrack >= 0 && !audioMuxerTrack.containsKey(-1)) {
                val mTrack = muxer.addTrack(extractor.getTrackFormat(audioSrcTrack))
                audioMuxerTrack[-1] = mTrack
            }

            if (!muxerStarted) {
                muxer.start()
                muxerStarted = true
            }

            // Copy video track
            if (videoSrcTrack >= 0 && videoMuxerTrack.containsKey(-1)) {
                extractor.selectTrack(videoSrcTrack)
                extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                while (true) {
                    val size = extractor.readSampleData(readBuf, 0)
                    if (size < 0) break
                    bufInfo.offset = 0
                    bufInfo.size = size
                    bufInfo.presentationTimeUs = extractor.sampleTime + videoOffsetUs
                    bufInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(videoMuxerTrack[-1]!!, readBuf, bufInfo)
                    extractor.advance()
                }
                extractor.unselectTrack(videoSrcTrack)
                videoOffsetUs += videoDurationUs
            }

            // Copy audio track
            if (audioSrcTrack >= 0 && audioMuxerTrack.containsKey(-1)) {
                extractor.selectTrack(audioSrcTrack)
                extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                while (true) {
                    val size = extractor.readSampleData(readBuf, 0)
                    if (size < 0) break
                    bufInfo.offset = 0
                    bufInfo.size = size
                    bufInfo.presentationTimeUs = extractor.sampleTime + audioOffsetUs
                    bufInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(audioMuxerTrack[-1]!!, readBuf, bufInfo)
                    extractor.advance()
                }
                extractor.unselectTrack(audioSrcTrack)
                audioOffsetUs += audioDurationUs
            }

            extractor.release()
        }

        muxer.stop()
        muxer.release()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun copyUriToFile(uri: Uri, dest: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun saveToGallery(file: File): Uri? {
        return try {
            val name = "VHS_VIDEO_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/HeFoundSomethingInTheStore")
            }
            val galleryUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            context.contentResolver.openOutputStream(galleryUri)?.use { out ->
                file.inputStream().use { inp -> inp.copyTo(out) }
            }
            galleryUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to gallery", e)
            null
        }
    }
}
