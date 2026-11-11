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
 * Syncs AirBeats' 12 live "Moods & moments" items (`FEmusic_moods_and_genres`) onto the
 * existing 12 pre-configured artwork images from [MusicRepository.browseCategories].
 */
object BrowseMoodsRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fetchJob: Job? = null
    private var syncedFromNetwork = false

    private val _categories = MutableStateFlow(MusicRepository.browseCategories)
    val categories: StateFlow<List<BrowseCategoryItem>> = _categories.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun ensureLoaded() {
        if (syncedFromNetwork || fetchJob?.isActive == true) return
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
