/* Copyright (C) 2026 DxV Studio ~~ Dark
 * Originally created by DxV Studio ~~ Dark.
 * Credits: DxV Studio, AirBeats, and Dark.
 *
 * This file is part of iTune and is licensed under the GNU General Public License v3.0 (GPL-3.0).
 * Any copying, modification, or redistribution of this code must strictly comply with the
 * terms of the project LICENSE file and retain full author credit.
 */

package com.darkxvenom.itune.backend

import okhttp3.HttpUrl
import okhttp3.Request
import java.util.Locale

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

/**
 * Shared utility for resolving the correct User-Agent, Origin, and Referer headers
 * based on the stream client query parameters embedded in YouTube stream URLs.
 * Ported from and referenced with AirBeats playback architecture.
 */
object StreamClientUtils {
    const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    const val USER_AGENT_IOS = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)"
    const val USER_AGENT_IOS_MUSIC = "com.google.ios.youtubemusic/7.02.2 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)"
    const val USER_AGENT_ANDROID = "com.google.android.youtube/21.10.38 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; Cronet/132.0.6834.79) gzip"
    const val USER_AGENT_ANDROID_MUSIC = "com.google.android.apps.youtube.music/7.02.52 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; Cronet/132.0.6834.79) gzip"
    const val USER_AGENT_TV = "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15"
    const val USER_AGENT_VISIONOS = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"
    const val USER_AGENT_ANDROID_VR = "com.google.android.apps.youtube.vr.oculus/1.37 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)"

    const val ORIGIN_YOUTUBE_MUSIC = "https://music.youtube.com"
    const val REFERER_YOUTUBE_MUSIC = "https://music.youtube.com/"
    const val ORIGIN_YOUTUBE = "https://www.youtube.com"
    const val REFERER_YOUTUBE_TV = "https://www.youtube.com/tv"

    data class OriginReferer(val origin: String?, val referer: String?)

    /** Resolve the correct User-Agent for a YouTube media request based on
     * the `c` query parameter from the stream URL.
     * 
     * YouTube periodically updates client parameter values. This function maps
     * known client identifiers to appropriate User-Agent strings for Google CDN bypass.
     * Defaults to iOS client for maximum compatibility with Google's CDN.
     */
    fun resolveUserAgent(clientParam: String): String {
        val client = clientParam.trim().uppercase(Locale.US)
        return when {
            client == "WEB_REMIX" || client == "WEB" || client == "WEB_CREATOR" || client == "MWEB" ||
                client.startsWith("WEB_EMBEDDED") -> USER_AGENT_WEB
            client == "TVHTML5" || client.startsWith("TVHTML5") -> USER_AGENT_TV
            client == "IOS_MUSIC" -> USER_AGENT_IOS_MUSIC
            client.startsWith("IOS") -> USER_AGENT_IOS
            client == "ANDROID_MUSIC" -> USER_AGENT_ANDROID_MUSIC
            client.startsWith("ANDROID_VR") -> USER_AGENT_ANDROID_VR
            client.startsWith("ANDROID") -> USER_AGENT_ANDROID
            client.startsWith("VISIONOS") -> USER_AGENT_VISIONOS
            // New/unknown clients: default to iOS for Google CDN bypass compatibility
            else -> USER_AGENT_IOS
        }
    }

    /** Determine the correct Origin and Referer for a YouTube media request.
     * Web-type clients need YouTube Music origin; TV clients need YouTube origin.
     */
    fun resolveOriginReferer(clientParam: String): OriginReferer {
        val client = clientParam.trim().uppercase(Locale.US)
        return when {
            client.startsWith("TVHTML5") -> OriginReferer(ORIGIN_YOUTUBE, REFERER_YOUTUBE_TV)
            client == "WEB" || client == "WEB_REMIX" || client == "WEB_CREATOR" || client == "MWEB" ||
                client.startsWith("WEB_EMBEDDED") -> OriginReferer(ORIGIN_YOUTUBE_MUSIC, REFERER_YOUTUBE_MUSIC)
            else -> OriginReferer(null, null)
        }
    }

    /** Checks if the host belongs to YouTube / Google Video media CDNs. */
    fun isYouTubeMediaHost(host: String): Boolean {
        return host.endsWith("googlevideo.com") ||
            host.endsWith("googleusercontent.com") ||
            host.endsWith("youtube.com") ||
            host.endsWith("youtube-nocookie.com") ||
            host.endsWith("ytimg.com")
    }

    /** Applies appropriate User-Agent, Origin, and Referer headers based on client parameters. */
    fun applyHeaders(requestBuilder: Request.Builder, url: HttpUrl): Request.Builder {
        val clientParam = url.queryParameter("c")?.trim().orEmpty()
        val userAgent = resolveUserAgent(clientParam)
        val originReferer = resolveOriginReferer(clientParam)

        requestBuilder.header("User-Agent", userAgent)
        if (originReferer.origin != null) {
            requestBuilder.header("Origin", originReferer.origin)
        } else {
            requestBuilder.removeHeader("Origin")
        }
        if (originReferer.referer != null) {
            requestBuilder.header("Referer", originReferer.referer)
        } else {
            requestBuilder.removeHeader("Referer")
        }
        return requestBuilder
    }
}
