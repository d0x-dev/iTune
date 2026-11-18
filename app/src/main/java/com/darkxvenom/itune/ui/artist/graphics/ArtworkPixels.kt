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

package com.darkxvenom.itune.ui.artist.graphics

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.request.ImageRequest

class Swatch(val rgb: Int, val population: Int)

fun ImageRequest.Builder.forPixelAccess(): ImageRequest.Builder = allowHardware(false)
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

fun Drawable.toImageBitmap(): ImageBitmap =
    toBitmap().copy(Bitmap.Config.ARGB_8888, false).asImageBitmap()

fun imageBitmapOf(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

fun ImageBitmap.argbPixels(): IntArray =
    IntArray(width * height).also { readPixels(it) }

fun paletteSwatches(image: ImageBitmap, maxColors: Int, clearFilters: Boolean): List<Swatch> {
    val builder = Palette.from(image.asAndroidBitmap()).maximumColorCount(maxColors)
    if (clearFilters) builder.clearFilters()
    return builder.generate().swatches.map { Swatch(it.rgb, it.population) }
}
