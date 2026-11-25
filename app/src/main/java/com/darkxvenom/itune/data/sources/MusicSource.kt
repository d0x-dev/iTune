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

/**
 * A URL the player can open, and what is expected to come back out of it.
 *
 * [headers] travel with the fetch because some sources bind the stream URL to
 * the request that asks for it.
 */
data class SourceStream(
    val url: String,
    val format: StreamFormat = StreamFormat(),
    val headers: Map<String, String> = emptyMap(),
    /**
     * Whether this is less than was asked for, taken because nothing better
     * turned up in time.
     */
    val belowRequest: Boolean = false,
    /**
     * How long the catalogue says this recording is, when it says.
     */
    val durationSec: Int? = null,
    /**
     * Which [MusicSource.configId] this came from, when known.
     */
    val sourceConfigId: String? = null,
    /**
     * Which backend inside a multi-backend source served this, in plain
     * words ("Lossless conversion", "Direct FLAC").
     */
    val backend: String? = null,
)

/**
 * How much of the stream the caller is asking for.
 */
sealed interface StreamRequest {
    /** Bit-exact, lossless stream. */
    data object Lossless : StreamRequest

    /** A transcode at or below [maxKbps]. */
    data class Capped(val maxKbps: Int) : StreamRequest

    /** Best available rendition. */
    data object Best : StreamRequest
}

/**
 * Whether a source is reachable and usable.
 */
sealed interface SourceHealth {
    data class Ok(val detail: String? = null) : SourceHealth
    data class Unreachable(val reason: String) : SourceHealth
    data class Rejected(val reason: String) : SourceHealth

    val isOk: Boolean get() = this is Ok
}

/**
 * One place tracks can come from.
 */
interface MusicSource {
    val configId: String
    val kind: SourceKind
    val displayName: String

    suspend fun health(): SourceHealth

    suspend fun search(
        query: String,
        limit: Int = 25,
        waitForAll: Boolean = false,
        request: StreamRequest? = null,
    ): List<MusicTrack>

    suspend fun stream(trackId: String, request: StreamRequest): SourceStream?
}

val MusicTrack.artist: String get() = artists
val MusicTrack.albumName: String? get() = album
val MusicTrack.durationText: String get() = duration
val MusicTrack.videoId: String get() = id
val MusicTrack.sourceQuality: String? get() = null
