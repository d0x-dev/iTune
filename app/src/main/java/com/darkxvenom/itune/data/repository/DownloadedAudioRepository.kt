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

package com.darkxvenom.itune.data.repository

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.darkxvenom.itune.data.model.Song
import java.io.File

/** Reads audio saved by iTune in the public Downloads/iTune folder. */
internal object DownloadedAudioRepository {
    private val audioExtensions = setOf("aac", "amr", "flac", "m4a", "mp3", "ogg", "opus", "wav", "webm")

    fun load(context: Context): List<Song> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        loadFromMediaStore(context)
    } else {
        loadFromLegacyFolder(context)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun loadFromMediaStore(context: Context): List<Song> {
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.TITLE,
            MediaStore.Downloads.MIME_TYPE,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_ADDED,
        )
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/iTune/"
        val songs = mutableListOf<Song>()

        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(relativePath),
            "${MediaStore.Downloads.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.TITLE)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn).orEmpty()
                val mimeType = cursor.getString(mimeColumn).orEmpty()
                if (!isAudioFile(name, mimeType)) continue

                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                songs += createSong(
                    context = context,
                    id = "download_$id",
                    uri = uri,
                    displayName = name,
                    title = cursor.getString(titleColumn).orEmpty(),
                    fileSizeBytes = cursor.getLong(sizeColumn),
                    dateAddedSeconds = cursor.getLong(dateColumn),
                )
            }
        }
        return songs
    }

    @Suppress("DEPRECATION")
    private fun loadFromLegacyFolder(context: Context): List<Song> {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "iTune",
        )
        return directory.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && isAudioFile(it.name, "") }
            .sortedByDescending(File::lastModified)
            .map { file ->
                val modifiedSeconds = file.lastModified() / 1_000L
                createSong(
                    context = context,
                    id = "download_${file.absolutePath.hashCode()}",
                    uri = Uri.fromFile(file),
                    displayName = file.name,
                    title = "",
                    fileSizeBytes = file.length(),
                    dateAddedSeconds = modifiedSeconds,
                )
            }
            .toList()
    }

    private fun isAudioFile(displayName: String, mimeType: String): Boolean {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return mimeType.startsWith("audio/", ignoreCase = true) || extension in audioExtensions
    }

    private fun createSong(
        context: Context,
        id: String,
        uri: Uri,
        displayName: String,
        title: String,
        fileSizeBytes: Long,
        dateAddedSeconds: Long,
    ): Song {
        val baseName = displayName.substringBeforeLast('.', displayName)
        val filenameTitle = baseName.substringBefore(" - ", baseName).trim()
        val artist = baseName.substringAfter(" - ", "").trim()
        val parsedTitle = title.trim().ifBlank { filenameTitle }

        return Song(
            videoId = id,
            title = parsedTitle.ifBlank { "Downloaded song" },
            artist = artist,
            thumbnailUrl = extractEmbeddedArtwork(context, uri),
            localUri = uri.toString(),
            downloadFormat = displayName.substringAfterLast('.', "").uppercase().ifBlank { null },
            localDateAddedSeconds = dateAddedSeconds.takeIf { it > 0L },
            localFileSizeBytes = fileSizeBytes.coerceAtLeast(0L),
        )
    }

    /**
     * Extracts embedded album art from a downloaded audio file and caches it locally so the
     * library can render covers for offline tracks even when the source is no longer online.
     * Returns a `file://` URI to the cached artwork, or null if no embedded art exists.
     */
    private fun extractEmbeddedArtwork(context: Context, audioUri: Uri): String? {
        return runCatching {
            val retriever = MediaMetadataRetriever().apply {
                setDataSource(context, audioUri)
            }
            val artBytes = retriever.embeddedPicture
            retriever.release()
            artBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
                val cacheFile = File(
                    context.cacheDir,
                    "itune_dl_art_${audioUri.lastPathSegment ?: System.currentTimeMillis()}.jpg",
                )
                cacheFile.writeBytes(bytes)
                "file://${cacheFile.absolutePath}"
            } ?: null
        }.getOrDefault(null)
    }
}
