package com.darkxvenom.itune.data.artist

import com.darkxvenom.itune.backend.Net
import com.darkxvenom.itune.data.model.ArtistPage
import com.darkxvenom.itune.data.model.HomeShelf
import com.darkxvenom.itune.data.model.Song
import okhttp3.Request
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

object AppleCatalogue {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36"

    fun matchKey(name: String): String =
        Normalizer.normalize(name.lowercase(Locale.ROOT).replace('$', 's'), Normalizer.Form.NFD)
            .filter { it.isLetterOrDigit() }

    fun titleKey(title: String): String? =
        matchKey(
            title.substringBefore(" (").substringBefore(" [")
                .removeSuffix(" - Single").removeSuffix(" - EP"),
        ).takeIf { it.length >= 4 }

    fun keys(titles: Collection<String>): Set<String> = titles.mapNotNullTo(HashSet(), ::titleKey)

    fun sharesTitles(artistId: Long, keys: Set<String>): Boolean {
        if (keys.isEmpty()) return false
        val request = Request.Builder()
            .url("https://itunes.apple.com/lookup?id=$artistId&entity=song&limit=200")
            .header("User-Agent", USER_AGENT)
            .build()
        val body = Net.client.newCall(request).execute()
            .use { if (it.isSuccessful) it.body?.string() else null } ?: return false
        val rows = runCatching { JSONObject(body).optJSONArray("results") }.getOrNull() ?: return false
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val trackKey = row.optString("trackName").takeIf { it.isNotBlank() }?.let(::titleKey)
            val collectionKey = row.optString("collectionName").takeIf { it.isNotBlank() }?.let(::titleKey)
            if ((trackKey != null && trackKey in keys) || (collectionKey != null && collectionKey in keys)) {
                return true
            }
        }
        return false
    }

    fun titlesOf(songs: List<Song>, sections: List<HomeShelf>): List<String> =
        songs.map { it.title } +
            sections.flatMap { it.items }.filter { it.browseId?.startsWith("MPRE") == true }.map { it.title }

    fun titlesOf(page: ArtistPage): List<String> = titlesOf(page.songs, page.sections)
}
