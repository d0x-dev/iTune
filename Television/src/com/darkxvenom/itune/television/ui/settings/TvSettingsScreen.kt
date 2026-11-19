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

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tv
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.Coil
import com.darkxvenom.itune.BuildConfig
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.auth.WebSessionMode
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.television.focus.SFProDisplay
import com.darkxvenom.itune.television.focus.tvFocusable
import com.darkxvenom.itune.television.ui.common.TvAmbientBackground
import com.darkxvenom.itune.ui.auth.GoogleLoginScreen
import com.darkxvenom.itune.ui.theme.AppleOledBlack

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

private const val DEFAULT_BLURB = "Playback, sound and display preferences for iTune on this TV."

/**
 * Settings Screen matching BitChord & Apple TV (tvOS) layout:
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

    Box(modifier = modifier.fillMaxSize().background(AppleOledBlack)) {
        TvAmbientBackground(Modifier.fillMaxSize(), dim = 0.35f)

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
                        onFocused = { blurb = "Multiple lyric sources: LRCLIB, Kugou, Paxsenix, BetterLyrics in priority order." },
                        onClick = {
                            Toast.makeText(context, "All ${settingsState.lyricsProviderOrder.size} providers active", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "AI Lyrics Translation",
                        value = onOff(settingsState.lyricsTranslationEnabled),
                        onFocused = { blurb = "Real-time AI machine translation for international and multilingual song lyrics." },
                        onClick = {
                            SettingsStore.setLyricsTranslationEnabled(context, !settingsState.lyricsTranslationEnabled)
                        },
                    )
                }

                // 5. Display & Personalization
                item { TvListHeader("Display & Visuals") }
                item {
                    TvListRow(
                        title = "Player Style",
                        value = settingsState.playerType,
                        leadingIcon = Icons.Rounded.Tv,
                        onFocused = { blurb = "Choose between authentic Apple Music TV layout, Dynamic Liquid Glass, or Minimal." },
                        onClick = {
                            val styles = listOf("Apple TV", "Dynamic", "Minimal")
                            val curIdx = styles.indexOf(settingsState.playerType).coerceAtLeast(0)
                            val next = styles[(curIdx + 1) % styles.size]
                            SettingsStore.setPlayerType(context, next)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Liquid Glass Chrome",
                        value = onOff(settingsState.liquidGlassChrome),
                        onFocused = { blurb = "Applies real-time blurred frosted glass platters to buttons and cards." },
                        onClick = {
                            SettingsStore.setBoolean(context, SettingsStore.KEY_LIQUID_GLASS, !settingsState.liquidGlassChrome)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Home Hero Cards",
                        value = settingsState.topCardType,
                        onFocused = { blurb = "Layout style of the top recommendations shelf on the Home screen." },
                        onClick = {
                            val cardTypes = listOf("Portrait", "Square", "Landscape")
                            val curIdx = cardTypes.indexOf(settingsState.topCardType).coerceAtLeast(0)
                            val next = cardTypes[(curIdx + 1) % cardTypes.size]
                            SettingsStore.setString(context, SettingsStore.KEY_TOP_CARD_TYPE, next)
                        },
                    )
                }
                item {
                    TvListRow(
                        title = "Splash Screen",
                        value = onOff(settingsState.splashScreenEnabled),
                        onFocused = { blurb = "Shows the branded splash animation on cold start." },
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

                // 7. General & Maintenance
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
                            Toast.makeText(context, "Cache Cleared", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(context, "iTune v${BuildConfig.VERSION_NAME} for Android TV", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
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
                contentDescription = "iTune Waves",
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(size * 0.52f),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "iTune",
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
