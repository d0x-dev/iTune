package com.darkxvenom.itune.share

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.share.recognition.ShazamSignatureGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class IdentifiedTrack(
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?
)

class SharedMusicIdentifier(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun identify(input: SharedMusicInput): IdentifiedTrack = withContext(Dispatchers.IO) {
        var temporaryFile: File? = null
        try {
            val mediaUri = when {
                input.uri != null -> input.uri
                !input.text.isNullOrBlank() -> {
                    temporaryFile = resolveSharedUrl(input.text)
                        ?: throw IllegalArgumentException(
                            "iTune couldn't access this link. Try sharing the video file itself, or use a direct public media link."
                        )
                    Uri.fromFile(temporaryFile)
                }
                else -> throw IllegalArgumentException("No audio, video, or supported link was shared.")
            }

            val pcm = decodeToMono16k(mediaUri)
            if (pcm.size < 16000 * 3) {
                throw IllegalArgumentException("The shared media is too short or contains no usable audio.")
            }

            val generator = ShazamSignatureGenerator()
            generator.feedPcm16Mono(pcm)
            val signature = generator.nextSignatureOrNull()
                ?: throw IllegalArgumentException("Couldn't create an audio fingerprint from this media.")

            recognizeWithShazam(signature.uri, signature.sampleDurationMs)
        } finally {
            temporaryFile?.delete()
        }
    }

    private suspend fun resolveSharedUrl(sharedUrl: String): File? {
        val url = sharedUrl.trim()
        val youtubeId = Regex("""(?:youtu\.be/|youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|live/|embed/))([A-Za-z0-9_-]{11})""")
            .find(url)?.groupValues?.getOrNull(1)

        val mediaUrl = if (youtubeId != null) {
            // Initialize NewPipe before using the app's existing YouTube stream resolver.
            MusicBackend.init(context.applicationContext)
            MusicBackend.resolveAudioStream(youtubeId)
                ?: return null
        } else {
            resolveDirectOrPageMedia(url) ?: return null
        }

        val request = Request.Builder()
            .url(mediaUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            .header("Accept", "*/*")
            .build()

        val file = File(context.cacheDir, "share_identify_${UUID.randomUUID()}.media")
        val success = runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body ?: return@use false
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 30L * 1024L * 1024L) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
                file.length() > 4096
            }
        }.getOrDefault(false)

        return if (success) file else {
            file.delete()
            null
        }
    }

    private fun resolveDirectOrPageMedia(url: String): String? {
        val lower = url.lowercase()
        if (lower.substringBefore('?').let {
                it.endsWith(".mp4") || it.endsWith(".m4a") || it.endsWith(".mp3") ||
                    it.endsWith(".webm") || it.endsWith(".wav") || it.endsWith(".ogg") ||
                    it.endsWith(".aac") || it.endsWith(".flac")
            }) return url

        // Public pages sometimes expose their playable media through Open Graph or HTML5 metadata.
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            .build()
        val html = runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()?.take(5 * 1024 * 1024)
            }
        }.getOrNull() ?: return null

        val patterns = listOf(
            Regex("""<meta[^>]+(?:property|name)=["'](?:og:video:secure_url|og:video|twitter:player:stream)["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<video[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""["'](?:video_url|playable_url|playback_url)["']\s*:\s*["']([^"']+\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE)
        )
        val candidate = patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }
            ?.replace("\\/", "/")
            ?.replace("&amp;", "&")
            ?.replace("\\u0026", "&")
            ?.takeIf { it.startsWith("http") && !it.startsWith("blob:") }
        return candidate
    }

    private fun decodeToMono16k(uri: Uri): ShortArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val pcmBytes = ByteArrayOutputStream()
        var sampleRate = 44100
        var channels = 2
        try {
            if (uri.scheme == "file") extractor.setDataSource(uri.path!!)
            else extractor.setDataSource(context, uri, null)

            var audioTrack = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    sampleRate = format.integerOr(MediaFormat.KEY_SAMPLE_RATE, 44100)
                    channels = format.integerOr(MediaFormat.KEY_CHANNEL_COUNT, 2)
                    break
                }
            }
            if (audioTrack < 0) throw IllegalArgumentException("This file doesn't contain an audio track.")

            extractor.selectTrack(audioTrack)
            val format = extractor.getTrackFormat(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("Unsupported audio format.")
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var outputBytes = 0L
            val maxOutputBytes = 12L * 1024L * 1024L

            while (!outputEnded && outputBytes < maxOutputBytes) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        inputBuffer.clear()
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        sampleRate = outputFormat.integerOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = outputFormat.integerOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                    }
                    outputIndex >= 0 -> {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            output.get(bytes)
                            pcmBytes.write(bytes)
                            outputBytes += bytes.size
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEnded = true
                    }
                }
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }

        val raw = pcmBytes.toByteArray()
        val frameSize = channels * 2
        val frames = raw.size / frameSize
        if (frames <= 0 || sampleRate <= 0) throw IllegalArgumentException("Couldn't decode audio from this media.")

        val mono = ShortArray(frames)
        for (frame in 0 until frames) {
            var sum = 0
            val base = frame * frameSize
            for (channel in 0 until channels) {
                val offset = base + channel * 2
                val sample = (raw[offset].toInt() and 0xff) or (raw[offset + 1].toInt() shl 8)
                sum += sample.toShort().toInt()
            }
            mono[frame] = (sum / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val outputCount = (frames.toDouble() * 16000.0 / sampleRate).roundToInt().coerceAtLeast(0)
        val resampled = ShortArray(outputCount)
        for (i in 0 until outputCount) {
            val sourcePosition = i.toDouble() * sampleRate / 16000.0
            val left = sourcePosition.toInt().coerceIn(0, frames - 1)
            val right = (left + 1).coerceAtMost(frames - 1)
            val fraction = sourcePosition - left
            resampled[i] = (mono[left] + (mono[right] - mono[left]) * fraction)
                .roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return resampled
    }

    private fun recognizeWithShazam(signature: String, durationMs: Long): IdentifiedTrack {
        val timestamp = System.currentTimeMillis() / 1000L
        val requestBody = JSONObject()
            .put("geolocation", JSONObject().put("altitude", 0).put("latitude", 0).put("longitude", 0))
            .put("signature", JSONObject().put("samplems", durationMs).put("timestamp", timestamp).put("uri", signature))
            .put("timestamp", timestamp)
            .put("timezone", "Asia/Kolkata")

        val tag = UUID.randomUUID().toString().uppercase()
        val requestId = UUID.randomUUID().toString()
        val request = Request.Builder()
            .url("https://amp.shazam.com/discovery/v5/en/US/android/-/tag/$tag/$requestId?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3")
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 14; iTune)")
            .header("Content-Language", "en_US")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            if (response.code == 404) throw IllegalArgumentException("No matching song was found.")
            if (response.code == 429) throw IllegalStateException("Music recognition is busy. Please try again shortly.")
            if (!response.isSuccessful) throw IllegalStateException("Music recognition failed (HTTP ${response.code}).")
            val root = JSONObject(response.body?.string().orEmpty())
            val track = root.optJSONObject("track")
                ?: throw IllegalArgumentException("No matching song was found.")
            val title = track.optString("title").takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("No matching song was found.")
            val artist = track.optString("subtitle", "Unknown artist")
            val images = track.optJSONObject("images")
            val sections = track.optJSONArray("sections")
            var album: String? = null
            if (sections != null) {
                for (i in 0 until sections.length()) {
                    val metadata = sections.optJSONObject(i)?.optJSONArray("metadata") ?: continue
                    for (j in 0 until metadata.length()) {
                        val item = metadata.optJSONObject(j) ?: continue
                        if (item.optString("title").equals("Album", ignoreCase = true)) {
                            album = item.optString("text").takeIf { it.isNotBlank() }
                        }
                    }
                }
            }
            IdentifiedTrack(
                title = title,
                artist = artist,
                album = album,
                artworkUrl = images?.optString("coverarthq")?.takeIf { it.isNotBlank() }
                    ?: images?.optString("coverart")?.takeIf { it.isNotBlank() }
            )
        }
    }

    private fun MediaFormat.integerOr(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback
}
