package com.darkxvenom.itune.data.library

import android.content.Context
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.NewReleaseAlbum
import com.darkxvenom.itune.data.artist.ArtistInnertubeClient
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.model.HomeFeedItem
import com.darkxvenom.itune.data.model.HomeFeedShelf
import com.darkxvenom.itune.data.model.HomeHeroCard
import com.darkxvenom.itune.data.model.HomeItemKind
import com.darkxvenom.itune.data.model.HomeShelfGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Real YouTube Music data for the Home screen.
 *
 *  - Signed in: the account's personalized `FEmusic_home` feed (quick picks, mixes, albums for you,
 *    listen again, …) plus podcasts.
 *  - Signed out: YouTube Music's default home feed, Explore (trending songs, new albums & singles,
 *    music videos), Charts (top artists, top songs) and podcasts.
 *  - Always: shelves built from the on-device [ListeningProfileStore] (recently played, top artists,
 *    "Because you listened to …").
 */
object HomeFeedRepository {
    private const val MAX_CONTINUATIONS = 3

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loadJob: Job? = null
    private var personalJob: Job? = null
    private var photoJob: Job? = null

    private data class Snapshot(val remote: List<HomeFeedShelf>)

    private val cache = ConcurrentHashMap<String, Snapshot>()
    private val radioCache = ConcurrentHashMap<String, HomeFeedShelf>()
    @Volatile private var activeKey: String? = null

    private val _remoteShelves = MutableStateFlow<List<HomeFeedShelf>>(emptyList())
    val remoteShelves: StateFlow<List<HomeFeedShelf>> = _remoteShelves.asStateFlow()

    private val _personalShelves = MutableStateFlow<List<HomeFeedShelf>>(emptyList())
    val personalShelves: StateFlow<List<HomeFeedShelf>> = _personalShelves.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    private fun sessionKey(): String {
        val signed = GoogleAuthStore.isSignedIn
        val account = if (signed) GoogleAuthStore.activeSessionKey().orEmpty() else "anon"
        return "$account|${SettingsStore.localeSignature()}"
    }

    /** Loads the feed for the current account/locale; cached feeds show instantly, then refresh. */
    fun ensureLoaded() {
        val key = sessionKey()
        if (key != activeKey) {
            activeKey = key
            _remoteShelves.value = cache[key]?.remote.orEmpty()
            load(key)
        } else if (_remoteShelves.value.isEmpty() && loadJob?.isActive != true) {
            load(key)
        }
    }

    fun refresh() {
        val key = sessionKey()
        activeKey = key
        load(key)
    }

    private fun load(key: String) {
        loadJob?.cancel()
        loadJob = scope.launch {
            _isLoading.value = true
            _failed.value = false
            try {
                val signed = GoogleAuthStore.isSignedIn
                val parts = listOf(
                    async { runCatching { loadHome(signed) }.getOrDefault(emptyList()) },
                    async {
                        if (signed) emptyList<HomeFeedShelf>() else
                            runCatching { loadSingle("FEmusic_explore", HomeShelfGroup.TRENDING, "explore") }
                                .getOrDefault(emptyList())
                    },
                    async {
                        if (signed) emptyList<HomeFeedShelf>() else
                            runCatching { loadSingle("FEmusic_charts", HomeShelfGroup.CHARTS, "charts") }
                                .getOrDefault(emptyList())
                    },
                    async {
                        runCatching { loadSingle("FEmusic_non_music_audio", HomeShelfGroup.PODCASTS, "podcasts") }
                            .getOrDefault(emptyList())
                    },
                ).awaitAll()
                val merged = parts.flatten().dedupeByTitle()
                if (sessionKey() != key) return@launch
                if (merged.isNotEmpty()) {
                    cache[key] = Snapshot(merged)
                    _remoteShelves.value = merged
                } else if (_remoteShelves.value.isEmpty()) {
                    _failed.value = true
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadHome(signed: Boolean): List<HomeFeedShelf> {
        val out = mutableListOf<HomeFeedShelf>()
        var root = ArtistInnertubeClient.browse("FEmusic_home", anonymous = !signed)
        var page = HomeFeedParser.parse(root, HomeShelfGroup.FEED, "home0")
        out += page.shelves
        var n = 0
        while (page.continuation != null && n < MAX_CONTINUATIONS) {
            n++
            root = runCatching {
                ArtistInnertubeClient.browseContinuation(page.continuation!!, anonymous = !signed)
            }.getOrNull() ?: break
            page = HomeFeedParser.parse(root, HomeShelfGroup.FEED, "home$n")
            out += page.shelves
        }
        return out
    }

    private suspend fun loadSingle(browseId: String, group: HomeShelfGroup, prefix: String): List<HomeFeedShelf> {
        val root = ArtistInnertubeClient.browse(browseId, anonymous = !GoogleAuthStore.isSignedIn)
        return HomeFeedParser.parse(root, group, prefix).shelves
    }

    private fun List<HomeFeedShelf>.dedupeByTitle(): List<HomeFeedShelf> {
        val seen = HashSet<String>()
        return filter { seen.add(it.title.trim().lowercase() + "|" + it.strapline.trim().lowercase()) }
    }

    // ── Personal (on-device listening) shelves ─────────────────────────────────────────────────────

    /** Rebuilds listening-based shelves; radio seeds are fetched only when the top songs change. */
    fun refreshPersonal(context: Context) {
        ListeningProfileStore.ensureInit(context)
        LibraryStore.ensureInit(context)
        personalJob?.cancel()
        personalJob = scope.launch {
            val shelves = mutableListOf<HomeFeedShelf>()

            val recent = LibraryStore.recentSongs.value.take(20)
            if (recent.isNotEmpty()) {
                shelves += HomeFeedShelf(
                    key = "personal:recent",
                    title = "Recently Played",
                    items = recent.map { it.toMusicTrack().toItem() }.distinctBy { it.stableKey },
                    group = HomeShelfGroup.PERSONAL,
                )
            }

            val topArtists = ListeningProfileStore.topArtists(10)
            val artistItems = topArtists.filter { !it.artistId.isNullOrBlank() }.distinctBy { it.artistId }.map {
                HomeFeedItem(
                    kind = HomeItemKind.ARTIST,
                    title = it.name,
                    subtitle = "${it.plays} ${if (it.plays == 1) "play" else "plays"}",
                    thumbnailUrl = it.photoUrl ?: ListeningProfileStore.topSongs(100)
                        .firstOrNull { s -> s.id == it.topSongId }?.thumbnailUrl,
                    browseId = it.artistId,
                    artistName = it.name,
                    artistId = it.artistId,
                )
            }
            if (artistItems.size >= 2) {
                shelves += HomeFeedShelf(
                    key = "personal:top_artists",
                    title = "Your Top Artists",
                    strapline = "BASED ON WHAT YOU PLAY",
                    items = artistItems,
                    group = HomeShelfGroup.PERSONAL,
                )
            }
            _personalShelves.value = shelves + _personalShelves.value.filter { it.key.startsWith("personal:radio") }
            resolveArtistPhotos(topArtists)

            // "Because you listened to …" — radio seeded by the user's most-played songs
            val seeds = ListeningProfileStore.topSongs(2)
            val radioShelves = seeds.mapIndexedNotNull { idx, song ->
                radioCache[song.id] ?: run {
                    val tracks = runCatching { MusicBackend.fetchRadioQueue(song.id) }.getOrDefault(emptyList())
                        .filter { it.id != song.id }.take(15)
                    if (tracks.size < 4) null else HomeFeedShelf(
                        key = "personal:radio:$idx:${song.id}",
                        title = song.title,
                        strapline = "BECAUSE YOU LISTENED TO",
                        items = tracks.map { it.toItem() },
                        group = HomeShelfGroup.PERSONAL,
                    ).also { radioCache[song.id] = it }
                }
            }
            _personalShelves.value = shelves + radioShelves
        }
    }

    private fun resolveArtistPhotos(artists: List<ListeningProfileStore.ArtistStat>) {
        val missing = artists.filter { it.photoUrl == null && !it.artistId.isNullOrBlank() }.take(6)
        if (missing.isEmpty()) return
        photoJob?.cancel()
        photoJob = scope.launch {
            var changed = false
            for (a in missing) {
                val root: JSONObject = runCatching { ArtistInnertubeClient.browse(a.artistId!!) }.getOrNull() ?: continue
                val url = HomeFeedParser.parseArtistPhoto(root) ?: continue
                ListeningProfileStore.setArtistPhoto(a.key, url)
                changed = true
            }
            if (changed) {
                val updated = ListeningProfileStore.topArtists(10)
                _personalShelves.value = _personalShelves.value.map { shelf ->
                    if (shelf.key != "personal:top_artists") shelf else shelf.copy(
                        items = shelf.items.map { item ->
                            val photo = updated.firstOrNull { it.artistId == item.artistId }?.photoUrl
                            if (photo != null) item.copy(thumbnailUrl = photo) else item
                        },
                    )
                }
            }
        }
    }

    /** Tracks for a mix / watch-playlist tile that has no browse page (e.g. “My Supermix”). */
    suspend fun loadWatchPlaylist(playlistId: String, videoId: String? = null): List<MusicTrack> =
        runCatching {
            HomeFeedParser.parsePanelTracks(ArtistInnertubeClient.nextPlaylist(playlistId, videoId))
        }.getOrDefault(emptyList())

    // ── Hero cards ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Builds the top carousel from real data: a headline pick from the feed, the newest release,
     * a chart/trending pick, the user's top artist and a podcast.
     */
    fun buildHeroCards(
        remote: List<HomeFeedShelf>,
        personal: List<HomeFeedShelf>,
        releases: List<NewReleaseAlbum>,
        signedIn: Boolean,
    ): List<HomeHeroCard> {
        val cards = mutableListOf<HomeHeroCard>()
        val used = HashSet<String>()
        fun add(card: HomeHeroCard?) {
            if (card != null && card.imageUrl != null && used.add(card.item.stableKey)) cards += card
        }

        // 1. Headline pick from the feed (personal mix / album for you / trending playlist)
        val feedPick = remote.firstOrNull {
            it.group == HomeShelfGroup.FEED && !it.isSongShelf && !it.isArtistShelf &&
                it.items.any { i -> i.kind == HomeItemKind.PLAYLIST || i.kind == HomeItemKind.ALBUM }
        }
        feedPick?.items?.filter { (it.kind == HomeItemKind.PLAYLIST || it.kind == HomeItemKind.ALBUM) && it.thumbnailUrl != null }
            ?.take(2)
            ?.forEachIndexed { idx, item ->
                add(
                    HomeHeroCard(
                        id = if (idx == 0) "hero_feed" else "hero_feed_$idx",
                        category = if (signedIn) "Made for you" else "Featured",
                        title = item.title, subtitle = feedPick.title,
                        caption = item.subtitle.ifBlank { feedPick.strapline.ifBlank { feedPick.title } },
                        imageUrl = item.thumbnailUrl, item = item,
                    ),
                )
            }

        // 2. Newest release
        releases.firstOrNull()?.let { r ->
            add(
                HomeHeroCard(
                    id = "hero_new", category = "New release",
                    title = r.title, subtitle = r.artist.ifBlank { r.releaseType.label },
                    caption = r.subtitleLine, imageUrl = r.thumbnailUrl,
                    item = HomeFeedItem(
                        kind = HomeItemKind.ALBUM, title = r.title, subtitle = r.subtitleLine,
                        thumbnailUrl = r.thumbnailUrl, browseId = r.browseId,
                        artistName = r.artist, artistId = r.artistId, isExplicit = r.isExplicit,
                    ),
                ),
            )
        }

        // 3. Trending / chart song
        val trending = remote.firstOrNull { it.group == HomeShelfGroup.TRENDING && it.isSongShelf }
            ?: remote.firstOrNull { it.title.contains("quick picks", true) && it.isSongShelf }
            ?: remote.firstOrNull { it.isSongShelf }
        trending?.items?.firstOrNull { it.thumbnailUrl != null }?.let { item ->
            add(
                HomeHeroCard(
                    id = "hero_trending", category = if (trending.group == HomeShelfGroup.TRENDING) "Trending now" else trending.title,
                    title = item.title, subtitle = item.artistName.ifBlank { item.subtitle },
                    caption = "Play ${trending.title} starting with ${item.title}",
                    imageUrl = item.thumbnailUrl, item = item, queue = trending.items,
                ),
            )
        }

        // 4. Top artist — from the listening profile, else the charts
        val topArtist = personal.firstOrNull { it.key == "personal:top_artists" }?.items?.firstOrNull()
        val chartArtist = remote.firstOrNull { it.isArtistShelf }?.items?.firstOrNull()
        (topArtist ?: chartArtist)?.let { item ->
            add(
                HomeHeroCard(
                    id = "hero_artist", category = if (topArtist != null) "Your top artist" else "Popular artist",
                    title = item.title, subtitle = item.subtitle,
                    caption = if (topArtist != null) "Because you keep coming back" else "Trending on YouTube Music",
                    imageUrl = item.thumbnailUrl, item = item,
                ),
            )
        }

        // 5. Podcast
        remote.firstOrNull { it.group == HomeShelfGroup.PODCASTS }
            ?.items?.firstOrNull { it.kind == HomeItemKind.PODCAST }?.let { item ->
                add(
                    HomeHeroCard(
                        id = "hero_podcast", category = "Podcast", title = item.title,
                        subtitle = item.subtitle, caption = "Popular on YouTube Music",
                        imageUrl = item.thumbnailUrl, item = item,
                    ),
                )
            }
        return cards
    }

    fun MusicTrack.toItem(): HomeFeedItem = HomeFeedItem(
        kind = if (isVideo) HomeItemKind.VIDEO else HomeItemKind.SONG,
        title = title, subtitle = artists, thumbnailUrl = thumbnailUrl, videoId = id,
        artistName = artists, artistId = artistId, albumName = album, albumId = albumId, isExplicit = isExplicit,
    )
}
