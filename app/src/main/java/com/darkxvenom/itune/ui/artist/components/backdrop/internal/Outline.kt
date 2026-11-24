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
package com.darkxvenom.itune.ui.artist.components.backdrop.internal

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
// Integrated into iTune by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark

internal fun Canvas.clipOutline(outline: Outline, path: Path?) {
    when (outline) {
        is Outline.Rectangle -> clipRect(outline.rect)
        is Outline.Rounded -> {
            path!!.rewind()
            path.addRoundRect(outline.roundRect)
            clipPath(path)
        }

        is Outline.Generic -> clipPath(outline.path)
    }
}
