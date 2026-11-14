package com.darkxvenom.itune.backend

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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

object AudioDownloader {
    private const val TAG = "AudioDownloader"
    private const val CHANNEL_ID = "itune_audio_downloads_v2"
    private const val CHANNEL_NAME = "Audio Downloads"
    const val DEFAULT_FOREGROUND_ID = 2000

    private const val CHUNK_SIZE = 512L * 1024L // 512 KB chunks for fast parallel pulls + instant byte-exact resume
    private const val PARALLEL_WORKERS = 3

    private val nextId = AtomicInteger(2100)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tasks = ConcurrentHashMap<Int, DownloadTaskState>()
    private val notifications = ConcurrentHashMap<Int, Notification>()

    private class DownloadTaskState(
        val id: Int,
        val track: MusicTrack,
        val option: AudioDownloadOption,
        val isPaused: AtomicBoolean = AtomicBoolean(false),
        val isCancelled: AtomicBoolean = AtomicBoolean(false),
        val activeCalls: ConcurrentHashMap<Call, Boolean> = ConcurrentHashMap(),
        @Volatile var downloadedBytes: Long = 0L,
        @Volatile var totalBytes: Long = -1L,
        @Volatile var smoothedSpeedBps: Double = 0.0,
        @Volatile var lastNotifyTimeMs: Long = 0L,
        @Volatile var lastSpeedSampleMs: Long = System.currentTimeMillis(),
        @Volatile var lastSpeedSampleBytes: Long = 0L,
        @Volatile var albumArtBitmap: Bitmap? = null,
        @Volatile var job: Job? = null,
    )

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
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

    fun hasActiveDownloads(): Boolean = tasks.isNotEmpty()

    fun getOrBuildNotification(context: Context, notificationId: Int, fallbackTitle: String): Notification {
        notifications[notificationId]?.let { return it }
        return buildProgressNotification(
            context = context,
            notificationId = notificationId,
            title = fallbackTitle,
            subText = "Audio Download",
            contentText = "Preparing download…",
            percent = 0,
            indeterminate = true,
            isPaused = false,
            largeIcon = null
        )
    }

    fun download(context: Context, track: MusicTrack, option: AudioDownloadOption) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        val notificationId = nextId.incrementAndGet()

        val taskState = DownloadTaskState(
            id = notificationId,
            track = track,
            option = option,
        )
        tasks[notificationId] = taskState

        val initialNotification = buildProgressNotification(
            context = appContext,
            notificationId = notificationId,
            title = track.title,
            subText = option.formatLabel,
            contentText = "0% · Starting download…",
            percent = 0,
            indeterminate = true,
            isPaused = false,
            largeIcon = null
        )
        notifications[notificationId] = initialNotification
        tryStartService(appContext, notificationId, track.title)
        safeNotify(appContext, notificationId, initialNotification)

        taskState.job = scope.launch {
            // Load artwork bitmap asynchronously for notification LargeIcon without blocking download
            launch {
                taskState.albumArtBitmap = loadArtworkBitmap(appContext, track)
            }

            try {
                performAudioDownload(appContext, taskState)
            } catch (cancelled: CancellationException) {
                NotificationManagerCompat.from(appContext).cancel(notificationId)
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (!taskState.isCancelled.get()) {
                    Log.e(TAG, "Audio download failed for '${track.title}'", error)
                    showFailedNotification(
                        appContext,
                        notificationId,
                        track.title,
                        error.message ?: "Download failed",
                        taskState.albumArtBitmap
                    )
                }
            } finally {
                tasks.remove(notificationId)
                notifications.remove(notificationId)
                if (tasks.isEmpty()) {
                    stopService(appContext, removeNotification = true)
                    NotificationManagerCompat.from(appContext).cancel(notificationId)
                } else {
                    val nextEntry = tasks.entries.firstOrNull()
                    if (nextEntry != null) {
                        tryStartService(appContext, nextEntry.key, nextEntry.value.track.title)
                    }
                    NotificationManagerCompat.from(appContext).cancel(notificationId)
                }
            }
        }
    }

    fun togglePause(context: Context, notificationId: Int) {
        val appContext = context.applicationContext
        val task = tasks[notificationId] ?: return
        if (task.isCancelled.get()) return

        val nowPaused = !task.isPaused.get()
        task.isPaused.set(nowPaused)

        if (nowPaused) {
            // Immediately close active HTTP streams so no data is pulled while paused
            task.activeCalls.keys.forEach { call -> runCatching { call.cancel() } }
            task.activeCalls.clear()
            task.smoothedSpeedBps = 0.0
        } else {
            task.lastSpeedSampleMs = System.currentTimeMillis()
            task.lastSpeedSampleBytes = task.downloadedBytes
            task.smoothedSpeedBps = 0.0
        }

        val total = task.totalBytes
        val read = task.downloadedBytes
        val percent = if (total > 0) ((read * 100L) / total).toInt().coerceIn(0, 100) else 0
        val text = if (nowPaused) {
            if (total > 0) {
                "Paused · $percent% (${formatBytes(read)} / ${formatBytes(total)})"
            } else {
                "Paused · ${formatBytes(read)}"
            }
        } else {
            if (total > 0) {
                "Resuming · $percent% (${formatBytes(read)} / ${formatBytes(total)})"
            } else {
                "Resuming · ${formatBytes(read)}"
            }
        }

        val notif = buildProgressNotification(
            context = appContext,
            notificationId = notificationId,
            title = task.track.title,
            subText = task.option.formatLabel,
            contentText = text,
            percent = percent,
            indeterminate = total <= 0 && !nowPaused,
            isPaused = nowPaused,
            largeIcon = task.albumArtBitmap
        )
        notifications[notificationId] = notif
        safeNotify(appContext, notificationId, notif)
    }

    fun cancel(context: Context, notificationId: Int) {
        val appContext = context.applicationContext
        val task = tasks.remove(notificationId)
        if (task != null) {
            task.isCancelled.set(true)
            task.isPaused.set(false)
            task.activeCalls.keys.forEach { call -> runCatching { call.cancel() } }
            task.activeCalls.clear()
            task.job?.cancel(CancellationException("Cancelled by user"))
        }
        notifications.remove(notificationId)
        NotificationManagerCompat.from(appContext).cancel(notificationId)
        if (tasks.isEmpty()) {
            stopService(appContext, removeNotification = true)
        }
    }

    private suspend fun performAudioDownload(
        context: Context,
        task: DownloadTaskState
    ) = withContext(Dispatchers.IO) {
        val cleanTitle = "${task.track.title} - ${task.track.artists}"
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(90)
        val downloadExt = if (task.option.needsOggRemux) "webm" else task.option.extension
        val tempFile = File(context.cacheDir, "itune_dl_${task.id}_${System.currentTimeMillis()}.$downloadExt")
        val remuxedFile = if (task.option.needsOggRemux) {
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

            if (force || now - task.lastNotifyTimeMs >= 420L || (total > 0 && read >= total)) {
                task.lastNotifyTimeMs = now
                val percent = if (total > 0) ((read * 100L) / total).toInt().coerceIn(0, 100) else 0
                val speedText = if (task.smoothedSpeedBps > 512.0) " · ${formatSpeed(task.smoothedSpeedBps)}" else ""
                val text = if (total > 0) {
                    "$percent% · ${formatBytes(read)} / ${formatBytes(total)}$speedText"
                } else {
                    "${formatBytes(read)}$speedText"
                }
                val notif = buildProgressNotification(
                    context = context,
                    notificationId = task.id,
                    title = task.track.title,
                    subText = task.option.formatLabel,
                    contentText = text,
                    percent = percent,
                    indeterminate = total <= 0,
                    isPaused = false,
                    largeIcon = task.albumArtBitmap
                )
                notifications[task.id] = notif
                safeNotify(context, task.id, notif)
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

            val actualExt = if (sourceToSave === remuxedFile) "ogg" else task.option.extension
            val actualMime = if (sourceToSave === remuxedFile) "audio/ogg" else task.option.mimeType
            val finalFileName = "$cleanTitle [${task.option.bitrateKbps}kbps].$actualExt"
            val savedUri = saveToDownloads(context, sourceToSave, finalFileName, actualMime)
                ?: throw IOException("Could not save audio to Downloads")
            task.albumArtBitmap?.let { bitmap ->
                saveArtworkToDownloads(context, bitmap, finalFileName)
            }

            showCompleteNotification(
                context = context,
                notificationId = task.id,
                title = task.track.title,
                detailText = "${task.option.formatLabel} · ${formatBytes(sourceToSave.length())} · Saved to Downloads/iTune",
                fileUri = savedUri,
                mimeType = actualMime,
                largeIcon = task.albumArtBitmap
            )
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
        targetFile.parentFile?.mkdirs()

        // 1. Probe content length and Range support
        var totalBytes = -1L
        var supportsRange = false
        try {
            val probeReq = Request.Builder()
                .url(task.option.streamUrl)
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
                                    .url(task.option.streamUrl)
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
                                                // Paused cleanly mid-chunk: exit stream read so we don't block socket
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
        initialTotalBytes: Long,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        var bytesWritten = 0L
        var totalBytes = initialTotalBytes
        val buffer = ByteArray(32 * 1024)

        while (!task.isCancelled.get() && isActive) {
            while (task.isPaused.get() && !task.isCancelled.get() && isActive) {
                delay(150L)
            }
            if (task.isCancelled.get() || !isActive) break

            val reqBuilder = Request.Builder()
                .url(task.option.streamUrl)
                .header("User-Agent", Net.USER_AGENT)
            if (bytesWritten > 0L) {
                reqBuilder.header("Range", "bytes=$bytesWritten-")
            }
            val call = Net.client.newCall(reqBuilder.build())
            task.activeCalls[call] = true

            var pausedMidStream = false
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("Network error ${resp.code}")
                    val body = resp.body ?: throw IOException("Empty response body")
                    val appendMode = resp.code == 206 && bytesWritten > 0L
                    if (!appendMode && bytesWritten > 0L) {
                        bytesWritten = 0L
                    }
                    if (totalBytes <= 0L) {
                        val contentLen = body.contentLength()
                        if (contentLen > 0L) {
                            totalBytes = if (appendMode) bytesWritten + contentLen else contentLen
                        }
                    }
                    java.io.FileOutputStream(targetFile, appendMode).use { out ->
                        body.byteStream().use { input ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
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

    private fun saveToDownloads(context: Context, sourceFile: File, fileName: String, mimeType: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
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
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
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

    private fun saveArtworkToDownloads(context: Context, bitmap: Bitmap, audioFileName: String): String? {
        val imageName = audioFileName.substringBeforeLast('.') + "-cover.jpg"
        val temp = File(context.cacheDir, "itune_cover_${System.currentTimeMillis()}.jpg")
        return try {
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, imageName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/iTune")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output -> temp.inputStream().use { it.copyTo(output) } }
                        ?: throw IOException("Could not save artwork")
                    context.contentResolver.update(uri, ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }, null, null)
                } catch (error: Exception) {
                    context.contentResolver.delete(uri, null, null)
                    throw error
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "iTune")
                dir.mkdirs()
                val artworkFile = File(dir, imageName)
                temp.copyTo(artworkFile, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(artworkFile.absolutePath), arrayOf("image/jpeg"), null)
            }
            @Suppress("DEPRECATION")
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "iTune/$imageName").absolutePath
        } catch (error: Exception) {
            Log.w(TAG, "Could not save download artwork", error)
            null
        } finally {
            temp.delete()
        }
    }

    private fun buildProgressNotification(
        context: Context,
        notificationId: Int,
        title: String,
        subText: String,
        contentText: String,
        percent: Int,
        indeterminate: Boolean,
        isPaused: Boolean,
        largeIcon: Bitmap?
    ): Notification {
        val pauseResumeIcon = if (isPaused) {
            android.R.drawable.ic_media_play
        } else {
            android.R.drawable.ic_media_pause
        }
        val pauseResumeLabel = if (isPaused) "Resume" else "Pause"

        val pauseResumeIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadActionReceiver.ACTION_PAUSE_RESUME
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, notificationId)
        }
        val pauseResumePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadActionReceiver.ACTION_CANCEL
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, notificationId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = PendingIntent.getActivity(
            context,
            notificationId + 50_000,
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent)
            .addAction(pauseResumeIcon, pauseResumeLabel, pauseResumePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }
        return builder.build()
    }

    private fun showCompleteNotification(
        context: Context,
        notificationId: Int,
        title: String,
        detailText: String,
        fileUri: Uri?,
        mimeType: String,
        largeIcon: Bitmap?
    ) {
        val pendingIntent = fileUri?.let { uri ->
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            PendingIntent.getActivity(
                context,
                notificationId,
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Complete · $title")
            .setContentText(detailText)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (largeIcon != null) builder.setLargeIcon(largeIcon)
        if (pendingIntent != null) builder.setContentIntent(pendingIntent)

        safeNotify(context, notificationId + 10_000, builder.build())
    }

    private fun showFailedNotification(
        context: Context,
        notificationId: Int,
        title: String,
        reason: String,
        largeIcon: Bitmap?
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Failed · $title")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (largeIcon != null) builder.setLargeIcon(largeIcon)
        safeNotify(context, notificationId + 10_000, builder.build())
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

    private fun tryStartService(context: Context, notificationId: Int, title: String) {
        runCatching {
            val intent = Intent(context, AudioDownloadService::class.java).apply {
                action = AudioDownloadService.ACTION_START_OR_SYNC
                putExtra(AudioDownloadService.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(AudioDownloadService.EXTRA_TITLE, title)
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
