/*
 * Copyright (C) 2026 DxV Studio ~~ Dark
 *
 * Originally created by DxV Studio ~~ Dark.
 * Credits: DxV Studio, AirBeats, and Dark.
 *
 * This file is part of iTune and is licensed under the GNU General Public License v3.0 (GPL-3.0).
 * Any copying, modification, or redistribution of this code must strictly comply with the
 * terms of the project LICENSE file and retain full author credit.
 */

package com.darkxvenom.itune.backend

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Metadata record for a cached offline audio track.
 * Stores complete audio file reference, artist details, and track metadata with
 * local high-resolution artwork for guaranteed offline display.
 */
data class CachedMusicTrack(
    val id: String,
    val title: String,
    val artists: String,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val thumbnailUrl: String? = null,
    val localThumbnailPath: String? = null,
    val durationSec: Long = 0L,
    val audioFilePath: String,
    val fileSizeBytes: Long = 0L,
    val cachedAtMs: Long = System.currentTimeMillis(),
    var lastPlayedMs: Long = System.currentTimeMillis(),
    val liked: Boolean = false,
    /** Where the audio came from: "yt" (YouTube Music) or "saavn" (JioSaavn upgrade). */
    val source: String = "yt",
) {
    fun resolvedArtworkUri(): String? {
        if (!localThumbnailPath.isNullOrBlank()) {
            val f = File(localThumbnailPath)
            if (f.exists() && f.length() > 0) return "file://${f.absolutePath}"
        }
        return thumbnailUrl?.highQualityThumbnail() ?: thumbnailUrl
    }

    fun toMusicTrack(): MusicTrack = MusicTrack(
        id = id,
        title = title,
        artists = artists,
        artistId = artistId,
        album = album,
        albumId = albumId,
        thumbnailUrl = resolvedArtworkUri(),
        durationSec = durationSec,
        liked = liked,
        isVideo = false,
    )
}

internal fun shouldCacheAfterFullPlayback(
    activeTrackId: String?,
    eligibleTrackId: String?,
    cachingEnabled: Boolean,
    alreadyCached: Boolean,
    positionMs: Long,
    durationMs: Long,
): Boolean {
    if (!cachingEnabled || alreadyCached || activeTrackId.isNullOrBlank() || activeTrackId != eligibleTrackId) return false
    if (durationMs <= 0L) return true
    val endToleranceMs = maxOf(1_500L, durationMs / 50L)
    return positionMs >= (durationMs - endToleranceMs).coerceAtLeast(0L)
}

/**
 * Dedicated offline audio caching engine for iTune.
 * - Stores complete tracks for offline playback after natural playback completion or an explicit download.
 * - Stores complete metadata (title, artists, artistId, album, albumId, high-res thumbnail, duration).
 * - Manages storage limits (LRU eviction) and user storage settings.
 * - Saves high-quality album art locally so cached songs render album covers even in airplane mode.
 */
object MusicCacheManager {
    private const val TAG = "MusicCacheManager"
    private const val PREFS_NAME = "music_cache_settings"
    private const val KEY_ENABLED = "caching_enabled"
    private const val KEY_LIMIT_MB = "max_cache_limit_mb"
    private const val KEY_QUALITY = "cache_quality"

    const val QUALITY_LOW = "Low"
    const val QUALITY_MEDIUM = "Medium"
    const val QUALITY_HIGH = "High"
    private const val INDEX_FILE = "cached_songs_index.json"

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var cacheDir: File
    private lateinit var thumbsDir: File
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Active download jobs for live caching
    private val activeDownloads = ConcurrentHashMap<String, Job>()

    // Observable Compose states for UI
    val cachedTracks = mutableStateListOf<CachedMusicTrack>()
    val totalStorageBytes = mutableLongStateOf(0L)
    val cachingEnabled = mutableStateOf(true)
    val maxCacheLimitMB = mutableLongStateOf(2048L) // 2 GB default
    val cacheQuality = mutableStateOf(QUALITY_HIGH)

    /** Target AAC bitrate in kbps for the quality, or null for High (best available, never re-encoded). */
    fun targetBitrateKbps(quality: String): Int? = when (quality) {
        QUALITY_LOW -> 64
        QUALITY_MEDIUM -> 128
        else -> null
    }

    private const val CHUNK_SIZE = 2L * 1024L * 1024L // 2MB ranged chunks: completely bypasses YouTube CDN rate limiter
    private const val PARALLEL_WORKERS = 3 // 3 concurrent streams for maximum bandwidth

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cacheDir = File(app.filesDir, "music_cache").apply { if (!exists()) mkdirs() }
        thumbsDir = File(cacheDir, "thumbs").apply { if (!exists()) mkdirs() }

        cachingEnabled.value = prefs.getBoolean(KEY_ENABLED, true)
        maxCacheLimitMB.value = prefs.getLong(KEY_LIMIT_MB, 2048L)
        cacheQuality.value = prefs.getString(KEY_QUALITY, QUALITY_HIGH) ?: QUALITY_HIGH

        loadIndex()
    }

    private fun loadIndex() {
        scope.launch {
            try {
                val indexFile = File(cacheDir, INDEX_FILE)
                val loadedList = mutableListOf<CachedMusicTrack>()
                var totalBytes = 0L

                if (indexFile.exists()) {
                    val raw = indexFile.readText()
                    if (raw.isNotBlank()) {
                        val arr = JSONArray(raw)
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val id = o.optString("id")
                            val audioPath = o.optString("audioFilePath")
                            if (id.isBlank() || audioPath.isBlank()) continue

                            val audioFile = File(cacheDir, "$id.m4a").takeIf { it.exists() && it.length() > 0 }
                                ?: File(audioPath).takeIf { it.exists() && it.length() > 0 }

                            if (audioFile != null && audioFile.length() > 0) {
                                val size = audioFile.length()
                                totalBytes += size
                                val localThumb = o.optString("localThumbnailPath").ifBlank {
                                    File(thumbsDir, "$id.jpg").takeIf { it.exists() && it.length() > 0 }?.absolutePath
                                }
                                loadedList.add(
                                    CachedMusicTrack(
                                        id = id,
                                        title = o.optString("title"),
                                        artists = o.optString("artists"),
                                        artistId = o.optString("artistId").ifBlank { null },
                                        album = o.optString("album").ifBlank { null },
                                        albumId = o.optString("albumId").ifBlank { null },
                                        thumbnailUrl = o.optString("thumbnailUrl").ifBlank { null },
                                        localThumbnailPath = localThumb,
                                        durationSec = o.optLong("durationSec", 0L),
                                        audioFilePath = audioFile.absolutePath,
                                        fileSizeBytes = size,
                                        cachedAtMs = o.optLong("cachedAtMs", System.currentTimeMillis()),
                                        lastPlayedMs = o.optLong("lastPlayedMs", System.currentTimeMillis()),
                                        liked = o.optBoolean("liked", false),
                                        source = o.optString("source", "yt").ifBlank { "yt" },
                                    )
                                )
                            }
                        }
                    }
                }

                // Sort by last played descending
                loadedList.sortByDescending { it.lastPlayedMs }

                withContext(Dispatchers.Main) {
                    cachedTracks.clear()
                    cachedTracks.addAll(loadedList)
                    totalStorageBytes.longValue = totalBytes
                }

                enforceCacheLimit()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading cached music index", e)
            }
        }
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    private fun persistIndex(providedSnapshot: List<CachedMusicTrack>? = null) {
        scope.launch {
            try {
                val snapshot = providedSnapshot ?: withContext(Dispatchers.Main) { cachedTracks.toList() }
                val arr = JSONArray()
                var total = 0L
                snapshot.forEach { track ->
                    total += track.fileSizeBytes
                    arr.put(
                        JSONObject()
                            .put("id", track.id)
                            .put("title", track.title)
                            .put("artists", track.artists)
                            .put("artistId", track.artistId)
                            .put("album", track.album)
                            .put("albumId", track.albumId)
                            .put("thumbnailUrl", track.thumbnailUrl)
                            .put("localThumbnailPath", track.localThumbnailPath)
                            .put("durationSec", track.durationSec)
                            .put("audioFilePath", track.audioFilePath)
                            .put("fileSizeBytes", track.fileSizeBytes)
                            .put("cachedAtMs", track.cachedAtMs)
                            .put("lastPlayedMs", track.lastPlayedMs)
                            .put("liked", track.liked)
                            .put("source", track.source)
                    )
                }
                val indexFile = File(cacheDir, INDEX_FILE)
                indexFile.writeText(arr.toString())

                withContext(Dispatchers.Main) {
                    totalStorageBytes.longValue = total
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error persisting cached music index", e)
            }
        }
    }

    /**
     * Retrieves the cached track metadata if present in cache index.
     */
    fun getCachedTrack(trackId: String): CachedMusicTrack? =
        cachedTracks.firstOrNull { it.id == trackId }

    /**
     * Checks if the given track is already cached offline and returns its file.
     */
    fun getCachedAudioFile(trackId: String): File? {
        val entry = cachedTracks.firstOrNull { it.id == trackId }
        if (entry != null) {
            val file = File(entry.audioFilePath)
            if (file.exists() && file.length() > 0) return file
        }
        val defaultFile = File(cacheDir, "$trackId.m4a")
        return if (defaultFile.exists() && defaultFile.length() > 0) defaultFile else null
    }

    /**
     * Returns true if track is cached and ready for offline playback.
     */
    fun isTrackCached(trackId: String): Boolean = getCachedAudioFile(trackId) != null

    /** Exact source tag (e.g. "Tidal Hi-Fi", "Hi-Res Lossless", "JioSaavn", "YouTube Music") for a cached track, or null when it isn't cached. */
    fun cachedSource(trackId: String): String? =
        if (!isTrackCached(trackId)) null else cachedTracks.firstOrNull { it.id == trackId }?.source ?: "YouTube Music"

    private val streamCacheJobs = ConcurrentHashMap<String, Job>()

    /**
     * Downloads an arbitrary audio stream (Tidal Hi-Fi, Hi-Res direct, JioSaavn, Addons, etc.) into the cache.
     * When [replaceExisting] is set it swaps out an existing cached copy of the same track (written to a temp file
     * first, so the old copy stays valid until the new one is complete). Otherwise it only caches if the track
     * isn't cached yet and caching is on.
     * [onDone] is called with true when the cache now holds the stream copy.
     */
    fun cacheStream(
        track: MusicTrack,
        url: String,
        source: String = "Lossless",
        replaceExisting: Boolean = false,
        onDone: ((Boolean) -> Unit)? = null,
    ) {
        val existing = cachedTracks.firstOrNull { it.id == track.id }
        if (existing?.source == source && getCachedAudioFile(track.id) != null) { onDone?.invoke(true); return }
        if (existing == null && !cachingEnabled.value) { onDone?.invoke(false); return }
        if (existing != null && !replaceExisting) { onDone?.invoke(false); return }
        if (streamCacheJobs.containsKey(track.id)) return
        streamCacheJobs[track.id] = scope.launch {
            val safeSourceTag = source.filter { it.isLetterOrDigit() }.ifBlank { "src" }
            val tmp = File(cacheDir, "${track.id}.$safeSourceTag.tmp")
            val finalFile = File(cacheDir, "${track.id}.m4a")
            var ok = false
            try {
                val req = okhttp3.Request.Builder().url(url).header("User-Agent", Net.USER_AGENT).build()
                Net.client.newCall(req).execute().use { resp ->
                    val body = resp.body
                    if (!resp.isSuccessful || body == null) return@use
                    tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
                val expected = 64 * 1024L
                if (tmp.exists() && tmp.length() > expected) {
                    // Atomic swap: the old file (possibly still playing) is replaced only now.
                    val moved = tmp.renameTo(finalFile) || runCatching {
                        tmp.copyTo(finalFile, overwrite = true); true
                    }.getOrDefault(false)
                    if (moved && finalFile.length() > 0) {
                        val thumb = File(thumbsDir, "${track.id}.jpg").takeIf { it.exists() && it.length() > 0 }?.absolutePath
                        val entry = CachedMusicTrack(
                            id = track.id, title = track.title, artists = track.artists,
                            artistId = track.artistId, album = track.album, albumId = track.albumId,
                            thumbnailUrl = existing?.thumbnailUrl ?: track.thumbnailUrl?.highQualityThumbnail() ?: track.thumbnailUrl,
                            localThumbnailPath = existing?.localThumbnailPath ?: thumb,
                            durationSec = track.durationSec, audioFilePath = finalFile.absolutePath,
                            fileSizeBytes = finalFile.length(),
                            cachedAtMs = System.currentTimeMillis(),
                            lastPlayedMs = existing?.lastPlayedMs ?: System.currentTimeMillis(),
                            liked = track.liked, source = source,
                        )
                        withContext(Dispatchers.Main) {
                            val idx = cachedTracks.indexOfFirst { it.id == track.id }
                            if (idx >= 0) cachedTracks[idx] = entry else cachedTracks.add(0, entry)
                            persistIndex(cachedTracks.toList())
                        }
                        enforceCacheLimit()
                        ok = true
                        Log.d(TAG, "Cached $source copy of '${track.title}' (${formatStorageSize(finalFile.length())})")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "$source cache failed for ${track.id}: ${e.message}")
            } finally {
                streamCacheJobs.remove(track.id)
                if (tmp.exists()) tmp.delete()
            }
            withContext(Dispatchers.Main) { onDone?.invoke(ok) }
        }
    }

    /**
     * Downloads a JioSaavn stream into the cache. Kept for backward compatibility.
     */
    fun cacheSaavnStream(
        track: MusicTrack,
        url: String,
        replaceExisting: Boolean,
        onDone: ((Boolean) -> Unit)? = null,
    ) = cacheStream(track, url, source = "JioSaavn", replaceExisting = replaceExisting, onDone = onDone)

    /**
     * Marks track as recently played for LRU (Least Recently Used) cache ranking.
     */
    fun markTrackPlayed(trackId: String) {
        val idx = cachedTracks.indexOfFirst { it.id == trackId }
        if (idx >= 0) {
            val item = cachedTracks[idx]
            item.lastPlayedMs = System.currentTimeMillis()
            val snapshot = cachedTracks.toList()
            persistIndex(snapshot)
        }
    }

    /**
     * Downloads a complete stream after an explicit request or completed playback.
     */
    fun cacheTrackOnDemand(track: MusicTrack, onComplete: ((Boolean) -> Unit)? = null, force: Boolean = false) {
        if (isTrackCached(track.id)) {
            onComplete?.invoke(true)
            return
        }
        scope.launch {
            val url = MusicBackend.resolveAudioStreamUrl(track.id, title = track.title, artist = track.artists)
            if (url.isNullOrBlank()) {
                withContext(Dispatchers.Main) { onComplete?.invoke(false) }
                return@launch
            }
            val trace = com.darkxvenom.itune.audio.StreamTraceStore.traces.value[track.id]
            val resolvedSource = trace?.source ?: "YouTube Music"
            startLiveCaching(track, url, source = resolvedSource, force = force)
            var success = false
            for (i in 0 until 50) {
                delay(400)
                if (isTrackCached(track.id)) {
                    success = true
                    break
                }
            }
            withContext(Dispatchers.Main) { onComplete?.invoke(success) }
        }
    }

    /**
     * Downloads the complete requested stream into the offline cache.
     * Uses 2MB ranged parallel chunks to bypass YouTube's CDN bandwidth throttling.
     * Downloads high-quality thumbnail alongside the audio file for 100% offline coverage.
     */
    fun startLiveCaching(track: MusicTrack, playbackStreamUrl: String, source: String = "YouTube Music", force: Boolean = false) {
        if (!cachingEnabled.value && !force) return
        if (isTrackCached(track.id)) return
        if (activeDownloads.containsKey(track.id)) return
        val isYt = source == "yt" || source == "YouTube" || source == "YouTube Music" || playbackStreamUrl.contains("googlevideo.com")
        // Playing a non-YouTube stream (Tidal, Hi-Res direct, JioSaavn, Addons, etc.): cache THAT exact stream!
        if (!isYt) {
            cacheStream(track, playbackStreamUrl, source = source, replaceExisting = false)
            return
        }

        val job = scope.launch {
            val tmpFile = File(cacheDir, "${track.id}.tmp")
            val finalFile = File(cacheDir, "${track.id}.m4a")
            val thumbFile = File(thumbsDir, "${track.id}.jpg")

            try {
                // Pick the stream for the selected cache quality (falls back to the playback stream)
                val quality = cacheQuality.value
                val choice = MusicBackend.resolveAudioStreamForCache(track.id, quality)
                val streamUrl = choice?.url ?: playbackStreamUrl
                val sourceKbps = choice?.bitrateKbps ?: 0

                // 0. Cache high-resolution thumbnail in background
                val hqThumbUrl = track.thumbnailUrl?.highQualityThumbnail() ?: track.thumbnailUrl
                if (!hqThumbUrl.isNullOrBlank() && (!thumbFile.exists() || thumbFile.length() <= 0)) {
                    runCatching {
                        val thumbReq = Request.Builder()
                            .url(hqThumbUrl)
                            .header("User-Agent", Net.USER_AGENT)
                            .build()
                        Net.client.newCall(thumbReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                resp.body?.byteStream()?.use { input ->
                                    FileOutputStream(thumbFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        }
                    }
                }

                // 1. Probe total file size with Range: bytes=0-0
                var totalBytes = -1L
                var supportsRange = false

                try {
                    val probeReq = Request.Builder()
                        .url(streamUrl)
                        .header("User-Agent", Net.USER_AGENT)
                        .header("Range", "bytes=0-0")
                        .build()

                    Net.client.newCall(probeReq).execute().use { resp ->
                        if (resp.code == 206) {
                            supportsRange = true
                            val cr = resp.header("Content-Range")
                            if (cr != null && cr.contains("/")) {
                                totalBytes = cr.substringAfter("/").trim().toLongOrNull() ?: -1L
                            }
                        } else if (resp.isSuccessful) {
                            totalBytes = resp.body?.contentLength() ?: -1L
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Range probe failed for ${track.id}: ${e.message}")
                }

                // 2. High-speed chunked ranged download
                if (supportsRange && totalBytes > 0) {
                    val chunkCount = ((totalBytes + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
                    val nextChunkIndex = AtomicInteger(0)
                    val activeCalls = ConcurrentHashMap<Call, Boolean>()

                    val raf = RandomAccessFile(tmpFile, "rw")
                    raf.setLength(totalBytes)
                    val fileChannel = raf.channel

                    try {
                        coroutineScope {
                            val workers = (0 until minOf(PARALLEL_WORKERS, chunkCount)).map {
                                launch {
                                    val buffer = ByteArray(64 * 1024)
                                    while (isActive) {
                                        val chunkIdx = nextChunkIndex.getAndIncrement()
                                        if (chunkIdx >= chunkCount) break

                                        val chunkStart = chunkIdx * CHUNK_SIZE
                                        val chunkEnd = minOf(chunkStart + CHUNK_SIZE - 1, totalBytes - 1)

                                        var chunkSuccess = false
                                        var attempt = 0

                                        while (!chunkSuccess && attempt < 3 && isActive) {
                                            attempt++
                                            try {
                                                val chunkReq = Request.Builder()
                                                    .url(streamUrl)
                                                    .header("User-Agent", Net.USER_AGENT)
                                                    .header("Range", "bytes=$chunkStart-$chunkEnd")
                                                    .build()

                                                val call = Net.client.newCall(chunkReq)
                                                activeCalls[call] = true

                                                try {
                                                    call.execute().use { resp ->
                                                        if (resp.code != 206 && resp.code != 200) {
                                                            throw java.io.IOException("HTTP ${resp.code} for chunk $chunkIdx")
                                                        }
                                                        val stream = resp.body?.byteStream()
                                                            ?: throw java.io.IOException("Empty body for chunk $chunkIdx")
                                                        var currentChunkOffset = chunkStart
                                                        var r: Int

                                                        while (stream.read(buffer).also { r = it } != -1) {
                                                            ensureActive()
                                                            val byteBuffer = ByteBuffer.wrap(buffer, 0, r)
                                                            while (byteBuffer.hasRemaining()) {
                                                                val written = fileChannel.write(byteBuffer, currentChunkOffset)
                                                                currentChunkOffset += written
                                                            }
                                                        }
                                                        chunkSuccess = true
                                                    }
                                                } finally {
                                                    activeCalls.remove(call)
                                                }
                                            } catch (e: Exception) {
                                                if (!isActive) throw e
                                                delay(200)
                                            }
                                        }
                                        if (!chunkSuccess) {
                                            throw java.io.IOException("Failed downloading chunk $chunkIdx after 3 attempts")
                                        }
                                    }
                                }
                            }
                            workers.forEach { it.join() }
                        }
                    } finally {
                        activeCalls.keys.forEach { runCatching { it.cancel() } }
                        activeCalls.clear()
                        try { fileChannel.close() } catch (_: Exception) {}
                        try { raf.close() } catch (_: Exception) {}
                    }
                }

                // 3. Fallback direct stream if ranged chunk didn't complete
                if (!tmpFile.exists() || tmpFile.length() <= 0) {
                    val directReq = Request.Builder()
                        .url(streamUrl)
                        .header("User-Agent", Net.USER_AGENT)
                        .build()

                    Net.client.newCall(directReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            resp.body?.byteStream()?.use { input ->
                                FileOutputStream(tmpFile).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    var r: Int
                                    while (input.read(buffer).also { r = it } != -1) {
                                        ensureActive()
                                        output.write(buffer, 0, r)
                                    }
                                    output.flush()
                                }
                            }
                        }
                    }
                }

                // 3b. Compress after download when a lower cache quality is selected
                val targetKbps = targetBitrateKbps(quality)
                if (targetKbps != null && tmpFile.exists() && tmpFile.length() > 0) {
                    val actualKbps = if (track.durationSec > 0) {
                        (tmpFile.length() * 8L / 1000L / track.durationSec).toInt()
                    } else sourceKbps
                    if (actualKbps > targetKbps * 115 / 100) {
                        val encFile = File(cacheDir, "${track.id}.enc.tmp")
                        val done = AudioTranscoder.transcodeToAac(
                            input = tmpFile,
                            output = encFile,
                            targetBitrateBps = targetKbps * 1000,
                            isActive = { isActive },
                        )
                        // Keep the smaller file; if transcoding failed, the original download is kept
                        if (done && encFile.length() in 1 until tmpFile.length()) {
                            tmpFile.delete()
                            encFile.renameTo(tmpFile)
                            Log.d(TAG, "Compressed '${track.title}' to ${targetKbps}kbps")
                        }
                        if (encFile.exists()) encFile.delete()
                    }
                }

                // 4. Commit cached audio file safely with fallback copy
                if (tmpFile.exists() && tmpFile.length() > 0) {
                    if (finalFile.exists()) finalFile.delete()
                    val moved = tmpFile.renameTo(finalFile) || run {
                        try {
                            tmpFile.copyTo(finalFile, overwrite = true)
                            tmpFile.delete()
                            finalFile.exists()
                        } catch (_: Exception) {
                            false
                        }
                    }

                    if (moved && finalFile.exists() && finalFile.length() > 0) {
                        val localThumbPath = if (thumbFile.exists() && thumbFile.length() > 0) {
                            thumbFile.absolutePath
                        } else null

                        val cachedTrack = CachedMusicTrack(
                            id = track.id,
                            title = track.title,
                            artists = track.artists,
                            artistId = track.artistId,
                            album = track.album,
                            albumId = track.albumId,
                            thumbnailUrl = hqThumbUrl,
                            localThumbnailPath = localThumbPath,
                            durationSec = track.durationSec,
                            audioFilePath = finalFile.absolutePath,
                            fileSizeBytes = finalFile.length(),
                            cachedAtMs = System.currentTimeMillis(),
                            lastPlayedMs = System.currentTimeMillis(),
                            liked = track.liked,
                            source = source,
                        )

                        withContext(Dispatchers.Main) {
                            val existingIdx = cachedTracks.indexOfFirst { it.id == track.id }
                            if (existingIdx >= 0) {
                                cachedTracks[existingIdx] = cachedTrack
                            } else {
                                cachedTracks.add(0, cachedTrack)
                            }
                            val snapshot = cachedTracks.toList()
                            persistIndex(snapshot)
                        }

                        enforceCacheLimit()
                        Log.d(TAG, "Live cache completed successfully for '${track.title}' (${formatStorageSize(finalFile.length())})")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during live caching of ${track.id}: ${e.message}")
            } finally {
                activeDownloads.remove(track.id)
                if (tmpFile.exists()) tmpFile.delete()
            }
        }

        activeDownloads[track.id] = job
    }

    /**
     * Removes a cached song from disk and database.
     */
    fun removeCachedTrack(trackId: String): Boolean {
        activeDownloads[trackId]?.cancel()
        activeDownloads.remove(trackId)

        val idx = cachedTracks.indexOfFirst { it.id == trackId }
        if (idx >= 0) {
            val item = cachedTracks.removeAt(idx)
            val file = File(item.audioFilePath)
            if (file.exists()) file.delete()
            item.localThumbnailPath?.let { p ->
                val tf = File(p)
                if (tf.exists()) tf.delete()
            }
            val snapshot = cachedTracks.toList()
            persistIndex(snapshot)
            return true
        }
        return false
    }

    /**
     * Clears all cached offline songs and frees up device storage.
     */
    fun clearAllCache() {
        scope.launch {
            activeDownloads.values.forEach { it.cancel() }
            activeDownloads.clear()

            cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    file.listFiles()?.forEach { it.delete() }
                } else if (file.name != INDEX_FILE) {
                    file.delete()
                }
            }

            withContext(Dispatchers.Main) {
                cachedTracks.clear()
                totalStorageBytes.longValue = 0L
            }

            persistIndex()
        }
    }

    /**
     * Enforces the user's storage limit using LRU eviction.
     */
    fun enforceCacheLimit() {
        val limitMB = maxCacheLimitMB.longValue
        if (limitMB <= 0) return // Unlimited
        val maxBytes = limitMB * 1024L * 1024L

        scope.launch {
            while (totalStorageBytes.longValue > maxBytes && cachedTracks.isNotEmpty()) {
                val oldest = cachedTracks.minByOrNull { it.lastPlayedMs } ?: break
                Log.d(TAG, "Cache limit exceeded (${formatStorageSize(totalStorageBytes.longValue)} > ${limitMB}MB). Evicting LRU: ${oldest.title}")
                removeCachedTrack(oldest.id)
            }
        }
    }

    fun setCachingEnabled(enabled: Boolean) {
        cachingEnabled.value = enabled
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Applies to songs cached from now on; already cached songs are left as they are. */
    fun setCacheQuality(quality: String) {
        cacheQuality.value = quality
        prefs.edit().putString(KEY_QUALITY, quality).apply()
    }

    fun setMaxCacheLimitMB(limitMB: Long) {
        maxCacheLimitMB.longValue = limitMB
        prefs.edit().putLong(KEY_LIMIT_MB, limitMB).apply()
        enforceCacheLimit()
    }

    fun getAllCachedAsMusicTracks(): List<MusicTrack> {
        return cachedTracks.map { it.toMusicTrack() }
    }

    fun formatStorageSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.1f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }
}
