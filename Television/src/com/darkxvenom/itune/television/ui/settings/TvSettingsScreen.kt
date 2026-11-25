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

package com.darkxvenom.itune.television.ui.settings


import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.Coil
import com.darkxvenom.itune.BuildConfig
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.lyrics.LyricsHelper
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.auth.WebSessionMode
import com.darkxvenom.itune.data.backup.BackupRestoreManager
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.spotify.SpotifyLibrarySyncManager
import com.darkxvenom.itune.ishare.IShareSyncManager
import com.darkxvenom.itune.television.focus.SFProDisplay
import com.darkxvenom.itune.television.focus.TvAppleSilverBg
import com.darkxvenom.itune.television.focus.tvFocusable
import com.darkxvenom.itune.ui.auth.GoogleLoginScreen
import java.io.File

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

private const val DEFAULT_BLURB = "Playback, sound and display preferences for iTune on this TV."

/**
 * Settings Screen matching Apple TV (tvOS) layout:
 * - Left column: App Logo / Mark and dynamic description ("blurb") that updates smoothly on focus.
 * - Right column: Grouped settings rows with in-place value display and toggling.
 * - Comprehensive settings options from the app module.
 */
@Composable
fun TvSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val settingsState by SettingsStore.state.collectAsStateWithLifecycle()
    val ytAccountState by GoogleAuthStore.state.collectAsStateWithLifecycle()

    var blurb by remember { mutableStateOf(DEFAULT_BLURB) }
    var loginMode by remember { mutableStateOf<WebSessionMode?>(null) }
    var showSpotifyDialog by remember { mutableStateOf(false) }
    var showSpotifyWebLogin by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showLyricsProvidersDialog by remember { mutableStateOf(false) }

    val ishareSession by IShareSyncManager.session.collectAsStateWithLifecycle()
    val ishareIsHost by IShareSyncManager.isHost.collectAsStateWithLifecycle()
    val localIp by IShareSyncManager.localIp.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize().background(TvAppleSilverBg)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
        ) {
            // Left Column (42% width): App Mark + Animated Focused Blurb Description
            Column(
                modifier = Modifier
                    .weight(0.40f)
                    .fillMaxHeight()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                TvAppMark(size = 92.dp)
                Spacer(modifier = Modifier.height(20.dp))
                AnimatedContent(
                    targetState = blurb,
                    transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                    label = "tvSettingsBlurb",
                ) { text ->
                    Text(
                        text = text,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.5.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 280.dp),
                    )
                }
            }

            // Right Column (60% width): Grouped Settings List
            LazyColumn(
                modifier = Modifier
                    .weight(0.60f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(end = 44.dp, bottom = 48.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 1. Account & Synchronization
                item { TvListHeader("Account & Cloud") }
                item {
                    TvListRow(
                        title = "YouTube Music",
                        value = if (ytAccountState.isSignedIn) ytAccountState.accountName ?: "Signed In" else "Sign In",
                        leadingIcon = Icons.Rounded.AccountCircle,
                        onFocused = { blurb = "Your YouTube Music library, playlists and personalized recommendations come from this account." },
                        onClick = { loginMode = WebSessionMode.SIGN_IN },
                    )
                }
                item {
                    TvListRow(
                        title = "Spotify Account",
                        value = if (settingsState.spotifySpdcToken.isNotBlank()) "Connected" else "Connect",
                        leadingIcon = Icons.Rounded.Sync,
                        onFocused = { blurb = "Connects Spotify to fetch Canvas motion video artwork and synchronize your Spotify playlists." },
                        onClick = { showSpotifyDialog = true },
                    )
                }
                item {
                    TvListRow(
                        title = "Spotify Canvas Auto-Hide",
                        value = onOff(settingsState.spotifyCanvasAutoHide),
                        onFocused = { blurb = "Hides Spotify Canvas motion artwork when navigating to lyrics." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_SPOTIFY_CANVAS_AUTO_HIDE, !settingsState.spotifyCanvasAutoHide)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Display Name",
                        value = settingsState.displayName.ifBlank { "Dark" },
                        onFocused = { blurb = "Your profile display name across iTune." },
                        onClick = { /* Display name prompt or default */ },
                    )
                }
                item {
                    TvListRow(
                        title = "Sync Library",
                        value = onOff(settingsState.syncLibrary),
                        onFocused = { blurb = "Synchronizes your saved playlists, albums and favorite songs automatically." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_SYNC_LIBRARY, !settingsState.syncLibrary)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Listening History",
                        value = onOff(settingsState.useListeningHistory),
                        onFocused = { blurb = "Builds Home recommendations from the songs you listen to on this TV." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_LISTENING_HISTORY, !settingsState.useListeningHistory)
                        },
                    )
                }

                // 2. Playback & Transition
                item { TvListHeader("Playback") }
                item {
                    TvListRow(
                        title = "Automix",
                        value = onOff(settingsState.automixEnabled || settingsState.autoMix),
                        onFocused = { blurb = "Blends one song into the next, matched to their tempo and rhythm." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_AUTOMIX, !(settingsState.automixEnabled || settingsState.autoMix))
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Crossfade",
                        value = settingsState.crossfadeDuration,
                        onFocused = { blurb = "Fades each song out as the next one fades in. Select to cycle duration." },
                        onClick = {
                            val next = when (settingsState.crossfadeDuration) {
                                "Off" -> "3s"
                                "3s" -> "6s"
                                "6s" -> "10s"
                                "10s" -> "12s"
                                else -> "Off"
                            }
                            SettingsStore.setString(context, SettingsStore.KEY_CROSSFADE, next)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Sound Check",
                        value = onOff(settingsState.soundCheck),
                        onFocused = { blurb = "Normalizes volume so every song plays at the same perceived loudness level." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_SOUND_CHECK, !settingsState.soundCheck)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Pause on Zero Volume",
                        value = onOff(settingsState.pauseOnZeroVolume),
                        onFocused = { blurb = "Automatically pauses audio playback when the TV is muted." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_PAUSE_ON_ZERO_VOLUME, !settingsState.pauseOnZeroVolume)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Auto-Skip on Error",
                        value = onOff(settingsState.autoSkipOnError),
                        onFocused = { blurb = "Automatically advances to the next track if a network stream error occurs." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_AUTO_SKIP_ON_ERROR, !settingsState.autoSkipOnError)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Permanent Shuffle",
                        value = onOff(settingsState.permanentShuffle),
                        onFocused = { blurb = "Always starts playlists and albums in randomized shuffle mode." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_PERMANENT_SHUFFLE, !settingsState.permanentShuffle)
                        },
                    )
                }

                // 3. Sound & Audio Engine
                item { TvListHeader("Sound & Audio Engine") }
                item {
                    TvListRow(
                        title = "Dolby Atmos",
                        value = onOff(settingsState.dolbyAtmos),
                        leadingIcon = Icons.Rounded.GraphicEq,
                        onFocused = { blurb = "Enables Dolby Atmos spatial surround sound processing on supported audio setups." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_DOLBY_ATMOS, !settingsState.dolbyAtmos)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Spatial Audio",
                        value = settingsState.spatialAudioMode,
                        onFocused = { blurb = "Widens stereo music using 3D virtual surround audio channels. Select to cycle mode." },
                        onClick = {
                            val next = when (settingsState.spatialAudioMode) {
                                "Automatic" -> "Always On"
                                "Always On" -> "Off"
                                else -> "Automatic"
                            }
                            SettingsStore.setString(context, SettingsStore.KEY_SPATIAL_AUDIO, next)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Lossless Audio",
                        value = onOff(settingsState.losslessAudio),
                        onFocused = { blurb = "Preserves every detail of the original studio recording up to 24-bit / 192 kHz." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_LOSSLESS_AUDIO, !settingsState.losslessAudio)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Streaming Quality",
                        value = settingsState.audioQuality,
                        onFocused = { blurb = "Higher bitrates produce superior acoustics on soundbars and home theaters." },
                        onClick = {
                            val next = when (settingsState.audioQuality) {
                                "Extreme" -> "High Quality"
                                "High Quality" -> "Standard"
                                else -> "Extreme"
                            }
                            SettingsStore.setString(context, SettingsStore.KEY_AUDIO_QUALITY, next)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Equalizer Preset",
                        value = settingsState.eqPreset,
                        onFocused = { blurb = "Shapes frequency response with finely tuned hardware EQ curves. Select to cycle." },
                        onClick = {
                            val presets = listOf("Late Night", "Bass Booster", "Vocal Booster", "Acoustic", "Flat", "Off")
                            val curIdx = presets.indexOf(settingsState.eqPreset).coerceAtLeast(0)
                            val next = presets[(curIdx + 1) % presets.size]
                            SettingsStore.setString(context, SettingsStore.KEY_EQ_PRESET, next)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "PCM Output Format",
                        value = settingsState.pcmOutputFormat,
                        onFocused = { blurb = "Direct DAC output encoding format for connected audio receivers." },
                        onClick = {
                            val formats = listOf("16-bit PCM", "24-bit PCM", "32-bit Float")
                            val curIdx = formats.indexOf(settingsState.pcmOutputFormat).coerceAtLeast(0)
                            val next = formats[(curIdx + 1) % formats.size]
                            SettingsStore.setString(context, SettingsStore.KEY_PCM_OUTPUT_FORMAT, next)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Low-Pass Filter",
                        value = onOff(settingsState.lowPassFilterEnabled),
                        onFocused = { blurb = "Smooths ultra-high harmonic frequencies for a warmer, vintage tone." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_LOW_PASS_FILTER, !settingsState.lowPassFilterEnabled)
                        },
                    )
                }

                // 4. Now Playing & Artwork
                item { TvListHeader("Now Playing & Artwork") }
                item {
                    TvListRow(
                        title = "Time-Synced Lyrics",
                        value = onOff(settingsState.timeSyncedLyrics),
                        leadingIcon = Icons.Rounded.MusicNote,
                        onFocused = { blurb = "Displays real-time synchronized karaoke lyrics that follow along with the music line by line." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_TIME_SYNCED_LYRICS, !settingsState.timeSyncedLyrics)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Motion Artwork (Canvas)",
                        value = onOff(settingsState.animatedArtwork),
                        onFocused = { blurb = "Plays looping video artwork behind the player when available." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_ANIMATED_ARTWORK, !settingsState.animatedArtwork)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Motion Artwork in Lyrics",
                        value = onOff(settingsState.showSyncedLyricsOverCanvas),
                        onFocused = { blurb = "Keeps video artwork playing in the background during full-screen synchronized lyrics." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_SHOW_SYNCED_LYRICS_OVER_CANVAS, !settingsState.showSyncedLyricsOverCanvas)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Spotify Canvas Support",
                        value = onOff(settingsState.prioritizeSpotifyCanvas),
                        onFocused = { blurb = "Fetches and prioritizes Spotify Canvas video loops for popular songs." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_PRIORITIZE_SPOTIFY_CANVAS, !settingsState.prioritizeSpotifyCanvas)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Lyrics Providers",
                        value = "${settingsState.lyricsProviderOrder.size - settingsState.disabledLyricsProviders.size} Active",
                        leadingIcon = Icons.Rounded.Tune,
                        onFocused = { blurb = "Configure multiple lyric sources: LRCLIB, KuGou, Paxsenix, BetterLyrics, and more in custom priority order." },
                        onClick = { showLyricsProvidersDialog = true },
                    )
                }
                item {
                    TvListRow(
                        title = "AI Lyrics Translation",
                        value = onOff(settingsState.lyricsTranslationEnabled),
                        leadingIcon = Icons.Rounded.Language,
                        onFocused = { blurb = "Real-time AI machine translation for international and multilingual song lyrics." },
                        onClick = {
                            SettingsStore.setLyricsTranslationEnabled(context, !settingsState.lyricsTranslationEnabled)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "AI Translation Provider",
                        value = settingsState.lyricsTranslationProvider,
                        onFocused = { blurb = "Choose AI provider for synchronized lyrics translation." },
                        onClick = {
                            val providers = listOf("OpenRouter", "Gemini", "OpenAI", "Claude")
                            val curIdx = providers.indexOf(settingsState.lyricsTranslationProvider).coerceAtLeast(0)
                            val next = providers[(curIdx + 1) % providers.size]
                            SettingsStore.setLyricsTranslationProvider(context, next)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Translation Target Language",
                        value = settingsState.lyricsTranslateLanguage.ifBlank { "English" },
                        onFocused = { blurb = "Target language for AI translated lyrics. Select to cycle through languages." },
                        onClick = {
                            val languages = listOf("English", "Spanish", "French", "German", "Japanese", "Korean", "Hindi", "Italian", "Chinese")
                            val curIdx = languages.indexOf(settingsState.lyricsTranslateLanguage).coerceAtLeast(0)
                            val next = languages[(curIdx + 1) % languages.size]
                            SettingsStore.setString(context, SettingsStore.KEY_LYRICS_TRANSLATE_LANGUAGE, next)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Replace Original Lyrics",
                        value = onOff(settingsState.lyricsReplaceOriginal),
                        onFocused = { blurb = "Replaces the original song lyrics with translated lines instead of showing both." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_LYRICS_REPLACE_ORIGINAL, !settingsState.lyricsReplaceOriginal)
                        },
                    )
                }

                // 5. Display & Visuals
                item { TvListHeader("Display & Visuals") }
                item {
                    TvListRow(
                        title = "Splash Screen",
                        value = onOff(settingsState.splashScreenEnabled),
                        onFocused = { blurb = "Shows the branded splash animation with typed logo gliding into the top bar." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_SPLASH_SCREEN, !settingsState.splashScreenEnabled)
                        },
                    )
                }

                // 6. Content & Filtering
                item { TvListHeader("Content & Filtering") }
                item {
                    TvListRow(
                        title = "Hide Explicit Content",
                        value = onOff(settingsState.hideExplicitContent),
                        leadingIcon = Icons.Rounded.Security,
                        onFocused = { blurb = "Filters songs containing explicit language and tags from searches and shelves." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_HIDE_EXPLICIT, !settingsState.hideExplicitContent)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Content Language",
                        value = settingsState.contentLanguage,
                        onFocused = { blurb = "Preferred language for music discovery, playlists and recommendations." },
                        onClick = { /* language toggle / cycle */ },
                    )
                }
                item {
                    TvListRow(
                        title = "Content Country",
                        value = settingsState.contentCountry,
                        onFocused = { blurb = "Country chart region for trending tracks and charts." },
                        onClick = { /* country toggle / cycle */ },
                    )
                }

                // 8. iShare (Listen Together)
                item { TvListHeader("iShare (Listen Together)") }
                item {
                    TvListRow(
                        title = if (ishareSession != null) "Leave iShare Session" else "Host Local Wi-Fi Session",
                        value = if (ishareSession != null) "Connected: ${ishareSession?.code}" else "Start Host",
                        leadingIcon = Icons.Rounded.Wifi,
                        onFocused = { blurb = "Synchronizes music playback across TVs, phones and tablets on your local Wi-Fi network." },
                        onClick = {
                            if (ishareSession != null) {
                                IShareSyncManager.leaveSession()
                                Toast.makeText(context, context.getString(R.string.toast_left_ishare_session), Toast.LENGTH_SHORT).show()
                            } else {
                                IShareSyncManager.start(context)
                                IShareSyncManager.startLanHost()
                                Toast.makeText(context, context.getString(R.string.toast_ishare_lan_host_started), Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Local TV IP",
                        value = localIp ?: "Wi-Fi Not Connected",
                        onFocused = { blurb = "IP address used by other devices to connect to this TV." },
                        onClick = { IShareSyncManager.refreshLocalIp() },
                    )
                }

                // 9. Backup & Restore (Password Encrypted)
                item { TvListHeader("Backup & Restore (Password Encrypted)") }
                item {
                    TvListRow(
                        title = "Back Up iTune (Encrypted)",
                        value = "Save",
                        leadingIcon = Icons.Rounded.CloudUpload,
                        onFocused = { blurb = "Exports your complete library, playlists, settings and audio configuration encrypted with AES-256." },
                        onClick = { showBackupDialog = true },
                    )
                }
                item {
                    TvListRow(
                        title = "Restore from Encrypted Backup",
                        value = "Restore",
                        leadingIcon = Icons.Rounded.CloudDownload,
                        onFocused = { blurb = "Restores your library and settings from a password-protected .itune backup file." },
                        onClick = { showRestoreDialog = true },
                    )
                }

                // 10. General & Maintenance
                item { TvListHeader("General") }
                item {
                    TvListRow(
                        title = "Sleep Timer",
                        value = settingsState.sleepTimer,
                        leadingIcon = Icons.Rounded.Timer,
                        onFocused = { blurb = "Automatically pauses music playback after the selected timer runs out." },
                        onClick = {
                            val timers = listOf("Off", "15 min", "30 min", "45 min", "60 min", "End of track")
                            val curIdx = timers.indexOf(settingsState.sleepTimer).coerceAtLeast(0)
                            val next = timers[(curIdx + 1) % timers.size]
                            SettingsStore.setString(context, SettingsStore.KEY_SLEEP_TIMER, next)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Clear Cache",
                        value = settingsState.cachedSizeLabel,
                        leadingIcon = Icons.Rounded.Delete,
                        onFocused = { blurb = "Frees up TV internal storage by clearing cached song audio and album artwork." },
                        onClick = {
                            runCatching {
                                Coil.imageLoader(context).diskCache?.clear()
                                Coil.imageLoader(context).memoryCache?.clear()
                            }
                            SettingsStore.setString(context, SettingsStore.KEY_CACHE_LABEL, "0 MB")
                            Toast.makeText(context, context.getString(R.string.toast_cache_cleared), Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                if (ytAccountState.isSignedIn) {
                    item {
                        TvListRow(
                            title = "Sign Out",
                            value = ytAccountState.accountName ?: "Google",
                            onFocused = { blurb = "Disconnects your Google / YouTube Music account from this TV." },
                            onClick = {
                                GoogleAuthStore.signOut(context)
                                Toast.makeText(context, context.getString(R.string.toast_signed_out_successfully), Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
                item {
                    TvListRow(
                        title = "About iTune",
                        value = "v${BuildConfig.VERSION_NAME}",
                        leadingIcon = Icons.Rounded.Info,
                        onFocused = { blurb = "iTune for Android TV — Engineered by DxV Studio & Dark. Licensed under GPL-3.0." },
                        onClick = {
                            Toast.makeText(context, context.getString(R.string.toast_itune_v_for_android_tv, BuildConfig.VERSION_NAME), Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }

        // TV Lyrics Providers modal
        if (showLyricsProvidersDialog) {
            TvLyricsProvidersModal(
                onDismiss = { showLyricsProvidersDialog = false },
            )
        }

        // TV Password Backup modal
        if (showBackupDialog) {
            TvPasswordBackupModal(
                onDismiss = { showBackupDialog = false },
            )
        }

        // TV Password Restore modal
        if (showRestoreDialog) {
            TvPasswordRestoreModal(
                onDismiss = { showRestoreDialog = false },
            )
        }

        // TV Spotify Account modal
        if (showSpotifyDialog) {
            TvSpotifyAccountModal(
                onLaunchWebLogin = {
                    showSpotifyDialog = false
                    showSpotifyWebLogin = true
                },
                onDismiss = { showSpotifyDialog = false },
            )
        }

        // TV Spotify Web Login overlay
        if (showSpotifyWebLogin) {
            TvSpotifyLoginScreen(
                onSignedIn = { showSpotifyWebLogin = false },
                onClose = { showSpotifyWebLogin = false },
            )
        }

        // TV Google Login overlay when requested
        loginMode?.let { mode ->
            GoogleLoginScreen(
                mode = mode,
                onSignedIn = { loginMode = null },
                onClose = { loginMode = null },
            )
        }
    }
}

// ── Components: TvAppMark, TvListRow, TvListHeader ─────────────────────────────────────────────

@Composable
fun TvAppMark(size: Dp = 92.dp, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFFA2446), Color(0xFFC70024)),
                    )
                )
                .shadow(20.dp, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_itune_waves),
                contentDescription = stringResource(R.string.ui_itune_waves),
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(size * 0.52f),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.app_name),
            color = Color.White,
            fontFamily = SFProDisplay,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
        )
    }
}

@Composable
fun TvListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    onFocused: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    if (onFocused != null) {
        LaunchedEffect(isFocused) {
            if (isFocused) onFocused()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .tvFocusable(
                shape = RoundedCornerShape(10.dp),
                focusedBorderWidth = 0.dp,
                scaleOnFocus = 1.02f,
                interactionSource = interaction,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isFocused) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column {
                Text(
                    text = title,
                    color = if (isFocused) Color.Black else Color.White,
                    fontSize = 14.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = if (isFocused) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.55f),
                        fontSize = 11.5.sp,
                        fontFamily = SFProDisplay,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                color = if (isFocused) Color.Black.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.60f),
                fontSize = 13.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun TvListHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 11.5.sp,
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 14.dp, top = 16.dp, bottom = 4.dp),
    )
}

private fun onOff(value: Boolean): String = if (value) "On" else "Off"

// ── Modals: Lyrics Providers, Password Backup & Restore ────────────────────────────────────────

@Composable
private fun TvLyricsProvidersModal(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val settings by SettingsStore.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF01C1C1E))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                .padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.ui_lyrics_providers),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.close),
                    color = Color(0xFF007AFF),
                    fontSize = 15.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(LyricsHelper.PROVIDER_METADATA) { provider ->
                    val isEnabled = !settings.disabledLyricsProviders.contains(provider.id)
                    val interaction = remember { MutableInteractionSource() }
                    val focused by interaction.collectIsFocusedAsState()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusable(
                                shape = RoundedCornerShape(12.dp),
                                scaleOnFocus = 1.02f,
                                elevationOnFocus = 0.dp,
                                interactionSource = interaction,
                                onClick = {
                                    SettingsStore.setLyricsProviderEnabled(context, provider.id, !isEnabled)
                                },
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val c = if (focused) Color.Black else Color.White
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = provider.displayName,
                                color = c,
                                fontSize = 14.5.sp,
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = provider.description,
                                color = c.copy(alpha = 0.60f),
                                fontSize = 11.5.sp,
                                fontFamily = SFProDisplay,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isEnabled) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.ui_active),
                                tint = if (focused) Color.Black else Color(0xFF30D158),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvPasswordBackupModal(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF01C1C1E))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.ui_back_up_itune_aes_256),
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ui_your_library_playlists_and_settings_will_be_encrypted),
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 12.5.sp,
                fontFamily = SFProDisplay,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Password Field
            BasicTextField(
                value = password,
                onValueChange = { password = it },
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = SFProDisplay),
                cursorBrush = SolidColor(Color.White),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .focusRequester(focusRequester)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (password.isEmpty()) {
                        Text(stringResource(R.string.ui_encryption_password_min_8_chars), color = Color.White.copy(alpha = 0.38f), fontSize = 14.sp)
                    }
                    inner()
                },
            )

            Spacer(Modifier.height(10.dp))

            // Confirm Password Field
            BasicTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = SFProDisplay),
                cursorBrush = SolidColor(Color.White),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (confirmPassword.isEmpty()) {
                        Text(stringResource(R.string.ui_confirm_password), color = Color.White.copy(alpha = 0.38f), fontSize = 14.sp)
                    }
                    inner()
                },
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TvModalActionButton(
                    label = "Cancel",
                    isPrimary = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )

                val valid = password.length >= 8 && password == confirmPassword
                TvModalActionButton(
                    label = "Save Backup",
                    isPrimary = true,
                    onClick = {
                        if (valid) {
                            try {
                                val bytes = BackupRestoreManager.createEncryptedBackup(context, setOf("all"), password.toCharArray())
                                val target = File(context.filesDir, "itune_tv_backup.itune")
                                target.writeBytes(bytes)
                                runCatching {
                                    val ext = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "itune_tv_backup.itune")
                                    ext.writeBytes(bytes)
                                }
                                Toast.makeText(context, context.getString(R.string.tv_encrypted_backup_saved, bytes.size / 1024), Toast.LENGTH_LONG).show()
                                onDismiss()
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.toast_backup_failed, e.message), Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, context.getString(R.string.toast_passwords_must_match_and_have_at_least_8_character), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TvPasswordRestoreModal(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF01C1C1E))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.ui_restore_itune_backup),
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ui_enter_the_password_used_when_your_itune_backup_was_cre),
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 12.5.sp,
                fontFamily = SFProDisplay,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            BasicTextField(
                value = password,
                onValueChange = { password = it },
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = SFProDisplay),
                cursorBrush = SolidColor(Color.White),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .focusRequester(focusRequester)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (password.isEmpty()) {
                        Text(stringResource(R.string.ui_backup_password), color = Color.White.copy(alpha = 0.38f), fontSize = 14.sp)
                    }
                    inner()
                },
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TvModalActionButton(
                    label = "Cancel",
                    isPrimary = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )

                TvModalActionButton(
                    label = "Restore",
                    isPrimary = true,
                    onClick = {
                        if (password.isNotBlank()) {
                            try {
                                val target = File(context.filesDir, "itune_tv_backup.itune").takeIf { it.exists() }
                                    ?: File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "itune_tv_backup.itune").takeIf { it.exists() }

                                if (target == null || !target.exists()) {
                                    Toast.makeText(context, context.getString(R.string.toast_no_backup_file_found_in_storage), Toast.LENGTH_LONG).show()
                                } else {
                                    val bytes = target.readBytes()
                                    val scanned = BackupRestoreManager.scanEncryptedBackup(bytes, password.toCharArray())
                                    BackupRestoreManager.restore(context, scanned, setOf("all"))
                                    Toast.makeText(context, context.getString(R.string.toast_library_settings_restored), Toast.LENGTH_LONG).show()
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.toast_restore_failed, e.message), Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, context.getString(R.string.toast_enter_your_backup_password), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TvModalActionButton(
    label: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val bg = when {
        focused -> Color.White
        isPrimary -> Color(0xFF007AFF)
        else -> Color.White.copy(alpha = 0.12f)
    }
    val textC = if (focused) Color.Black else Color.White

    Box(
        modifier = modifier
            .height(44.dp)
            .tvFocusable(
                shape = RoundedCornerShape(12.dp),
                scaleOnFocus = 1.04f,
                elevationOnFocus = 4.dp,
                interactionSource = interaction,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textC,
            fontSize = 15.sp,
            fontFamily = SFProDisplay,
            fontWeight = if (focused || isPrimary) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

// ── TV Spotify Account Modal & In-App Web Login ───────────────────────────────────────────────

@Composable
private fun TvSpotifyAccountModal(
    onLaunchWebLogin: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val isConnected = settings.spotifySpdcToken.isNotBlank()
    var rawSpDcInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF01C1C1E))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                .padding(26.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) Color(0xFF1DB954) else Color(0xFFFF453A)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.ui_spotify_account),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringResource(R.string.close),
                    color = Color(0xFF007AFF),
                    fontSize = 15.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }

            if (isConnected) {
                Text(
                    text = stringResource(R.string.ui_spotify_is_currently_connected_motion_artwork_canvas_a),
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 13.5.sp,
                    fontFamily = SFProDisplay,
                    lineHeight = 18.sp,
                )

                Spacer(Modifier.height(20.dp))

                TvModalActionButton(
                    label = "Sync Spotify Library Now",
                    isPrimary = true,
                    onClick = {
                        SpotifyLibrarySyncManager.launchSync(context, force = true)
                        Toast.makeText(context, context.getString(R.string.toast_syncing_spotify_library), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))

                TvModalActionButton(
                    label = "Disconnect Spotify Account",
                    isPrimary = false,
                    onClick = {
                        SettingsStore.setSpotifySpdcToken(context, "")
                        Toast.makeText(context, context.getString(R.string.toast_spotify_disconnected), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = stringResource(R.string.ui_connect_your_spotify_account_to_enable_canvas_looping),
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 13.5.sp,
                    fontFamily = SFProDisplay,
                    lineHeight = 18.sp,
                )

                Spacer(Modifier.height(18.dp))

                TvModalActionButton(
                    label = "Sign In with Spotify (Web)",
                    isPrimary = true,
                    onClick = onLaunchWebLogin,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.ui_or_enter_sp_dc_cookie),
                    color = Color.White.copy(alpha = 0.40f),
                    fontSize = 11.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )

                Spacer(Modifier.height(8.dp))

                BasicTextField(
                    value = rawSpDcInput,
                    onValueChange = { rawSpDcInput = it },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontFamily = SFProDisplay,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(Color(0xFF1DB954)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    decorationBox = { innerTextField ->
                        if (rawSpDcInput.isEmpty()) {
                            Text(
                                text = stringResource(R.string.ui_paste_sp_dc_token_here),
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 13.5.sp,
                                fontFamily = SFProDisplay,
                            )
                        }
                        innerTextField()
                    },
                )

                Spacer(Modifier.height(14.dp))

                TvModalActionButton(
                    label = "Save & Connect SP_DC",
                    isPrimary = false,
                    onClick = {
                        val clean = rawSpDcInput.trim().substringAfter("sp_dc=").substringBefore(";").trim()
                        if (clean.isNotBlank()) {
                            SettingsStore.setSpotifySpdcToken(context, clean)
                            Toast.makeText(context, context.getString(R.string.toast_spotify_connected_f5a7d96a), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, context.getString(R.string.toast_please_enter_a_valid_sp_dc_cookie), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TvSpotifyLoginScreen(
    onSignedIn: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var isFinished by remember { mutableStateOf(false) }

    fun saveSpDcAndFinish(rawCookie: String) {
        if (isFinished) return
        val spDc = if (rawCookie.contains("sp_dc=")) {
            rawCookie.substringAfter("sp_dc=").substringBefore(";").trim()
        } else {
            rawCookie.trim()
        }
        if (spDc.isNotBlank()) {
            isFinished = true
            SettingsStore.setSpotifySpdcToken(context, spDc)
            Toast.makeText(context, context.getString(R.string.toast_spotify_connected_f5a7d96a), Toast.LENGTH_SHORT).show()
            onSignedIn()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    val defaultUserAgent = settings.userAgentString
                    settings.userAgentString = defaultUserAgent.replace("; wv", "")

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    fun inspect(url: String?) {
                        if (url == null || isFinished) return
                        val cookies = cookieManager.getCookie(url)
                            ?: cookieManager.getCookie("https://open.spotify.com")
                            ?: cookieManager.getCookie("https://accounts.spotify.com")
                            ?: ""
                        if (cookies.contains("sp_dc=")) {
                            saveSpDcAndFinish(cookies)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            inspect(url)
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            inspect(url)
                        }
                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            inspect(url)
                        }
                    }
                    loadUrl("https://accounts.spotify.com/login")
                }
            },
        )

        // Close button at top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.70f))
                .clickable(onClick = onClose)
                .padding(12.dp),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
        }
    }
}
