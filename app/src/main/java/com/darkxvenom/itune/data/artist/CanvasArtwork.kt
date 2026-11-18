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

import java.text.Normalizer
import java.util.Locale

enum class CanvasSource { SPOTIFY, OTHER }

data class CanvasArtwork(
    val url: String,
    val fallbackUrl: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val source: CanvasSource = CanvasSource.OTHER,
    val portraitUrl: String? = null,
) {
    fun matches(wantTitle: String, wantArtist: String, wantAlbum: String? = null): Boolean {
        val titleOk = title == null || wantTitle.isBlank() ||
            titlesCompatible(got = title, want = wantTitle)

        val artistOk = artist == null || wantArtist.isBlank() ||
            artistsCompatible(
                gotArtistsRaw = artist,
                gotTitleRaw = title.orEmpty(),
                wantArtistsRaw = wantArtist,
                wantTitleRaw = wantTitle,
            )

        return titleOk && artistOk
    }
}

private val VERSION_MODIFIERS = listOf(
    "remix", "live", "acoustic", "sped up", "slowed", "reverb",
    "instrumental", "karaoke", "a cappella", "acapella", "nightcore",
    "8d", "cover", "lofi", "lo fi",
)

private val TITLE_DECORATION_REGEX = Regex(
    """\s*(?:\((?:feat\.?|ft\.?|featuring|with|from|music from|radio edit|explicit|clean|remastered|deluxe|bonus|single|official)[^)]*\)|\[(?:feat\.?|ft\.?|featuring|with|from|music from|explicit|clean|remastered|deluxe|bonus|official)[^]]*]|\s*-\s*(?:from|music from|single|remastered|deluxe|radio edit|explicit|clean).*)$""",
    RegexOption.IGNORE_CASE,
)

private val FEAT_IN_TITLE_REGEX = Regex(
    """(?:\(|\[|\s)(?:feat\.?|ft\.?|featuring|with)\s+([^)\]\-]+)""",
    RegexOption.IGNORE_CASE,
)

fun String.baseTitleForMatch(): String {
    var current = this.trim()
    repeat(3) {
        val stripped = current.replace(TITLE_DECORATION_REGEX, "").trim()
        if (stripped == current || stripped.isBlank()) return@repeat
        current = stripped
    }
    return current.normalizeForMatch()
}

fun extractFeaturedArtistsFromTitle(title: String): List<String> =
    FEAT_IN_TITLE_REGEX.findAll(title)
        .flatMap { splitArtists(it.groupValues[1]).asSequence() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

fun titlesCompatible(got: String, want: String): Boolean {
    val gotFull = got.normalizeForMatch()
    val wantFull = want.normalizeForMatch()
    if (gotFull.isBlank() || wantFull.isBlank()) return true
    if (gotFull == wantFull) return true

    // Reject mismatches on distinct track versions (remix, live, sped up, karaoke, etc.)
    for (mod in VERSION_MODIFIERS) {
        val inGot = gotFull.contains(mod)
        val inWant = wantFull.contains(mod)
        if (inGot != inWant) return false
    }

    val gotBase = got.baseTitleForMatch()
    val wantBase = want.baseTitleForMatch()
    if (gotBase.isBlank() || wantBase.isBlank()) return false
    return gotBase == wantBase
}

fun artistsCompatible(
    gotArtistsRaw: String,
    gotTitleRaw: String,
    wantArtistsRaw: String,
    wantTitleRaw: String = "",
): Boolean {
    val wanted = splitArtists(wantArtistsRaw)
    val credited = (splitArtists(gotArtistsRaw) + extractFeaturedArtistsFromTitle(gotTitleRaw)).distinct()
    if (wanted.isEmpty() || credited.isEmpty()) return false

    if (wanted.all { want -> credited.any { it == want } }) return true

    // For multi-artist tracks (e.g. "The Weeknd, Playboi Carti & Madonna"), allow when
    // the primary artist matches and no conflicting unrelated primary artist is present.
    val wantPrimary = wanted.first()
    val creditedPrimary = splitArtists(gotArtistsRaw).firstOrNull() ?: credited.first()
    val primaryMatches = wantPrimary == creditedPrimary ||
        credited.any { it == wantPrimary }
    if (!primaryMatches) return false

    val allWanted = (wanted + extractFeaturedArtistsFromTitle(wantTitleRaw)).distinct()
    val overlap = allWanted.count { w -> credited.any { it == w } }
    return overlap >= 1 && (wanted.size == 1 || credited.first() == wantPrimary || overlap >= 2)
}

fun String.normalizeForMatch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

fun splitArtists(raw: String): List<String> =
    raw.split(ARTIST_SEPARATORS)
        .map { it.normalizeForMatch() }
        .filter { it.isNotBlank() }

private val ARTIST_SEPARATORS = Regex(
    "(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
    RegexOption.IGNORE_CASE,
)
