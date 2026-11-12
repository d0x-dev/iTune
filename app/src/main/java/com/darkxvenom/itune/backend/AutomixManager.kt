package com.darkxvenom.itune.backend

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.darkxvenom.itune.data.library.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Automix Engine:
 * Fetches authentic YouTube Music algorithmic radio recommendations via InnerTube (/v1/next with RDAMVM)
 * and keeps a continuous, realistic stream of upcoming tracks ready.
 *
 * When the active user queue nears its end and Automix is enabled in Settings,
 * the next automix track is seamlessly appended into MusicPlayerState.queue
 * so playback transitions continuously with real-time crossfade.
 */
object AutomixManager {
    private const val TAG = "AutomixManager"

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var fetchJob: Job? = null

    /** Observable list of automix recommendations for UI rendering in Queue */
    val automixTracks: SnapshotStateList<MusicTrack> = mutableStateListOf()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var lastSeededTrackId: String? = null

    fun init(context: Context) {
        // Ready for lifecycle binding
    }

    /**
     * Fetches fresh YouTube Music algorithmic radio / automix recommendations for the playing track.
     */
    fun fetchAutomixForTrack(seedTrack: MusicTrack, force: Boolean = false) {
        if (!force && seedTrack.id == lastSeededTrackId && automixTracks.isNotEmpty()) {
            return
        }
        lastSeededTrackId = seedTrack.id
        fetchJob?.cancel()

        fetchJob = scope.launch {
            _isLoading.value = true
            try {
                val fetched = withContext(Dispatchers.IO) {
                    MusicBackend.fetchRadioQueue(seedTrack.id)
                }

                // Filter out the seed track and any track currently in the playback queue
                val existingQueueIds = MusicPlayerState.queue.map { it.id }.toSet()
                val filtered = fetched.filter { it.id != seedTrack.id && it.id !in existingQueueIds }

                automixTracks.clear()
                if (filtered.isNotEmpty()) {
                    automixTracks.addAll(filtered)
                } else {
                    // Offline fallback: use cached songs to keep endless playback alive even in offline mode
                    val cachedTracks = MusicCacheManager.cachedTracks.toList()
                        .filter { it.id != seedTrack.id && it.id !in existingQueueIds }
                        .map { it.toMusicTrack() }
                    if (cachedTracks.isNotEmpty()) {
                        automixTracks.addAll(cachedTracks.shuffled())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching automix tracks for ${seedTrack.title}: ${e.message}")
                if (automixTracks.isEmpty()) {
                    val fallback = MusicCacheManager.cachedTracks.toList()
                        .filter { it.id != seedTrack.id }
                        .map { it.toMusicTrack() }
                    automixTracks.addAll(fallback)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Inserts the selected automix track immediately after the currently playing track.
     */
    fun playNextAutomix(track: MusicTrack, index: Int = -1) {
        if (index in automixTracks.indices) {
            automixTracks.removeAt(index)
        } else {
            automixTracks.removeAll { it.id == track.id }
        }
        MusicPlayerState.playNext(track)
    }

    /**
     * Appends the selected automix track to the end of the user's active queue.
     */
    fun addToQueueAutomix(track: MusicTrack, index: Int = -1) {
        if (index in automixTracks.indices) {
            automixTracks.removeAt(index)
        } else {
            automixTracks.removeAll { it.id == track.id }
        }
        MusicPlayerState.addToQueue(listOf(track))
    }

    /**
     * Called by MusicPlayerState or CrossfadeAudioEngine when the active playback queue
     * is at or near its end (e.g. queueIndex >= queue.size - 1).
     * Automatically pops the next algorithmic track and appends it to the queue.
     */
    fun ensureNextTrackEnqueued(): MusicTrack? {
        if (!SettingsStore.state.value.automixEnabled) return null
        if (automixTracks.isEmpty()) {
            val current = MusicPlayerState.currentTrack
            if (current != null && !_isLoading.value) {
                fetchAutomixForTrack(current, force = true)
            }
            return null
        }

        val nextTrack = automixTracks.removeAt(0)
        MusicPlayerState.addToQueue(listOf(nextTrack))

        // Trigger background pre-caching for this newly auto-enqueued track
        MusicCacheManager.cacheTrackOnDemand(nextTrack)

        // If automix recommendations are running low, fetch more based on this next track
        if (automixTracks.size <= 2) {
            fetchAutomixForTrack(nextTrack, force = true)
        }

        return nextTrack
    }

    fun peekNextAutomix(): MusicTrack? = automixTracks.firstOrNull()

    fun clearAutomix() {
        fetchJob?.cancel()
        automixTracks.clear()
        lastSeededTrackId = null
        _isLoading.value = false
    }
}
