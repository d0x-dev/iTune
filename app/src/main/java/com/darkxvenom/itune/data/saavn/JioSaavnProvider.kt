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

package com.darkxvenom.itune.data.saavn

import android.util.Base64
import android.util.Log
import com.darkxvenom.itune.audio.StreamTrace
import com.darkxvenom.itune.audio.StreamTraceStore
import com.darkxvenom.itune.backend.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** What the player pill should say for a track while the JioSaavn provider is on. */
enum class SaavnStatus { SEARCHING, LOSSLESS, FALLBACK }

/** Per-track JioSaavn status, observed by the player's quality capsule. */
object SaavnQuality {
    private val _statuses = MutableStateFlow<Map<String, SaavnStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, SaavnStatus>> = _statuses

    fun clear(trackId: String) = _statuses.update { it - trackId }

    fun set(trackId: String, status: SaavnStatus) = _statuses.update { cur ->
        val next = if (cur.size > 60) cur.entries.drop(30).associate { it.key to it.value } else cur
        next + (trackId to status)
    }
}

/**
 * Optional JioSaavn stream provider (same catalogue + stream decoding Dial uses).
 *
 * For a YouTube Music track it searches JioSaavn, accepts only a confident match
 * (title + artist, then title + first word of the artist) and returns a 320/160kbps
 * AAC stream. Any miss returns null so the caller falls back to YouTube.
 */
object JioSaavnProvider {
    private const val TAG = "JioSaavn"
    private const val API = "https://www.jiosaavn.com/api.php"
    private const val CACHE_TTL_MS = 20 * 60_000L

    private class Cached(val url: String?, val at: Long, val trace: StreamTrace? = null)
    private val cache = ConcurrentHashMap<String, Cached>()

    private class Hit(val id: String, val title: String, val artists: List<String>, val encUrl: String, val has320: Boolean)

    /** A playable JioSaavn URL for this track, or null (→ use YouTube). */
    suspend fun resolve(
        trackId: String, title: String, artist: String, excludedUrls: Set<String>, force: Boolean = false,
    ): String? =
        withContext(Dispatchers.IO) {
            val cached = cache[trackId]
            cached?.trace?.let { StreamTraceStore.put(trackId, it) }
            if (!force && cached != null && System.currentTimeMillis() - cached.at < CACHE_TTL_MS) {
                val url = cached.url?.takeIf { it !in excludedUrls }
                SaavnQuality.set(trackId, if (url != null) SaavnStatus.LOSSLESS else SaavnStatus.FALLBACK)
                return@withContext url
            }
            SaavnQuality.set(trackId, SaavnStatus.SEARCHING)
            val steps = mutableListOf<String>()
            StreamTraceStore.put(trackId, StreamTrace("Resolving…", "", emptyList()))
            val found = runCatching { find(title, artist, steps) }
                .onFailure {
                    Log.w(TAG, "lookup failed: ${it.message}")
                    steps += "JioSaavn lookup failed (${it.javaClass.simpleName})"
                }
                .getOrNull()
            val url = found?.first?.takeIf { it !in excludedUrls }
            val trace = if (url != null) {
                StreamTrace("JioSaavn", found.second, steps.toList())
            } else {
                steps += "No usable JioSaavn stream → falling back to YouTube Music"
                StreamTrace("JioSaavn (no match)", "", steps.toList())
            }
            StreamTraceStore.put(trackId, trace)
            cache[trackId] = Cached(url, System.currentTimeMillis(), trace)
            SaavnQuality.set(trackId, if (url != null) SaavnStatus.LOSSLESS else SaavnStatus.FALLBACK)
            url
        }

    /** True for a stream URL that came from JioSaavn's CDN. */
    fun isSaavnUrl(url: String): Boolean = url.contains("saavncdn.com", ignoreCase = true)

    /** Status-free lookup, used for background cache upgrades. */
    suspend fun lookup(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        runCatching { find(title, artist)?.first }.getOrNull()
    }

    /** Returns (stream URL, quality label) and appends the exact steps taken to [steps]. */
    private fun find(title: String, artist: String, steps: MutableList<String> = mutableListOf()): Pair<String, String>? {
        val query = "$title $artist"
        steps += "Asked JioSaavn (jiosaavn.com api, search.getResults) for \"$query\""
        val hits = search(query)
        steps += "JioSaavn returned ${hits.size} result(s)"
        val (match, rule) = pickMatch(hits, title, artist) ?: run {
            steps += "No result passed the Title + Artist check (or Title + first word of Artist)"
            return null
        }
        steps += "Matched \"${match.title}\" by ${match.artists.joinToString(", ")} — rule: $rule"
        val (url, kbps) = streamFor(match, steps) ?: return null
        return url to "$kbps kbps • AAC (MP4)"
    }

    // ── matching ────────────────────────────────────────────────────────────

    private fun norm(s: String): String = s.lowercase()
        .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        .replace(Regex("\\s[-–]\\s(from|feat|ft|with)\\b.*"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun artistList(s: String): List<String> =
        s.split(Regex("\\s*(?:,|&|\\band\\b|\\bx\\b|\\bfeat\\.?\\b|\\bft\\.?\\b|/)\\s*", RegexOption.IGNORE_CASE))
            .map(::norm).filter { it.isNotBlank() }

    /** 1) title + artist match directly; 2) title + first word of the artist, exact. */
    private fun pickMatch(hits: List<Hit>, title: String, artist: String): Pair<Hit, String>? {
        val t = norm(title)
        if (t.isBlank()) return null
        val wanted = artistList(artist)
        val sameTitle = hits.filter { norm(it.title) == t }
        sameTitle.firstOrNull { h ->
            val have = h.artists.flatMap(::artistList)
            wanted.any { w -> have.any { it == w } }
        }?.let { return it to "Title + Artist" }
        val first = wanted.firstOrNull()?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: return null
        return sameTitle.firstOrNull { h ->
            h.artists.flatMap { norm(it).split(' ') }.any { it == first }
        }?.let { it to "Title + first word of Artist (\"$first\")" }
    }

    // ── JioSaavn API ────────────────────────────────────────────────────────

    private fun call(params: Map<String, String>): String? {
        val url = API.toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("_format", "json"); addQueryParameter("_marker", "0")
            addQueryParameter("api_version", "4"); addQueryParameter("ctx", "android")
        }.build()
        val req = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-IN,en;q=0.9")
            .header("Cookie", "explicit_content=1")
            .build()
        return Net.client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }

    private fun parseSong(o: JSONObject): Hit? {
        val more = o.optJSONObject("more_info") ?: return null
        val id = o.optString("id").ifBlank { return null }
        val artists = more.optJSONObject("artistMap")?.optJSONArray("primary_artists")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() } }
        }.orEmpty()
        return Hit(
            id = id,
            title = o.optString("title"),
            artists = artists,
            encUrl = more.optString("encrypted_media_url"),
            has320 = more.optString("320kbps").equals("true", ignoreCase = true),
        )
    }

    private fun search(query: String): List<Hit> {
        val body = call(mapOf("__call" to "search.getResults", "q" to query, "p" to "1", "n" to "10")) ?: return emptyList()
        val arr: JSONArray = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::parseSong) }
    }

    private fun streamFor(hit: Hit, steps: MutableList<String>): Pair<String, Int>? {
        // Search rows normally carry the encrypted URL; fetch details only if they don't.
        val enc = hit.encUrl.ifBlank {
            val body = call(mapOf("__call" to "song.getDetails", "pids" to hit.id)) ?: return null
            val root = JSONObject(body)
            val song = root.optJSONArray("songs")?.optJSONObject(0)
                ?: root.optJSONObject(hit.id)
                ?: root.keys().asSequence().mapNotNull { root.optJSONObject(it) }.firstOrNull()
                ?: return null
            steps += "Fetched stream details (song.getDetails) for id ${hit.id}"
            return decode(parseSong(song) ?: return null, steps)
        }
        return decode(Hit(hit.id, hit.title, hit.artists, enc, hit.has320), steps)
    }

    private fun decode(hit: Hit, steps: MutableList<String>): Pair<String, Int>? {
        val url = decrypt(hit.encUrl).takeIf { it.isNotBlank() } ?: run {
            steps += "Could not decrypt the CDN link"
            return null
        }
        steps += "Decrypted the encrypted CDN link (DES) → saavncdn stream"
        val m = Regex("_(48|96|160|320)\\.(mp4|aac|mp3)(?=[?#]|$)", RegexOption.IGNORE_CASE).find(url)
        if (m == null) {
            steps += "Rendition not named in the URL; using it as given"
            return url to 160
        }
        val offered = m.groupValues[1].toInt()
        val best = if (hit.has320) url.replaceRange(m.range, "_320.${m.groupValues[2]}") else url
        val kbps = if (hit.has320) 320 else offered
        steps += if (hit.has320) "Catalogue lists a 320 kbps file → switched $offered → 320 kbps" else "Highest rendition offered: $offered kbps"
        // 48/96kbps is worse than YouTube's stream: let the caller fall back instead.
        if (kbps <= 96) {
            steps += "$kbps kbps is below YouTube quality → rejected"
            return null
        }
        return best to kbps
    }

    private fun decrypt(enc: String): String = runCatching {
        val key = SecretKeySpec("38346591".toByteArray(Charsets.UTF_8), "DES")
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding").apply { init(Cipher.DECRYPT_MODE, key) }
        String(cipher.doFinal(Base64.decode(enc, Base64.DEFAULT)), Charsets.UTF_8).trim()
    }.getOrDefault("")
}
