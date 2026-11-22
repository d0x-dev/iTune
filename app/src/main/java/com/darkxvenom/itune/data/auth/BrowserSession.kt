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

package com.darkxvenom.itune.data.auth

import android.webkit.CookieManager

/**
 * Manages the in-app WebView's Google/YouTube cookie jar locally without calling
 * Google's server-side logout endpoint so existing sessions stay valid when switching accounts.
 */
object BrowserSession {
    private val GOOGLE_ORIGINS = listOf(
        "https://accounts.google.com",
        "https://www.google.com",
        "https://music.youtube.com",
        "https://www.youtube.com",
        "https://youtube.com",
    )

    private val REMOVE_ORIGINS = listOf(
        "https://accounts.google.com",
        "https://music.youtube.com",
        "https://www.youtube.com",
        "https://youtube.com",
        "https://.google.com",
        "https://.youtube.com",
    )

    private val REMOVE_DOMAINS = mapOf(
        "https://accounts.google.com" to "accounts.google.com",
        "https://music.youtube.com" to "music.youtube.com",
        "https://www.youtube.com" to "www.youtube.com",
        "https://youtube.com" to ".youtube.com",
        "https://.google.com" to ".google.com",
        "https://.youtube.com" to ".youtube.com",
    )

    fun clearGoogleCookies() {
        runCatching {
            val cm = CookieManager.getInstance()
            val names = linkedSetOf<String>()
            REMOVE_ORIGINS.forEach { url ->
                cm.getCookie(url)
                    ?.split(';')
                    ?.forEach { entry ->
                        val name = entry.substringBefore('=').trim()
                        if (name.isNotEmpty()) names.add(name)
                    }
            }
            REMOVE_ORIGINS.forEach { url ->
                val domain = REMOVE_DOMAINS[url] ?: return@forEach
                names.forEach { name ->
                    val secureAttr = if (name.startsWith("__Secure-") || name.startsWith("__Host-")) "; Secure" else ""
                    if (name.startsWith("__Host-")) {
                        cm.setCookie(url, "$name=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT$secureAttr")
                    } else {
                        cm.setCookie(url, "$name=; Domain=$domain; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT$secureAttr")
                    }
                }
            }
            cm.removeSessionCookies(null)
            cm.flush()
        }
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    fun installGoogleCookies(cookieHeader: String) {
        if (cookieHeader.isBlank()) return
        runCatching {
            clearGoogleCookies()
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            val entries = parseCookieHeader(cookieHeader)
            entries.forEach { (name, value) ->
                val secure = if (name.startsWith("__Secure-")) "; Secure" else ""
                if (name.startsWith("__Host-")) {
                    cm.setCookie("https://music.youtube.com", "$name=$value; Path=/; Secure")
                } else {
                    cm.setCookie("https://.youtube.com", "$name=$value; Domain=.youtube.com; Path=/$secure")
                    cm.setCookie("https://.google.com", "$name=$value; Domain=.google.com; Path=/$secure")
                    cm.setCookie("https://music.youtube.com", "$name=$value; Path=/$secure")
                    cm.setCookie("https://www.youtube.com", "$name=$value; Path=/$secure")
                }
            }
            cm.flush()
        }
    }

    fun readMergedYouTubeCookies(): String? = runCatching {
        val cm = CookieManager.getInstance()
        val merged = linkedMapOf<String, String>()
        // Query root auth domains first, then YouTube domains, so music.youtube.com specific cookies take precedence.
        listOf(
            "https://accounts.google.com",
            "https://google.com",
            "https://.google.com",
            "https://youtube.com",
            "https://.youtube.com",
            "https://www.youtube.com",
            "https://music.youtube.com",
        ).forEach { url ->
            cm.getCookie(url)?.takeIf { it.isNotBlank() }?.let { header ->
                parseCookieHeader(header).forEach { (k, v) ->
                    if (v.isNotBlank()) merged[k] = v
                }
            }
        }
        if (merged.isEmpty()) return@runCatching null
        merged.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }.getOrNull()
}
