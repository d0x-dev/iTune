package com.darkxvenom.itune.data.artist

import android.content.Context
import android.content.SharedPreferences
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.model.BrowseType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ArtistStateStore {
    private const val PREFS_NAME = "itune_artist_store"
    private const val KEY_SUBSCRIBED = "subscribed_artists"
    private const val KEY_SAVED_RELEASES = "saved_releases"
    private const val KEY_SIG_ENABLED = "signature_api_enabled"
    private const val KEY_SIG_URL = "signature_api_url"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _subscribedArtists = MutableStateFlow<Set<String>>(emptySet())
    val subscribedArtists: StateFlow<Set<String>> = _subscribedArtists.asStateFlow()

    private val _savedReleases = MutableStateFlow<Set<String>>(emptySet())
    val savedReleases: StateFlow<Set<String>> = _savedReleases.asStateFlow()

    private val _signatureApiEnabled = MutableStateFlow(false)
    val signatureApiEnabled: StateFlow<Boolean> = _signatureApiEnabled.asStateFlow()

    private val _signatureApiUrl = MutableStateFlow("")
    val signatureApiUrl: StateFlow<String> = _signatureApiUrl.asStateFlow()

    fun ensureInit(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p
            _subscribedArtists.value = p.getStringSet(KEY_SUBSCRIBED, emptySet()).orEmpty().toSet()
            _savedReleases.value = p.getStringSet(KEY_SAVED_RELEASES, emptySet()).orEmpty().toSet()
            _signatureApiEnabled.value = p.getBoolean(KEY_SIG_ENABLED, false)
            _signatureApiUrl.value = ArtistSignatureRepository.normalizeBaseUrl(
                p.getString(KEY_SIG_URL, "").orEmpty(),
            )
            LibraryStore.ensureInit(context)
            LibraryStore.syncLegacyIds(
                context = context,
                favoriteTrackIds = emptyList(),
                subscribedArtistIds = _subscribedArtists.value,
                savedReleaseIds = _savedReleases.value,
            )
        }
    }

    fun isSubscribed(browseId: String): Boolean = browseId in _subscribedArtists.value

    fun toggleSubscription(context: Context, browseId: String): Boolean {
        ensureInit(context)
        val current = _subscribedArtists.value.toMutableSet()
        val nowSubscribed = if (browseId in current) {
            current.remove(browseId)
            false
        } else {
            current.add(browseId)
            true
        }
        _subscribedArtists.value = current
        prefs?.edit()?.putStringSet(KEY_SUBSCRIBED, current)?.apply()
        return nowSubscribed
    }

    fun isReleaseSaved(browseId: String): Boolean = browseId in _savedReleases.value

    fun toggleReleaseSaved(context: Context, browseId: String): Boolean {
        ensureInit(context)
        val current = _savedReleases.value.toMutableSet()
        val nowSaved = if (browseId in current) {
            current.remove(browseId)
            false
        } else {
            current.add(browseId)
            true
        }
        _savedReleases.value = current
        prefs?.edit()?.putStringSet(KEY_SAVED_RELEASES, current)?.apply()
        return nowSaved
    }
}
