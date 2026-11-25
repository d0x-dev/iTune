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

package com.darkxvenom.itune.data.sources

import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicTrack

/**
 * YouTube Music, wrapped so it can be ordered alongside other streaming sources.
 */
class YouTubeSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.YOUTUBE
    override val displayName: String get() = config.label.ifBlank { SourceKind.YOUTUBE.label }

    override suspend fun health(): SourceHealth = SourceHealth.Ok()

    override suspend fun search(
        query: String,
        limit: Int,
        waitForAll: Boolean,
        request: StreamRequest?,
    ): List<MusicTrack> {
        return MusicBackend.searchMusic(query).take(limit)
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        val url = MusicBackend.resolveAudioStream(videoId = trackId) ?: return null
        return SourceStream(
            url = url,
            format = StreamFormat(codec = "opus", kbps = 160),
            backend = "YouTube Music Extractor",
        )
    }
}
