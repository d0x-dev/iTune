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

package com.darkxvenom.itune.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Prepares the slow-changing artwork backdrop away from Compose's frame loop. */
internal object DynamicArtworkProcessor {
    private const val SIZE = 192
    private const val BLUR_RADIUS = 16
    private const val SATURATION = 1.7f
    private const val BRIGHTNESS = 0.56f

    // Secondary style: crisp resolution and lighter optical blur
    private const val SIZE_SECONDARY = 320
    private const val BLUR_RADIUS_SECONDARY = 6
    private const val SATURATION_SECONDARY = 1.35f
    private const val BRIGHTNESS_SECONDARY = 0.62f

    private const val CACHE_BYTES = 8 * 320 * 320 * 4

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val nativeAvailable = runCatching { System.loadLibrary("itune_artwork") }.isSuccess

    suspend fun load(
        context: Context,
        imageUrl: String,
        isSecondary: Boolean = false,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = if (isSecondary) "${imageUrl}_secondary" else imageUrl
        cache.get(cacheKey)?.let { return@withContext it }

        val targetSize = if (isSecondary) SIZE_SECONDARY else SIZE
        val blurRadius = if (isSecondary) BLUR_RADIUS_SECONDARY else BLUR_RADIUS
        val saturation = if (isSecondary) SATURATION_SECONDARY else SATURATION
        val brightness = if (isSecondary) BRIGHTNESS_SECONDARY else BRIGHTNESS

        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(Size(targetSize, targetSize))
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withContext null
        val decoded = result.drawable.toBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val source = decoded.copy(Bitmap.Config.ARGB_8888, true) ?: return@withContext null
        val processed = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val completed = nativeAvailable && runCatching {
            blurAndGrade(source, processed, blurRadius, saturation, brightness)
        }.getOrDefault(false)
        val backdrop = if (completed) processed else source
        cache.put(cacheKey, backdrop)
        backdrop
    }

    private external fun blurAndGrade(
        source: Bitmap,
        target: Bitmap,
        radius: Int,
        saturation: Float,
        brightness: Float,
    ): Boolean
}
