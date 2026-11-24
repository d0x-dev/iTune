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

import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.Paint
import com.darkxvenom.itune.ui.artist.components.backdrop.RuntimeShader
import com.darkxvenom.itune.ui.artist.components.backdrop.asAndroidRuntimeShader

internal fun Paint.blur(radius: Float) {
    this.asFrameworkPaint().maskFilter =
        if (radius > 0f) BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        else null
}
// Integrated into iTune by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark

internal fun Paint.setRuntimeShader(runtimeShader: RuntimeShader?) {
    this.asFrameworkPaint().shader = runtimeShader?.asAndroidRuntimeShader()
}
