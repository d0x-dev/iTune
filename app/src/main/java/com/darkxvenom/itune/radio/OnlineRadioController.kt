package com.darkxvenom.itune.radio

import android.content.Context
import android.os.Build
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.darkxvenom.itune.audio.AudioEngineManager
import com.darkxvenom.itune.audio.AudioOutputManager
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.Net
import com.darkxvenom.itune.data.library.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Separate dedicated ExoPlayer + Paginated Catalog Controller exclusively for Online FM Radio streams.
 * - Paginates in 120-station chunks so ALL stations in a 10,000+ country are browsable with zero lag/crash.
 * - Runs debounced server-side regional search so any station across the full 10,000+ catalog is found immediately.
 * - Integrates AudioEngineManager DSP + AudioOutputManager routing + mutual exclusion with MusicPlayerState.
 */
@OptIn(UnstableApi::class)
object OnlineRadioController : Player.Listener {
    private const val QUEUE_WINDOW_RADIUS = 80
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var appContext: Context? = null
    private var radioPlayer: ExoPlayer? = null
    private var initialized = false
    private var triedFallbackUrl = false
    private var outputObserverJob: Job? = null
    private var locationLoadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var searchJob: Job? = null
    private var sleepJob: Job? = null

    private val loadedStationIds = HashSet<String>(512)
    private var activeRegionToken: Long = 0L

    private val _state = MutableStateFlow(OnlineRadioUiState())
    val state: StateFlow<OnlineRadioUiState> = _state.asStateFlow()

    @Synchronized
    fun ensureInit(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        appContext = app

        val httpFactory = OkHttpDataSource.Factory(Net.client).setUserAgent(Net.USER_AGENT)
        val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(app, httpFactory))

        val renderersFactory = object : DefaultRenderersFactory(app) {
            override fun buildAudioSink(
                ctx: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(ctx)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(
                    arrayOf(
                        AudioEngineManager.djProcessor,
                        AudioEngineManager.spatialProcessor,
                        AudioEngineManager.eightDProcessor,
                    ),
                )
                .build()
        }

        val player = ExoPlayer.Builder(app, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLooper(Looper.getMainLooper())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply { addListener(this@OnlineRadioController) }

        radioPlayer = player

        outputObserverJob = scope.launch {
            AudioOutputManager.state.collectLatest { out ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    radioPlayer?.setPreferredAudioDevice(out.activeDevice?.info)
                }
            }
        }

        val favorites = OnlineRadioRepository.loadFavorites(app)
        val savedCountry = OnlineRadioRepository.loadSavedCountry(app)
        val savedCity = OnlineRadioRepository.loadSavedCity(app)

        _state.value = _state.value.copy(
            selectedCountry = savedCountry,
            selectedCity = savedCity,
            favoriteStations = favorites,
            favoriteIds = favorites.map { it.uuid }.toSet(),
        )
        initialized = true
    }

    fun ensureCatalogLoaded(forceRefresh: Boolean = false) {
        val cur = _state.value
        if (!forceRefresh && cur.countries.isNotEmpty() && cur.stations.isNotEmpty()) return
        loadCatalogInternal(forceRefresh = forceRefresh)
    }

    fun refreshCurrentRegion() {
        loadCatalogInternal(forceRefresh = true)
    }

    fun selectCountry(country: RadioCountryOption) {
        val ctx = appContext ?: return
        OnlineRadioRepository.saveSelectedCountry(ctx, country)
        OnlineRadioRepository.saveSelectedCity(ctx, RADIO_ALL_CITIES)
        resetPaginationState()
        _state.value = _state.value.copy(
            selectedCountry = country,
            selectedCity = RADIO_ALL_CITIES,
            cities = listOf(RadioCityOption(RADIO_ALL_CITIES, country.stationCount)),
            stations = emptyList(),
            searchQuery = "",
            searchResults = emptyList(),
            searchingStations = false,
            loadingCities = true,
            loadingStations = true,
            loadingMoreStations = false,
            hasMoreStations = false,
            nextStationOffset = 0,
            totalRegionStations = country.stationCount,
            errorMessage = null,
        )
        loadCountryDetails(country, targetCity = RADIO_ALL_CITIES, forceRefresh = false)
    }

    fun selectCity(city: String) {
        val ctx = appContext ?: return
        val country = _state.value.selectedCountry ?: return
        val normalizedCity = city.ifBlank { RADIO_ALL_CITIES }
        OnlineRadioRepository.saveSelectedCity(ctx, normalizedCity)
        resetPaginationState()
        val expectedCount = if (normalizedCity == RADIO_ALL_CITIES) {
            country.stationCount
        } else {
            _state.value.cities.firstOrNull { it.name.equals(normalizedCity, ignoreCase = true) }?.stationCount ?: 0
        }
        val token = activeRegionToken
        _state.value = _state.value.copy(
            selectedCity = normalizedCity,
            stations = emptyList(),
            searchQuery = "",
            searchResults = emptyList(),
            searchingStations = false,
            loadingStations = true,
            loadingMoreStations = false,
            hasMoreStations = false,
            nextStationOffset = 0,
            totalRegionStations = expectedCount,
            errorMessage = null,
        )
        locationLoadJob?.cancel()
        locationLoadJob = scope.launch {
            try {
                val page = OnlineRadioRepository.fetchStationsPage(country, normalizedCity, offset = 0)
                if (token != activeRegionToken) return@launch
                loadedStationIds.clear()
                page.stations.forEach { loadedStationIds.add(it.uuid) }
                _state.value = _state.value.copy(
                    stations = page.stations,
                    loadingStations = false,
                    hasMoreStations = page.hasMore,
                    nextStationOffset = page.rawCount,
                    totalRegionStations = maxOf(expectedCount, page.stations.size),
                    errorMessage = if (page.stations.isEmpty()) "No online FM stations found in $normalizedCity" else null,
                )
            } catch (e: Exception) {
                if (token != activeRegionToken) return@launch
                _state.value = _state.value.copy(
                    loadingStations = false,
                    errorMessage = e.message ?: "Couldn't load stations for $normalizedCity",
                )
            }
        }
    }

    fun loadMoreStations() {
        val cur = _state.value
        val country = cur.selectedCountry ?: return
        if (cur.loadingStations || cur.loadingMoreStations || !cur.hasMoreStations) return

        val token = activeRegionToken
        val city = cur.selectedCity
        val offset = cur.nextStationOffset
        loadMoreJob?.cancel()
        _state.value = cur.copy(loadingMoreStations = true)

        loadMoreJob = scope.launch {
            try {
                val page = OnlineRadioRepository.fetchStationsPage(country, city, offset = offset)
                if (token != activeRegionToken) return@launch

                val newOnes = page.stations.filter { loadedStationIds.add(it.uuid) }
                val latest = _state.value
                val combined = if (newOnes.isEmpty()) latest.stations else latest.stations + newOnes
                _state.value = latest.copy(
                    stations = combined,
                    loadingMoreStations = false,
                    hasMoreStations = page.hasMore && page.rawCount > 0,
                    nextStationOffset = offset + page.rawCount,
                    totalRegionStations = maxOf(latest.totalRegionStations, combined.size),
                )
            } catch (_: Exception) {
                if (token != activeRegionToken) return@launch
                _state.value = _state.value.copy(loadingMoreStations = false)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        val cur = _state.value
        _state.value = cur.copy(searchQuery = query)
        searchJob?.cancel()

        val clean = query.trim()
        if (clean.length < 2) {
            _state.value = _state.value.copy(
                searchResults = emptyList(),
                searchingStations = false,
            )
            return
        }

        val country = cur.selectedCountry ?: return
        val city = cur.selectedCity
        val token = activeRegionToken

        searchJob = scope.launch {
            delay(260L)
            val localMatches = _state.value.stations.filter { st ->
                st.name.contains(clean, ignoreCase = true) ||
                    st.cityState.contains(clean, ignoreCase = true) ||
                    st.tags.any { it.contains(clean, ignoreCase = true) }
            }
            _state.value = _state.value.copy(
                searchResults = localMatches.take(RADIO_PAGE_SIZE),
                searchingStations = true,
            )

            val remote = OnlineRadioRepository.searchStationsInRegion(country, city, clean)
            if (token != activeRegionToken || _state.value.searchQuery.trim() != clean) return@launch

            val merged = (localMatches + remote).distinctBy { it.uuid }
            _state.value = _state.value.copy(
                searchResults = merged,
                searchingStations = false,
            )
        }
    }

    fun playStation(
        station: OnlineRadioStation,
        queue: List<OnlineRadioStation> = _state.value.stations,
        expandPlayer: Boolean = true,
    ) {
        val ctx = appContext ?: return
        ensureInit(ctx)
        MusicPlayerState.pause()

        val boundedQueue = buildBoundedQueue(source = queue, target = station)
        triedFallbackUrl = false

        _state.value = _state.value.copy(
            currentStation = station,
            stationQueue = boundedQueue,
            hasActiveSession = true,
            playerExpanded = expandPlayer || _state.value.playerExpanded,
            isBuffering = true,
            liveMetadataTitle = null,
            streamError = null,
        )

        startStreamUrl(station.streamUrl)
        scope.launch {
            OnlineRadioRepository.recordStationClick(station.uuid)
        }
    }

    fun togglePlayPause() {
        val player = radioPlayer ?: return
        val station = _state.value.currentStation ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            MusicPlayerState.pause()
            if (player.playbackState == Player.STATE_IDLE || _state.value.streamError != null) {
                playStation(station, _state.value.stationQueue, expandPlayer = _state.value.playerExpanded)
            } else {
                player.play()
            }
        }
    }

    fun pauseIfPlaying() {
        radioPlayer?.let { p ->
            if (p.isPlaying || p.playWhenReady) {
                p.pause()
            }
        }
    }

    fun skipNextStation() {
        val cur = _state.value
        val source = cur.stations.ifEmpty { cur.stationQueue }
        if (source.isEmpty()) return
        val curIdx = source.indexOfFirst { it.uuid == cur.currentStation?.uuid }
        val next = source[(curIdx + 1) % source.size]
        if (curIdx >= source.size - 15 && cur.hasMoreStations) {
            loadMoreStations()
        }
        playStation(next, source, expandPlayer = cur.playerExpanded)
    }

    fun skipPreviousStation() {
        val cur = _state.value
        val source = cur.stations.ifEmpty { cur.stationQueue }
        if (source.isEmpty()) return
        val curIdx = source.indexOfFirst { it.uuid == cur.currentStation?.uuid }
        val prevIdx = if (curIdx <= 0) source.lastIndex else curIdx - 1
        playStation(source[prevIdx], source, expandPlayer = cur.playerExpanded)
    }

    fun toggleFavorite(station: OnlineRadioStation) {
        val ctx = appContext ?: return
        val list = _state.value.favoriteStations.toMutableList()
        val existing = list.indexOfFirst { it.uuid == station.uuid }
        if (existing >= 0) {
            list.removeAt(existing)
        } else {
            list.add(0, station)
        }
        OnlineRadioRepository.saveFavorites(ctx, list)
        _state.value = _state.value.copy(
            favoriteStations = list,
            favoriteIds = list.map { it.uuid }.toSet(),
        )
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _state.value = _state.value.copy(playerExpanded = expanded)
    }

    fun closeSession() {
        sleepJob?.cancel()
        radioPlayer?.stop()
        radioPlayer?.clearMediaItems()
        _state.value = _state.value.copy(
            currentStation = null,
            isPlaying = false,
            isBuffering = false,
            hasActiveSession = false,
            playerExpanded = false,
            liveMetadataTitle = null,
            sleepTimerRemainingSec = null,
            streamError = null,
        )
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _state.value = _state.value.copy(sleepTimerRemainingSec = null)
            return
        }
        sleepJob = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0 && isActive) {
                _state.value = _state.value.copy(sleepTimerRemainingSec = remaining)
                delay(1000)
                remaining--
            }
            if (isActive) {
                pauseIfPlaying()
                _state.value = _state.value.copy(sleepTimerRemainingSec = null)
            }
        }
    }

    fun release() {
        sleepJob?.cancel()
        locationLoadJob?.cancel()
        loadMoreJob?.cancel()
        searchJob?.cancel()
        outputObserverJob?.cancel()
        radioPlayer?.removeListener(this)
        radioPlayer?.release()
        radioPlayer = null
        initialized = false
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _state.value = _state.value.copy(
            isPlaying = isPlaying,
            isBuffering = if (isPlaying) false else _state.value.isBuffering,
        )
        val sessionId = radioPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && isPlaying) {
            AudioEngineManager.attachAudioSession(sessionId, true)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        val buffering = playbackState == Player.STATE_BUFFERING
        _state.value = _state.value.copy(isBuffering = buffering)
        if (playbackState == Player.STATE_READY) {
            val sessionId = radioPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
            if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
                AudioEngineManager.attachAudioSession(sessionId, radioPlayer?.isPlaying == true)
            }
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        val title = mediaMetadata.title?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val artist = mediaMetadata.artist?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val combined = listOfNotNull(artist, title).joinToString(" — ").ifBlank { null }
        if (combined != null) {
            _state.value = _state.value.copy(liveMetadataTitle = combined)
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        val station = _state.value.currentStation
        if (!triedFallbackUrl && station != null && station.fallbackUrl.isNotBlank()) {
            triedFallbackUrl = true
            startStreamUrl(station.fallbackUrl)
            return
        }
        _state.value = _state.value.copy(
            isPlaying = false,
            isBuffering = false,
            streamError = "Stream offline or unreachable",
        )
    }

    private fun startStreamUrl(url: String) {
        val player = radioPlayer ?: return
        player.stop()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    private fun buildBoundedQueue(
        source: List<OnlineRadioStation>,
        target: OnlineRadioStation,
    ): List<OnlineRadioStation> {
        if (source.size <= QUEUE_WINDOW_RADIUS * 2 + 1) {
            return if (source.any { it.uuid == target.uuid }) source else listOf(target) + source
        }
        val idx = source.indexOfFirst { it.uuid == target.uuid }
        if (idx < 0) {
            return listOf(target) + source.take(QUEUE_WINDOW_RADIUS * 2)
        }
        val from = (idx - QUEUE_WINDOW_RADIUS).coerceAtLeast(0)
        val to = (idx + QUEUE_WINDOW_RADIUS + 1).coerceAtMost(source.size)
        return source.subList(from, to).toList()
    }

    private fun resetPaginationState() {
        activeRegionToken++
        locationLoadJob?.cancel()
        loadMoreJob?.cancel()
        searchJob?.cancel()
        loadedStationIds.clear()
    }

    private fun loadCatalogInternal(forceRefresh: Boolean) {
        val ctx = appContext ?: return
        resetPaginationState()
        locationLoadJob = scope.launch {
            _state.value = _state.value.copy(loadingCountries = true, errorMessage = null)
            val countries = OnlineRadioRepository.fetchCountries(forceRefresh = forceRefresh)
            if (countries.isEmpty()) {
                _state.value = _state.value.copy(
                    loadingCountries = false,
                    loadingCities = false,
                    loadingStations = false,
                    loadingMoreStations = false,
                    errorMessage = "Connect to the internet to browse worldwide Countries, Cities & FM Stations.",
                )
                return@launch
            }

            val saved = _state.value.selectedCountry
            val defaultGl = SettingsStore.effectiveGl()
            val resolvedCountry = when {
                saved != null -> countries.firstOrNull { it.code.equals(saved.code, ignoreCase = true) } ?: saved
                else -> countries.firstOrNull { it.code.equals(defaultGl, ignoreCase = true) }
                    ?: countries.firstOrNull { it.code == "IN" }
                    ?: countries.first()
            }
            OnlineRadioRepository.saveSelectedCountry(ctx, resolvedCountry)
            val targetCity = if (saved == null || saved.code == resolvedCountry.code) {
                _state.value.selectedCity
            } else {
                RADIO_ALL_CITIES
            }

            _state.value = _state.value.copy(
                countries = countries,
                selectedCountry = resolvedCountry,
                selectedCity = targetCity,
                loadingCountries = false,
                loadingCities = true,
                loadingStations = true,
                loadingMoreStations = false,
                hasMoreStations = false,
                nextStationOffset = 0,
                totalRegionStations = resolvedCountry.stationCount,
            )
            loadCountryDetails(resolvedCountry, targetCity = targetCity, forceRefresh = forceRefresh)
        }
    }

    private fun loadCountryDetails(
        country: RadioCountryOption,
        targetCity: String,
        forceRefresh: Boolean,
    ) {
        val token = activeRegionToken
        locationLoadJob?.cancel()
        locationLoadJob = scope.launch {
            try {
                val citiesDeferred = async { OnlineRadioRepository.fetchCitiesForCountry(country, forceRefresh) }
                val stationsDeferred = async {
                    OnlineRadioRepository.fetchStationsPage(
                        country = country,
                        city = targetCity,
                        offset = 0,
                        forceRefresh = forceRefresh,
                    )
                }
                val cities = citiesDeferred.await()
                val page = stationsDeferred.await()
                if (token != activeRegionToken) return@launch

                loadedStationIds.clear()
                page.stations.forEach { loadedStationIds.add(it.uuid) }

                val regionCount = if (targetCity == RADIO_ALL_CITIES || targetCity.isBlank()) {
                    country.stationCount
                } else {
                    cities.firstOrNull { it.name.equals(targetCity, ignoreCase = true) }?.stationCount
                        ?: page.stations.size
                }

                _state.value = _state.value.copy(
                    cities = cities,
                    stations = page.stations,
                    loadingCities = false,
                    loadingStations = false,
                    hasMoreStations = page.hasMore,
                    nextStationOffset = page.rawCount,
                    totalRegionStations = maxOf(regionCount, page.stations.size),
                    errorMessage = if (page.stations.isEmpty()) "No active FM streams returned for ${country.name}." else null,
                )
            } catch (e: Exception) {
                if (token != activeRegionToken) return@launch
                _state.value = _state.value.copy(
                    loadingCities = false,
                    loadingStations = false,
                    errorMessage = e.message ?: "Failed to load online FM stations",
                )
            }
        }
    }
}
