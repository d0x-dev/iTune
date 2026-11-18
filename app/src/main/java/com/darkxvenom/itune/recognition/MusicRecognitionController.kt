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

package com.darkxvenom.itune.recognition

import kotlinx.coroutines.flow.MutableStateFlow

object MusicRecognitionController {
    /**
     * Observable flag to notify UI (e.g. HomeScreen) that recognition screen should open.
     */
    val openRecognitionRequested = MutableStateFlow(false)

    /**
     * Cached last recognition result.
     */
    val lastResult = MutableStateFlow<IRecognitionResult?>(null)
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    fun requestOpen() {
        openRecognitionRequested.value = true
    }

    fun consumeOpenRequest(): Boolean {
        val current = openRecognitionRequested.value
        if (current) openRecognitionRequested.value = false
        return current
    }
}
