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

package com.darkxvenom.itune.data.festival

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.darkxvenom.itune.backend.Net
import com.darkxvenom.itune.data.updates.AppUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.CacheControl
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

@Immutable
data class FestivalConfig(
    val enabled: Boolean = false,
    val text: String = "",
    val colorHex: String = "",
    val color: Color = Color.Unspecified,
)

/**
 * Remote Dynamic Festival Mode manager for iTune.
 * Retrieves and parses configuration from the remote GitHub endpoint safely and asynchronously.
 * Caches the last valid configuration in SharedPreferences for offline and instant startup support.
 */
object FestivalManager {
    private const val TAG = "FestivalManager"
    const val CONFIG_URL = "https://raw.githubusercontent.com/d0x-dev/iTune/refs/heads/main/assets/festivals/festivals.json"
    private const val PREFS_NAME = "itune_festival_prefs"
    private const val KEY_CACHED_JSON = "cached_festival_json"

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val isFetching = AtomicBoolean(false)
    private var appPrefs: SharedPreferences? = null

    private val _config = MutableStateFlow(FestivalConfig(enabled = false))
    val config: StateFlow<FestivalConfig> = _config.asStateFlow()

    /** Ensures the animation sequence executes only once per app session */
    var hasPlayedInSession: Boolean = false

    fun init(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        appPrefs = prefs

        // 1. Immediately populate from local cache if available so startup is instant
        val cachedJson = prefs.getString(KEY_CACHED_JSON, null)
        if (!cachedJson.isNullOrBlank()) {
            _config.value = parseFestivalConfig(cachedJson)
        }

        // 2. Fetch remote configuration
        fetchRemoteConfig(prefs)
    }

    fun refresh() {
        val prefs = appPrefs ?: return
        fetchRemoteConfig(prefs)
    }

    private fun fetchRemoteConfig(prefs: SharedPreferences) {
        if (!isFetching.compareAndSet(false, true)) return

        scope.launch {
            try {
                val nocacheUrl = "$CONFIG_URL?nocache=${System.currentTimeMillis()}"
                val request = Request.Builder()
                    .url(nocacheUrl)
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Expires", "0")
                    .header("User-Agent", Net.USER_AGENT)
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .get()
                    .build()

                Net.client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val rawJson = response.body?.string().orEmpty().trim()
                        if (rawJson.isNotEmpty()) {
                            val parsed = parseFestivalConfig(rawJson)
                            _config.value = parsed
                            prefs.edit().putString(KEY_CACHED_JSON, rawJson).apply()
                            Log.i(TAG, "Festival configuration updated: enabled=${parsed.enabled}, text='${parsed.text}', color='${parsed.colorHex}'")
                        }
                    } else {
                        Log.w(TAG, "Festival config response code: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load festival config: ${e.message}")
            } finally {
                isFetching.set(false)
            }
        }
    }

    /**
     * Parses the festival JSON safely according to requirements:
     * - Checks "festival" boolean first.
     * - If false, text and color are not required or validated.
     * - If true, validates non-blank "text" and valid hex "color".
     * - Optional "version": a single version ("1.2.0"), a list (["1.2.0", "1.2.1"]) or a comma-separated
     *   string. The festival applies only when the installed app version is in it; otherwise it is ignored.
     *   Without a "version" field the festival applies to every version (as before).
     */
    fun parseFestivalConfig(rawJson: String): FestivalConfig {
        return try {
            val trimmed = rawJson.trim()
            if (trimmed.isEmpty()) return FestivalConfig(enabled = false)

            val json = JSONObject(trimmed)
            val festival = json.optBoolean("festival", false)
            if (!festival) {
                return FestivalConfig(enabled = false)
            }

            if (!appliesToThisVersion(json)) {
                Log.i(TAG, "Festival ignored: installed ${AppUpdateManager.installedVersion} is not in its version list")
                return FestivalConfig(enabled = false)
            }

            val text = json.optString("text", "").trim()
            val colorHex = json.optString("color", "").trim()

            if (text.isEmpty() || colorHex.isEmpty()) {
                return FestivalConfig(enabled = false)
            }

            val parsedColor = parseHexColor(colorHex) ?: return FestivalConfig(enabled = false)

            FestivalConfig(
                enabled = true,
                text = text,
                colorHex = colorHex,
                color = parsedColor,
            )
        } catch (_: Exception) {
            FestivalConfig(enabled = false)
        }
    }

    private fun normVersion(v: String): String = v.trim().removePrefix("v").removePrefix("V").trim()

    /** True when "version" is absent, or lists this app's version name (or version code). */
    private fun appliesToThisVersion(json: JSONObject): Boolean {
        if (!json.has("version") || json.isNull("version")) return true
        val raw = json.opt("version")
        val listed: List<String> = when (raw) {
            is org.json.JSONArray -> (0 until raw.length()).map { raw.opt(it)?.toString().orEmpty() }
            else -> raw?.toString().orEmpty().split(',', ';', '|')
        }.map(::normVersion).filter { it.isNotEmpty() }
        if (listed.isEmpty()) return true
        val name = normVersion(AppUpdateManager.installedVersion)
        val code = AppUpdateManager.installedVersionCode.toString()
        return listed.any { it.equals(name, ignoreCase = true) || it == code }
    }

    fun parseHexColor(hex: String): Color? {
        return try {
            val clean = hex.trim().removePrefix("#")
            when (clean.length) {
                3 -> {
                    val r = clean.substring(0, 1).repeat(2).toInt(16)
                    val g = clean.substring(1, 2).repeat(2).toInt(16)
                    val b = clean.substring(2, 3).repeat(2).toInt(16)
                    Color(r, g, b)
                }
                6 -> {
                    val num = clean.toLong(16)
                    val r = ((num shr 16) and 0xFF).toInt()
                    val g = ((num shr 8) and 0xFF).toInt()
                    val b = (num and 0xFF).toInt()
                    Color(r, g, b)
                }
                8 -> {
                    val num = clean.toLong(16)
                    val a = ((num shr 24) and 0xFF).toInt()
                    val r = ((num shr 16) and 0xFF).toInt()
                    val g = ((num shr 8) and 0xFF).toInt()
                    val b = (num and 0xFF).toInt()
                    Color(r, g, b, a)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
