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
import com.darkxvenom.itune.data.tidal.TidalApi
import com.darkxvenom.itune.data.tidal.TidalInstances
import java.util.Locale

private const val TAG = "iTune"

/**
 * Tidal Hi-Fi through compatible instances: genuine lossless FLAC streamed
 * straight from the CDN.
 */
class TidalSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.TIDAL

    companion object {
        const val MIN_GENUINE_KBPS = 300L
    }

    override val displayName: String get() = config.label.ifBlank {
        config.baseUrl.substringAfter("://").substringBefore('/').ifBlank { SourceKind.TIDAL.label }
    }

    override suspend fun health(): SourceHealth {
        val base = TidalApi.normalize(config.baseUrl)
            ?: return SourceHealth.Rejected("Enter a server URL")
        val endpoints = TidalInstances.endpoints(base)
        val answers = endpoints.any { url -> runCatching { TidalApi.isLive(url) }.getOrDefault(false) }
        return if (answers) SourceHealth.Ok() else SourceHealth.Unreachable("no instance reachable")
    }

    override suspend fun search(
        query: String,
        limit: Int,
        waitForAll: Boolean,
        request: StreamRequest?,
    ): List<MusicTrack> {
        for (endpoint in TidalInstances.endpoints(config.baseUrl)) {
            val memo = TidalApi.knownProtocol(endpoint)
            if (memo != TidalApi.Protocol.HIFI) {
                val tracks = try {
                    TidalApi.searchTracks(endpoint, query, limit)
                } catch (e: Exception) {
                    TrackLog.d(TAG, "  ✗ tidal tracks search failed: ${e.message}")
                    null
                }
                if (tracks != null) {
                    TidalApi.noteProtocol(endpoint, TidalApi.Protocol.TRACKS)
                    if (tracks.isEmpty()) continue
                    TrackLog.d(TAG, "  ✓ tidal ${tracks.size} rows (tracks)")
                    return tracks.take(limit).map { it.toMusicTrack(endpoint) }
                }
            }
            if (memo != TidalApi.Protocol.TRACKS) {
                val hifi = try {
                    TidalApi.search(endpoint, query, limit)
                } catch (e: Exception) {
                    TrackLog.d(TAG, "  ✗ tidal hifi search failed: ${e.message}")
                    TidalApi.forgetProtocol(endpoint)
                    continue
                }
                if (hifi.isEmpty()) continue
                TidalApi.noteProtocol(endpoint, TidalApi.Protocol.HIFI)
                TrackLog.d(TAG, "  ✓ tidal ${hifi.size} rows (hifi)")
                return hifi.take(limit).map { it.toMusicTrack(endpoint) }
            }
        }
        return emptyList()
    }

    private fun TidalApi.TidalTrack.toMusicTrack(endpoint: String): MusicTrack {
        val packed = TidalApi.packId(protocol, id)
        TidalApi.memoizeOrigin(packed, endpoint, durationSec)
        val thumb = artDirectUrl ?: coverSlug?.let { TidalApi.coverUrl(it) }
        return MusicTrack(
            id = packed,
            title = title,
            artists = artist.ifBlank { "Unknown Artist" },
            thumbnailUrl = thumb,
            durationSec = durationSec?.toLong() ?: 0L,
            album = album,
            isExplicit = explicit == true,
        )
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        val (protocol, id) = TidalApi.unpackId(trackId)
        if (protocol == TidalApi.Protocol.TRACKS) {
            val origin = TidalApi.originOf(trackId)
            val base = origin?.base
                ?: TidalInstances.endpoints(config.baseUrl).firstOrNull()
                ?: return null
            val url = TidalApi.tracksStreamUrl(base, id)
            val durationSec = origin?.durationSec
            if (durationSec != null && durationSec > 60) {
                val sizeBytes = TidalApi.contentLength(url)
                if (sizeBytes != null && sizeBytes * 8L / durationSec / 1000L < MIN_GENUINE_KBPS) {
                    TrackLog.w(
                        TAG,
                        "  ✗ tidal $id is ${sizeBytes / 1024}KB for ${durationSec}s; refusing fake",
                    )
                    return null
                }
            }
            TrackLog.d(TAG, "  ✓ tidal direct ${url.take(96)}")
            return SourceStream(
                url = url,
                format = StreamFormat(codec = "flac"),
                backend = "Tidal CDN",
            )
        }
        val tiers = when (request) {
            is StreamRequest.Lossless -> TidalApi.LOSSLESS_TIERS
            is StreamRequest.Capped -> if (request.maxKbps >= 1000) {
                listOf(TidalApi.LOSSLESS, TidalApi.HIGH)
            } else {
                listOf(TidalApi.HIGH, TidalApi.LOW)
            }
            is StreamRequest.Best -> listOf(TidalApi.HIGH)
        }
        for (endpoint in TidalInstances.endpoints(config.baseUrl)) {
            val answer = try {
                TidalApi.streamUrl(endpoint, id, tiers)
            } catch (e: Exception) {
                TrackLog.d(TAG, "  ✗ tidal stream failed: ${e.message}")
                continue
            } ?: continue
            val codec = answer.mimeType?.substringAfter('/')?.lowercase(Locale.ROOT)
                ?.takeIf { it.isNotBlank() } ?: "flac"
            TrackLog.d(TAG, "  ✓ tidal ${answer.quality.ifBlank { "FLAC" }} ${answer.url.take(96)}")
            return SourceStream(
                url = answer.url,
                format = StreamFormat(codec = codec),
                belowRequest = request is StreamRequest.Lossless &&
                    answer.quality.isNotBlank() &&
                    answer.quality != TidalApi.HI_RES,
                backend = "Tidal Hi-Fi",
            )
        }
        return null
    }
}
