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

package com.darkxvenom.itune.playback

import androidx.media3.common.MimeTypes
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

/**
 * Whether the URL serving a track points at direct audio or at a manifest (HLS / DASH).
 */
object StreamContainer {

    fun markedForManifestReopen(uri: String, mime: String): String {
        val transport = when (mime) {
            MimeTypes.APPLICATION_MPD -> "dash"
            MimeTypes.APPLICATION_M3U8 -> "hls"
            else -> return uri
        }
        val fragmentAt = uri.indexOf('#')
        val base = if (fragmentAt >= 0) uri.substring(0, fragmentAt) else uri
        val fragment = if (fragmentAt >= 0) uri.substring(fragmentAt) else ""
        val separator = if ('?' in base) '&' else '?'
        return "$base$separator$REOPEN_PARAMETER=$transport$fragment"
    }

    fun manifestMimeOf(url: String): String? =
        declared[url]
            ?: when (url.substringBefore('?').substringAfterLast('.').lowercase(Locale.ROOT)) {
                "m3u8" -> MimeTypes.APPLICATION_M3U8
                "mpd" -> MimeTypes.APPLICATION_MPD
                else -> null
            }

    fun declare(url: String, transport: String) {
        val mime = when (transport.lowercase(Locale.ROOT)) {
            "hls" -> MimeTypes.APPLICATION_M3U8
            "dash" -> MimeTypes.APPLICATION_MPD
            else -> return
        }
        if (declared.size >= MAX_REMEMBERED) declared.clear()
        declared[url] = mime
    }

    private val declared = ConcurrentHashMap<String, String>()

    fun isManifest(url: String) = manifestMimeOf(url) != null

    private val serving = ConcurrentHashMap<String, String>()

    fun served(mediaId: String, url: String) {
        if (serving.size >= MAX_REMEMBERED) serving.clear()
        serving[mediaId] = url
    }

    fun manifestServing(mediaId: String): String? = serving[mediaId]?.let(::manifestMimeOf)

    private const val REOPEN_PARAMETER = "manifest_reopen"
    private const val MAX_REMEMBERED = 64
}
