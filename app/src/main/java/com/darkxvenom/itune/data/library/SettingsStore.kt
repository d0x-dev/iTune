package com.darkxvenom.itune.data.library

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import com.darkxvenom.itune.backend.MusicBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class SettingsUiState(
    val displayName: String = "Dark Venom",
    val welcomeCompleted: Boolean = false,
    val accountSubtitle: String = "iTune ID, Spatial Audio & Library",
    val spatialAudioMode: String = "Automatic",
    val dolbyAtmos: Boolean = true,
    val losslessAudio: Boolean = true,
    val audioQuality: String = "Hi-Res Lossless",
    val eqPreset: String = "Late Night",
    val crossfadeDuration: String = "6 sec",
    val soundCheck: Boolean = true,
    val animatedArtwork: Boolean = true,
    val timeSyncedLyrics: Boolean = true,
    val liquidGlassChrome: Boolean = true,
    val tactileHaptics: Boolean = true,
    val cellularStreaming: String = "High Quality",
    val downloadOverWifiOnly: Boolean = true,
    val automaticDownloads: Boolean = false,
    val optimizeStorage: String = "16 GB",
    val cachedSizeLabel: String = "284 MB",
    val syncLibrary: Boolean = true,
    val useListeningHistory: Boolean = true,
    val contentLanguage: String = ContentLocaleCatalog.SYSTEM_DEFAULT,
    val contentCountry: String = ContentLocaleCatalog.SYSTEM_DEFAULT,
    val hideExplicitContent: Boolean = false,
    val explicitContent: String = "Allowed",
    val addFavoriteSongs: Boolean = true,
    val newMusicAlerts: Boolean = true,
    val offlineMode: Boolean = false,
    val sleepTimer: String = "Off",
)

/**
 * Persists Apple-styled Settings preferences in SharedPreferences and exposes reactive StateFlow state.
 */
object SettingsStore {
    private const val PREFS_NAME = "itune_settings_prefs"

    const val KEY_DISPLAY_NAME = "display_name"
    const val KEY_WELCOME_COMPLETED = "welcome_completed"
    const val KEY_SPATIAL_AUDIO = "spatial_audio_mode"
    const val KEY_DOLBY_ATMOS = "dolby_atmos"
    const val KEY_LOSSLESS_AUDIO = "lossless_audio"
    const val KEY_AUDIO_QUALITY = "audio_quality"
    const val KEY_EQ_PRESET = "eq_preset"
    const val KEY_CROSSFADE = "crossfade_duration"
    const val KEY_SOUND_CHECK = "sound_check"
    const val KEY_ANIMATED_ARTWORK = "animated_artwork"
    const val KEY_TIME_SYNCED_LYRICS = "time_synced_lyrics"
    const val KEY_LIQUID_GLASS = "liquid_glass_chrome"
    const val KEY_TACTILE_HAPTICS = "tactile_haptics"
    const val KEY_CELLULAR_STREAMING = "cellular_streaming"
    const val KEY_DOWNLOAD_WIFI_ONLY = "download_over_wifi_only"
    const val KEY_AUTO_DOWNLOADS = "automatic_downloads"
    const val KEY_OPTIMIZE_STORAGE = "optimize_storage"
    const val KEY_CACHE_LABEL = "cached_size_label"
    const val KEY_SYNC_LIBRARY = "sync_library"
    const val KEY_LISTENING_HISTORY = "use_listening_history"
    const val KEY_CONTENT_LANGUAGE = "content_language"
    const val KEY_CONTENT_COUNTRY = "content_country"
    const val KEY_HIDE_EXPLICIT = "hide_explicit_content"
    const val KEY_EXPLICIT_CONTENT = "explicit_content"
    const val KEY_ADD_FAVORITE_SONGS = "add_favorite_songs"
    const val KEY_NEW_MUSIC_ALERTS = "new_music_alerts"
    const val KEY_OFFLINE_MODE = "offline_mode"
    const val KEY_SLEEP_TIMER = "sleep_timer"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun ensureInit(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p
            _state.value = loadFromPrefs(p)
        }
    }

    fun isHapticsEnabled(): Boolean = _state.value.tactileHaptics

    fun effectiveHl(): String = ContentLocaleCatalog.resolveEffectiveHl(_state.value.contentLanguage)

    fun effectiveGl(): String = ContentLocaleCatalog.resolveEffectiveGl(_state.value.contentCountry)

    fun isHideExplicitEnabled(): Boolean = _state.value.hideExplicitContent

    fun localeSignature(): String = "${effectiveHl()}|${effectiveGl()}"

    fun setBoolean(context: Context, key: String, value: Boolean) {
        ensureInit(context)
        prefs?.edit()?.putBoolean(key, value)?.apply()
        val cur = _state.value
        _state.value = when (key) {
            KEY_WELCOME_COMPLETED -> cur.copy(welcomeCompleted = value)
            KEY_DOLBY_ATMOS -> cur.copy(dolbyAtmos = value)
            KEY_LOSSLESS_AUDIO -> cur.copy(losslessAudio = value)
            KEY_SOUND_CHECK -> cur.copy(soundCheck = value)
            KEY_ANIMATED_ARTWORK -> cur.copy(animatedArtwork = value)
            KEY_TIME_SYNCED_LYRICS -> cur.copy(timeSyncedLyrics = value)
            KEY_LIQUID_GLASS -> cur.copy(liquidGlassChrome = value)
            KEY_TACTILE_HAPTICS -> cur.copy(tactileHaptics = value)
            KEY_DOWNLOAD_WIFI_ONLY -> cur.copy(downloadOverWifiOnly = value)
            KEY_AUTO_DOWNLOADS -> cur.copy(automaticDownloads = value)
            KEY_SYNC_LIBRARY -> cur.copy(syncLibrary = value)
            KEY_LISTENING_HISTORY -> cur.copy(useListeningHistory = value)
            KEY_HIDE_EXPLICIT -> {
                val mode = if (value) "Clean Only" else "Allowed"
                prefs?.edit()?.putString(KEY_EXPLICIT_CONTENT, mode)?.apply()
                cur.copy(hideExplicitContent = value, explicitContent = mode)
            }
            KEY_ADD_FAVORITE_SONGS -> cur.copy(addFavoriteSongs = value)
            KEY_NEW_MUSIC_ALERTS -> cur.copy(newMusicAlerts = value)
            KEY_OFFLINE_MODE -> cur.copy(offlineMode = value)
            else -> cur
        }
    }

    fun setString(context: Context, key: String, value: String) {
        ensureInit(context)
        val trimmed = value.trim()
        if (key == KEY_DISPLAY_NAME && trimmed.isEmpty()) return
        val normalized = when (key) {
            KEY_CONTENT_LANGUAGE -> ContentLocaleCatalog.normalizeLanguageChoice(trimmed)
            KEY_CONTENT_COUNTRY -> ContentLocaleCatalog.normalizeCountryChoice(trimmed)
            else -> trimmed
        }
        prefs?.edit()?.putString(key, normalized)?.apply()
        val cur = _state.value
        val localeChanged = (key == KEY_CONTENT_LANGUAGE && normalized != cur.contentLanguage) ||
            (key == KEY_CONTENT_COUNTRY && normalized != cur.contentCountry)
        _state.value = when (key) {
            KEY_DISPLAY_NAME -> cur.copy(displayName = normalized)
            KEY_SPATIAL_AUDIO -> cur.copy(spatialAudioMode = normalized)
            KEY_AUDIO_QUALITY -> cur.copy(audioQuality = normalized)
            KEY_EQ_PRESET -> cur.copy(eqPreset = normalized)
            KEY_CROSSFADE -> cur.copy(crossfadeDuration = normalized)
            KEY_CELLULAR_STREAMING -> cur.copy(cellularStreaming = normalized)
            KEY_OPTIMIZE_STORAGE -> cur.copy(optimizeStorage = normalized)
            KEY_CONTENT_LANGUAGE -> cur.copy(contentLanguage = normalized)
            KEY_CONTENT_COUNTRY -> cur.copy(contentCountry = normalized)
            KEY_EXPLICIT_CONTENT -> {
                val hide = normalized.equals("Clean Only", ignoreCase = true)
                prefs?.edit()?.putBoolean(KEY_HIDE_EXPLICIT, hide)?.apply()
                cur.copy(explicitContent = normalized, hideExplicitContent = hide)
            }
            KEY_SLEEP_TIMER -> cur.copy(sleepTimer = normalized)
            KEY_CACHE_LABEL -> cur.copy(cachedSizeLabel = normalized)
            else -> cur
        }
        if (localeChanged) {
            MusicBackend.clearLocalizedCaches()
            BrowseMoodsRepository.refresh()
            NewReleasesRepository.refresh()
        }
    }

    fun clearAudioCache(context: Context) {
        setString(context, KEY_CACHE_LABEL, "0 MB")
    }

    fun completeWelcome(context: Context, displayName: String) {
        val clean = displayName.trim()
        if (clean.isNotEmpty()) {
            setString(context, KEY_DISPLAY_NAME, clean)
        }
        setBoolean(context, KEY_WELCOME_COMPLETED, true)
    }

    private fun loadFromPrefs(p: SharedPreferences): SettingsUiState {
        val defaults = SettingsUiState()
        val legacyExplicit = p.getString(KEY_EXPLICIT_CONTENT, defaults.explicitContent) ?: defaults.explicitContent
        val hideExplicit = if (p.contains(KEY_HIDE_EXPLICIT)) {
            p.getBoolean(KEY_HIDE_EXPLICIT, defaults.hideExplicitContent)
        } else {
            legacyExplicit.equals("Clean Only", ignoreCase = true)
        }
        return SettingsUiState(
            displayName = p.getString(KEY_DISPLAY_NAME, defaults.displayName)?.ifBlank { defaults.displayName }
                ?: defaults.displayName,
            welcomeCompleted = p.getBoolean(KEY_WELCOME_COMPLETED, defaults.welcomeCompleted),
            spatialAudioMode = p.getString(KEY_SPATIAL_AUDIO, defaults.spatialAudioMode)
                ?: defaults.spatialAudioMode,
            dolbyAtmos = p.getBoolean(KEY_DOLBY_ATMOS, defaults.dolbyAtmos),
            losslessAudio = p.getBoolean(KEY_LOSSLESS_AUDIO, defaults.losslessAudio),
            audioQuality = p.getString(KEY_AUDIO_QUALITY, defaults.audioQuality) ?: defaults.audioQuality,
            eqPreset = p.getString(KEY_EQ_PRESET, defaults.eqPreset) ?: defaults.eqPreset,
            crossfadeDuration = p.getString(KEY_CROSSFADE, defaults.crossfadeDuration) ?: defaults.crossfadeDuration,
            soundCheck = p.getBoolean(KEY_SOUND_CHECK, defaults.soundCheck),
            animatedArtwork = p.getBoolean(KEY_ANIMATED_ARTWORK, defaults.animatedArtwork),
            timeSyncedLyrics = p.getBoolean(KEY_TIME_SYNCED_LYRICS, defaults.timeSyncedLyrics),
            liquidGlassChrome = p.getBoolean(KEY_LIQUID_GLASS, defaults.liquidGlassChrome),
            tactileHaptics = p.getBoolean(KEY_TACTILE_HAPTICS, defaults.tactileHaptics),
            cellularStreaming = p.getString(KEY_CELLULAR_STREAMING, defaults.cellularStreaming)
                ?: defaults.cellularStreaming,
            downloadOverWifiOnly = p.getBoolean(KEY_DOWNLOAD_WIFI_ONLY, defaults.downloadOverWifiOnly),
            automaticDownloads = p.getBoolean(KEY_AUTO_DOWNLOADS, defaults.automaticDownloads),
            optimizeStorage = p.getString(KEY_OPTIMIZE_STORAGE, defaults.optimizeStorage)
                ?: defaults.optimizeStorage,
            cachedSizeLabel = p.getString(KEY_CACHE_LABEL, defaults.cachedSizeLabel) ?: defaults.cachedSizeLabel,
            syncLibrary = p.getBoolean(KEY_SYNC_LIBRARY, defaults.syncLibrary),
            useListeningHistory = p.getBoolean(KEY_LISTENING_HISTORY, defaults.useListeningHistory),
            contentLanguage = ContentLocaleCatalog.normalizeLanguageChoice(
                p.getString(KEY_CONTENT_LANGUAGE, defaults.contentLanguage),
            ),
            contentCountry = ContentLocaleCatalog.normalizeCountryChoice(
                p.getString(KEY_CONTENT_COUNTRY, defaults.contentCountry),
            ),
            hideExplicitContent = hideExplicit,
            explicitContent = if (hideExplicit) "Clean Only" else "Allowed",
            addFavoriteSongs = p.getBoolean(KEY_ADD_FAVORITE_SONGS, defaults.addFavoriteSongs),
            newMusicAlerts = p.getBoolean(KEY_NEW_MUSIC_ALERTS, defaults.newMusicAlerts),
            offlineMode = p.getBoolean(KEY_OFFLINE_MODE, defaults.offlineMode),
            sleepTimer = p.getString(KEY_SLEEP_TIMER, defaults.sleepTimer) ?: defaults.sleepTimer,
        )
    }
}
