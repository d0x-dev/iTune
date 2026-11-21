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

package com.darkxvenom.itune.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * What really happened to get a track's audio: which source supplied the stream URL, how it was found
 * and picked, and the quality it delivers. Shown on the Signal Pipeline page (player + settings).
 */
data class StreamTrace(
    /** "JioSaavn", "YouTube Music", "Local cache (YouTube)", "Local cache (JioSaavn)". */
    val source: String,
    /** Quality of the chosen stream, e.g. "320 kbps • AAC (MP4)". Empty when unknown (cache). */
    val quality: String,
    /** Ordered, human-readable steps, oldest first. */
    val steps: List<String>,
)

object StreamTraceStore {
    private val _traces = MutableStateFlow<Map<String, StreamTrace>>(emptyMap())
    val traces: StateFlow<Map<String, StreamTrace>> = _traces

    fun put(trackId: String, trace: StreamTrace) = _traces.update { cur ->
        val base = if (cur.size > 40) cur.entries.drop(20).associate { it.key to it.value } else cur
        base + (trackId to trace)
    }

    /** Adds a step to the trace being built for [trackId] (starting a new one if none is current). */
    fun addStep(trackId: String, step: String) = _traces.update { cur ->
        val t = cur[trackId] ?: StreamTrace("Resolving…", "", emptyList())
        cur + (trackId to t.copy(steps = t.steps + step))
    }

    fun begin(trackId: String) = _traces.update { it + (trackId to StreamTrace("Resolving…", "", emptyList())) }
}
