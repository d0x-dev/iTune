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

import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.saavn.JioSaavnProvider
import java.util.Locale

private const val TAG = "iTune"

class JioSaavnSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.JIOSAAVN
    override val displayName: String get() = config.label.ifBlank { SourceKind.JIOSAAVN.label }

    override suspend fun health(): SourceHealth = SourceHealth.Ok()

    override suspend fun search(
        query: String,
        limit: Int,
        waitForAll: Boolean,
        request: StreamRequest?,
    ): List<MusicTrack> {
        TrackLog.d(TAG, "▶ JioSaavn search query=\"$query\" limit=$limit")
        val hits = JioSaavnProvider.searchHits(query)
        return hits.take(limit).map { hit ->
            MusicTrack(
                id = SourceRegistry.trackKey(config.id, hit.id),
                title = hit.title,
                artists = hit.artists.joinToString(", ").ifBlank { "Unknown Artist" },
                durationSec = hit.duration.toLong(),
                album = hit.album,
            )
        }
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        val cleanId = SourceRegistry.parseTrackKey(trackId)?.second
            ?: trackId.removePrefix("JS:").substringAfterLast("::").trim()
        val cached = JioSaavnProvider.streamUrlCache[cleanId]
        if (cached != null) {
            return SourceStream(
                url = cached.first,
                format = StreamFormat(codec = "mp4", kbps = cached.second),
                backend = "JioSaavn CDN",
            )
        }
        val hit = JioSaavnProvider.getHitById(cleanId) ?: return null
        val (url, kbps) = JioSaavnProvider.streamForHit(hit) ?: return null
        return SourceStream(
            url = url,
            format = StreamFormat(codec = "mp4", kbps = kbps),
            backend = "JioSaavn CDN",
        )
    }
}
