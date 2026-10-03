package com.phlogistonistic.radiometry.identify

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * Identifies music on a stream that doesn't publish its own metadata: pulls ~30 s of the raw
 * stream, decodes it, and queries overlapping windows newest-first so one window landing on an
 * intro, breakdown, or crossfade doesn't sink the attempt.
 */
class SongIdentifier(private val context: Context, private val matcher: SongMatcher) {

    suspend fun identify(streamUrl: String): MatchOutcome = withContext(Dispatchers.IO) {
        matcher.unavailableReason?.let { return@withContext MatchOutcome.Unavailable(it) }
        if (streamUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            return@withContext MatchOutcome.Unavailable("HLS streams can't be sampled for identification.")
        }
        val file = File.createTempFile("radiometry-sample", ".bin", context.cacheDir)
        try {
            capture(streamUrl, file)
            val audio = decodeToMono(file)
            Log.i(TAG, "decoded %.1fs @ %d Hz".format(audio.seconds, audio.sampleRate))
            if (audio.seconds < 4) return@withContext MatchOutcome.Error(IllegalStateException("Not enough audio"))

            val window = minOf(WINDOW_SECONDS, matcher.maxQuerySeconds)
            for (clip in audio.windows(window, WINDOW_STEP_SECONDS)) {
                coroutineContext.ensureActive()
                when (val outcome = matcher.match(clip, audio.sampleRate)) {
                    MatchOutcome.NoMatch -> Log.i(TAG, "no match for window")
                    else -> return@withContext outcome
                }
            }
            MatchOutcome.NoMatch
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Log.w(TAG, "identify failed", e)
            MatchOutcome.Error(e)
        } finally {
            file.delete()
        }
    }

    private suspend fun capture(streamUrl: String, into: File) {
        val connection = URL(streamUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            // Shoutcast/Icecast advertise bitrate in kbps; use it to know when we have enough.
            val kbps = connection.getHeaderField("icy-br")?.substringBefore(',')?.trim()?.toIntOrNull()
            val target = kbps?.let { (it * 1000 / 8 * (SAMPLE_SECONDS + 2)).toInt() }?.coerceAtMost(MAX_BYTES) ?: MAX_BYTES
            val start = System.currentTimeMillis()
            var total = 0
            connection.inputStream.use { input ->
                into.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (total < target && System.currentTimeMillis() - start < MAX_WALL_MS) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        total += read
                    }
                }
            }
            Log.i(TAG, "captured $total bytes (${connection.contentType}) in ${System.currentTimeMillis() - start} ms")
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeToMono(file: File): MonoAudio {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("No audio track in stream sample")
        extractor.selectTrack(track)
        val inputFormat = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        val mono = ShortArrayBuilder()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val size = extractor.readSampleData(codec.getInputBuffer(inIndex)!!, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.LITTLE_ENDIAN)
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        mono.appendDownmixed(buffer, channels, floatPcm)
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }
        return MonoAudio(mono.toArray(), sampleRate).resampledToSupportedRate()
    }

    private class MonoAudio(val samples: ShortArray, val sampleRate: Int) {
        val seconds: Double get() = samples.size.toDouble() / sampleRate

        /** Overlapping windows as 16-bit LE PCM, newest first so a match reflects what's on air now. */
        fun windows(windowSeconds: Double, stepSeconds: Double): List<ByteArray> {
            val length = minOf(samples.size, (sampleRate * windowSeconds).toInt())
            val step = (sampleRate * stepSeconds).toInt()
            return generateSequence(samples.size - length) { it - step }
                .takeWhile { it >= 0 }
                .map { start ->
                    val bytes = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in start until start + length) bytes.putShort(samples[i])
                    bytes.array()
                }
                .toList()
        }

        /** ShazamKit accepts 48, 44.1, 32 or 16 kHz; linearly resample anything else to 44.1 kHz. */
        fun resampledToSupportedRate(): MonoAudio {
            if (sampleRate in SUPPORTED_RATES) return this
            val target = 44_100
            val out = ShortArray((samples.size.toLong() * target / sampleRate).toInt())
            for (i in out.indices) {
                val pos = i.toDouble() * sampleRate / target
                val a = pos.toInt().coerceAtMost(samples.size - 1)
                val b = (a + 1).coerceAtMost(samples.size - 1)
                val t = pos - a
                out[i] = (samples[a] * (1 - t) + samples[b] * t).toInt().toShort()
            }
            return MonoAudio(out, target)
        }
    }

    private class ShortArrayBuilder {
        private var data = ShortArray(44_100 * 40)
        private var size = 0

        fun appendDownmixed(buffer: ByteBuffer, channels: Int, floatPcm: Boolean) {
            val bytesPerSample = if (floatPcm) 4 else 2
            val frames = buffer.remaining() / (bytesPerSample * channels)
            ensureCapacity(size + frames)
            repeat(frames) {
                var sum = 0.0
                repeat(channels) {
                    sum += if (floatPcm) buffer.float.toDouble() * Short.MAX_VALUE else buffer.short.toDouble()
                }
                data[size++] = (sum / channels).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        private fun ensureCapacity(needed: Int) {
            if (needed > data.size) data = data.copyOf(maxOf(needed, data.size * 2))
        }

        fun toArray(): ShortArray = data.copyOf(size)
    }

    private companion object {
        const val TAG = "RadiometryIdentify"
        const val SAMPLE_SECONDS = 30.0
        const val WINDOW_SECONDS = 12.0
        const val WINDOW_STEP_SECONDS = 6.0
        const val MAX_WALL_MS = 40_000L
        const val MAX_BYTES = 4_000_000
        val SUPPORTED_RATES = setOf(48_000, 44_100, 32_000, 16_000)
    }
}
