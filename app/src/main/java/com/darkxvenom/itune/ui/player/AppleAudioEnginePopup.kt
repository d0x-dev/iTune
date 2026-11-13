package com.darkxvenom.itune.ui.player

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.darkxvenom.itune.R
import com.darkxvenom.itune.audio.AirBeatsEqPresets
import com.darkxvenom.itune.audio.AudioEngineManager
import com.darkxvenom.itune.audio.AudioEngineUiState
import com.darkxvenom.itune.audio.AudioOutputManager
import com.darkxvenom.itune.audio.AudioOutputUiState
import com.darkxvenom.itune.audio.AudioVisualizerStats
import com.darkxvenom.itune.audio.DjPreset
import com.darkxvenom.itune.audio.NormalizationProfile
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.ui.settings.AppleGlassSwitch
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlin.math.abs
import kotlin.math.roundToInt

internal val LocalEngineCardSurface = compositionLocalOf { Color(0xFF0E0E10) }
internal val LocalEngineElevatedSurface = compositionLocalOf { Color(0xFF1C1C1E) }

private val EngineCardBlack: Color
    @Composable get() = LocalEngineCardSurface.current
private val EngineElevatedBlack: Color
    @Composable get() = LocalEngineElevatedSurface.current
private val EngineActivePillBlack = Color(0xFF343438)
private val EngineActiveGreen = Color(0xFF30D158)

enum class EnginePopupPage {
    MAIN,
    DOLBY_ATMOS,
    EIGHT_D,
    SPATIAL,
    EQUALIZER,
    DJ_CONSOLE,
    NORMALIZATION,
    BIT_PERFECT,
    PIPELINE,
}

internal fun audioEnginePageTitle(page: EnginePopupPage): String = when (page) {
    EnginePopupPage.MAIN -> "Audio Engine"
    EnginePopupPage.DOLBY_ATMOS -> "Dolby Atmos"
    EnginePopupPage.EIGHT_D -> "8D Spatial Orbit"
    EnginePopupPage.SPATIAL -> "Spatial Audio"
    EnginePopupPage.EQUALIZER -> "Equalizer"
    EnginePopupPage.DJ_CONSOLE -> "DJ Studio Console"
    EnginePopupPage.NORMALIZATION -> "Audio Normalization"
    EnginePopupPage.BIT_PERFECT -> "USB Bit-Perfect DAC"
    EnginePopupPage.PIPELINE -> "Signal Pipeline"
}

internal fun audioEnginePageSubtitle(
    page: EnginePopupPage,
    state: AudioEngineUiState,
    outputName: String,
    bitPerfectActive: Boolean,
    trackTitle: String?,
): String = when (page) {
    EnginePopupPage.MAIN -> if (state.bypassAllEffects) {
        "Pure Audio Bypass Active"
    } else {
        "${state.pillBadge.text} • $outputName"
    }
    EnginePopupPage.DOLBY_ATMOS -> if (state.dolbyAtmosEnabled) {
        if (state.dolbyWidthWide) "Wide Virtual Atmos • Active" else "Balanced Atmos • Active"
    } else {
        "Disabled"
    }
    EnginePopupPage.EIGHT_D -> if (state.eightDAudioEnabled) "${state.eightDAudioLevel}D 360° Orbit Active" else "Disabled"
    EnginePopupPage.SPATIAL -> if (state.spatialAudioEnabled) "${"%.1f".format(state.spatialWidth)}x Soundstage" else "Disabled"
    EnginePopupPage.EQUALIZER -> if (state.eqEnabled) "Preset: ${state.eqPreset}" else "Disabled (Flat)"
    EnginePopupPage.DJ_CONSOLE -> if (state.djEnabled) "Live PCM DSP Studio Active" else "Turned Off"
    EnginePopupPage.NORMALIZATION -> if (state.normalizationEnabled) {
        "${state.normalizationProfile.label} (${state.normalizationProfile.targetLufs.toInt()} LUFS)"
    } else {
        "Disabled"
    }
    EnginePopupPage.BIT_PERFECT -> if (bitPerfectActive) "Bit-Exact Hardware Path" else "API 34+ USB DAC"
    EnginePopupPage.PIPELINE -> "${trackTitle ?: "Ready"} -> $outputName"
}

/**
 * Apple liquid-glass water-drop popup opened from the Full Player center quality/engine pill (`iMusic • 128k` / `Dolby Atmos` / `8D Audio`).
 * Main screen exposes clean On/Off cards for every AirBeats audio effect + Equalizer + DJ Panel + Bit-Perfect + Normalization.
 * Tapping any toggle updates DSP immediately; tapping a title card opens that effect's dedicated options view.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AppleAudioEnginePopup(
    visible: Boolean,
    track: MusicTrack? = null,
    artUrl: String?,
    anchorCenterXPx: Float,
    anchorCenterYPx: Float,
    statusBarTopDp: Dp,
    navBottomDp: Dp,
    maxHeightDp: Dp,
    rootCoordsProvider: () -> LayoutCoordinates?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current

    val state by AudioEngineManager.state.collectAsStateWithLifecycle()
    val outputState by AudioOutputManager.state.collectAsStateWithLifecycle()
    val vizStats by AudioEngineManager.visualizerManager.stats.collectAsStateWithLifecycle()

    var activePage by remember(visible) { androidx.compose.runtime.mutableStateOf(EnginePopupPage.MAIN) }

    BackHandler(enabled = visible) {
        if (activePage != EnginePopupPage.MAIN) {
            activePage = EnginePopupPage.MAIN
        } else {
            onDismiss()
        }
    }

    val headerTitle = audioEnginePageTitle(activePage)
    val headerSubtitle = audioEnginePageSubtitle(
        page = activePage,
        state = state,
        outputName = outputState.activeDevice?.name ?: "Phone Speaker",
        bitPerfectActive = outputState.bitPerfectActive,
        trackTitle = track?.title,
    )

    AppleFrostedPopupOverlay(
        visible = visible,
        title = headerTitle,
        subtitle = headerSubtitle,
        artUrl = artUrl,
        anchorCenterXPx = anchorCenterXPx,
        anchorCenterYPx = anchorCenterYPx,
        maxHeightDp = maxHeightDp,
        statusBarTopDp = statusBarTopDp,
        rootCoordsProvider = rootCoordsProvider,
        onDismiss = onDismiss,
        onBack = {
            if (activePage != EnginePopupPage.MAIN) {
                activePage = EnginePopupPage.MAIN
            } else {
                onDismiss()
            }
        },
        isTopAnchored = false,
        bottomClearanceDp = (88.dp + navBottomDp).coerceIn(76.dp, maxHeightDp * 0.45f),
        hasInputFocusPage = false,
        modifier = modifier,
        headerActions = {
            if (activePage != EnginePopupPage.MAIN) {
                EngineHeaderIconButton(
                    contentDescription = "Back to Audio Engine",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        activePage = EnginePopupPage.MAIN
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (activePage == EnginePopupPage.EQUALIZER || activePage == EnginePopupPage.DJ_CONSOLE) {
                EngineHeaderIconButton(
                    contentDescription = "Reset",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (activePage == EnginePopupPage.EQUALIZER) {
                            AudioEngineManager.resetEqualizer()
                        } else {
                            AudioEngineManager.applyDjPreset(DjPreset.DEFAULT)
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Reset",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        },
    ) {
        AnimatedContent(
            targetState = activePage,
            transitionSpec = {
                if (targetState == EnginePopupPage.MAIN) {
                    (slideInHorizontally(tween(240)) { -it / 3 } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(150)))
                } else {
                    (slideInHorizontally(tween(240)) { it / 3 } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(tween(240)) { -it / 3 } + fadeOut(tween(150)))
                }
            },
            label = "audioEngineSubpage",
        ) { page ->
            when (page) {
                EnginePopupPage.MAIN -> MainAudioEngineList(
                    state = state,
                    bitPerfectActive = outputState.bitPerfectActive,
                    activeOutputName = outputState.activeDevice?.name ?: "Phone Speaker",
                    onOpenPage = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        activePage = it
                    },
                )
                else -> AudioEngineSubpageContent(
                    page = page,
                    track = track,
                    state = state,
                    outputState = outputState,
                    vizStats = vizStats,
                    onOpenSystemDolby = { AudioEngineManager.openSystemDolbyOrAudioEffects(context) },
                )
            }
        }
    }
}

/**
 * Reusable editable detail content shared between [AppleAudioEnginePopup] and Settings Audio Effect sub-screens.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun AudioEngineSubpageContent(
    page: EnginePopupPage,
    track: MusicTrack?,
    state: AudioEngineUiState,
    outputState: AudioOutputUiState,
    vizStats: AudioVisualizerStats,
    onOpenSystemDolby: () -> Boolean,
) {
    when (page) {
        EnginePopupPage.DOLBY_ATMOS -> DolbyAtmosOptionsPage(
            state = state,
            onOpenSystemDolby = onOpenSystemDolby,
        )
        EnginePopupPage.EIGHT_D -> EightDOptionsPage(state = state)
        EnginePopupPage.SPATIAL -> SpatialAudioOptionsPage(state = state)
        EnginePopupPage.EQUALIZER -> EqualizerOptionsPage(state = state)
        EnginePopupPage.DJ_CONSOLE -> DjConsoleOptionsPage(state = state, vizStats = vizStats)
        EnginePopupPage.NORMALIZATION -> NormalizationOptionsPage(state = state)
        EnginePopupPage.BIT_PERFECT -> BitPerfectOptionsPage(
            state = state,
            bitPerfectActive = outputState.bitPerfectActive,
            usbPresent = outputState.usbDevicePresent,
        )
        EnginePopupPage.PIPELINE, EnginePopupPage.MAIN -> SignalPipelinePage(
            track = track,
            state = state,
            deviceName = outputState.activeDevice?.name ?: "Phone Speaker",
            sampleRate = outputState.activeDevice?.sampleRateLabel ?: "48 kHz",
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MainAudioEngineList(
    state: AudioEngineUiState,
    bitPerfectActive: Boolean,
    activeOutputName: String,
    onOpenPage: (EnginePopupPage) -> Unit,
) {
    val view = LocalView.current
    val effectsDisabled = state.bypassAllEffects || (state.bitPerfectEnabled && bitPerfectActive)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EngineSectionLabel("SOUND EFFECTS & EQUALIZER", "Tap title for options")

        EngineToggleCardRow(
            iconRes = R.drawable.ic_dolby_atmos,
            title = "Dolby Atmos",
            subtitle = if (state.dolbyAtmosEnabled && !effectsDisabled) {
                if (state.dolbyWidthWide) "Spatial Atmos Wide Stage active" else "Atmos Surround active"
            } else {
                "Off • Tap title to configure Atmos"
            },
            checked = state.dolbyAtmosEnabled && !effectsDisabled,
            switchEnabled = !effectsDisabled,
            onCardClick = { onOpenPage(EnginePopupPage.DOLBY_ATMOS) },
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setDolbyAtmosEnabled(it)
            },
        )

        EngineToggleCardRow(
            iconRes = R.drawable.ic_fx_8d,
            title = "8D Audio",
            subtitle = if (state.eightDAudioEnabled && !effectsDisabled) {
                "${state.eightDAudioLevel}D 360° binaural orbit active"
            } else {
                "Off • Orbital ITD & head-shadow"
            },
            checked = state.eightDAudioEnabled && !effectsDisabled,
            switchEnabled = !effectsDisabled,
            onCardClick = { onOpenPage(EnginePopupPage.EIGHT_D) },
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setEightDAudioEnabled(it)
            },
        )

        EngineToggleCardRow(
            iconRes = R.drawable.ic_fx_spatial,
            title = "Spatial Audio",
            subtitle = if (state.spatialAudioEnabled && !effectsDisabled) {
                "${"%.1f".format(state.spatialWidth)}x stereo soundstage"
            } else {
                "Off • Mid/Side width + crossfeed"
            },
            checked = state.spatialAudioEnabled && !effectsDisabled,
            switchEnabled = !effectsDisabled,
            onCardClick = { onOpenPage(EnginePopupPage.SPATIAL) },
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setSpatialAudioEnabled(it)
            },
        )

        EngineToggleCardRow(
            iconRes = R.drawable.ic_settings_equalizer,
            title = "Equalizer",
            subtitle = if (state.eqEnabled && !effectsDisabled) {
                "${state.eqPreset} • Dots & Seekbar EQ"
            } else {
                "Off • 5-band Dot Curve + Sliders"
            },
            checked = state.eqEnabled && !effectsDisabled,
            switchEnabled = !effectsDisabled,
            onCardClick = { onOpenPage(EnginePopupPage.EQUALIZER) },
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setEqualizerEnabled(it)
            },
        )

        EngineToggleCardRow(
            iconRes = R.drawable.ic_fx_low_pass,
            title = "Low-Pass Filtering",
            subtitle = if (state.lowPassFilterActive && !effectsDisabled) {
                "Active on hook line (11s) • Bass filter"
            } else if (state.lowPassFilterEnabled && !effectsDisabled) {
                "On • Scanning lyrics for repeated hook line"
            } else {
                "Off • Auto-applies low-pass filter on chorus hook"
            },
            checked = state.lowPassFilterEnabled && !effectsDisabled,
            switchEnabled = !effectsDisabled,
            onCardClick = {
                val next = !state.lowPassFilterEnabled
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setLowPassFilterEnabled(next)
            },
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setLowPassFilterEnabled(it)
            },
        )

        EngineToggleCardRow(
            iconRes = R.drawable.ic_dj_console,
            title = "DJ Studio Panel",
            subtitle = if (state.djEnabled && !effectsDisabled) {
                buildString {
                    if (state.djBoostEnabled) append("${state.djBoostPercent}% Boost • ")
                    if (state.echoEnabled) append("Echo • ")
                    if (abs(state.filterSweep) > 0.02f) append("Filter • ")
                    if (state.tempoSpeed != 1f) append("${"%.2f".format(state.tempoSpeed)}x")
                    if (isEmpty()) append("Live DSP Console active")
                }
            } else {
                "Off • Echo, Filter, Flanger, Deck, Boost"
            },
            checked = state.djEnabled && !effectsDisabled,
            switchEnabled = !effectsDisabled,
            onCardClick = { onOpenPage(EnginePopupPage.DJ_CONSOLE) },
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setDjEnabled(it)
            },
        )

        Spacer(Modifier.height(2.dp))
        EngineSectionLabel("LOUDNESS & PURE OUTPUT", null)

        EngineToggleCardRow(
            iconRes = R.drawable.ic_fx_normalization,
            title = "Audio Normalization",
            subtitle = if (state.normalizationEnabled && !effectsDisabled) {
                "${state.normalizationProfile.label} (${state.normalizationProfile.targetLufs.toInt()} LUFS)"
            } else {
                "Off • Sound Check loudness target"
            },
            checked = state.normalizationEnabled && !effectsDisabled,
            switchEnabled = !effectsDisabled,
            onCardClick = { onOpenPage(EnginePopupPage.NORMALIZATION) },
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setNormalizationEnabled(it)
            },
        )

        EngineToggleCardRow(
            iconRes = R.drawable.ic_output_bit_perfect,
            title = "USB Bit-Perfect DAC",
            subtitle = if (bitPerfectActive) {
                "Hardware bit-exact lock active"
            } else if (state.bitPerfectEnabled) {
                "Armed for USB DAC • Direct PCM"
            } else {
                "Off • Bypass Android resampler on USB"
            },
            checked = state.bitPerfectEnabled,
            switchEnabled = true,
            onCardClick = { onOpenPage(EnginePopupPage.BIT_PERFECT) },
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setBitPerfectEnabled(it)
            },
        )

        EngineToggleCardRow(
            iconRes = R.drawable.ic_fx_bypass,
            title = "Pure Audio Bypass",
            subtitle = if (state.bypassAllEffects) {
                "Active • All DSP locked for untouched source"
            } else {
                "Off • Allow active DSP effects"
            },
            checked = state.bypassAllEffects,
            switchEnabled = true,
            onCardClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setBypassAllEffects(!state.bypassAllEffects)
            },
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setBypassAllEffects(it)
            },
        )

        EngineActionCardRow(
            iconRes = R.drawable.ic_output_pipeline,
            title = "Signal Pipeline",
            subtitle = "${state.pillBadge.text} -> $activeOutputName",
            onClick = { onOpenPage(EnginePopupPage.PIPELINE) },
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun DolbyAtmosOptionsPage(
    state: AudioEngineUiState,
    onOpenSystemDolby: () -> Boolean,
) {
    val context = LocalContext.current
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EngineSwitchBannerCard(
            iconRes = R.drawable.ic_dolby_atmos,
            title = "Dolby Atmos",
            subtitle = if (state.dolbyAtmosEnabled) {
                "Updates player pill & enables multi-channel spatializer"
            } else {
                "Enable for spatial Atmos stage & pill badge"
            },
            checked = state.dolbyAtmosEnabled,
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setDolbyAtmosEnabled(it)
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EngineCardBlack)
                .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EngineSectionLabel("ATMOS PROCESSING", null)
            EngineKeyValueRow("Decoder Hardware", if (state.dolbyHardwareSupported) "EAC3-JOC Supported" else "Virtual Atmos Spatializer")
            EngineKeyValueRow("Soundstage Mode", if (state.dolbyWidthWide) "Wide Concert (2.75x)" else "Studio Balanced (2.25x)")

            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EngineBlackChoiceChip(
                    label = "Balanced 2.25x",
                    selected = !state.dolbyWidthWide,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        AudioEngineManager.setDolbyWidthWide(false)
                    },
                    modifier = Modifier.weight(1f),
                )
                EngineBlackChoiceChip(
                    label = "Wide Stage 2.75x",
                    selected = state.dolbyWidthWide,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        AudioEngineManager.setDolbyWidthWide(true)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        EngineBlackPillButton(
            label = "Open System Dolby / Audio Effects Panel",
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (!onOpenSystemDolby()) {
                    android.widget.Toast.makeText(context, "System sound panel opened", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun EightDOptionsPage(state: AudioEngineUiState) {
    val view = LocalView.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EngineSwitchBannerCard(
            iconRes = R.drawable.ic_fx_8d,
            title = "8D Audio (360° Binaural)",
            subtitle = if (state.eightDAudioEnabled) "${state.eightDAudioLevel}D orbit with ITD + HRTF filter" else "Best experienced with earbuds or headphones",
            checked = state.eightDAudioEnabled,
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setEightDAudioEnabled(it)
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EngineCardBlack)
                .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Orbit Intensity & Speed",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${state.eightDAudioLevel}D",
                    color = EngineActiveGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = when {
                    state.eightDAudioLevel <= 3 -> "Gentle ambient rotation (~25s per circle)"
                    state.eightDAudioLevel <= 7 -> "Smooth mid-tempo rotation (~14s per circle)"
                    state.eightDAudioLevel == 8 -> "Classic 8D binaural orbit (~9s per circle)"
                    state.eightDAudioLevel <= 12 -> "Dynamic fast surround (~6s per circle)"
                    else -> "Maximum 360° speed & separation (~3.5s per circle)"
                },
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )

            EngineCapsuleSlider(
                fraction = (state.eightDAudioLevel - 1) / 15f,
                onFractionChange = { frac ->
                    val lvl = (1 + (frac * 15f).roundToInt()).coerceIn(1, 16)
                    if (lvl != state.eightDAudioLevel) {
                        AudioEngineManager.setEightDAudioLevel(lvl)
                    }
                },
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(1, 4, 8, 12, 16).forEach { lvl ->
                    EngineBlackChoiceChip(
                        label = "${lvl}D",
                        selected = state.eightDAudioLevel == lvl,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            AudioEngineManager.setEightDAudioLevel(lvl)
                            if (!state.eightDAudioEnabled) AudioEngineManager.setEightDAudioEnabled(true)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SpatialAudioOptionsPage(state: AudioEngineUiState) {
    val view = LocalView.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EngineSwitchBannerCard(
            iconRes = R.drawable.ic_fx_spatial,
            title = "Spatial Audio",
            subtitle = "Mid/Side stereo expansion & natural pinna crossfeed",
            checked = state.spatialAudioEnabled,
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setSpatialAudioEnabled(it)
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EngineCardBlack)
                .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Soundstage Width", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text("${"%.2f".format(state.spatialWidth)}x", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            EngineCapsuleSlider(
                fraction = (state.spatialWidth - 1.0f) / 2.0f,
                onFractionChange = { f -> AudioEngineManager.setSpatialWidth(1.0f + f * 2.0f) },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        AudioEngineManager.setSpatialCrossfeed(!state.spatialCrossfeed)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Acoustic Head-Shadow Crossfeed", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("15ms opposite-ear delayed low-pass blend", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp)
                }
                AppleGlassSwitch(
                    checked = state.spatialCrossfeed,
                    onCheckedChange = { AudioEngineManager.setSpatialCrossfeed(it) },
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun EqualizerOptionsPage(state: AudioEngineUiState) {
    val view = LocalView.current
    // View mode: 0 = Both (Dot Curve + Seekbars simultaneously), 1 = Dots Only, 2 = Sliders Only
    var viewMode by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EngineSwitchBannerCard(
            iconRes = R.drawable.ic_settings_equalizer,
            title = "Equalizer",
            subtitle = if (state.eqEnabled) {
                val hwNote = if (state.eqHardwareAttached) "Hardware DSP linked" else "Active on playback session"
                "${state.eqPreset} • $hwNote"
            } else {
                "Enable to shape 5-band frequency response"
            },
            checked = state.eqEnabled,
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setEqualizerEnabled(it)
            },
        )

        EngineToggleCardRow(
            iconRes = R.drawable.ic_fx_low_pass,
            title = "Low-Pass Filtering (Chorus Hook)",
            subtitle = if (state.lowPassFilterActive) {
                "Active now on song hook line"
            } else if (state.lowPassFilterEnabled) {
                "Auto-triggers +15dB bass / -15dB treble cut on chorus hook"
            } else {
                "Auto-applies low-pass EQ curve on highest repeated lyric"
            },
            checked = state.lowPassFilterEnabled,
            switchEnabled = true,
            onCardClick = {
                val next = !state.lowPassFilterEnabled
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setLowPassFilterEnabled(next)
            },
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setLowPassFilterEnabled(it)
            },
        )

        // Apple-styled black segmented mode switcher: Both / Dot Curve / Seekbars
        EngineBlackSegmentedBar(
            labels = listOf("Both", "Dot Curve", "Seekbars"),
            selectedIndex = viewMode,
            onSelect = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                viewMode = it
            },
        )

        // Interactive Apple Dot-Based Spline EQ Graph
        AnimatedVisibility(visible = viewMode == 0 || viewMode == 1) {
            EqualizerDotCurveCard(
                bandsMb = state.eqBandsMb,
                freqsHz = state.eqFrequenciesHz,
                minMb = state.eqMinMb,
                maxMb = state.eqMaxMb,
                enabled = state.eqEnabled,
                onBandChange = { idx, mb ->
                    AudioEngineManager.setEqualizerBandLevel(idx, mb)
                },
            )
        }

        // Apple Capsule Seekbar-Based Band Sliders
        AnimatedVisibility(visible = viewMode == 0 || viewMode == 2) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EngineCardBlack)
                    .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EngineSectionLabel("FREQUENCY BAND FADERS", "dB")
                state.eqBandsMb.forEachIndexed { index, levelMb ->
                    val hz = state.eqFrequenciesHz.getOrNull(index) ?: 0
                    val label = if (hz >= 1000) "${hz / 1000} kHz" else "${hz} Hz"
                    val span = (state.eqMaxMb - state.eqMinMb).coerceAtLeast(1).toFloat()
                    val frac = ((levelMb - state.eqMinMb) / span).coerceIn(0f, 1f)
                    val db = levelMb / 100f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(52.dp),
                        )
                        EngineCapsuleSlider(
                            fraction = frac,
                            onFractionChange = { nextFrac ->
                                val nextMb = (state.eqMinMb + (nextFrac * span)).roundToInt()
                                AudioEngineManager.setEqualizerBandLevel(index, nextMb)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "%+.1f".format(db),
                            color = if (levelMb == 0) Color.White.copy(alpha = 0.50f) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(42.dp).padding(start = 6.dp),
                        )
                    }
                }
            }
        }

        // AirBeats 12 EQ Presets in Obsidian Black Pills
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EngineCardBlack)
                .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .padding(12.dp),
        ) {
            EngineSectionLabel("APPLE & AIRBEATS PRESETS", state.eqPreset)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                AirBeatsEqPresets.forEach { preset ->
                    val selected = state.eqPreset.equals(preset.name, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) EngineActivePillBlack else EngineElevatedBlack)
                            .border(
                                width = 0.6.dp,
                                color = if (selected) Color.White.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.12f),
                                shape = CircleShape,
                            )
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                AudioEngineManager.applyEqPreset(preset)
                            }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = preset.name,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.70f),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerDotCurveCard(
    bandsMb: List<Int>,
    freqsHz: List<Int>,
    minMb: Int,
    maxMb: Int,
    enabled: Boolean,
    onBandChange: (Int, Int) -> Unit,
) {
    val span = (maxMb - minMb).coerceAtLeast(1).toFloat()
    var activeBandIdx by remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EngineCardBlack)
            .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Interactive Dot Curve", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            val statusText = if (activeBandIdx in bandsMb.indices) {
                val db = bandsMb[activeBandIdx] / 100f
                "%+.1f dB".format(db)
            } else {
                "Drag any dot"
            }
            Text(statusText, color = if (enabled) EngineActiveGreen else Color.White.copy(alpha = 0.55f), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(10.dp))

        val dotInnerColor = EngineCardBlack
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp)
                .pointerInput(bandsMb.size, minMb, maxMb) {
                    val count = bandsMb.size.coerceAtLeast(2)
                    val padH = 16.dp.toPx()
                    val padV = 14.dp.toPx()
                    fun handlePoint(pos: Offset) {
                        val usableW = (size.width - padH * 2f).coerceAtLeast(1f)
                        val usableH = (size.height - padV * 2f).coerceAtLeast(1f)
                        val relX = ((pos.x - padH) / usableW).coerceIn(0f, 1f)
                        val idx = (relX * (count - 1)).roundToInt().coerceIn(0, count - 1)
                        activeBandIdx = idx
                        val relY = 1f - ((pos.y - padV) / usableH).coerceIn(0f, 1f)
                        val nextMb = (minMb + relY * span).roundToInt().coerceIn(minMb, maxMb)
                        onBandChange(idx, nextMb)
                    }
                    detectTapGestures { offset -> handlePoint(offset) }
                }
                .pointerInput(bandsMb.size, minMb, maxMb) {
                    val count = bandsMb.size.coerceAtLeast(2)
                    val padH = 16.dp.toPx()
                    val padV = 14.dp.toPx()
                    fun handleDrag(pos: Offset) {
                        val usableW = (size.width - padH * 2f).coerceAtLeast(1f)
                        val usableH = (size.height - padV * 2f).coerceAtLeast(1f)
                        val relX = ((pos.x - padH) / usableW).coerceIn(0f, 1f)
                        val idx = (relX * (count - 1)).roundToInt().coerceIn(0, count - 1)
                        activeBandIdx = idx
                        val relY = 1f - ((pos.y - padV) / usableH).coerceIn(0f, 1f)
                        val nextMb = (minMb + relY * span).roundToInt().coerceIn(minMb, maxMb)
                        onBandChange(idx, nextMb)
                    }
                    detectDragGestures(
                        onDragStart = { offset -> handleDrag(offset) },
                        onDragEnd = { activeBandIdx = -1 },
                        onDragCancel = { activeBandIdx = -1 },
                        onDrag = { change, _ ->
                            change.consume()
                            handleDrag(change.position)
                        },
                    )
                },
        ) {
            val padH = 16.dp.toPx()
            val padV = 14.dp.toPx()
            val w = (size.width - padH * 2f).coerceAtLeast(1f)
            val h = (size.height - padV * 2f).coerceAtLeast(1f)

            // Reference dB Grid Lines (+15, 0, -15)
            listOf(0f, 0.5f, 1f).forEach { frac ->
                val y = padV + h * frac
                drawLine(
                    color = if (frac == 0.5f) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                    start = Offset(padH, y),
                    end = Offset(size.width - padH, y),
                    strokeWidth = if (frac == 0.5f) 1.2.dp.toPx() else 0.7.dp.toPx(),
                )
            }

            val count = bandsMb.size.coerceAtLeast(1)
            val pts = bandsMb.mapIndexed { idx, mb ->
                val x = if (count > 1) padH + w * (idx.toFloat() / (count - 1)) else size.width / 2f
                val fracY = ((mb - minMb) / span).coerceIn(0f, 1f)
                val y = padV + h * (1f - fracY)
                Offset(x, y)
            }

            // Smooth Apple Spline Curve + Soft Fill
            if (pts.size >= 2) {
                val curvePath = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 0 until pts.size - 1) {
                        val p0 = pts[i]
                        val p1 = pts[i + 1]
                        val midX = (p0.x + p1.x) / 2f
                        cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                    }
                }
                val fillPath = Path().apply {
                    addPath(curvePath)
                    lineTo(pts.last().x, padV + h)
                    lineTo(pts.first().x, padV + h)
                    close()
                }
                val lineColor = if (enabled) Color.White else Color.White.copy(alpha = 0.45f)
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.24f), Color.Transparent),
                        startY = padV,
                        endY = padV + h,
                    ),
                )
                drawPath(
                    path = curvePath,
                    color = lineColor,
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            // Frequency Column Stem + Interactive Circular Dot Nodes
            pts.forEachIndexed { idx, pt ->
                val isDraggingThis = activeBandIdx == idx
                drawLine(
                    color = Color.White.copy(alpha = 0.12f),
                    start = Offset(pt.x, padV),
                    end = Offset(pt.x, padV + h),
                    strokeWidth = 0.8.dp.toPx(),
                )
                if (isDraggingThis) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.28f),
                        radius = 13.dp.toPx(),
                        center = pt,
                    )
                }
                drawCircle(
                    color = dotInnerColor,
                    radius = if (isDraggingThis) 8.dp.toPx() else 6.5.dp.toPx(),
                    center = pt,
                )
                drawCircle(
                    color = if (isDraggingThis) EngineActiveGreen else Color.White,
                    radius = if (isDraggingThis) 6.dp.toPx() else 4.5.dp.toPx(),
                    center = pt,
                )
            }
        }

        // Frequency Axis Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            freqsHz.forEach { hz ->
                val txt = if (hz >= 1000) "${hz / 1000}k" else "${hz}"
                Text(txt, color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun DjConsoleOptionsPage(
    state: AudioEngineUiState,
    vizStats: AudioVisualizerStats,
) {
    val view = LocalView.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Master, 1=Echo, 2=Mixer, 3=Deck

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EngineSwitchBannerCard(
            iconRes = R.drawable.ic_dj_console,
            title = "Studio DJ Engine",
            subtitle = if (state.djEnabled) "Live O(1) PCM DSP + 200% Amplifier" else "Enable to process Echo, Filter, Flanger & Deck",
            checked = state.djEnabled,
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setDjEnabled(it)
            },
        )

        EngineBlackSegmentedBar(
            labels = listOf("Master", "Echo", "Mixer", "Deck"),
            selectedIndex = selectedTab,
            onSelect = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                selectedTab = it
            },
        )

        when (selectedTab) {
            0 -> DjMasterTab(state = state, vizStats = vizStats)
            1 -> DjEchoTab(state = state)
            2 -> DjMixerTab(state = state)
            else -> DjDeckTab(state = state)
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun DjMasterTab(
    state: AudioEngineUiState,
    vizStats: AudioVisualizerStats,
) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 3 Circular Energy Dials (Bass / Mid / Treble) copied from AirBeats AudioFxSheet
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DjEnergyDialCard("BASS", vizStats.bassDb, vizStats.bass, vizStats.isBassPeak, Modifier.weight(1f))
            DjEnergyDialCard("MID", vizStats.midDb, vizStats.mid, false, Modifier.weight(1f))
            DjEnergyDialCard("TREBLE", vizStats.trebleDb, vizStats.treble, false, Modifier.weight(1f))
        }

        // 200% Master Digital Amplifier Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EngineCardBlack)
                .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Master Amplifier Boost", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    Text("Hardware LoudnessEnhancer (up to +18 dB)", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp)
                }
                Text(
                    text = "${state.djBoostPercent}%",
                    color = if (state.djBoostPercent > 100) EngineActiveGreen else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            EngineCapsuleSlider(
                fraction = (state.djBoostPercent - 100) / 100f,
                onFractionChange = { f -> AudioEngineManager.setDjBoostPercent(100 + (f * 100f).roundToInt()) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(100, 125, 150, 175, 200).forEach { pct ->
                    EngineBlackChoiceChip(
                        label = "$pct%",
                        selected = state.djBoostPercent == pct,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            AudioEngineManager.setDjBoostPercent(pct)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 7 Instant DJ Presets from AirBeats
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EngineCardBlack)
                .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .padding(12.dp),
        ) {
            EngineSectionLabel("INSTANT 1-TAP DJ PRESETS", null)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DjPreset.entries.forEach { preset ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(EngineElevatedBlack)
                            .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                AudioEngineManager.applyDjPreset(preset)
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(preset.iconRes),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(preset.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(preset.subtitle, color = Color.White.copy(alpha = 0.56f), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun DjEchoTab(state: AudioEngineUiState) {
    val view = LocalView.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(EngineCardBlack)
            .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    AudioEngineManager.setEchoEnabled(!state.echoEnabled)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Stereo Echo & Delay", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text("Spatial reflections (${state.echoDelayMs} ms)", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
            }
            AppleGlassSwitch(checked = state.echoEnabled, onCheckedChange = AudioEngineManager::setEchoEnabled)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    AudioEngineManager.setEchoPingPong(!state.echoPingPong)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("3D Ping-Pong Bounce", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Alternates reflections L <-> R", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp)
            }
            AppleGlassSwitch(checked = state.echoPingPong, onCheckedChange = AudioEngineManager::setEchoPingPong)
        }

        LabeledCapsuleControl("Delay Time", "${state.echoDelayMs} ms", (state.echoDelayMs - 40) / 760f) {
            AudioEngineManager.setEchoDelayMs(40 + (it * 760f).roundToInt())
        }
        LabeledCapsuleControl("Feedback Repeats", "${(state.echoFeedback * 100).roundToInt()}%", (state.echoFeedback - 0.05f) / 0.80f) {
            AudioEngineManager.setEchoFeedback(0.05f + it * 0.80f)
        }
        LabeledCapsuleControl("Wet / Dry Mix", "${(state.echoWetMix * 100).roundToInt()}%", state.echoWetMix) {
            AudioEngineManager.setEchoWetMix(it)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Slap 90ms" to (90 to 0.25f), "Dub 280ms" to (280 to 0.45f), "Space 480ms" to (480 to 0.65f)).forEach { (label, pair) ->
                EngineBlackChoiceChip(
                    label = label,
                    selected = state.echoEnabled && abs(state.echoDelayMs - pair.first) < 25,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        AudioEngineManager.setEchoEnabled(true)
                        AudioEngineManager.setEchoDelayMs(pair.first)
                        AudioEngineManager.setEchoFeedback(pair.second)
                        if (state.echoWetMix < 0.2f) AudioEngineManager.setEchoWetMix(0.45f)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun DjMixerTab(state: AudioEngineUiState) {
    val view = LocalView.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(EngineCardBlack)
            .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("DJ Bipolar Club Filter", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = when {
                        state.filterSweep < -0.04f -> "Low-Pass Muffle (${(state.filterSweep * 100).roundToInt()}%)"
                        state.filterSweep > 0.04f -> "High-Pass Bass Cut (+${(state.filterSweep * 100).roundToInt()}%)"
                        else -> "Flat / Center (Bypassed)"
                    },
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 11.sp,
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(EngineElevatedBlack)
                    .border(0.5.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        AudioEngineManager.setFilterSweep(0f)
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text("Snap 0", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        EngineCapsuleSlider(
            fraction = (state.filterSweep + 1f) / 2f,
            onFractionChange = { f -> AudioEngineManager.setFilterSweep(f * 2f - 1f) },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { AudioEngineManager.setFlangerEnabled(!state.flangerEnabled) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Jet Flanger Swoosh", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("LFO swept jet phase modulation", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp)
            }
            AppleGlassSwitch(checked = state.flangerEnabled, onCheckedChange = AudioEngineManager::setFlangerEnabled)
        }

        AnimatedVisibility(visible = state.flangerEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabeledCapsuleControl("Flanger LFO Rate", "${"%.1f".format(state.flangerRate)} Hz", (state.flangerRate - 0.1f) / 3.9f) {
                    AudioEngineManager.setFlangerRate(0.1f + it * 3.9f)
                }
                LabeledCapsuleControl("Flanger Depth", "${(state.flangerDepth * 100).roundToInt()}%", state.flangerDepth) {
                    AudioEngineManager.setFlangerDepth(it)
                }
            }
        }

        LabeledCapsuleControl("Warm Analog Tape Saturation", "${(state.saturation * 100).roundToInt()}%", state.saturation) {
            AudioEngineManager.setSaturation(it)
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun DjDeckTab(state: AudioEngineUiState) {
    val view = LocalView.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(EngineCardBlack)
            .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { AudioEngineManager.setDjTurntableLinked(!state.turntableLinked) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Vinyl Turntable Link", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text(if (state.turntableLinked) "Speed & pitch locked together" else "Independent BPM tempo & key pitch", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp)
            }
            AppleGlassSwitch(checked = state.turntableLinked, onCheckedChange = AudioEngineManager::setDjTurntableLinked)
        }

        LabeledCapsuleControl("Playback Speed (BPM)", "${"%.2f".format(state.tempoSpeed)}x", (state.tempoSpeed - 0.5f) / 1.5f) { f ->
            val spd = 0.5f + f * 1.5f
            AudioEngineManager.setDjTempoAndPitch(spd, if (state.turntableLinked) spd else state.pitch)
        }

        if (!state.turntableLinked) {
            LabeledCapsuleControl("Pitch Shift (Key)", "${"%.2f".format(state.pitch)}x", (state.pitch - 0.5f) / 1.5f) { f ->
                AudioEngineManager.setDjTempoAndPitch(state.tempoSpeed, 0.5f + f * 1.5f)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("0.85x Slow" to 0.85f, "1.0x Pure" to 1.0f, "1.25x Night" to 1.25f, "1.5x Fast" to 1.5f).forEach { (label, spd) ->
                EngineBlackChoiceChip(
                    label = label,
                    selected = abs(state.tempoSpeed - spd) < 0.02f,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        AudioEngineManager.setDjTempoAndPitch(spd, if (state.turntableLinked) spd else state.pitch)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun NormalizationOptionsPage(state: AudioEngineUiState) {
    val view = LocalView.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EngineSwitchBannerCard(
            iconRes = R.drawable.ic_fx_normalization,
            title = "Audio Normalization (Sound Check)",
            subtitle = if (state.normalizationEnabled) {
                "Applied gain: %+.1f dB via LoudnessEnhancer".format(state.activeAppliedGainMb / 100f)
            } else {
                "Matches track perceived loudness evenly"
            },
            checked = state.normalizationEnabled,
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setNormalizationEnabled(it)
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EngineCardBlack)
                .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EngineSectionLabel("TARGET LUFS LOUDNESS", "${state.normalizationProfile.targetLufs.toInt()} LUFS")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NormalizationProfile.entries.forEach { profile ->
                    EngineBlackChoiceChip(
                        label = "${profile.label} ${profile.targetLufs.toInt()}",
                        selected = state.normalizationProfile == profile,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            AudioEngineManager.setNormalizationProfile(profile)
                            if (!state.normalizationEnabled) AudioEngineManager.setNormalizationEnabled(true)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            LabeledCapsuleControl(
                label = "Pre-Gain Offset",
                valueText = "%+.1f dB".format(state.normalizationOffsetMb / 100f),
                fraction = (state.normalizationOffsetMb + 1200) / 2400f,
                onFractionChange = { f ->
                    val mb = (-1200 + (f * 2400f).roundToInt()).coerceIn(-1200, 1200)
                    AudioEngineManager.setNormalizationOffsetMb(mb)
                },
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun BitPerfectOptionsPage(
    state: AudioEngineUiState,
    bitPerfectActive: Boolean,
    usbPresent: Boolean,
) {
    val view = LocalView.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EngineSwitchBannerCard(
            iconRes = R.drawable.ic_output_bit_perfect,
            title = "USB Bit-Perfect Direct DAC",
            subtitle = when {
                Build.VERSION.SDK_INT < 34 -> "Requires Android 14+ (API 34+)"
                bitPerfectActive -> "Bit-exact PCM directly to USB hardware"
                usbPresent -> "USB DAC connected — ready to lock mixer bypass"
                else -> "Connect a USB-C DAC or headset to engage"
            },
            checked = state.bitPerfectEnabled,
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AudioEngineManager.setBitPerfectEnabled(it)
            },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EngineCardBlack)
                .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EngineKeyValueRow("Mixer Mode", if (bitPerfectActive) "MIXER_BEHAVIOR_BIT_PERFECT" else "Standard AudioTrack Mixer")
            EngineKeyValueRow("USB Hardware", if (usbPresent) "Detected" else "Not connected")
            EngineKeyValueRow("DSP Isolation", if (bitPerfectActive) "EQ/FX Locked for Bit-Exact Audio" else "DSP Available")
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SignalPipelinePage(
    track: MusicTrack?,
    state: AudioEngineUiState,
    deviceName: String,
    sampleRate: String,
) {
    val locked = state.bypassAllEffects || (state.bitPerfectEnabled && state.bitPerfectActive)
    val dspChain = buildList {
        if (locked) {
            add("Bypassed (Bit-Exact Direct)")
        } else {
            if (state.dolbyAtmosEnabled) add("Dolby Atmos")
            if (state.eightDAudioEnabled) add("${state.eightDAudioLevel}D Orbit")
            if (state.spatialAudioEnabled) add("Spatial ${"%.1f".format(state.spatialWidth)}x")
            if (state.eqEnabled) add("EQ (${state.eqPreset})")
            if (state.djEnabled) add("DJ Studio")
            if (state.normalizationEnabled) add("Norm ${state.normalizationProfile.targetLufs.toInt()} LUFS")
        }
        if (isEmpty()) add("Clean Passthrough")
    }.joinToString(" • ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(EngineCardBlack)
            .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EngineSectionLabel("LIVE AUDIO SIGNAL CHAIN", null)
        EngineKeyValueRow("1. Track", track?.let { "${it.title} — ${it.artists}" } ?: "No active track (Engine Ready)")
        EngineKeyValueRow("2. Decoder", "Media3 / PCM 16-bit")
        EngineKeyValueRow("3. Sample Rate", sampleRate)
        EngineKeyValueRow("4. Active DSP", dspChain)
        EngineKeyValueRow("5. Output Sink", deviceName)
    }
}

// ── Shared Obsidian Black UI Building Blocks ──

@Composable
private fun EngineToggleCardRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    switchEnabled: Boolean,
    onCardClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (checked) EngineElevatedBlack else EngineCardBlack,
        animationSpec = tween(160),
        label = "engineRowBg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(
                width = 0.6.dp,
                color = if (checked) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onCardClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (checked) EngineActivePillBlack else Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = title,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Open $title options",
                    tint = Color.White.copy(alpha = 0.42f),
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.alpha(if (switchEnabled) 1f else 0.42f)) {
            AppleGlassSwitch(
                checked = checked,
                onCheckedChange = { newValue -> if (switchEnabled) onCheckedChange(newValue) },
            )
        }
    }
}

@Composable
private fun EngineActionCardRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(EngineCardBlack)
            .border(0.6.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = title,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.White.copy(alpha = 0.58f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun EngineSwitchBannerCard(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (checked) EngineElevatedBlack else EngineCardBlack)
            .border(
                width = 0.6.dp,
                color = if (checked) EngineActiveGreen.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (checked) EngineActivePillBlack else Color.White.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.62f), fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        AppleGlassSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DjEnergyDialCard(
    title: String,
    dbValue: Int,
    energy: Float,
    isPeak: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeColor = if (isPeak || energy > 0.7f) AppleMusicRed else if (energy > 0.35f) EngineActiveGreen else Color.White
    Box(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(EngineCardBlack)
            .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(68.dp)) {
            val stroke = 4.dp.toPx()
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = (energy.coerceIn(0.05f, 1f) * 360f),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = AppleTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text("+$dbValue dB", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EngineBlackSegmentedBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(EngineCardBlack)
            .padding(2.5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { idx, label ->
            val selected = idx == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(29.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) EngineActivePillBlack else Color.Transparent)
                    .clickable { onSelect(idx) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) Color.White else AppleTextSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EngineBlackChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(if (selected) EngineActivePillBlack else EngineElevatedBlack)
            .border(
                width = 0.6.dp,
                color = if (selected) Color.White.copy(alpha = 0.36f) else Color.White.copy(alpha = 0.12f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.68f),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EngineBlackPillButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(EngineCardBlack)
            .border(0.6.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LabeledCapsuleControl(
    label: String,
    valueText: String,
    fraction: Float,
    onFractionChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(valueText, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        }
        EngineCapsuleSlider(fraction = fraction, onFractionChange = onFractionChange)
    }
}

@Composable
private fun EngineCapsuleSlider(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = modifier
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    onFractionChange((offset.x / w).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    onFractionChange((change.position.x / w).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(6.5.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun EngineSectionLabel(title: String, trailing: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = AppleTextSecondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                color = Color.White.copy(alpha = 0.56f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun EngineKeyValueRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EngineHeaderIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(33.dp)
            .clip(CircleShape)
            .background(EngineCardBlack)
            .border(0.6.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
