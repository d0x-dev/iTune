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

package com.darkxvenom.itune.data.library

import android.content.Context
import com.darkxvenom.itune.diagnostics.LiveLogs
import com.darkxvenom.itune.diagnostics.CrashReports
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.data.auth.EncryptedAuthPrefs
import com.darkxvenom.itune.data.canvas.SpotifyToken
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
    /** Optional JioSaavn stream provider: tried before YouTube when on. */
    val jioSaavnProvider: Boolean = false,
    /** With the JioSaavn provider on: replace cached YouTube songs with JioSaavn quality. */
    val upgradeCachedSongs: Boolean = false,
    /** Side of the screen the in-app volume bar appears on: "Right" or "Left". */
    val volumeBarSide: String = "Right",
    val audioQuality: String = "Extreme",
    val downloadQuality: String = "Best Available",
    val pcmOutputFormat: String = "16-bit PCM",
    val eqPreset: String = "Late Night",
    val crossfadeDuration: String = "Off",
    val autoMix: Boolean = false,
    val automixEnabled: Boolean = false,
    val afterglowDuration: String = "Off",
    val soundCheck: Boolean = true,
    val animatedArtwork: Boolean = true,
    val canvasOverCellular: Boolean = false,
    val spotifySpdcToken: String = "",
    val spotifyCanvasAutoHide: Boolean = true,
    val prioritizeSpotifyCanvas: Boolean = false,
    val showSyncedLyricsOverCanvas: Boolean = true,
    val timeSyncedLyrics: Boolean = true,
    val playerType: String = "Dynamic",
    val playerStyle: String = SettingsStore.PLAYER_STYLE_PRIMARY,
    val playerDynamicColoring: Boolean = true,
    val fullScreenCoverArt: Boolean = true,
    val coverCornerRadius: Int = 8,
    val hideQualityTag: Boolean = false,
    val hideVolumeBar: Boolean = false,
    val topCardType: String = "Portrait",
    val liquidGlassNav: Boolean = false,
    val appFont: String = "system",
    /** Settings screen layout: false = Maximised (one long list), true = Minimised (grouped into separate screens). */
    val settingsMinimised: Boolean = false,
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
    val appLanguage: String = AppLocaleManager.SYSTEM_DEFAULT,
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
    val autoSkipOnError: Boolean = true,
    val permanentShuffle: Boolean = false,
    val autoSleepTimerEnabled: Boolean = false,
    val autoSleepStartHour: Int = 23,
    val autoSleepStartMinute: Int = 0,
    val autoSleepEndHour: Int = 7,
    val autoSleepEndMinute: Int = 0,
    val autoSleepDays: Set<String> = setOf("All"),
    val proxyEnabled: Boolean = false,
    val proxyType: String = "HTTP",
    val proxyHost: String = "",
    val proxyPort: Int = 8080,
    val proxyUser: String = "",
    val proxyPass: String = "",
    val pauseSearchHistory: Boolean = false,
    val pauseListeningHistory: Boolean = false,
    val realTimeLogs: Boolean = false,
    val developerMode: Boolean = false,
    val disableScreenshot: Boolean = false,
    val pauseOnZeroVolume: Boolean = false,
    val lowPassFilterEnabled: Boolean = false,
    val aodEnabled: Boolean = false,
    val aodTimeoutSeconds: Int = 10,
    val aodImmersive: Boolean = true,
    val aodKeepScreenOn: Boolean = true,
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
    const val KEY_JIOSAAVN_PROVIDER = "jiosaavn_provider"
    const val KEY_UPGRADE_CACHED = "upgrade_cached_songs"
    const val KEY_VOLUME_BAR_SIDE = "volume_bar_side"
    const val KEY_AUDIO_QUALITY = "audio_quality"
    const val KEY_DOWNLOAD_QUALITY = "download_quality"
    const val KEY_PCM_OUTPUT_FORMAT = "pcm_output_format"

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

    fun normalizeAfterglowDuration(raw: String?): String = when (raw?.trim()?.lowercase()) {
        "5 sec", "5 seconds" -> "5 sec"
        "10 sec", "10 seconds" -> "10 sec"
        "15 sec", "15 seconds" -> "15 sec"
        else -> "Off"
    }
    const val KEY_EQ_PRESET = "eq_preset"
    const val KEY_CROSSFADE = "crossfade_duration"
    const val KEY_AUTOMIX = "automix_enabled"
    const val KEY_AFTERGLOW_DURATION = "afterglow_duration"
    const val KEY_SOUND_CHECK = "sound_check"
    const val KEY_ANIMATED_ARTWORK = "animated_artwork"
    const val KEY_CANVAS_OVER_CELLULAR = "canvas_over_cellular"
    const val KEY_SPOTIFY_SPDC_TOKEN = "spotify_spdc_token"
    const val KEY_SPOTIFY_CANVAS_AUTO_HIDE = "spotify_canvas_auto_hide"
    const val KEY_PRIORITIZE_SPOTIFY_CANVAS = "prioritize_spotify_canvas"
    const val KEY_SHOW_SYNCED_LYRICS_OVER_CANVAS = "show_synced_lyrics_over_canvas"
    const val KEY_TIME_SYNCED_LYRICS = "time_synced_lyrics"
    const val KEY_PLAYER_TYPE = "player_type"
    const val KEY_PLAYER_STYLE = "player_style"
    const val KEY_PLAYER_DYNAMIC_COLORING = "player_dynamic_coloring"
    const val KEY_FULL_SCREEN_COVER_ART = "full_screen_cover_art"
    const val KEY_COVER_CORNER_RADIUS = "cover_corner_radius"
    const val KEY_HIDE_QUALITY_TAG = "hide_quality_tag"
    const val KEY_HIDE_VOLUME_BAR = "hide_volume_bar"
    const val KEY_TOP_CARD_TYPE = "top_card_type"
    const val KEY_LIQUID_GLASS_NAV = "liquid_glass_nav"
    const val KEY_APP_FONT = "app_font"
    const val TOP_CARD_TYPE_PORTRAIT = "Portrait"
    const val TOP_CARD_TYPE_LANDSCAPE = "Landscape"
    const val TOP_CARD_TYPE_APPLE = "Apple"
    const val PLAYER_STYLE_PRIMARY = "Primary"
    const val PLAYER_STYLE_SECONDARY = "Secondary"
    const val KEY_LIQUID_GLASS = "liquid_glass_chrome"
    const val KEY_SETTINGS_MINIMISED = "settings_minimised"
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
    const val KEY_APP_LANGUAGE = "app_language"
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
        "youtube_music",
        "genius",
        "azlyrics",
        "musixmatch",
        "letssingit"
    )
    const val KEY_STOP_MUSIC_ON_TASK_CLEAR = "stop_music_on_task_clear"
    const val KEY_AUTO_SKIP_ON_ERROR = "auto_skip_on_error"

    // Permanent Shuffle
    const val KEY_PERMANENT_SHUFFLE = "permanent_shuffle"

    // Auto Sleep Timer
    const val KEY_AUTO_SLEEP_ENABLED = "auto_sleep_enabled"
    const val KEY_AUTO_SLEEP_START_HOUR = "auto_sleep_start_hour"
    const val KEY_AUTO_SLEEP_START_MINUTE = "auto_sleep_start_minute"
    const val KEY_AUTO_SLEEP_END_HOUR = "auto_sleep_end_hour"
    const val KEY_AUTO_SLEEP_END_MINUTE = "auto_sleep_end_minute"
    const val KEY_AUTO_SLEEP_DAYS = "auto_sleep_days"

    // Proxy Settings
    const val KEY_PROXY_ENABLED = "proxy_enabled"
    const val KEY_PROXY_TYPE = "proxy_type"
    const val KEY_PROXY_HOST = "proxy_host"
    const val KEY_PROXY_PORT = "proxy_port"
    const val KEY_PROXY_USER = "proxy_user"
    const val KEY_PROXY_PASS = "proxy_pass"

    // Privacy & Security
    const val KEY_PAUSE_SEARCH_HISTORY = "pause_search_history"
    const val KEY_PAUSE_LISTENING_HISTORY = "pause_listening_history"
    const val KEY_REAL_TIME_LOGS = "real_time_logs"
    const val KEY_DEVELOPER_MODE = "developer_mode"
    const val KEY_DISABLE_SCREENSHOT = "disable_screenshot"
    const val KEY_PAUSE_ON_ZERO_VOLUME = "pause_on_zero_volume"
    const val KEY_LOW_PASS_FILTER = "low_pass_filter_enabled"
    const val KEY_AOD_ENABLED = "aod_enabled"
    const val KEY_AOD_TIMEOUT = "aod_timeout_seconds"
    const val KEY_AOD_IMMERSIVE = "aod_immersive_mode"
    const val KEY_AOD_KEEP_SCREEN_ON = "aod_keep_screen_on"

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
            // Remove the retired light/system theme preference so old installs stay Dark-only too.
            p.edit().remove("app_theme").apply()
            _state.value = loadFromPrefs(context.applicationContext, p)
        }
    }

    fun reloadBackupState(context: Context) {
        ensureInit(context)
        prefs?.let { _state.value = loadFromPrefs(context.applicationContext, it) }
    }

    fun isStopMusicOnTaskClearEnabled(context: Context): Boolean {
        ensureInit(context)
        return _state.value.stopMusicOnTaskClear
    }

    fun isHapticsEnabled(): Boolean = _state.value.tactileHaptics
    fun isPauseSearchHistory(): Boolean = _state.value.pauseSearchHistory
    fun isPauseListeningHistory(): Boolean = _state.value.pauseListeningHistory
    fun isDisableScreenshot(): Boolean = _state.value.disableScreenshot
    fun isPauseOnZeroVolume(): Boolean = _state.value.pauseOnZeroVolume
    fun isLowPassFilterEnabled(): Boolean = _state.value.lowPassFilterEnabled
    fun isAodEnabled(): Boolean = _state.value.aodEnabled
    fun getAodTimeoutSeconds(): Int = _state.value.aodTimeoutSeconds
    fun isAodImmersive(): Boolean = _state.value.aodImmersive
    fun isAodKeepScreenOn(): Boolean = _state.value.aodKeepScreenOn

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
        if (key == KEY_DEVELOPER_MODE) {
            CrashReports.setEnabled(context.applicationContext, value)
            if (!value) LiveLogs.setEnabled(context, false)
            _state.value = _state.value.copy(developerMode = CrashReports.isEnabled(), realTimeLogs = LiveLogs.enabled)
            return
        }
        if (key == KEY_REAL_TIME_LOGS) {
            LiveLogs.setEnabled(context, value)
            _state.value = _state.value.copy(realTimeLogs = LiveLogs.enabled)
            return
        }
        prefs?.edit()?.putBoolean(key, value)?.apply()
        val cur = _state.value
        _state.value = when (key) {
            KEY_WELCOME_COMPLETED -> cur.copy(welcomeCompleted = value)
            KEY_SPLASH_SCREEN -> cur.copy(splashScreenEnabled = value)
            KEY_DOLBY_ATMOS -> cur.copy(dolbyAtmos = value)
            KEY_AUTOMIX -> cur.copy(autoMix = value, automixEnabled = value)
            KEY_LOSSLESS_AUDIO -> cur.copy(losslessAudio = value)
            KEY_JIOSAAVN_PROVIDER -> cur.copy(jioSaavnProvider = value)
            KEY_UPGRADE_CACHED -> cur.copy(upgradeCachedSongs = value)
            KEY_SOUND_CHECK -> cur.copy(soundCheck = value)
            KEY_ANIMATED_ARTWORK -> cur.copy(animatedArtwork = value)
            KEY_CANVAS_OVER_CELLULAR -> cur.copy(canvasOverCellular = value)
            KEY_SPOTIFY_CANVAS_AUTO_HIDE -> cur.copy(spotifyCanvasAutoHide = value)
            KEY_PRIORITIZE_SPOTIFY_CANVAS -> cur.copy(prioritizeSpotifyCanvas = value)
            KEY_SHOW_SYNCED_LYRICS_OVER_CANVAS -> cur.copy(showSyncedLyricsOverCanvas = value)
            KEY_PLAYER_DYNAMIC_COLORING -> cur.copy(playerDynamicColoring = value)
            KEY_FULL_SCREEN_COVER_ART -> cur.copy(fullScreenCoverArt = value)
            KEY_HIDE_QUALITY_TAG -> cur.copy(hideQualityTag = value)
            KEY_HIDE_VOLUME_BAR -> cur.copy(hideVolumeBar = value)
            KEY_LIQUID_GLASS_NAV -> cur.copy(liquidGlassNav = value)
            KEY_TIME_SYNCED_LYRICS -> cur.copy(timeSyncedLyrics = value)
            KEY_LIQUID_GLASS -> cur.copy(liquidGlassChrome = value)
            KEY_SETTINGS_MINIMISED -> cur.copy(settingsMinimised = value)
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
            KEY_AUTO_SKIP_ON_ERROR -> cur.copy(autoSkipOnError = value)
            KEY_PERMANENT_SHUFFLE -> cur.copy(permanentShuffle = value)
            KEY_AUTO_SLEEP_ENABLED -> cur.copy(autoSleepTimerEnabled = value)
            KEY_PROXY_ENABLED -> cur.copy(proxyEnabled = value)
            KEY_PAUSE_SEARCH_HISTORY -> cur.copy(pauseSearchHistory = value)
            KEY_PAUSE_LISTENING_HISTORY -> cur.copy(pauseListeningHistory = value)
            KEY_DISABLE_SCREENSHOT -> cur.copy(disableScreenshot = value)
            KEY_PAUSE_ON_ZERO_VOLUME -> cur.copy(pauseOnZeroVolume = value)
            KEY_LOW_PASS_FILTER -> cur.copy(lowPassFilterEnabled = value)
            KEY_AOD_ENABLED -> cur.copy(aodEnabled = value)
            KEY_AOD_IMMERSIVE -> cur.copy(aodImmersive = value)
            KEY_AOD_KEEP_SCREEN_ON -> cur.copy(aodKeepScreenOn = value)
            else -> cur
        }
    }

    fun setInt(context: Context, key: String, value: Int) {
        ensureInit(context)
        prefs?.edit()?.putInt(key, value)?.apply()
        val cur = _state.value
        _state.value = when (key) {
            KEY_AOD_TIMEOUT -> cur.copy(aodTimeoutSeconds = value)
            KEY_COVER_CORNER_RADIUS -> cur.copy(coverCornerRadius = value)
            else -> cur
        }
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    fun setString(context: Context, key: String, value: String) {
        ensureInit(context)
        val trimmed = value.trim()
        if (key == KEY_DISPLAY_NAME && trimmed.isEmpty()) return
        val normalized = when (key) {
            KEY_CONTENT_LANGUAGE -> ContentLocaleCatalog.normalizeLanguageChoice(trimmed)
            KEY_CONTENT_COUNTRY -> ContentLocaleCatalog.normalizeCountryChoice(trimmed)
            KEY_APP_LANGUAGE -> AppLocaleManager.normalizeLanguageChoice(trimmed)
            KEY_PLAYER_STYLE -> normalizePlayerStyle(trimmed)
            else -> trimmed
        }
        prefs?.edit()?.putString(key, normalized)?.apply()
        if (key == KEY_APP_LANGUAGE) AppLocaleManager.setLanguage(context, normalized)
        val cur = _state.value
        val localeChanged = (key == KEY_CONTENT_LANGUAGE && normalized != cur.contentLanguage) ||
            (key == KEY_CONTENT_COUNTRY && normalized != cur.contentCountry)
        _state.value = when (key) {
            KEY_DISPLAY_NAME -> cur.copy(displayName = normalized)
            KEY_PLAYER_TYPE -> cur.copy(playerType = if (normalized.equals("Static", ignoreCase = true)) "Static" else "Dynamic")
            KEY_PLAYER_STYLE -> cur.copy(playerStyle = normalizePlayerStyle(normalized))
            KEY_TOP_CARD_TYPE -> cur.copy(
                topCardType = when {
                    normalized.equals(TOP_CARD_TYPE_LANDSCAPE, ignoreCase = true) -> TOP_CARD_TYPE_LANDSCAPE
                    normalized.equals(TOP_CARD_TYPE_APPLE, ignoreCase = true) -> TOP_CARD_TYPE_APPLE
                    else -> TOP_CARD_TYPE_PORTRAIT
                },
            )
            KEY_APP_FONT -> cur.copy(appFont = normalized)
            KEY_SPATIAL_AUDIO -> cur.copy(spatialAudioMode = normalized)
            KEY_AUDIO_QUALITY -> cur.copy(audioQuality = normalizeAudioQuality(normalized))
            KEY_VOLUME_BAR_SIDE -> cur.copy(volumeBarSide = if (normalized.equals("Left", true)) "Left" else "Right")
            KEY_DOWNLOAD_QUALITY -> cur.copy(downloadQuality = normalized.takeIf { it in setOf("Best Available", "Maximum", "High", "Standard", "Data Saver") } ?: "Best Available")
            KEY_PCM_OUTPUT_FORMAT -> cur.copy(pcmOutputFormat = if (normalized == "32-bit float PCM") normalized else "16-bit PCM")
            KEY_EQ_PRESET -> cur.copy(eqPreset = normalized)
            KEY_CROSSFADE -> cur.copy(crossfadeDuration = normalized)
            KEY_AFTERGLOW_DURATION -> cur.copy(afterglowDuration = normalizeAfterglowDuration(normalized))
            KEY_CELLULAR_STREAMING -> cur.copy(cellularStreaming = normalized)
            KEY_OPTIMIZE_STORAGE -> cur.copy(optimizeStorage = normalized)
            KEY_CONTENT_LANGUAGE -> cur.copy(contentLanguage = normalized)
            KEY_CONTENT_COUNTRY -> cur.copy(contentCountry = normalized)
            KEY_APP_LANGUAGE -> cur.copy(appLanguage = normalized)
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

    fun setSpotifySpdcToken(context: Context, token: String) {
        ensureInit(context)
        val trimmed = token.trim()
        LiveLogs.event("Spotify", if (trimmed.isBlank()) "Disconnected" else "Session saved")
        EncryptedAuthPrefs.putEncrypted(
            context,
            KEY_SPOTIFY_SPDC_TOKEN,
            trimmed.ifBlank { null },
        )
        _state.value = _state.value.copy(spotifySpdcToken = trimmed)
        SpotifyToken.invalidate()
        com.darkxvenom.itune.data.canvas.CanvasRepository.clearCache()
        if (trimmed.isBlank()) {
            LibraryStore.clearSpotifyAccountDataOnSignOut(context)
        } else {
            com.darkxvenom.itune.data.spotify.SpotifyLibrarySyncManager.launchSync(context, force = true)
        }
    }

    fun setPlayerType(context: Context, type: String) {
        ensureInit(context)
        val valid = if (type.equals("Static", ignoreCase = true)) "Static" else "Dynamic"
        prefs?.edit()?.putString(KEY_PLAYER_TYPE, valid)?.apply()
        _state.value = _state.value.copy(playerType = valid)
    }

    fun setPlayerStyle(context: Context, style: String) {
        setString(context, KEY_PLAYER_STYLE, style)
    }

    private fun normalizePlayerStyle(value: String): String =
        if (value.equals(PLAYER_STYLE_SECONDARY, ignoreCase = true)) PLAYER_STYLE_SECONDARY else PLAYER_STYLE_PRIMARY

    fun isPermanentShuffleEnabled(): Boolean = _state.value.permanentShuffle

    fun setAutoSleepConfig(
        context: Context,
        enabled: Boolean,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        days: Set<String>
    ) {
        ensureInit(context)
        prefs?.edit()
            ?.putBoolean(KEY_AUTO_SLEEP_ENABLED, enabled)
            ?.putInt(KEY_AUTO_SLEEP_START_HOUR, startHour)
            ?.putInt(KEY_AUTO_SLEEP_START_MINUTE, startMinute)
            ?.putInt(KEY_AUTO_SLEEP_END_HOUR, endHour)
            ?.putInt(KEY_AUTO_SLEEP_END_MINUTE, endMinute)
            ?.putStringSet(KEY_AUTO_SLEEP_DAYS, days)
            ?.apply()
        _state.value = _state.value.copy(
            autoSleepTimerEnabled = enabled,
            autoSleepStartHour = startHour,
            autoSleepStartMinute = startMinute,
            autoSleepEndHour = endHour,
            autoSleepEndMinute = endMinute,
            autoSleepDays = days,
        )
    }

    fun setProxyConfig(
        context: Context,
        enabled: Boolean,
        type: String,
        host: String,
        port: Int,
        user: String,
        pass: String
    ) {
        ensureInit(context)
        prefs?.edit()
            ?.putBoolean(KEY_PROXY_ENABLED, enabled)
            ?.putString(KEY_PROXY_TYPE, type)
            ?.putString(KEY_PROXY_HOST, host)
            ?.putInt(KEY_PROXY_PORT, port)
            ?.putString(KEY_PROXY_USER, user)
            ?.putString(KEY_PROXY_PASS, pass)
            ?.apply()
        _state.value = _state.value.copy(
            proxyEnabled = enabled,
            proxyType = type,
            proxyHost = host,
            proxyPort = port,
            proxyUser = user,
            proxyPass = pass,
        )
    }

    fun setAudioQuality(context: Context, quality: String) {
        setString(context, KEY_AUDIO_QUALITY, quality)
    }

    fun setDownloadQuality(context: Context, quality: String) {
        setString(context, KEY_DOWNLOAD_QUALITY, quality)
    }

    fun setPcmOutputFormat(context: Context, format: String) {
        setString(context, KEY_PCM_OUTPUT_FORMAT, format)
    }

    fun setEqPreset(context: Context, preset: String) {
        setString(context, KEY_EQ_PRESET, preset)
    }

    fun setCrossfadeDuration(context: Context, duration: String) {
        setString(context, KEY_CROSSFADE, duration)
    }

    fun setAfterglowDuration(context: Context, duration: String) {
        setString(context, KEY_AFTERGLOW_DURATION, duration)
    }

    fun setCellularStreaming(context: Context, quality: String) {
        setString(context, KEY_CELLULAR_STREAMING, quality)
    }

    fun setOptimizeStorage(context: Context, storage: String) {
        setString(context, KEY_OPTIMIZE_STORAGE, storage)
    }

    fun setContentLanguage(context: Context, language: String) {
        setString(context, KEY_CONTENT_LANGUAGE, language)
    }

    fun setContentCountry(context: Context, country: String) {
        setString(context, KEY_CONTENT_COUNTRY, country)
    }

    fun setAppLanguage(context: Context, language: String) {
        setString(context, KEY_APP_LANGUAGE, language)
    }

    fun setExplicitContent(context: Context, content: String) {
        setString(context, KEY_EXPLICIT_CONTENT, content)
    }

    fun setSleepTimer(context: Context, timer: String) {
        setString(context, KEY_SLEEP_TIMER, timer)
    }

    fun setCachedSizeLabel(context: Context, label: String) {
        setString(context, KEY_CACHE_LABEL, label)
    }

    private fun loadFromPrefs(context: Context, p: SharedPreferences): SettingsUiState {
        return SettingsUiState(
            displayName = p.getString(KEY_DISPLAY_NAME, "Dark Venom") ?: "Dark Venom",
            welcomeCompleted = p.getBoolean(KEY_WELCOME_COMPLETED, false),
            splashScreenEnabled = p.getBoolean(KEY_SPLASH_SCREEN, true),
            spatialAudioMode = p.getString(KEY_SPATIAL_AUDIO, "Automatic") ?: "Automatic",
            dolbyAtmos = p.getBoolean(KEY_DOLBY_ATMOS, true),
            losslessAudio = p.getBoolean(KEY_LOSSLESS_AUDIO, true),
            jioSaavnProvider = p.getBoolean(KEY_JIOSAAVN_PROVIDER, false),
            upgradeCachedSongs = p.getBoolean(KEY_UPGRADE_CACHED, false),
            volumeBarSide = p.getString(KEY_VOLUME_BAR_SIDE, "Right") ?: "Right",
            audioQuality = p.getString(KEY_AUDIO_QUALITY, "Extreme") ?: "Extreme",
            downloadQuality = p.getString(KEY_DOWNLOAD_QUALITY, "Best Available") ?: "Best Available",
            pcmOutputFormat = p.getString(KEY_PCM_OUTPUT_FORMAT, "16-bit PCM") ?: "16-bit PCM",
            eqPreset = p.getString(KEY_EQ_PRESET, "Late Night") ?: "Late Night",
            crossfadeDuration = p.getString(KEY_CROSSFADE, "Off") ?: "Off",
            autoMix = p.getBoolean(KEY_AUTOMIX, false),
            automixEnabled = p.getBoolean(KEY_AUTOMIX, false),
            afterglowDuration = p.getString(KEY_AFTERGLOW_DURATION, "Off") ?: "Off",
            soundCheck = p.getBoolean(KEY_SOUND_CHECK, true),
            animatedArtwork = p.getBoolean(KEY_ANIMATED_ARTWORK, true),
            canvasOverCellular = p.getBoolean(KEY_CANVAS_OVER_CELLULAR, false),
            spotifySpdcToken = EncryptedAuthPrefs.getDecrypted(context, KEY_SPOTIFY_SPDC_TOKEN).orEmpty(),
            spotifyCanvasAutoHide = p.getBoolean(KEY_SPOTIFY_CANVAS_AUTO_HIDE, true),
            prioritizeSpotifyCanvas = p.getBoolean(KEY_PRIORITIZE_SPOTIFY_CANVAS, false),
            showSyncedLyricsOverCanvas = p.getBoolean(KEY_SHOW_SYNCED_LYRICS_OVER_CANVAS, true),
            timeSyncedLyrics = p.getBoolean(KEY_TIME_SYNCED_LYRICS, true),
            playerType = p.getString(KEY_PLAYER_TYPE, "Dynamic") ?: "Dynamic",
            playerStyle = p.getString(KEY_PLAYER_STYLE, PLAYER_STYLE_PRIMARY) ?: PLAYER_STYLE_PRIMARY,
            playerDynamicColoring = p.getBoolean(KEY_PLAYER_DYNAMIC_COLORING, true),
            fullScreenCoverArt = p.getBoolean(KEY_FULL_SCREEN_COVER_ART, true),
            coverCornerRadius = p.getInt(KEY_COVER_CORNER_RADIUS, 8),
            hideQualityTag = p.getBoolean(KEY_HIDE_QUALITY_TAG, false),
            hideVolumeBar = p.getBoolean(KEY_HIDE_VOLUME_BAR, false),
            topCardType = p.getString(KEY_TOP_CARD_TYPE, "Portrait") ?: "Portrait",
            liquidGlassNav = p.getBoolean(KEY_LIQUID_GLASS_NAV, false),
            appFont = p.getString(KEY_APP_FONT, "system") ?: "system",
            settingsMinimised = p.getBoolean(KEY_SETTINGS_MINIMISED, false),
            liquidGlassChrome = p.getBoolean(KEY_LIQUID_GLASS, true),
            tactileHaptics = p.getBoolean(KEY_TACTILE_HAPTICS, true),
            cellularStreaming = p.getString(KEY_CELLULAR_STREAMING, "High Quality") ?: "High Quality",
            downloadOverWifiOnly = p.getBoolean(KEY_DOWNLOAD_WIFI_ONLY, true),
            automaticDownloads = p.getBoolean(KEY_AUTO_DOWNLOADS, false),
            optimizeStorage = p.getString(KEY_OPTIMIZE_STORAGE, "16 GB") ?: "16 GB",
            cachedSizeLabel = p.getString(KEY_CACHE_LABEL, "284 MB") ?: "284 MB",
            syncLibrary = p.getBoolean(KEY_SYNC_LIBRARY, true),
            useListeningHistory = p.getBoolean(KEY_LISTENING_HISTORY, true),
            contentLanguage = p.getString(KEY_CONTENT_LANGUAGE, ContentLocaleCatalog.SYSTEM_DEFAULT) ?: ContentLocaleCatalog.SYSTEM_DEFAULT,
            contentCountry = p.getString(KEY_CONTENT_COUNTRY, ContentLocaleCatalog.SYSTEM_DEFAULT) ?: ContentLocaleCatalog.SYSTEM_DEFAULT,
            appLanguage = p.getString(KEY_APP_LANGUAGE, AppLocaleManager.SYSTEM_DEFAULT) ?: AppLocaleManager.SYSTEM_DEFAULT,
            hideExplicitContent = p.getBoolean(KEY_HIDE_EXPLICIT, false),
            explicitContent = p.getString(KEY_EXPLICIT_CONTENT, "Allowed") ?: "Allowed",
            addFavoriteSongs = p.getBoolean(KEY_ADD_FAVORITE_SONGS, true),
            newMusicAlerts = p.getBoolean(KEY_NEW_MUSIC_ALERTS, true),
            offlineMode = p.getBoolean(KEY_OFFLINE_MODE, false),
            sleepTimer = p.getString(KEY_SLEEP_TIMER, "Off") ?: "Off",
            lyricsProviderOrder = p.getString(KEY_LYRICS_PROVIDER_ORDER, "")
                ?.split(",")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: DEFAULT_LYRICS_PROVIDERS_ORDER,
            disabledLyricsProviders = p.getStringSet(KEY_LYRICS_DISABLED_PROVIDERS, emptySet())?.toSet() ?: emptySet(),
            lyricsTranslationEnabled = p.getBoolean(KEY_LYRICS_TRANSLATION_ENABLED, false),
            lyricsTranslationProvider = p.getString(KEY_LYRICS_TRANSLATION_PROVIDER, "OpenRouter") ?: "OpenRouter",
            lyricsTranslationApiKey = p.getString(KEY_LYRICS_TRANSLATION_API_KEY, "") ?: "",
            lyricsTranslationBaseUrl = p.getString(KEY_LYRICS_TRANSLATION_BASE_URL, "") ?: "",
            lyricsTranslationModel = p.getString(KEY_LYRICS_TRANSLATION_MODEL, "") ?: "",
            lyricsTranslationCustomPrompt = p.getString(KEY_LYRICS_TRANSLATION_CUSTOM_PROMPT, "") ?: "",
            lyricsReplaceOriginal = p.getBoolean(KEY_LYRICS_REPLACE_ORIGINAL, false),
            lyricsTranslateLanguage = p.getString(KEY_LYRICS_TRANSLATE_LANGUAGE, "hi-Latn") ?: "hi-Latn",
            lyricsTranslateMode = p.getString(KEY_LYRICS_TRANSLATE_MODE, "Literal") ?: "Literal",
            stopMusicOnTaskClear = p.getBoolean(KEY_STOP_MUSIC_ON_TASK_CLEAR, false),
            autoSkipOnError = p.getBoolean(KEY_AUTO_SKIP_ON_ERROR, true),
            permanentShuffle = p.getBoolean(KEY_PERMANENT_SHUFFLE, false),
            autoSleepTimerEnabled = p.getBoolean(KEY_AUTO_SLEEP_ENABLED, false),
            autoSleepStartHour = p.getInt(KEY_AUTO_SLEEP_START_HOUR, 23),
            autoSleepStartMinute = p.getInt(KEY_AUTO_SLEEP_START_MINUTE, 0),
            autoSleepEndHour = p.getInt(KEY_AUTO_SLEEP_END_HOUR, 7),
            autoSleepEndMinute = p.getInt(KEY_AUTO_SLEEP_END_MINUTE, 0),
            autoSleepDays = p.getStringSet(KEY_AUTO_SLEEP_DAYS, setOf("All"))?.toSet() ?: setOf("All"),
            proxyEnabled = p.getBoolean(KEY_PROXY_ENABLED, false),
            proxyType = p.getString(KEY_PROXY_TYPE, "HTTP") ?: "HTTP",
            proxyHost = p.getString(KEY_PROXY_HOST, "") ?: "",
            proxyPort = p.getInt(KEY_PROXY_PORT, 8080),
            proxyUser = p.getString(KEY_PROXY_USER, "") ?: "",
            proxyPass = p.getString(KEY_PROXY_PASS, "") ?: "",
            pauseSearchHistory = p.getBoolean(KEY_PAUSE_SEARCH_HISTORY, false),
            pauseListeningHistory = p.getBoolean(KEY_PAUSE_LISTENING_HISTORY, false),
            developerMode = CrashReports.isEnabled(),
            realTimeLogs = LiveLogs.enabled,
            disableScreenshot = p.getBoolean(KEY_DISABLE_SCREENSHOT, false),
            pauseOnZeroVolume = p.getBoolean(KEY_PAUSE_ON_ZERO_VOLUME, false),
            lowPassFilterEnabled = p.getBoolean(KEY_LOW_PASS_FILTER, false),
            aodEnabled = p.getBoolean(KEY_AOD_ENABLED, false),
            aodTimeoutSeconds = p.getInt(KEY_AOD_TIMEOUT, 10),
            aodImmersive = p.getBoolean(KEY_AOD_IMMERSIVE, true),
            aodKeepScreenOn = p.getBoolean(KEY_AOD_KEEP_SCREEN_ON, true),
        )
    }
}
