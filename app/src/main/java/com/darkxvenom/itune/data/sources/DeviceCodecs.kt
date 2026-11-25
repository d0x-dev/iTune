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

import android.media.MediaCodecList
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import java.util.Locale

/**
 * What this device can actually decode, queried once and cached.
 */
object DeviceCodecs {

    private const val TAG = "iTune"

    private val DOLBY_MIMES = listOf("audio/eac3-joc", "audio/eac3")

    @Volatile
    private var probed: Boolean? = null

    @Volatile
    internal var forced: Boolean? = null

    val playsDolbyAtmos: Boolean
        get() = forced ?: probed ?: probe().also { probed = it }

    private fun probe(): Boolean {
        val viaMedia3 = media3Decoders()
        if (viaMedia3 != null) {
            TrackLog.d(TAG, "Dolby Atmos decoders (Media3): ${viaMedia3.ifEmpty { listOf("none") }}")
            return viaMedia3.isNotEmpty()
        }
        return runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                !info.isEncoder && info.supportedTypes.any {
                    it.lowercase(Locale.ROOT) in DOLBY_MIMES
                }
            }.map { it.name }
        }.onSuccess {
            TrackLog.d(TAG, "Dolby Atmos decoders (platform): ${it.ifEmpty { listOf("none") }}")
        }.map {
            it.isNotEmpty()
        }.getOrElse {
            TrackLog.w(TAG, "could not read the codec list; assuming Dolby Atmos plays — ${it.message}")
            true
        }
    }

    @UnstableApi
    private fun media3Decoders(): List<String>? = runCatching {
        DOLBY_MIMES.flatMap { MediaCodecUtil.getDecoderInfos(it, false, false) }
            .map { it.name }
            .distinct()
    }.getOrElse {
        TrackLog.w(TAG, "Media3 could not enumerate Dolby decoders — ${it.message}")
        null
    }
}
