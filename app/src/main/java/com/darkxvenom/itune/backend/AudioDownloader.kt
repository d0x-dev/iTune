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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.darkxvenom.itune.MainActivity
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.lyrics.LyricsHelper
import com.darkxvenom.itune.backend.tagger.MediaTagger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

data class AudioDownloadOption(
    val bitrateKbps: Int,
    val label: String,
    val formatTag: String,
    val formatLabel: String,
    val extension: String,
    val mimeType: String,
    val approxSize: String?,
    val streamUrl: String,
    val isHighQuality: Boolean,
    val needsOggRemux: Boolean = false,
)
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

object AudioDownloader {
    private const val TAG = "AudioDownloader"
    private const val CHANNEL_ID = "itune_audio_downloads_v2"
    private const val CHANNEL_NAME = "Audio Downloads"
    const val DEFAULT_FOREGROUND_ID = 2000
    const val COMPLETE_NOTIFICATION_ID = 2001

    private const val CHUNK_SIZE = 512L * 1024L // 512 KB chunks for fast parallel pulls + byte-exact resume
    private const val PARALLEL_WORKERS = 3 // 3 parallel chunk workers for single track speed

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    data class DownloadQueueItem(
        val track: MusicTrack,
        val option: AudioDownloadOption? = null,
    )

    private class DownloadTaskState(
        val id: Int = DEFAULT_FOREGROUND_ID,
        val track: MusicTrack,
        val option: AudioDownloadOption? = null,
        val isPaused: AtomicBoolean = AtomicBoolean(false),
        val isCancelled: AtomicBoolean = AtomicBoolean(false),
        val activeCalls: ConcurrentHashMap<Call, Boolean> = ConcurrentHashMap(),
        @Volatile var resolvedOption: AudioDownloadOption? = null,
        @Volatile var downloadedBytes: Long = 0L,
        @Volatile var totalBytes: Long = -1L,
        @Volatile var smoothedSpeedBps: Double = 0.0,
        @Volatile var lastNotifyTimeMs: Long = 0L,
        @Volatile var lastSpeedSampleMs: Long = System.currentTimeMillis(),
        @Volatile var lastSpeedSampleBytes: Long = 0L,
        @Volatile var albumArtBitmap: Bitmap? = null,
        @Volatile var job: Job? = null,
    )

    private val downloadQueue = ConcurrentLinkedQueue<DownloadQueueItem>()
    private val queueLock = Any()
    @Volatile private var activeTask: DownloadTaskState? = null
    private val totalQueuedCount = AtomicInteger(0)
    private val completedCount = AtomicInteger(0)
    @Volatile private var lastFinishedTitle: String? = null

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live audio download progress, speed, pause and cancel controls"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun hasActiveDownloads(): Boolean = activeTask != null
    fun hasActiveOrPendingDownloads(): Boolean = activeTask != null || downloadQueue.isNotEmpty()

    fun getMasterNotification(context: Context): Notification {
        ensureChannel(context)
        val task = activeTask
        if (task != null) {
            val total = task.totalBytes
            val read = task.downloadedBytes
            val percent = if (total > 0) ((read * 100L) / total).toInt().coerceIn(0, 100) else 0
            val isPaused = task.isPaused.get()
            val speedText = if (task.smoothedSpeedBps > 512.0) " · ${formatSpeed(task.smoothedSpeedBps)}" else ""
            val queueRemaining = downloadQueue.size
            val queueText = if (queueRemaining > 0) " ($queueRemaining in queue)" else ""
            val contentText = if (isPaused) {
                "Paused · $percent%$queueText"
            } else if (total > 0) {
                "$percent% · ${formatBytes(read)} / ${formatBytes(total)}$speedText$queueText"
            } else {
                "${formatBytes(read)}$speedText$queueText"
            }
            return buildProgressNotification(
                context = context,
                title = task.track.title,
                subText = buildSubText(task),
                contentText = contentText,
                percent = percent,
                indeterminate = total <= 0 && !isPaused,
                isPaused = isPaused,
                largeIcon = task.albumArtBitmap
            )
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("iTune Downloads")
            .setContentText(context.getString(R.string.preparing_download))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun getOrBuildNotification(context: Context, notificationId: Int, fallbackTitle: String): Notification {
        return getMasterNotification(context)
    }

    fun download(context: Context, track: MusicTrack, option: AudioDownloadOption? = null) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        totalQueuedCount.incrementAndGet()
        downloadQueue.add(DownloadQueueItem(track, option))
        processNext(appContext)
    }

    fun downloadTracks(context: Context, tracks: List<MusicTrack>) {
        if (tracks.isEmpty()) return
        val appContext = context.applicationContext
        ensureChannel(appContext)
        totalQueuedCount.addAndGet(tracks.size)
        for (track in tracks) {
            downloadQueue.add(DownloadQueueItem(track, null))
        }
        processNext(appContext)
    }

    fun processNext(context: Context) {
        val appContext = context.applicationContext
        synchronized(queueLock) {
            if (activeTask != null) {
                updateNotificationForTask(appContext, activeTask, force = true)
                return
            }

            val nextItem = downloadQueue.poll()
            if (nextItem == null) {
                val totalFinished = completedCount.get()
                val lastTitle = lastFinishedTitle
                totalQueuedCount.set(0)
                completedCount.set(0)
                lastFinishedTitle = null

                stopService(appContext, removeNotification = true)
                NotificationManagerCompat.from(appContext).cancel(DEFAULT_FOREGROUND_ID)

                if (totalFinished > 0) {
                    showSummaryCompleteNotification(appContext, totalFinished, lastTitle)
                }
                return
            }

            startSingleTask(appContext, nextItem)
        }
    }

    private fun startSingleTask(context: Context, item: DownloadQueueItem) {
        val task = DownloadTaskState(
            id = DEFAULT_FOREGROUND_ID,
            track = item.track,
            option = item.option,
        )
        activeTask = task

        tryStartService(context)

        val initialNotif = buildProgressNotification(
            context = context,
            title = item.track.title,
            subText = buildSubText(task),
            contentText = "0% · Starting download…",
            percent = 0,
            indeterminate = true,
            isPaused = false,
            largeIcon = null
        )
        safeNotify(context, DEFAULT_FOREGROUND_ID, initialNotif)

        task.job = scope.launch {
            launch {
                val bmp = loadArtworkBitmap(context, item.track)
                task.albumArtBitmap = bmp
                if (activeTask === task && !task.isCancelled.get()) {
                    updateNotificationForTask(context, task, force = true)
                }
            }

            try {
                val resolvedOpt = task.option ?: run {
                    val extracted = runCatching {
                        MusicBackend.extractAudioQualities(item.track.id)
                    }.getOrNull().orEmpty()
                    MusicBackend.selectDownloadOption(extracted) ?: extracted.firstOrNull()
                }

                if (resolvedOpt == null) {
                    Log.e(TAG, "No downloadable stream found for '${item.track.title}'")
                } else {
                    task.resolvedOption = resolvedOpt
                    performAudioDownload(context, task)
                    completedCount.incrementAndGet()
                    lastFinishedTitle = item.track.title
                }
            } catch (cancelled: CancellationException) {
                // Cancelled or skipped
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (!task.isCancelled.get()) {
                    Log.e(TAG, "Audio download failed for '${item.track.title}'", error)
                    if (downloadQueue.isEmpty() && totalQueuedCount.get() <= 1) {
                        showFailedNotification(context, item.track.title, error.message ?: "Download failed", task.albumArtBitmap)
                    }
                }
            } finally {
                synchronized(queueLock) {
                    if (activeTask === task) {
                        activeTask = null
                    }
                }
                processNext(context)
            }
        }
    }

    fun togglePause(context: Context, notificationId: Int = DEFAULT_FOREGROUND_ID) {
        val appContext = context.applicationContext
        val task = activeTask ?: return
        if (task.isCancelled.get()) return

        val nowPaused = !task.isPaused.get()
        task.isPaused.set(nowPaused)

        if (nowPaused) {
            task.activeCalls.keys.forEach { call -> runCatching { call.cancel() } }
            task.activeCalls.clear()
            task.smoothedSpeedBps = 0.0
        } else {
            task.lastSpeedSampleMs = System.currentTimeMillis()
            task.lastSpeedSampleBytes = task.downloadedBytes
            task.smoothedSpeedBps = 0.0
        }

        updateNotificationForTask(appContext, task, force = true)
    }

    fun cancel(context: Context, notificationId: Int = DEFAULT_FOREGROUND_ID) {
        val appContext = context.applicationContext
        val task = activeTask
        if (task != null) {
            task.isCancelled.set(true)
            task.isPaused.set(false)
            task.activeCalls.keys.forEach { call -> runCatching { call.cancel() } }
            task.activeCalls.clear()
            task.job?.cancel(CancellationException("Skipped by user"))
        }
    }

    fun cancelAll(context: Context) {
        val appContext = context.applicationContext
        downloadQueue.clear()
        totalQueuedCount.set(0)
        completedCount.set(0)
        lastFinishedTitle = null

        val task = activeTask
        if (task != null) {
            task.isCancelled.set(true)
            task.isPaused.set(false)
            task.activeCalls.keys.forEach { call -> runCatching { call.cancel() } }
            task.activeCalls.clear()
            task.job?.cancel(CancellationException("Cancelled all by user"))
        }
        synchronized(queueLock) {
            activeTask = null
        }
        stopService(appContext, removeNotification = true)
        NotificationManagerCompat.from(appContext).cancel(DEFAULT_FOREGROUND_ID)
    }

    private fun updateNotificationForTask(context: Context, task: DownloadTaskState?, force: Boolean = false) {
        if (task == null) return
        val total = task.totalBytes
        val read = task.downloadedBytes
        val isPaused = task.isPaused.get()
        val percent = if (total > 0) ((read * 100L) / total).toInt().coerceIn(0, 100) else 0
        val speedText = if (task.smoothedSpeedBps > 512.0) " · ${formatSpeed(task.smoothedSpeedBps)}" else ""
        val queueRemaining = downloadQueue.size
        val queueText = if (queueRemaining > 0) " ($queueRemaining in queue)" else ""

        val text = if (isPaused) {
            if (total > 0) "Paused · $percent% (${formatBytes(read)} / ${formatBytes(total)})$queueText"
            else "Paused · ${formatBytes(read)}$queueText"
        } else if (total > 0) {
            "$percent% · ${formatBytes(read)} / ${formatBytes(total)}$speedText$queueText"
        } else {
            "${formatBytes(read)}$speedText$queueText"
        }

        val notif = buildProgressNotification(
            context = context,
            title = task.track.title,
            subText = buildSubText(task),
            contentText = text,
            percent = percent,
            indeterminate = total <= 0 && !isPaused,
            isPaused = isPaused,
            largeIcon = task.albumArtBitmap
        )
        safeNotify(context, DEFAULT_FOREGROUND_ID, notif)
    }

    private suspend fun performAudioDownload(
        context: Context,
        task: DownloadTaskState
    ) = withContext(Dispatchers.IO) {
        val option = task.resolvedOption ?: task.option ?: return@withContext
        val cleanTitle = "${task.track.title} - ${task.track.artists}"
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(90)
        val downloadExt = if (option.needsOggRemux) "webm" else option.extension
        val tempFile = File(context.cacheDir, "itune_dl_${task.id}_${System.currentTimeMillis()}.$downloadExt")
        val remuxedFile = if (option.needsOggRemux) {
            File(context.cacheDir, "itune_ogg_${task.id}_${System.currentTimeMillis()}.ogg")
        } else null

        fun reportProgress(read: Long, total: Long, force: Boolean = false) {
            task.downloadedBytes = read
            task.totalBytes = total
            if (task.isPaused.get() || task.isCancelled.get()) return

            val now = System.currentTimeMillis()
            val dtSpeed = now - task.lastSpeedSampleMs
            if (dtSpeed >= 400L) {
                val deltaBytes = (read - task.lastSpeedSampleBytes).coerceAtLeast(0L)
                val instantBps = (deltaBytes * 1000.0) / dtSpeed.coerceAtLeast(1L)
                task.smoothedSpeedBps = if (task.smoothedSpeedBps <= 0.0) {
                    instantBps
                } else {
                    task.smoothedSpeedBps * 0.62 + instantBps * 0.38
                }
                task.lastSpeedSampleMs = now
                task.lastSpeedSampleBytes = read
            }

            if (force || now - task.lastNotifyTimeMs >= 400L || (total > 0 && read >= total)) {
                task.lastNotifyTimeMs = now
                val percent = if (total > 0) ((read * 100L) / total).toInt().coerceIn(0, 100) else 0
                val speedText = if (task.smoothedSpeedBps > 512.0) " · ${formatSpeed(task.smoothedSpeedBps)}" else ""
                val queueRemaining = downloadQueue.size
                val queueText = if (queueRemaining > 0) " ($queueRemaining in queue)" else ""
                val text = if (total > 0) {
                    "$percent% · ${formatBytes(read)} / ${formatBytes(total)}$speedText$queueText"
                } else {
                    "${formatBytes(read)}$speedText$queueText"
                }
                val notif = buildProgressNotification(
                    context = context,
                    title = task.track.title,
                    subText = buildSubText(task),
                    contentText = text,
                    percent = percent,
                    indeterminate = total <= 0,
                    isPaused = false,
                    largeIcon = task.albumArtBitmap
                )
                safeNotify(context, DEFAULT_FOREGROUND_ID, notif)
            }
        }

        try {
            downloadPausable(task, tempFile) { read, total ->
                reportProgress(read, total)
            }

            if (task.isCancelled.get()) return@withContext

            val sourceToSave = if (remuxedFile != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ok = runCatching { remuxToPureOgg(tempFile, remuxedFile) }.getOrDefault(false)
                if (ok && remuxedFile.exists() && remuxedFile.length() > 0L) remuxedFile else tempFile
            } else tempFile

            val actualExt = if (sourceToSave === remuxedFile) "ogg" else option.extension
            val actualMime = if (sourceToSave === remuxedFile) "audio/ogg" else option.mimeType
            val finalFileName = "$cleanTitle [${option.bitrateKbps}kbps].$actualExt"

            val (coverBytes, coverMime) = loadArtworkBytes(context, task)
            val lyricsText = loadLyricsForTrack(task.track)

            MediaTagger.tagAudioFile(
                file = sourceToSave,
                track = task.track,
                coverBytes = coverBytes.takeIf { it.isNotEmpty() },
                coverMime = coverMime,
                lyrics = lyricsText
            )

            saveToDownloads(context, sourceToSave, finalFileName, actualMime, task.track)
                ?: throw IOException("Could not save audio to Downloads")
        } finally {
            runCatching { tempFile.delete() }
            runCatching { remuxedFile?.delete() }
        }
    }

    private fun remuxToPureOgg(inputFile: File, outputFile: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val extractor = MediaExtractor().apply { setDataSource(inputFile.absolutePath) }
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
        return try {
            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    trackIndex = muxer.addTrack(format)
                    break
                }
            }
            if (trackIndex == -1) return false
            muxer.start()
            val buffer = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(trackIndex, buffer, info)
                extractor.advance()
            }
            true
        } finally {
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
            runCatching { extractor.release() }
        }
    }

    private suspend fun downloadPausable(
        task: DownloadTaskState,
        targetFile: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val streamUrl = task.resolvedOption?.streamUrl ?: task.option?.streamUrl ?: return@withContext
        targetFile.parentFile?.mkdirs()

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
            Log.w(TAG, "Range probe failed: ${e.message}")
        }

        if (!supportsRange || totalBytes <= 0L) {
            downloadPausableDirectStream(task, targetFile, totalBytes, onProgress)
            return@withContext
        }

        val chunkCount = ((totalBytes + CHUNK_SIZE - 1L) / CHUNK_SIZE).toInt()
        val pendingChunks = ConcurrentLinkedQueue<Int>()
        for (i in 0 until chunkCount) pendingChunks.add(i)

        val chunkBytesWritten = AtomicLongArray(chunkCount)
        val totalDownloaded = AtomicLong(0L)
        onProgress(0L, totalBytes)

        val raf = RandomAccessFile(targetFile, "rw")
        raf.setLength(totalBytes)
        val fileChannel = raf.channel

        val currentJob = currentCoroutineContext()[Job]
        val cancelHook = currentJob?.invokeOnCompletion {
            task.activeCalls.keys.forEach { call -> runCatching { call.cancel() } }
            task.activeCalls.clear()
        }

        try {
            coroutineScope {
                val workerCount = minOf(PARALLEL_WORKERS, chunkCount)
                val workers = (0 until workerCount).map {
                    launch {
                        val buffer = ByteArray(32 * 1024)
                        while (isActive && !task.isCancelled.get() && totalDownloaded.get() < totalBytes) {
                            while (task.isPaused.get() && !task.isCancelled.get() && isActive) {
                                delay(150L)
                            }
                            if (task.isCancelled.get() || !isActive) break

                            val chunkIdx = pendingChunks.poll()
                            if (chunkIdx == null) {
                                delay(60L)
                                continue
                            }

                            val chunkBaseStart = chunkIdx * CHUNK_SIZE
                            val chunkEnd = minOf(chunkBaseStart + CHUNK_SIZE - 1L, totalBytes - 1L)
                            val chunkTargetLen = chunkEnd - chunkBaseStart + 1L

                            var attempt = 0
                            var completed = false

                            while (!completed && !task.isCancelled.get() && isActive) {
                                while (task.isPaused.get() && !task.isCancelled.get() && isActive) {
                                    delay(150L)
                                }
                                if (task.isCancelled.get() || !isActive) break

                                val alreadyInChunk = chunkBytesWritten.get(chunkIdx)
                                if (alreadyInChunk >= chunkTargetLen) {
                                    completed = true
                                    break
                                }

                                val reqStart = chunkBaseStart + alreadyInChunk
                                val chunkReq = Request.Builder()
                                    .url(streamUrl)
                                    .header("User-Agent", Net.USER_AGENT)
                                    .header("Range", "bytes=$reqStart-$chunkEnd")
                                    .build()

                                val call = Net.client.newCall(chunkReq)
                                task.activeCalls[call] = true

                                try {
                                    call.execute().use { resp ->
                                        if (resp.code != 206 && resp.code != 200) {
                                            throw IOException("HTTP ${resp.code} on chunk $chunkIdx")
                                        }
                                        val stream = resp.body?.byteStream() ?: throw IOException("Empty chunk body")
                                        var writeOffset = reqStart
                                        var readCount: Int
                                        while (stream.read(buffer).also { readCount = it } != -1) {
                                            if (task.isCancelled.get()) throw CancellationException("Cancelled")
                                            if (task.isPaused.get()) {
                                                break
                                            }
                                            val byteBuffer = ByteBuffer.wrap(buffer, 0, readCount)
                                            while (byteBuffer.hasRemaining()) {
                                                val written = fileChannel.write(byteBuffer, writeOffset)
                                                writeOffset += written
                                            }
                                            chunkBytesWritten.addAndGet(chunkIdx, readCount.toLong())
                                            val newTotal = totalDownloaded.addAndGet(readCount.toLong())
                                            onProgress(newTotal, totalBytes)
                                        }
                                    }
                                    if (chunkBytesWritten.get(chunkIdx) >= chunkTargetLen) {
                                        completed = true
                                    } else if (task.isPaused.get() && !task.isCancelled.get()) {
                                        pendingChunks.add(chunkIdx)
                                        break
                                    } else {
                                        throw IOException("Incomplete chunk $chunkIdx")
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    if (task.isCancelled.get()) throw CancellationException("Cancelled")
                                    if (task.isPaused.get()) {
                                        pendingChunks.add(chunkIdx)
                                        break
                                    }
                                    attempt++
                                    if (attempt >= 5) throw e
                                    delay(250L * attempt)
                                } finally {
                                    task.activeCalls.remove(call)
                                }
                            }
                        }
                    }
                }
                workers.forEach { it.join() }
            }
            fileChannel.force(true)
        } finally {
            cancelHook?.dispose()
            task.activeCalls.clear()
            runCatching { fileChannel.close() }
            runCatching { raf.close() }
        }
    }

    private suspend fun downloadPausableDirectStream(
        task: DownloadTaskState,
        targetFile: File,
        knownTotalBytes: Long,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val streamUrl = task.resolvedOption?.streamUrl ?: task.option?.streamUrl ?: return@withContext
        var bytesWritten = targetFile.length()
        var totalBytes = knownTotalBytes

        while (!task.isCancelled.get()) {
            while (task.isPaused.get() && !task.isCancelled.get()) {
                delay(150L)
            }
            if (task.isCancelled.get()) break

            val reqBuilder = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", Net.USER_AGENT)

            if (bytesWritten > 0L) {
                reqBuilder.header("Range", "bytes=$bytesWritten-")
            }

            val req = reqBuilder.build()
            val call = Net.client.newCall(req)
            task.activeCalls[call] = true

            var pausedMidStream = false

            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful && resp.code != 206) {
                        if (bytesWritten > 0L) {
                            bytesWritten = 0L
                            targetFile.delete()
                            return@use
                        }
                        throw IOException("HTTP error ${resp.code} during audio download")
                    }

                    if (totalBytes <= 0L) {
                        val cl = resp.body?.contentLength() ?: -1L
                        if (cl > 0L) totalBytes = bytesWritten + cl
                    }

                    val body = resp.body ?: throw IOException("Empty body response")
                    val source = body.byteStream()
                    val append = bytesWritten > 0L
                    targetFile.outputStream().use { fos ->
                        java.io.BufferedOutputStream(fos, 64 * 1024).use { out ->
                            val buffer = ByteArray(32 * 1024)
                            var read: Int
                            while (source.read(buffer).also { read = it } != -1) {
                                if (task.isCancelled.get()) throw CancellationException("Cancelled")
                                if (task.isPaused.get()) {
                                    pausedMidStream = true
                                    break
                                }
                                out.write(buffer, 0, read)
                                bytesWritten += read
                                onProgress(bytesWritten, totalBytes)
                            }
                            out.flush()
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (task.isCancelled.get()) throw CancellationException("Cancelled")
                if (task.isPaused.get()) {
                    pausedMidStream = true
                } else {
                    throw e
                }
            } finally {
                task.activeCalls.remove(call)
            }

            if (!pausedMidStream) break
        }
    }

    private fun saveToDownloads(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String,
        track: MusicTrack
    ): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.TITLE, track.title)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/iTune")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                val out = resolver.openOutputStream(uri) ?: throw IOException("Failed opening MediaStore output")
                out.use { outputStream ->
                    sourceFile.inputStream().use { inputStream -> inputStream.copyTo(outputStream) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                val publicFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "iTune/$fileName")
                MediaScannerConnection.scanFile(context, arrayOf(publicFile.absolutePath), arrayOf(mimeType), null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "iTune")
            dir.mkdirs()
            val destFile = File(dir, fileName)
            sourceFile.copyTo(destFile, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
            Uri.fromFile(destFile)
        }
    }

    private suspend fun loadArtworkBytes(context: Context, task: DownloadTaskState): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        val candidates = listOfNotNull(
            task.track.thumbnailUrl?.highQualityThumbnail(),
            task.track.thumbnailUrl,
            "https://i.ytimg.com/vi/${task.track.id}/maxresdefault.jpg",
            "https://i.ytimg.com/vi/${task.track.id}/hqdefault.jpg"
        ).filter { it.isNotBlank() }.distinct()

        for (url in candidates) {
            val res = runCatching {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", Net.USER_AGENT)
                    .build()
                Net.client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        val mime = resp.header("Content-Type")?.substringBefore(";")?.trim() ?: "image/jpeg"
                        if (bytes != null && bytes.isNotEmpty()) {
                            Pair(bytes, mime)
                        } else null
                    } else null
                }
            }.getOrNull()

            if (res != null) {
                if (task.albumArtBitmap == null) {
                    task.albumArtBitmap = runCatching {
                        BitmapFactory.decodeByteArray(res.first, 0, res.first.size)
                    }.getOrNull()
                }
                return@withContext res
            }
        }

        task.albumArtBitmap?.let { bmp ->
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            val bytes = out.toByteArray()
            if (bytes.isNotEmpty()) {
                return@withContext Pair(bytes, "image/jpeg")
            }
        }

        Pair(ByteArray(0), "image/jpeg")
    }

    private suspend fun loadLyricsForTrack(track: MusicTrack): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (MusicPlayerState.currentTrack?.id == track.id && MusicPlayerState.rawLyricsText.isNotBlank()) {
                return@withContext MusicPlayerState.rawLyricsText
            }
            withTimeoutOrNull(3500L) {
                LyricsHelper.loadLyricsWithRaw(
                    songId = track.id,
                    title = track.title,
                    artist = track.artists,
                    durationSec = track.durationSec.toInt()
                ).rawText.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun buildSubText(task: DownloadTaskState): String {
        val total = totalQueuedCount.get()
        val currentDone = completedCount.get()
        return if (total > 1) {
            "iTune · Track ${currentDone + 1} of $total"
        } else {
            val fmt = task.resolvedOption?.formatLabel ?: task.option?.formatLabel ?: "Audio"
            "iTune · $fmt"
        }
    }

    private fun buildProgressNotification(
        context: Context,
        title: String,
        subText: String,
        contentText: String,
        percent: Int,
        indeterminate: Boolean,
        isPaused: Boolean,
        largeIcon: Bitmap?
    ): Notification {
        val hasMoreInQueue = downloadQueue.isNotEmpty()

        val pauseResumeIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val pauseResumeLabel = if (isPaused) "Resume" else "Pause"
        val pauseResumeIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadActionReceiver.ACTION_PAUSE_RESUME
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, DEFAULT_FOREGROUND_ID)
        }
        val pauseResumePI = PendingIntent.getBroadcast(
            context,
            DEFAULT_FOREGROUND_ID * 10 + 1,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val skipIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadActionReceiver.ACTION_CANCEL
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, DEFAULT_FOREGROUND_ID)
        }
        val skipPI = PendingIntent.getBroadcast(
            context,
            DEFAULT_FOREGROUND_ID * 10 + 2,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelAllIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadActionReceiver.ACTION_CANCEL_ALL
        }
        val cancelAllPI = PendingIntent.getBroadcast(
            context,
            DEFAULT_FOREGROUND_ID * 10 + 3,
            cancelAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = PendingIntent.getActivity(
            context,
            DEFAULT_FOREGROUND_ID + 50_000,
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setSubText(subText)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setSmallIcon(if (isPaused) android.R.drawable.ic_media_pause else android.R.drawable.stat_sys_download)
            .setProgress(100, percent.coerceIn(0, 100), indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent)
            .addAction(pauseResumeIcon, pauseResumeLabel, pauseResumePI)

        if (hasMoreInQueue) {
            builder.addAction(android.R.drawable.ic_media_next, "Skip", skipPI)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel All", cancelAllPI)
        } else {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelAllPI)
        }

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }
        return builder.build()
    }

    private fun showSummaryCompleteNotification(context: Context, totalDownloaded: Int, lastTitle: String?) {
        val openAppIntent = PendingIntent.getActivity(
            context,
            COMPLETE_NOTIFICATION_ID + 50_000,
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (totalDownloaded == 1) "Download Complete" else "Downloads Complete"
        val message = if (totalDownloaded == 1) {
            "${lastTitle ?: "Track"} · Saved to Downloads/iTune"
        } else {
            "Downloaded $totalDownloaded tracks · Saved to Downloads/iTune"
        }

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        safeNotify(context, COMPLETE_NOTIFICATION_ID, notif)
    }

    private fun showFailedNotification(
        context: Context,
        title: String,
        reason: String,
        largeIcon: Bitmap?
    ) {
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Failed · $title")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .apply {
                if (largeIcon != null) setLargeIcon(largeIcon)
            }
            .build()
        safeNotify(context, COMPLETE_NOTIFICATION_ID, notif)
    }

    private suspend fun loadArtworkBitmap(context: Context, track: MusicTrack): Bitmap? {
        val artUrl = track.thumbnailUrl?.highQualityThumbnail() ?: track.thumbnailUrl ?: return null
        return runCatching {
            val req = ImageRequest.Builder(context)
                .data(artUrl)
                .size(Size(256, 256))
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(req)
            (result.drawable as? BitmapDrawable)?.bitmap
        }.getOrNull()
    }

    private fun tryStartService(context: Context) {
        runCatching {
            val intent = Intent(context, AudioDownloadService::class.java).apply {
                action = AudioDownloadService.ACTION_START_OR_SYNC
                putExtra(AudioDownloadService.EXTRA_NOTIFICATION_ID, DEFAULT_FOREGROUND_ID)
                putExtra(AudioDownloadService.EXTRA_TITLE, "iTune Downloads")
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun stopService(context: Context, removeNotification: Boolean) {
        runCatching {
            val intent = Intent(context, AudioDownloadService::class.java).apply {
                action = AudioDownloadService.ACTION_STOP_SERVICE
                putExtra(AudioDownloadService.EXTRA_REMOVE_NOTIFICATION, removeNotification)
            }
            context.startService(intent)
        }
    }

    private fun safeNotify(context: Context, notificationId: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {}
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            val kb = bytes / 1024.0
            String.format(Locale.US, "%.0f KB", kb)
        }
    }

    private fun formatSpeed(bytesPerSec: Double): String {
        val mbPerSec = bytesPerSec / (1024.0 * 1024.0)
        return if (mbPerSec >= 1.0) {
            String.format(Locale.US, "%.1f MB/s", mbPerSec)
        } else {
            val kbPerSec = (bytesPerSec / 1024.0).coerceAtLeast(1.0)
            String.format(Locale.US, "%.0f KB/s", kbPerSec)
        }
    }
}
