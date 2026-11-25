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

import android.net.Uri
import com.darkxvenom.itune.playback.audio.LosslessOutput
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SourceConfig(
    val id: String = UUID.randomUUID().toString(),
    val kind: SourceKind,
    /** What the user called it. Blank falls back to the server host or kind label. */
    val label: String = "",
    val baseUrl: String = "",
    /** JioSaavn is opt-in. */
    val enabled: Boolean = kind != SourceKind.JIOSAAVN,
    /** User entered settings by key (credentials/tokens). */
    val settings: Map<String, String> = emptyMap(),
    val allowDownloads: Boolean = true,
    val checkValidLossless: Boolean = false,
) {
    val awaitsLosslessOutput: Boolean
        get() = checkValidLossless && !LosslessOutput.capable

    val losslessOutputMissing: Boolean
        get() = awaitsLosslessOutput

    internal fun withPolicyOf(other: SourceConfig) =
        copy(allowDownloads = other.allowDownloads, checkValidLossless = other.checkValidLossless)

    val displayName: String
        get() = when (kind) {
            SourceKind.TIDAL -> SourceRegistry.TIDAL_DISPLAY_NAME
            SourceKind.HIRES -> SourceRegistry.HIRES_DISPLAY_NAME
            else -> label.ifBlank {
                baseUrl.takeIf { it.isNotBlank() }
                    ?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                    ?: kind.label
            }
        }

    val isComplete: Boolean
        get() = !kind.needsServer || baseUrl.isNotBlank()
}
