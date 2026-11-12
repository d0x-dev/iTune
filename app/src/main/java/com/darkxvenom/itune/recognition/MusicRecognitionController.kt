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

    fun requestOpen() {
        openRecognitionRequested.value = true
    }

    fun consumeOpenRequest(): Boolean {
        val current = openRecognitionRequested.value
        if (current) openRecognitionRequested.value = false
        return current
    }
}
