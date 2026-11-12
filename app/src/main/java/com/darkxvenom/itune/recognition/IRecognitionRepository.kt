package com.darkxvenom.itune.recognition

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class IRecognitionResult(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val label: String? = null,
    val isrc: String? = null,
    val shazamUrl: String? = null,
    val appleMusicUrl: String? = null,
)

class RecognitionNoMatchException : IOException("No match found")
class RecognitionPermissionException : SecurityException("Microphone permission is required")

/**
 * Short microphone capture + Shazam fingerprint lookup.
 *
 * Raw microphone samples stay in memory and are discarded immediately after signature
 * generation. Only the compact fingerprint signature is sent to Shazam.
 */
object IRecognitionRepository {
    private const val TAG = "iReconization"
    private const val SAMPLE_RATE = 16_000
    private const val CAPTURE_DURATION_MS = 5_000L
    private const val API_BASE = "https://amp.shazam.com/discovery/v5/en/US/android/-/tag"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun captureAndFingerprint(context: Context): ShazamSignature = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw RecognitionPermissionException()
        }

        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferBytes <= 0) {
            throw IOException("This device cannot initialize microphone recording.")
        }

        val bufferBytes = maxOf(minBufferBytes, 8_192)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes,
        )

        try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                throw IOException("The microphone could not be initialized.")
            }

            val requiredSamples = (SAMPLE_RATE * CAPTURE_DURATION_MS / 1_000L).toInt()
            val samples = ShortArray(requiredSamples)
            val readBuffer = ShortArray(maxOf(bufferBytes / 2, 2_048))

            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IOException("The microphone did not start recording.")
            }

            var written = 0
            while (written < requiredSamples) {
                coroutineContext.ensureActive()
                val requested = minOf(readBuffer.size, requiredSamples - written)
                val count = recorder.read(readBuffer, 0, requested, AudioRecord.READ_BLOCKING)
                when {
                    count > 0 -> {
                        System.arraycopy(readBuffer, 0, samples, written, count)
                        written += count
                    }
                    count == AudioRecord.ERROR_DEAD_OBJECT -> {
                        throw IOException("The microphone session ended unexpectedly.")
                    }
                    count == AudioRecord.ERROR_INVALID_OPERATION ||
                        count == AudioRecord.ERROR_BAD_VALUE -> {
                        throw IOException("Unable to read microphone audio (error $count).")
                    }
                }
            }

            withContext(Dispatchers.Default) {
                ShazamSignatureGenerator().apply {
                    feedPcm16Mono(samples)
                }.nextSignatureOrNull()
            } ?: throw IOException("Could not create an audio fingerprint. Try again in a quieter place.")
        } catch (security: SecurityException) {
            throw RecognitionPermissionException()
        } finally {
            runCatching {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            }
            runCatching { recorder.release() }
        }
    }

    suspend fun recognize(signature: ShazamSignature): IRecognitionResult = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis() / 1_000L
        val requestId = UUID.randomUUID().toString().uppercase(Locale.ROOT)
        val requestTag = UUID.randomUUID().toString()

        // Do not request location permission or transmit device location. The Shazam endpoint
        // accepts a neutral location object for fingerprint-only matching.
        val payload = JSONObject()
            .put(
                "geolocation",
                JSONObject()
                    .put("altitude", 0)
                    .put("latitude", 0)
                    .put("longitude", 0),
            )
            .put(
                "signature",
                JSONObject()
                    .put("samplems", signature.sampleDurationMs)
                    .put("timestamp", timestamp)
                    .put("uri", signature.uri),
            )
            .put("timestamp", timestamp)
            .put("timezone", TimeZone.getDefault().id)

        val url = "$API_BASE/$requestId/$requestTag" +
            "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3"
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 14; iTune)")
            .header("Content-Language", "en_US")
            .build()

        httpClient.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> throw RecognitionNoMatchException()
                response.code == 429 -> throw IOException("Recognition service is busy. Please wait a moment and try again.")
                response.code in 500..599 -> throw IOException("Recognition service is temporarily unavailable.")
                !response.isSuccessful -> throw IOException("Recognition failed (HTTP ${response.code}).")
            }

            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("The recognition service returned an empty response.")
            parseResult(JSONObject(body)) ?: throw RecognitionNoMatchException()
        }
    }

    private fun parseResult(root: JSONObject): IRecognitionResult? {
        val track = root.optJSONObject("track") ?: return null
        val title = track.optString("title").trim()
        val artist = track.optString("subtitle").trim()
        if (title.isBlank() || artist.isBlank()) return null

        var album: String? = null
        var releaseDate: String? = null
        var label: String? = null
        val sections = track.optJSONArray("sections")
        if (sections != null) {
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i) ?: continue
                if (!section.optString("type").equals("SONG", ignoreCase = true)) continue
                val metadata = section.optJSONArray("metadata") ?: continue
                for (j in 0 until metadata.length()) {
                    val entry = metadata.optJSONObject(j) ?: continue
                    val key = entry.optString("title")
                    val value = entry.optString("text").takeIf { it.isNotBlank() } ?: continue
                    when {
                        key.equals("Album", true) -> album = value
                        key.equals("Released", true) -> releaseDate = value
                        key.equals("Label", true) -> label = value
                    }
                }
            }
        }

        val images = track.optJSONObject("images")
        val hub = track.optJSONObject("hub")
        val options = hub?.optJSONArray("options")
        var appleMusicUrl: String? = null
        if (options != null) {
            for (i in 0 until options.length()) {
                val option = options.optJSONObject(i) ?: continue
                val provider = option.optString("providername")
                if (!provider.contains("apple", ignoreCase = true) &&
                    !option.optString("caption").contains("apple music", ignoreCase = true)
                ) continue
                val actions = option.optJSONArray("actions")
                val uri = actions?.optJSONObject(0)?.optString("uri")
                if (!uri.isNullOrBlank()) {
                    appleMusicUrl = uri
                    break
                }
            }
        }

        val shazamUrl = track.optString("url").takeIf { it.startsWith("http") }
        val genre = track.optJSONObject("genres")?.optString("primary")
            ?.takeIf { it.isNotBlank() }

        return IRecognitionResult(
            title = title,
            artist = artist,
            album = album,
            artworkUrl = images?.optString("coverarthq")?.takeIf { it.startsWith("http") }
                ?: images?.optString("coverart")?.takeIf { it.startsWith("http") },
            genre = genre,
            releaseDate = releaseDate,
            label = label,
            isrc = track.optString("isrc").takeIf { it.isNotBlank() },
            shazamUrl = shazamUrl,
            appleMusicUrl = appleMusicUrl,
        )
    }
}
