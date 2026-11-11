package com.darkxvenom.itune.data.library

import com.darkxvenom.itune.backend.MoodAndMomentRef
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.data.model.BrowseCategoryItem
import com.darkxvenom.itune.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Syncs live "Moods & moments" and regional "Genres" (`FEmusic_moods_and_genres`)
 * for the active Content Language (`hl`) and Content Country (`gl`), refreshing automatically
 * whenever the user changes locale preferences in Settings.
 */
object BrowseMoodsRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fetchJob: Job? = null
    @Volatile
    private var syncedLocaleSignature: String? = null

    private val _categories = MutableStateFlow(MusicRepository.browseCategories)
    val categories: StateFlow<List<BrowseCategoryItem>> = _categories.asStateFlow()

    private val _genres = MutableStateFlow<List<BrowseCategoryItem>>(emptyList())
    val genres: StateFlow<List<BrowseCategoryItem>> = _genres.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun ensureLoaded() {
        val curSig = SettingsStore.localeSignature()
        if (syncedLocaleSignature == curSig || fetchJob?.isActive == true) return
        refresh()
    }

    fun refresh() {
        if (fetchJob?.isActive == true) return
        fetchJob = scope.launch {
            _isRefreshing.value = true
            try {
                val liveMoods = MusicBackend.fetchMoodsAndMoments()
                if (liveMoods.isNotEmpty()) {
                    val baseImages = MusicRepository.browseCategories
                    val merged = liveMoods.take(baseImages.size).mapIndexed { idx, mood ->
                        val slot = baseImages[idx]
                        BrowseCategoryItem(
                            id = slot.id,
                            title = mood.title,
                            imageUrl = slot.imageUrl,
                            browseId = mood.browseId,
                            params = mood.params,
                        )
                    }
                    _categories.value = merged
                    syncedFromNetwork = true
                }
            } catch (_: Exception) {
                // Keep seeded defaults cleanly
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
