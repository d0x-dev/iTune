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
    val splashScreenEnabled: Boolean = true,
    val accountSubtitle: String = "iTune ID, Spatial Audio & Library",
    val spatialAudioMode: String = "Automatic",
    val dolbyAtmos: Boolean = true,
    val losslessAudio: Boolean = true,
    val audioQuality: String = "Extreme",
    val eqPreset: String = "Late Night",
    val crossfadeDuration: String = "Off",
    val autoMix: Boolean = false,
    val automixEnabled: Boolean = false,
    val soundCheck: Boolean = true,
    val animatedArtwork: Boolean = true,
    val timeSyncedLyrics: Boolean = true,
    val playerType: String = "Dynamic",
    val topCardType: String = "Portrait",
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
    val lyricsProviderOrder: List<String> = SettingsStore.DEFAULT_LYRICS_PROVIDERS_ORDER,
    val disabledLyricsProviders: Set<String> = emptySet(),
    // AI Lyrics Translation
    val lyricsTranslationEnabled: Boolean = false,
    val lyricsTranslationProvider: String = "OpenRouter",
    val lyricsTranslationApiKey: String = "",
    val lyricsTranslationBaseUrl: String = "",
    val lyricsTranslationModel: String = "",
    val lyricsTranslationCustomPrompt: String = "",
    val lyricsReplaceOriginal: Boolean = false,
    val lyricsTranslateLanguage: String = "hi-Latn",
    val lyricsTranslateMode: String = "Literal",
    val stopMusicOnTaskClear: Boolean = false,
)

/**
 * Persists Apple-styled Settings preferences in SharedPreferences and exposes reactive StateFlow state.
 */
object SettingsStore {
    private const val PREFS_NAME = "itune_settings_prefs"

    const val KEY_DISPLAY_NAME = "display_name"
    const val KEY_WELCOME_COMPLETED = "welcome_completed"
    const val KEY_SPLASH_SCREEN = "splash_screen_enabled"
    const val KEY_SPATIAL_AUDIO = "spatial_audio_mode"
    const val KEY_DOLBY_ATMOS = "dolby_atmos"
    const val KEY_LOSSLESS_AUDIO = "lossless_audio"
    const val KEY_AUDIO_QUALITY = "audio_quality"

    const val QUALITY_EXTREME = "Extreme"
    const val QUALITY_HIGH = "High"
    const val QUALITY_MEDIUM = "Medium"
    const val QUALITY_LOW = "Low"

    fun normalizeAudioQuality(raw: String?): String = when (raw?.trim()) {
        QUALITY_EXTREME, QUALITY_HIGH, QUALITY_MEDIUM, QUALITY_LOW -> raw.trim()
        "Hi-Res Lossless", "Lossless (24-bit)", "Lossless" -> QUALITY_EXTREME
        "High Quality" -> QUALITY_HIGH
        "High Efficiency" -> QUALITY_LOW
        else -> QUALITY_EXTREME
    }
    const val KEY_EQ_PRESET = "eq_preset"
    const val KEY_CROSSFADE = "crossfade_duration"
    const val KEY_AUTOMIX = "automix_enabled"
    const val KEY_SOUND_CHECK = "sound_check"
    const val KEY_ANIMATED_ARTWORK = "animated_artwork"
    const val KEY_TIME_SYNCED_LYRICS = "time_synced_lyrics"
    const val KEY_PLAYER_TYPE = "player_type"
    const val KEY_TOP_CARD_TYPE = "top_card_type"
    const val TOP_CARD_TYPE_PORTRAIT = "Portrait"
    const val TOP_CARD_TYPE_LANDSCAPE = "Landscape"
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
    const val KEY_LYRICS_PROVIDER_ORDER = "lyrics_provider_order"
    const val KEY_LYRICS_DISABLED_PROVIDERS = "lyrics_disabled_providers"
    // AI translation prefs
    const val KEY_LYRICS_TRANSLATION_ENABLED = "lyrics_translation_enabled"
    const val KEY_LYRICS_TRANSLATION_PROVIDER = "lyrics_translation_provider"
    const val KEY_LYRICS_TRANSLATION_API_KEY = "lyrics_translation_api_key"
    const val KEY_LYRICS_TRANSLATION_BASE_URL = "lyrics_translation_base_url"
    const val KEY_LYRICS_TRANSLATION_MODEL = "lyrics_translation_model"
    const val KEY_LYRICS_TRANSLATION_CUSTOM_PROMPT = "lyrics_translation_custom_prompt"
    const val KEY_LYRICS_REPLACE_ORIGINAL = "lyrics_replace_original"
    const val KEY_LYRICS_TRANSLATE_LANGUAGE = "lyrics_translate_language"
    const val KEY_LYRICS_TRANSLATE_MODE = "lyrics_translate_mode"

    val DEFAULT_LYRICS_PROVIDERS_ORDER = listOf(
        "lrclib",
        "kugou",
        "simpmusic",
        "youtube_subtitles",
        "paxsenix",
        "unison",
        "betterlyrics",
        "portato",
        "youly",
        "megalobiz",
        "youtube_music"
    )
    const val KEY_STOP_MUSIC_ON_TASK_CLEAR = "stop_music_on_task_clear"

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

    fun isStopMusicOnTaskClearEnabled(context: Context): Boolean {
        ensureInit(context)
        return _state.value.stopMusicOnTaskClear
    }

    fun isHapticsEnabled(): Boolean = _state.value.tactileHaptics

    fun effectiveHl(): String = ContentLocaleCatalog.resolveEffectiveHl(_state.value.contentLanguage)

    fun effectiveGl(): String = ContentLocaleCatalog.resolveEffectiveGl(_state.value.contentCountry)

    fun isHideExplicitEnabled(): Boolean = _state.value.hideExplicitContent

    fun localeSignature(): String = "${effectiveHl()}|${effectiveGl()}"

    fun parseCrossfadeDurationMs(setting: String): Int = when (setting.trim().lowercase()) {
        "2 sec" -> 2000
        "4 sec" -> 4000
        "6 sec" -> 6000
        "8 sec" -> 8000
        "12 sec" -> 12000
        else -> 0
    }

    val crossfadeDurationMs: Int get() = parseCrossfadeDurationMs(_state.value.crossfadeDuration)

    fun setBoolean(context: Context, key: String, value: Boolean) {
        ensureInit(context)
        prefs?.edit()?.putBoolean(key, value)?.apply()
        val cur = _state.value
        _state.value = when (key) {
            KEY_WELCOME_COMPLETED -> cur.copy(welcomeCompleted = value)
            KEY_SPLASH_SCREEN -> cur.copy(splashScreenEnabled = value)
            KEY_DOLBY_ATMOS -> cur.copy(dolbyAtmos = value)
            KEY_AUTOMIX -> cur.copy(autoMix = value, automixEnabled = value)
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
            KEY_STOP_MUSIC_ON_TASK_CLEAR -> cur.copy(stopMusicOnTaskClear = value)
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
            KEY_PLAYER_TYPE -> cur.copy(playerType = if (normalized.equals("Static", ignoreCase = true)) "Static" else "Dynamic")
            KEY_TOP_CARD_TYPE -> cur.copy(topCardType = if (normalized.equals(TOP_CARD_TYPE_LANDSCAPE, ignoreCase = true)) TOP_CARD_TYPE_LANDSCAPE else TOP_CARD_TYPE_PORTRAIT)
            KEY_SPATIAL_AUDIO -> cur.copy(spatialAudioMode = normalized)
            KEY_AUDIO_QUALITY -> cur.copy(audioQuality = normalizeAudioQuality(normalized))
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

    fun setDisplayName(context: Context, displayName: String) {
        val clean = displayName.trim()
        if (clean.isNotEmpty()) {
            setString(context, KEY_DISPLAY_NAME, clean)
        }
    }

    fun completeWelcome(context: Context, displayName: String) {
        setDisplayName(context, displayName)
        setBoolean(context, KEY_WELCOME_COMPLETED, true)
    }

    fun isLyricsProviderEnabled(id: String): Boolean =
        id !in _state.value.disabledLyricsProviders

    fun setLyricsProviderEnabled(context: Context, id: String, enabled: Boolean) {
        ensureInit(context)
        val curDisabled = _state.value.disabledLyricsProviders.toMutableSet()
        if (enabled) {
            curDisabled.remove(id)
        } else {
            curDisabled.add(id)
        }
        prefs?.edit()?.putStringSet(KEY_LYRICS_DISABLED_PROVIDERS, curDisabled)?.apply()
        _state.value = _state.value.copy(disabledLyricsProviders = curDisabled)
    }

    fun setLyricsProviderOrder(context: Context, newOrder: List<String>) {
        ensureInit(context)
        prefs?.edit()?.putString(KEY_LYRICS_PROVIDER_ORDER, newOrder.joinToString(","))?.apply()
        _state.value = _state.value.copy(lyricsProviderOrder = newOrder)
    }

    fun moveLyricsProvider(context: Context, fromIndex: Int, toIndex: Int) {
        ensureInit(context)
        val list = _state.value.lyricsProviderOrder.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices && fromIndex != toIndex) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            setLyricsProviderOrder(context, list)
        }
    }

    fun resetLyricsProviders(context: Context) {
        ensureInit(context)
        prefs?.edit()
            ?.remove(KEY_LYRICS_PROVIDER_ORDER)
            ?.remove(KEY_LYRICS_DISABLED_PROVIDERS)
            ?.apply()
        _state.value = _state.value.copy(
            lyricsProviderOrder = DEFAULT_LYRICS_PROVIDERS_ORDER,
            disabledLyricsProviders = emptySet()
        )
    }

    // ── AI Lyrics Translation ────────────────────────────────────────────────

    fun setLyricsTranslationEnabled(context: Context, enabled: Boolean) {
        ensureInit(context)
        prefs?.edit()?.putBoolean(KEY_LYRICS_TRANSLATION_ENABLED, enabled)?.apply()
        _state.value = _state.value.copy(lyricsTranslationEnabled = enabled)
    }

    fun setLyricsTranslationProvider(context: Context, provider: String) {
        ensureInit(context)
        prefs?.edit()?.putString(KEY_LYRICS_TRANSLATION_PROVIDER, provider)?.apply()
        _state.value = _state.value.copy(lyricsTranslationProvider = provider)
    }

    fun setLyricsTranslationApiKey(context: Context, key: String) {
        ensureInit(context)
        prefs?.edit()?.putString(KEY_LYRICS_TRANSLATION_API_KEY, key)?.apply()
        _state.value = _state.value.copy(lyricsTranslationApiKey = key)
    }

    fun setLyricsTranslationBaseUrl(context: Context, url: String) {
        ensureInit(context)
        prefs?.edit()?.putString(KEY_LYRICS_TRANSLATION_BASE_URL, url)?.apply()
        _state.value = _state.value.copy(lyricsTranslationBaseUrl = url)
    }

    fun setLyricsTranslationModel(context: Context, model: String) {
        ensureInit(context)
        prefs?.edit()?.putString(KEY_LYRICS_TRANSLATION_MODEL, model)?.apply()
        _state.value = _state.value.copy(lyricsTranslationModel = model)
    }

    fun setLyricsTranslationCustomPrompt(context: Context, prompt: String) {
        ensureInit(context)
        prefs?.edit()?.putString(KEY_LYRICS_TRANSLATION_CUSTOM_PROMPT, prompt)?.apply()
        _state.value = _state.value.copy(lyricsTranslationCustomPrompt = prompt)
    }

    fun setLyricsReplaceOriginal(context: Context, replace: Boolean) {
        ensureInit(context)
        prefs?.edit()?.putBoolean(KEY_LYRICS_REPLACE_ORIGINAL, replace)?.apply()
        _state.value = _state.value.copy(lyricsReplaceOriginal = replace)
    }

    fun setLyricsTranslateLanguage(context: Context, lang: String) {
        ensureInit(context)
        prefs?.edit()?.putString(KEY_LYRICS_TRANSLATE_LANGUAGE, lang)?.apply()
        _state.value = _state.value.copy(lyricsTranslateLanguage = lang)
    }

    fun setLyricsTranslateMode(context: Context, mode: String) {
        ensureInit(context)
        prefs?.edit()?.putString(KEY_LYRICS_TRANSLATE_MODE, mode)?.apply()
        _state.value = _state.value.copy(lyricsTranslateMode = mode)
    }

    fun setPlayerType(context: Context, type: String) {
        ensureInit(context)
        val valid = if (type.equals("Static", ignoreCase = true)) "Static" else "Dynamic"
        prefs?.edit()?.putString(KEY_PLAYER_TYPE, valid)?.apply()
        _state.value = _state.value.copy(playerType = valid)
    }

    private fun loadFromPrefs(p: SharedPreferences): SettingsUiState {
        val defaults = SettingsUiState()
        val legacyExplicit = p.getString(KEY_EXPLICIT_CONTENT, defaults.explicitContent) ?: defaults.explicitContent
        val hideExplicit = if (p.contains(KEY_HIDE_EXPLICIT)) {
            p.getBoolean(KEY_HIDE_EXPLICIT, defaults.hideExplicitContent)
        } else {
            legacyExplicit.equals("Clean Only", ignoreCase = true)
        }
        val savedOrderStr = p.getString(KEY_LYRICS_PROVIDER_ORDER, null)
        val parsedOrder = if (!savedOrderStr.isNullOrBlank()) {
            val list = savedOrderStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val missing = DEFAULT_LYRICS_PROVIDERS_ORDER.filter { it !in list }
            list + missing
        } else {
            DEFAULT_LYRICS_PROVIDERS_ORDER
        }
        val disabledSet = p.getStringSet(KEY_LYRICS_DISABLED_PROVIDERS, emptySet()) ?: emptySet()
        return SettingsUiState(
            displayName = p.getString(KEY_DISPLAY_NAME, defaults.displayName)?.ifBlank { defaults.displayName }
                ?: defaults.displayName,
            welcomeCompleted = p.getBoolean(KEY_WELCOME_COMPLETED, defaults.welcomeCompleted),
            splashScreenEnabled = p.getBoolean(KEY_SPLASH_SCREEN, defaults.splashScreenEnabled),
            spatialAudioMode = p.getString(KEY_SPATIAL_AUDIO, defaults.spatialAudioMode)
                ?: defaults.spatialAudioMode,
            dolbyAtmos = p.getBoolean(KEY_DOLBY_ATMOS, defaults.dolbyAtmos),
            losslessAudio = p.getBoolean(KEY_LOSSLESS_AUDIO, defaults.losslessAudio),
            audioQuality = normalizeAudioQuality(p.getString(KEY_AUDIO_QUALITY, defaults.audioQuality)),
            eqPreset = p.getString(KEY_EQ_PRESET, defaults.eqPreset) ?: defaults.eqPreset,
            crossfadeDuration = p.getString(KEY_CROSSFADE, defaults.crossfadeDuration) ?: defaults.crossfadeDuration,
            autoMix = p.getBoolean(KEY_AUTOMIX, defaults.autoMix),
            automixEnabled = p.getBoolean(KEY_AUTOMIX, defaults.automixEnabled),
            soundCheck = p.getBoolean(KEY_SOUND_CHECK, defaults.soundCheck),
            animatedArtwork = p.getBoolean(KEY_ANIMATED_ARTWORK, defaults.animatedArtwork),
            timeSyncedLyrics = p.getBoolean(KEY_TIME_SYNCED_LYRICS, defaults.timeSyncedLyrics),
            playerType = p.getString(KEY_PLAYER_TYPE, defaults.playerType) ?: defaults.playerType,
            topCardType = p.getString(KEY_TOP_CARD_TYPE, defaults.topCardType) ?: defaults.topCardType,
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
            lyricsProviderOrder = parsedOrder,
            disabledLyricsProviders = disabledSet,
            lyricsTranslationEnabled = p.getBoolean(KEY_LYRICS_TRANSLATION_ENABLED, defaults.lyricsTranslationEnabled),
            lyricsTranslationProvider = p.getString(KEY_LYRICS_TRANSLATION_PROVIDER, defaults.lyricsTranslationProvider) ?: defaults.lyricsTranslationProvider,
            lyricsTranslationApiKey = p.getString(KEY_LYRICS_TRANSLATION_API_KEY, defaults.lyricsTranslationApiKey) ?: defaults.lyricsTranslationApiKey,
            lyricsTranslationBaseUrl = p.getString(KEY_LYRICS_TRANSLATION_BASE_URL, defaults.lyricsTranslationBaseUrl) ?: defaults.lyricsTranslationBaseUrl,
            lyricsTranslationModel = p.getString(KEY_LYRICS_TRANSLATION_MODEL, defaults.lyricsTranslationModel) ?: defaults.lyricsTranslationModel,
            lyricsTranslationCustomPrompt = p.getString(KEY_LYRICS_TRANSLATION_CUSTOM_PROMPT, defaults.lyricsTranslationCustomPrompt) ?: defaults.lyricsTranslationCustomPrompt,
            lyricsReplaceOriginal = p.getBoolean(KEY_LYRICS_REPLACE_ORIGINAL, defaults.lyricsReplaceOriginal),
            lyricsTranslateLanguage = p.getString(KEY_LYRICS_TRANSLATE_LANGUAGE, defaults.lyricsTranslateLanguage) ?: defaults.lyricsTranslateLanguage,
            lyricsTranslateMode = p.getString(KEY_LYRICS_TRANSLATE_MODE, defaults.lyricsTranslateMode) ?: defaults.lyricsTranslateMode,
            stopMusicOnTaskClear = p.getBoolean(KEY_STOP_MUSIC_ON_TASK_CLEAR, defaults.stopMusicOnTaskClear),
        )
    }
}
