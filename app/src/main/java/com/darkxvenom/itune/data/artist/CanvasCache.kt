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

package com.darkxvenom.itune.data.artist

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@UnstableApi
object CanvasCache {

    private const val CACHE_LIMIT_BYTES = 150L * 1024 * 1024

    @Volatile
    private var cache: SimpleCache? = null
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    fun ensureInit(context: Context): SimpleCache {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: SimpleCache(
                File(context.applicationContext.cacheDir, "canvas"),
                LeastRecentlyUsedCacheEvictor(CACHE_LIMIT_BYTES),
                StandaloneDatabaseProvider(context.applicationContext),
            ).also { cache = it }
        }
    }

    fun dataSourceFactory(context: Context, upstream: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(ensureInit(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
