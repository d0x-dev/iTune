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

import android.util.Log
import com.darkxvenom.itune.backend.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Diagnostic logger for source resolution and stream extraction.
 */
object TrackLog {
    private const val DEFAULT_TAG = "iTune"

    private data class Line(val at: Long, val level: Char, val text: String, val track: String?)

    private val lines = ArrayDeque<Line>()
    private var totalChars = 0
    private val startedAt = ConcurrentHashMap<String, Long>()
    private val working = ThreadLocal<String?>()

    fun d(tag: String = DEFAULT_TAG, message: String, about: String? = working.get()) {
        Log.d(tag, message)
        record('D', message, about)
    }

    fun i(tag: String = DEFAULT_TAG, message: String, about: String? = working.get()) {
        Log.i(tag, message)
        record('I', message, about)
    }

    fun w(tag: String = DEFAULT_TAG, message: String, about: String? = working.get()) {
        Log.w(tag, message)
        record('W', message, about)
    }

    fun w(tag: String = DEFAULT_TAG, message: String, error: Throwable, about: String? = working.get()) {
        Log.w(tag, message, error)
        record('W', "$message\n${error.stackTraceToString()}", about)
    }

    fun e(tag: String = DEFAULT_TAG, message: String, about: String? = working.get()) {
        Log.e(tag, message)
        record('E', message, about)
    }

    fun e(tag: String = DEFAULT_TAG, message: String, error: Throwable, about: String? = working.get()) {
        Log.e(tag, message, error)
        record('E', "$message\n${error.stackTraceToString()}", about)
    }

    fun markStarted(trackId: String) {
        startedAt[trackId] = System.currentTimeMillis()
    }

    private fun record(level: Char, text: String, track: String?) {
        val trimmed = if (text.length > MAX_LINE_CHARS) text.take(MAX_LINE_CHARS) + "…" else text
        synchronized(lines) {
            lines.addLast(Line(System.currentTimeMillis(), level, trimmed, track))
            totalChars += trimmed.length
            while (totalChars > MAX_HELD_CHARS && lines.isNotEmpty()) {
                val dropped = lines.removeFirst()
                totalChars -= dropped.text.length
            }
        }
    }

    private const val MAX_HELD_CHARS = 256_000
    private const val MAX_LINE_CHARS = 2_000
}
