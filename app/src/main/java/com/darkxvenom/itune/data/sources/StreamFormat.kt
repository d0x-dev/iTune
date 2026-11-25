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

import java.util.Locale

/**
 * What a source is about to hand the decoder, as far as the source will say.
 * Includes codec, kbps, sampleRateHz, bitDepth, and tier calculation.
 */
data class StreamFormat(
    val codec: String? = null,
    val kbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
) {
    val isLossless: Boolean?
        get() = codec?.let { it.lowercase() in LOSSLESS_CODECS }

    val isHiRes: Boolean
        get() = isLossless == true && ((sampleRateHz ?: 0) > 48000 || (bitDepth ?: 0) > 16)

    val isDolbyAtmos: Boolean
        get() = codec?.lowercase() in DOLBY_ATMOS_CODECS

    val summary: String
        get() = if (isDolbyAtmos) "Dolby Atmos" else listOfNotNull(
            codec?.uppercase(Locale.ROOT),
            bitDepth?.let { "$it-bit" },
            sampleRateHz?.let { "${"%.1f".format(Locale.ROOT, it / 1000f).removeSuffix(".0")} kHz" },
            kbps?.takeIf { isLossless != true }?.let { "$it kbps" },
        ).joinToString(" · ").ifEmpty { "Standard" }

    val tierLabel: String
        get() = when {
            isHiRes -> "Hi-Res Lossless"
            isLossless == true -> "Lossless"
            isDolbyAtmos -> "Dolby Atmos"
            (kbps ?: 0) >= 256 -> "High Quality"
            else -> "Standard"
        }

    private companion object {
        val LOSSLESS_CODECS = setOf("flac", "alac", "wav", "aiff", "ape", "wv", "dsf", "dff")
        val DOLBY_ATMOS_CODECS = setOf("eac3-joc", "ec3-joc", "dolby-atmos")
    }
}
