package com.darkxvenom.itune.ui.player

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

internal enum class PlayerToolMode { RINGTONE, SNIPPET }

@Composable
internal fun PlayerToolsDialog(
    track: MusicTrack,
    mode: PlayerToolMode,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember(track.id) { mutableStateOf<File?>(null) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var durationMs by remember { mutableFloatStateOf((track.durationSec * 1000L).coerceAtLeast(30_000L).toFloat()) }
    var range by remember(track.id) {
        mutableStateOf(0f..(30_000f.coerceAtMost((track.durationSec * 1000L).takeIf { it > 0 }?.toFloat() ?: 30_000f)))
    }
    val player = remember(context) { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF } }

    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LaunchedEffect(track.id) {
        loading = true
        error = null
        try {
            val file = PlayerToolsAudio.prepareSource(context, track.id)
            source = file
            val actualDuration = PlayerToolsAudio.duration(file)
            if (actualDuration > 0) {
                durationMs = actualDuration.toFloat()
                val end = minOf(30_000f, actualDuration.toFloat())
                range = 0f..end
            }
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
        } catch (e: Exception) {
            error = e.message ?: "Unable to load audio"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(range, isPreviewPlaying) {
        while (isPreviewPlaying) {
            if (player.currentPosition >= range.endInclusive.toLong()) {
                player.seekTo(range.start.toLong())
            }
            kotlinx.coroutines.delay(100)
        }
    }

    val title = if (mode == PlayerToolMode.RINGTONE) "Set as Ringtone" else "Snippet Studio"
    AlertDialog(
        onDismissRequest = {
            player.stop()
            onDismiss()
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(track.artists, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("Preparing audio…", modifier = Modifier.padding(start = 10.dp))
                    }
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                if (isPreviewPlaying) {
                                    player.pause()
                                    isPreviewPlaying = false
                                } else {
                                    player.seekTo(range.start.toLong())
                                    player.play()
                                    isPreviewPlaying = true
                                }
                            }) {
                                Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Preview")
                            }
                            Text("${PlayerToolsAudio.time(range.start.toLong())} – ${PlayerToolsAudio.time(range.endInclusive.toLong())}")
                        }
                        Text("Select a clip (up to 30 seconds)", style = MaterialTheme.typography.bodySmall)
                        RangeSlider(
                            value = range,
                            onValueChange = { next ->
                                val start = next.start.coerceAtLeast(0f)
                                val end = next.endInclusive.coerceAtMost(durationMs)
                                val boundedEnd = end.coerceAtMost(start + 30_000f)
                                range = if (boundedEnd - start < 3_000f) start..(start + 3_000f).coerceAtMost(durationMs) else start..boundedEnd
                                player.seekTo(range.start.toLong())
                            },
                            valueRange = 0f..durationMs.coerceAtLeast(3_000f),
                            steps = 0,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = source != null && !loading && !working && error == null,
                onClick = {
                    val input = source ?: return@Button
                    working = true
                    player.pause()
                    scope.launch {
                        val output = withContext(Dispatchers.IO) {
                            PlayerToolsAudio.trim(input, range.start.toLong(), range.endInclusive.toLong(), context.cacheDir)
                        }
                        val result = if (output == null) null else withContext(Dispatchers.IO) {
                            if (mode == PlayerToolMode.RINGTONE) {
                                PlayerToolsAudio.saveRingtone(context, output, track.title, track.artists)
                            } else {
                                PlayerToolsAudio.saveSnippet(context, output, track.title, track.artists)
                            }
                        }
                        working = false
                        if (result == null) {
                            Toast.makeText(context, "Could not create audio clip", Toast.LENGTH_LONG).show()
                        } else if (mode == PlayerToolMode.RINGTONE) {
                            Toast.makeText(context, "Added to system ringtones. Choose it in Sound settings.", Toast.LENGTH_LONG).show()
                            runCatching {
                                context.startActivity(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Ringtone")
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, result)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Snippet saved to Music/iTune/Snippets", Toast.LENGTH_LONG).show()
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/mp4"
                                putExtra(Intent.EXTRA_STREAM, result)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(Intent.createChooser(share, "Share snippet")) }
                            onDismiss()
                        }
                    }
                }
            ) {
                if (working) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (mode == PlayerToolMode.RINGTONE) "Set Ringtone" else "Save Snippet")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(24.dp),
    )
}

private object PlayerToolsAudio {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun prepareSource(context: Context, videoId: String): File = withContext(Dispatchers.IO) {
        val url = MusicBackend.resolveAudioStream(videoId, targetQuality = "medium")
            ?: throw IllegalStateException("Could not resolve this song's audio stream")
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Audio download failed: HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("Empty audio response")
            if (body.contentLength() > 150L * 1024L * 1024L) throw IllegalStateException("Audio file is too large")
            val file = File(context.cacheDir, "player_tool_source_${videoId.hashCode()}.m4a")
            body.byteStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            if (file.length() == 0L) throw IllegalStateException("Downloaded audio is empty")
            file
        }
    }

    fun duration(file: File): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).firstNotNullOfOrNull { index ->
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true &&
                    format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000L else null
            } ?: 0L
        } finally { extractor.release() }
    }

    fun trim(source: File, startMs: Long, endMs: Long, cacheDir: File): File? {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        val out = File(cacheDir, "itune_clip_${System.currentTimeMillis()}.m4a")
        return try {
            extractor.setDataSource(source.absolutePath)
            val audioTrack = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(audioTrack)
            val format = extractor.getTrackFormat(audioTrack)
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(format)
            muxer.start()
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val info = MediaMuxer.BufferInfo()
            val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
            val endUs = endMs * 1000L
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0 || extractor.sampleTime < 0 || extractor.sampleTime >= endUs) break
                if (extractor.sampleTime >= startMs * 1000L) {
                    info.set(0, size, extractor.sampleTime - startMs * 1000L, extractor.sampleFlags)
                    muxer.writeSampleData(outTrack, buffer, info)
                }
                extractor.advance()
            }
            muxer.stop()
            if (out.exists() && out.length() > 0L) out else null
        } catch (_: Exception) {
            out.delete()
            null
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    fun saveRingtone(context: Context, file: File, title: String, artist: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return saveToMediaStore(context, file, title, artist, "${Environment.DIRECTORY_RINGTONES}/iTune", true)
    }

    fun saveSnippet(context: Context, file: File, title: String, artist: String): Uri? {
        return saveToMediaStore(context, file, title, artist, "${Environment.DIRECTORY_MUSIC}/iTune/Snippets", false)
    }

    private fun saveToMediaStore(context: Context, file: File, title: String, artist: String, path: String, ringtone: Boolean): Uri? {
        return try {
            val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeTitle (iTune).m4a")
                put(MediaStore.Audio.Media.TITLE, safeTitle)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, path)
                if (ringtone) put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: return null
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri
        } catch (_: Exception) { null }
    }

    fun time(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(total / 60L, total % 60L)
    }
}
