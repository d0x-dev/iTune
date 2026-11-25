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
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

private const val TAG = "iTune"

/**
 * iTune Hi-Res Lossless: direct FLAC files from open verified catalogues.
 */
class HiresDirectSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.HIRES

    override val displayName: String get() = config.label.ifBlank {
        SourceRegistry.HIRES_DISPLAY_NAME
    }

    override suspend fun health(): SourceHealth {
        val catalogueOk = try {
            HiResMonochromeBackend.probe().also {
                if (it) TrackLog.d(TAG, "  ✓ hires direct catalogue answers")
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            false
        }
        if (!catalogueOk) return SourceHealth.Unreachable("no Hi-Res catalogue answered")
        return SourceHealth.Ok()
    }

    override suspend fun search(
        query: String,
        limit: Int,
        waitForAll: Boolean,
        request: StreamRequest?,
    ): List<MusicTrack> {
        val q = query.trim().takeIf { it.isNotEmpty() } ?: return emptyList()
        val rows = try {
            HiResMonochromeBackend.searchTracks(q, limit.coerceIn(1, 50))
                .filter { HiResMonochromeBackend.isPlayableMatch(q, it.title) }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            TrackLog.d(TAG, "  ✗ hires direct search failed: ${e.message}")
            emptyList()
        }.distinctBy { it.id }
        if (rows.isNotEmpty()) TrackLog.d(TAG, "  ✓ hires direct ${rows.size} rows")
        return rows.take(limit).map { it.toMusicTrack() }
    }

    private fun HiResMonochromeBackend.MonoTrack.toMusicTrack(): MusicTrack {
        val packed = HiResMonochromeBackend.packId(id)
        val durSec = HiResMonochromeBackend.normalizeDurationMs(durationMs)?.div(1000)?.toLong() ?: 0L
        HiResMonochromeBackend.memoizeOrigin(
            packed,
            HiResMonochromeBackend.Origin(id, HiResMonochromeBackend.normalizeDurationMs(durationMs)),
        )
        return MusicTrack(
            id = packed,
            title = title,
            artists = artistNames.joinToString(", ").ifBlank { "Unknown Artist" },
            thumbnailUrl = artwork,
            durationSec = durSec,
            album = albumTitle ?: releaseTitle,
            isExplicit = explicit == true,
        )
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        val origin = HiResMonochromeBackend.originOf(trackId)
        val id = origin?.id ?: trackId.removePrefix("m:")
        val resolved = try {
            HiResMonochromeBackend.resolveFile(id, origin?.durationMs)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            TrackLog.d(TAG, "  ✗ hires direct stream resolve failed: ${e.message}")
            null
        } ?: return null

        val format = StreamFormat(
            codec = "flac",
            kbps = resolved.kbps,
            sampleRateHz = resolved.sampleRateHz,
            bitDepth = resolved.bitDepth,
        )
        val durSec = origin?.durationMs?.div(1000)?.toInt()
        TrackLog.d(TAG, "hires direct: serving $id at ${format.summary}")
        return SourceStream(
            url = resolved.url,
            format = format,
            durationSec = durSec,
            backend = "Direct FLAC",
        )
    }
}
