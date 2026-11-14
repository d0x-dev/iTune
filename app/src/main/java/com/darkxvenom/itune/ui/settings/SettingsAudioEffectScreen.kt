package com.darkxvenom.itune.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.darkxvenom.itune.R
import com.darkxvenom.itune.audio.AudioEngineManager
import com.darkxvenom.itune.audio.AudioOutputManager
import com.darkxvenom.itune.audio.DjPreset
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.ui.artist.components.BottomFadeScrim
import com.darkxvenom.itune.ui.artist.components.FrostedTopBar
import com.darkxvenom.itune.ui.artist.components.FrostedTopBarTitlePill
import com.darkxvenom.itune.ui.artist.components.LocalAppBackdrop
import com.darkxvenom.itune.ui.artist.components.LocalLiquidGlassEnabled
import com.darkxvenom.itune.ui.artist.components.TopFadeBlur
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.rememberLayerBackdrop
import com.darkxvenom.itune.ui.artist.components.isGlassSupported
import com.darkxvenom.itune.ui.artist.components.topBarContentPadding
import com.darkxvenom.itune.ui.artist.haptics.Haptic
import com.darkxvenom.itune.ui.artist.haptics.rememberHaptics
import com.darkxvenom.itune.ui.artist.theme.ITubeArtistTheme
import com.darkxvenom.itune.ui.components.rememberSpringOverscrollState
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.player.AudioEngineSubpageContent
import com.darkxvenom.itune.ui.player.EnginePopupPage
import com.darkxvenom.itune.ui.player.LocalEngineCardSurface
import com.darkxvenom.itune.ui.player.LocalEngineElevatedSurface
import com.darkxvenom.itune.ui.player.audioEnginePageSubtitle
import com.darkxvenom.itune.ui.player.audioEnginePageTitle
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private val SettingsEffectCardGray = Color(0xFF1C1C1E)
private val SettingsEffectElevatedGray = Color(0xFF28282C)

private data class SettingsEffectTabSpec(
    val page: EnginePopupPage,
    val label: String,
)

private val SettingsAudioEffectTabs = listOf(
    SettingsEffectTabSpec(EnginePopupPage.DOLBY_ATMOS, "Dolby Atmos"),
    SettingsEffectTabSpec(EnginePopupPage.EIGHT_D, "8D Audio"),
    SettingsEffectTabSpec(EnginePopupPage.SPATIAL, "Spatial"),
    SettingsEffectTabSpec(EnginePopupPage.EQUALIZER, "Equalizer"),
    SettingsEffectTabSpec(EnginePopupPage.DJ_CONSOLE, "DJ Studio"),
    SettingsEffectTabSpec(EnginePopupPage.NORMALIZATION, "Normalization"),
    SettingsEffectTabSpec(EnginePopupPage.BIT_PERFECT, "Bit-Perfect"),
    SettingsEffectTabSpec(EnginePopupPage.PIPELINE, "Pipeline"),
)

/**
 * Full-screen Apple Settings sub-screen hosting every editable audio effect page from the player
 * Audio Engine popup (`Dolby Atmos`, `8D Audio`, `Spatial`, `Equalizer`, `DJ Studio`, `Normalization`,
 * `USB Bit-Perfect DAC`, `Signal Pipeline`) styled with pure `#000000` OLED background and `#1C1C1E` cards.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SettingsAudioEffectScreen(
    initialPage: EnginePopupPage,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val appBackdrop = rememberLayerBackdrop()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val overscrollState = rememberSpringOverscrollState()

    var activePage by remember(initialPage) { mutableStateOf(initialPage) }

    val state by AudioEngineManager.state.collectAsStateWithLifecycle()
    val outputState by AudioOutputManager.state.collectAsStateWithLifecycle()
    val vizStats by AudioEngineManager.visualizerManager.stats.collectAsStateWithLifecycle()
    val currentTrack = MusicPlayerState.currentTrack

    BackHandler {
        onBack()
    }

    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 72
        }
    }

    val pageTitle = audioEnginePageTitle(activePage)
    val pageSubtitle = audioEnginePageSubtitle(
        page = activePage,
        state = state,
        outputName = outputState.activeDevice?.name ?: "Phone Speaker",
        bitPerfectActive = outputState.bitPerfectActive,
        trackTitle = currentTrack?.title,
    )

    ITubeArtistTheme {
        CompositionLocalProvider(
            LocalAppBackdrop provides appBackdrop,
            LocalLiquidGlassEnabled provides isGlassSupported(),
            LocalEngineCardSurface provides SettingsEffectCardGray,
            LocalEngineElevatedSurface provides SettingsEffectElevatedGray,
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(AppleOledBlack),
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = topBarContentPadding() + 6.dp,
                        bottom = if (hasMiniPlayer) 210.dp else 140.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .springOverscroll(overscrollState)
                        .hazeSource(state = hazeState),
                ) {
                    item(key = "effect_header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp),
                        ) {
                            Text(
                                text = pageTitle,
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.6).sp,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = pageSubtitle,
                                color = AppleTextSecondary,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    item(key = "effect_switcher_tabs") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SettingsAudioEffectTabs.forEach { tab ->
                                val selected = tab.page == activePage
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (selected) Color(0xFF343438) else SettingsEffectCardGray)
                                        .border(
                                            width = 0.6.dp,
                                            color = if (selected) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.12f),
                                            shape = CircleShape,
                                        )
                                        .clickable {
                                            haptics.play(Haptic.Tap)
                                            activePage = tab.page
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = tab.label,
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }

                    item(key = "effect_editable_body") {
                        AnimatedContent(
                            targetState = activePage,
                            transitionSpec = {
                                (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 8 }) togetherWith
                                    (fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -it / 8 })
                            },
                            label = "settingsAudioEffectBody",
                        ) { page ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                            ) {
                                AudioEngineSubpageContent(
                                    page = page,
                                    track = currentTrack,
                                    state = state,
                                    outputState = outputState,
                                    vizStats = vizStats,
                                    onOpenSystemDolby = {
                                        AudioEngineManager.openSystemDolbyOrAudioEffects(context)
                                    },
                                )
                            }
                        }
                    }
                }

                TopFadeBlur(
                    hazeState = hazeState,
                    pageColor = AppleOledBlack,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                FrostedTopBar(
                    transparentBackdrop = true,
                    artworkPageChrome = true,
                    backButtonHazeState = hazeState,
                    onBack = {
                        haptics.play(Haptic.Tap)
                        onBack()
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                    centerContent = {
                        AnimatedVisibility(visible = isScrolled) {
                            FrostedTopBarTitlePill(hazeState = hazeState) {
                                Text(
                                    text = pageTitle,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = {
                        if (activePage == EnginePopupPage.EQUALIZER || activePage == EnginePopupPage.DJ_CONSOLE) {
                            IconButton(
                                onClick = {
                                    haptics.play(Haptic.Tap)
                                    if (activePage == EnginePopupPage.EQUALIZER) {
                                        AudioEngineManager.resetEqualizer()
                                        Toast.makeText(context, "Equalizer reset to Flat", Toast.LENGTH_SHORT).show()
                                    } else {
                                        AudioEngineManager.applyDjPreset(DjPreset.DEFAULT)
                                        Toast.makeText(context, "DJ Console reset", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_di_reset),
                                    contentDescription = "Reset $pageTitle",
                                    colorFilter = ColorFilter.tint(Color.White),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                )

                BottomFadeScrim(
                    withMiniPlayer = hasMiniPlayer,
                    pageColor = AppleOledBlack,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
