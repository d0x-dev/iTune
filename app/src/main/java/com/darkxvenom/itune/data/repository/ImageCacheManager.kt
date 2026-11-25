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

package com.darkxvenom.itune.data.repository

import android.content.Context
import java.io.File
import java.net.URL
import java.net.URLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Caches remote images (library pins, recent albums, browse categories) from GitHub
 * on first load so they never hit the network again.
 *
 * Images are stored in the app's private cache dir as:
 *   context.cacheDir/itune_img_<sanitized-url-hash>.png
 */
object ImageCacheManager {

    private const val CACHE_PREFIX = "itune_img_"
    private const val CACHE_EXT = ".png"

    /**
     * Returns a local file:// URL if the image is already cached, otherwise the original
     * remote URL. The download is kicked off lazily on the calling thread (should be
     * invoked from a background coroutine via [cacheImage]).
     */
    fun cachedImageUrl(url: String, context: Context): String {
        val file = cacheFileFor(url, context)
        return if (file.exists()) file.absolutePath.toFileUri() else url
    }

    /**
     * Downloads [url] once and saves it to the app cache. Safe to call multiple times;
     * subsequent calls are no-ops if the file already exists.
     */
    suspend fun cacheImage(url: String, context: Context) = withContext(Dispatchers.IO) {
        val file = cacheFileFor(url, context)
        if (file.exists()) return@withContext

        val connection: URLConnection = URL(url).openConnection()
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.useCaches = false

        val temporary = File.createTempFile(CACHE_PREFIX, ".tmp", context.cacheDir)
        try {
            connection.inputStream.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            temporary.renameTo(file)
        } catch (_: java.io.IOException) {
            // Coil can still load the original URL when the cache cannot be written.
        } finally {
            temporary.delete()
        }
    }

    private fun cacheFileFor(url: String, context: Context): File {
        val fileName = CACHE_PREFIX + url.hashCode().toString() + CACHE_EXT
        return File(context.cacheDir, fileName)
    }

    private fun String.toFileUri(): String {
        return "file://$this"
    }
}
