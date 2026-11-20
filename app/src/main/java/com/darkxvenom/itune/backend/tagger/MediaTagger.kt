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

package com.darkxvenom.itune.backend.tagger

import android.util.Log
import com.darkxvenom.itune.backend.MusicTrack
import java.io.File
import java.util.Locale

/**
 * Unified entry point to embed metadata (Title, Artist, Album, Album Artist, Lyrics, Cover Art)
 * directly into downloaded audio files (.m4a, .mp3, .ogg, .webm, .flac) without external dependencies.
 */
object MediaTagger {

    private const val TAG = "MediaTagger"

    fun tagAudioFile(
        file: File,
        track: MusicTrack,
        coverBytes: ByteArray?,
        coverMime: String = "image/jpeg",
        lyrics: String? = null,
    ): Boolean {
        if (!file.exists() || file.length() <= 0L) return false

        val ext = file.extension.lowercase(Locale.ROOT)
        val originalBytes = runCatching { file.readBytes() }.getOrNull() ?: return false
        val title = track.title.trim()
        val artist = track.artists.trim()
        val album = track.album?.trim()?.ifBlank { null } ?: title
        val coverIsPng = coverMime.contains("png", ignoreCase = true)

        val taggedBytes = runCatching {
            when (ext) {
                "m4a", "mp4" -> Mp4Tagger.tag(
                    bytes = originalBytes,
                    title = title,
                    artist = artist,
                    album = album,
                    lyrics = lyrics,
                    cover = coverBytes,
                    coverIsPng = coverIsPng,
                )
                "mp3" -> Id3Tagger.tag(
                    bytes = originalBytes,
                    title = title,
                    artist = artist,
                    album = album,
                    lyrics = lyrics,
                    cover = coverBytes,
                    coverMime = coverMime,
                )
                "flac" -> FlacTagger.tag(
                    bytes = originalBytes,
                    title = title,
                    artist = artist,
                    album = album,
                    lyrics = lyrics,
                    cover = coverBytes,
                    coverMime = coverMime,
                )
                "webm", "mkv", "ogg", "opus" -> WebmTagger.tag(
                    bytes = originalBytes,
                    title = title,
                    artist = artist,
                    album = album,
                    lyrics = lyrics,
                    cover = coverBytes,
                    coverMime = coverMime,
                )
                else -> originalBytes
            }
        }.onFailure {
            Log.w(TAG, "Failed tagging ${file.name}: ${it.message}")
        }.getOrDefault(originalBytes)

        if (taggedBytes !== originalBytes && taggedBytes.isNotEmpty()) {
            val written = runCatching {
                file.writeBytes(taggedBytes)
                true
            }.getOrDefault(false)
            if (written) {
                Log.d(TAG, "Successfully embedded metadata & artwork into ${file.name}")
                return true
            }
        }
        return false
    }
}
