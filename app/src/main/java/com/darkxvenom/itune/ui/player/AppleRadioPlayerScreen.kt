package com.darkxvenom.itune.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.darkxvenom.itune.R
import com.darkxvenom.itune.audio.AudioEngineManager
import com.darkxvenom.itune.audio.AudioOutputManager
import com.darkxvenom.itune.radio.OnlineRadioController
import com.darkxvenom.itune.radio.OnlineRadioStation
import com.darkxvenom.itune.radio.OnlineRadioUiState
import com.darkxvenom.itune.ui.artist.theme.SFProDisplay
import com.darkxvenom.itune.ui.components.AnimatedEqualizer
import com.darkxvenom.itune.ui.theme.AppleGlassBorder
import com.darkxvenom.itune.ui.theme.AppleGlassMiniPlayer
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlin.math.roundToInt

private enum class RadioMorePage {
    MAIN,
    DETAILS,
    SLEEP_TIMER,
}

private fun lerpFloatRadio(start: Float, stop: Float, fraction: Float): Float {
    val f = fraction.coerceIn(0f, 1f)
    return start + (stop - start) * f
}

private fun lerpDpRadio(start: Dp, stop: Dp, fraction: Float): Dp {
    val f = fraction.coerceIn(0f, 1f)
    return (start.value + (stop.value - start.value) * f).coerceAtLeast(0f).dp
}

/**
 * Separate Apple-styled Floating Mini-Player capsule for Live Online FM Radio.
 * Supports full-gesture swipe-down dismiss, left/right station skipping, and 1:1 upward expansion via SheetMorph.
 */
@Composable
fun FloatingRadioMiniPlayer(
    state: OnlineRadioUiState,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNextStation: () -> Unit,
    showEmptyPlaceholder: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val station = state.currentStation ?: return
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(54.dp)
            .shadow(
                elevation = 14.dp,
                shape = CircleShape,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(CircleShape)
            .background(AppleGlassMiniPlayer)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
                ),
            )
            .border(0.75.dp, AppleGlassBorder, CircleShape)
            .clickable(onClick = onExpand)
            .padding(start = 10.dp, end = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showEmptyPlaceholder) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF28282C)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Radio,
                        contentDescription = "FM Radio",
                        tint = AppleMusicRed,
                        modifier = Modifier.size(18.dp),
                    )
                    if (!station.faviconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(station.faviconUrl)
                                .size(128)
                                .crossfade(200)
                                .build(),
                            contentDescription = station.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (!showEmptyPlaceholder) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.name,
                        color = AppleTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.liveMetadataTitle ?: "LIVE • ${station.locationLabel}",
                        color = AppleTextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(onClick = onPlayPause, modifier = Modifier.size(36.dp)) {
                    if (state.isBuffering && !state.isPlaying) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Image(
                            painter = painterResource(
                                if (state.isPlaying) R.drawable.pause else R.drawable.play,
                            ),
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                IconButton(onClick = onNextStation, modifier = Modifier.size(36.dp)) {
                    Image(
                        painter = painterResource(R.drawable.skip_next),
                        contentDescription = "Next Station",
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.88f)),
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Separate Apple-styled Online FM Radio Player matching ITuneMusicPlayer visuals & controls:
 * - Full-bleed edge-to-edge fading artwork + luminous blurred backdrop (no boxy frame)
 * - Continuous spring-morphing 58dp rounded header thumbnail + sliding Title/Star/More row when opening Stations Queue
 * - Live Broadcast bar + Audio Engine pill (`iMusic • Codec • Bitrate` / Dolby / 8D / EQ)
 * - Same Prev / Play-Pause / Next transport + exact Apple IosVolumeSlider
 * - Clean borderless Audio Output icon button (no capsule, no device name text) + Sleep Timer + Queue toggle
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AppleRadioPlayerOverlay(
    state: OnlineRadioUiState,
    sheetProgress: Float,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val station = state.currentStation ?: return
    val sp = sheetProgress.coerceIn(0f, 1f)
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    val audioEngineState by AudioEngineManager.state.collectAsStateWithLifecycle()
    val audioOutputState by AudioOutputManager.state.collectAsStateWithLifecycle()

    var isStationsDrawerOpen by remember { mutableStateOf(false) }
    var isMoreMenuOpen by remember { mutableStateOf(false) }
    var isAudioOutputPopupOpen by remember { mutableStateOf(false) }
    var isAudioEnginePopupOpen by remember { mutableStateOf(false) }
    var openSleepTimerDirectly by remember { mutableStateOf(false) }

    val panelProgress by animateFloatAsState(
        targetValue = if (isStationsDrawerOpen) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.90f,
            stiffness = 170f,
        ),
        label = "radioPanelProgress",
    )

    var rootBoxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var playerTitleVisualTopPx by remember { mutableFloatStateOf(0f) }
    var liveBarTopPx by remember { mutableFloatStateOf(0f) }
    var moreAnchorXPx by remember { mutableFloatStateOf(0f) }
    var moreAnchorYPx by remember { mutableFloatStateOf(0f) }
    var engineAnchorXPx by remember { mutableFloatStateOf(0f) }
    var engineAnchorYPx by remember { mutableFloatStateOf(0f) }
    var outputAnchorXPx by remember { mutableFloatStateOf(0f) }
    var outputAnchorYPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(sp) {
        if (sp < 0.85f) {
            if (isMoreMenuOpen) isMoreMenuOpen = false
            if (isAudioOutputPopupOpen) isAudioOutputPopupOpen = false
            if (isAudioEnginePopupOpen) isAudioEnginePopupOpen = false
        }
    }

    BackHandler(enabled = sp > 0.25f) {
        when {
            isMoreMenuOpen -> isMoreMenuOpen = false
            isAudioOutputPopupOpen -> isAudioOutputPopupOpen = false
            isAudioEnginePopupOpen -> isAudioEnginePopupOpen = false
            isStationsDrawerOpen -> isStationsDrawerOpen = false
            else -> onCollapse()
        }
    }

    val isFav = state.favoriteIds.contains(station.uuid)
    val fullElementsAlpha = ((sp - 0.15f) / 0.85f).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { rootBoxCoords = it },
    ) {
        val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val fullBleedHeight = (maxWidth * 1.12f).coerceIn(280.dp, maxHeight * 0.56f)

        // 1. Ambient Blurred Artwork Backdrop
        Crossfade(
            targetState = station.faviconUrl,
            animationSpec = tween(480, easing = FastOutSlowInEasing),
            label = "radioBgCrossfade",
            modifier = Modifier.fillMaxSize(),
        ) { artUrl ->
            if (!artUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artUrl)
                        .size(128)
                        .crossfade(200)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(54.dp)
                        .graphicsLayer {
                            scaleX = 1.45f
                            scaleY = 1.45f
                            alpha = 0.88f
                        },
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF4B111C), Color(0xFF14090C), Color.Black),
                                center = Offset(540f, 380f),
                                radius = 1200f,
                            ),
                        ),
                )
            }
        }

        // 2. Dark Apple Music Luminous Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.28f),
                            0.38f to Color.Black.copy(alpha = 0.16f),
                            0.66f to Color.Black.copy(alpha = 0.56f),
                            1f to Color.Black.copy(alpha = 0.84f),
                        ),
                    ),
                ),
        )

        val queueBottomPadDp = if (liveBarTopPx > 0f) {
            (maxHeight - with(density) { liveBarTopPx.toDp() } + 10.dp).coerceAtLeast(navBottomDp + 258.dp)
        } else {
            navBottomDp + 276.dp
        }

        // 3. Stations Queue List (revealed smoothly below morphing mini header & fading cleanly above Live bar)
        if (panelProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .zIndex(22f)
                    .fillMaxSize()
                    .padding(
                        top = statusBarTopDp + 106.dp,
                        bottom = queueBottomPadDp,
                    )
                    .graphicsLayer {
                        alpha = panelProgress
                        translationY = (1f - panelProgress) * 32.dp.toPx()
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.04f to Color.Black,
                                    0.86f to Color.Black,
                                    1f to Color.Transparent,
                                ),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                RadioInPlayerStationsList(
                    stations = state.stationQueue.ifEmpty { state.stations },
                    currentStation = station,
                    isPlaying = state.isPlaying,
                    onSelectStation = { selected ->
                        OnlineRadioController.playStation(selected, expandPlayer = true)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 3b. Protective Bottom Scrim rising above the Live seek bar so seek/transport controls cover queue edges
            Box(
                modifier = Modifier
                    .zIndex(24f)
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(queueBottomPadDp + 44.dp)
                    .graphicsLayer { alpha = panelProgress * fullElementsAlpha }
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.20f to Color(0xFF0A080A).copy(alpha = 0.72f),
                                0.42f to Color.Black.copy(alpha = 0.90f),
                                1f to Color.Black.copy(alpha = 0.96f),
                            ),
                        ),
                    ),
            )
        }

        // 4. Continuous Morphing Edge-to-Edge Artwork -> 58dp Queue Thumbnail
        val progressClamped = panelProgress.coerceIn(0f, 1f)
        val fullArtStart = lerpDpRadio(0.dp, 24.dp, panelProgress)
        val fullArtTop = lerpDpRadio(0.dp, statusBarTopDp + 34.dp, panelProgress)
        val fullArtWidth = lerpDpRadio(maxWidth, 58.dp, panelProgress).coerceAtLeast(1.dp)
        val fullArtHeight = lerpDpRadio(fullBleedHeight, 58.dp, panelProgress).coerceAtLeast(1.dp)
        val fullArtRadius = lerpDpRadio(0.dp, 11.dp, progressClamped).coerceAtLeast(0.dp)

        val artStart = lerpDpRadio(10.dp, fullArtStart, sp)
        val artTop = lerpDpRadio(10.dp, fullArtTop, sp)
        val artWidth = lerpDpRadio(38.dp, fullArtWidth, sp).coerceAtLeast(1.dp)
        val artHeight = lerpDpRadio(38.dp, fullArtHeight, sp).coerceAtLeast(1.dp)
        val artRadius = lerpDpRadio(6.dp, fullArtRadius, sp).coerceAtLeast(0.dp)

        Box(
            modifier = Modifier
                .zIndex(35f)
                .offset { IntOffset(artStart.roundToPx(), artTop.roundToPx()) }
                .size(width = artWidth, height = artHeight)
                .clip(RoundedCornerShape(artRadius))
                .then(
                    if (panelProgress > 0.85f) {
                        Modifier
                            .border(
                                1.dp,
                                Color.White.copy(alpha = (0.22f * panelProgress).coerceIn(0f, 1f)),
                                RoundedCornerShape(artRadius),
                            )
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isStationsDrawerOpen = false
                            }
                    } else {
                        Modifier
                    },
                ),
        ) {
            val playerFadeWeight = (1f - panelProgress * 8f).coerceIn(0f, 1f)
            val fadeStart = lerpFloatRadio(1.0f, 0.66f, playerFadeWeight)
            val fadeEndAlpha = lerpFloatRadio(1.0f, 0.0f, playerFadeWeight)

            Crossfade(
                targetState = station.faviconUrl,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                label = "radioArtworkCrossfade",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (fadeEndAlpha < 0.99f) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Black,
                                        fadeStart to Color.Black,
                                        1f to Color.Black.copy(alpha = fadeEndAlpha),
                                    ),
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    },
            ) { artUrl ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF43151F), Color(0xFF1D0D12), Color(0xFF101014)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val iconScale = lerpFloatRadio(1f, 0.42f, panelProgress)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Radio,
                            contentDescription = null,
                            tint = AppleMusicRed,
                            modifier = Modifier.size(84.dp),
                        )
                        if (panelProgress < 0.35f) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = station.countryCode.ifBlank { "FM" },
                                color = Color.White.copy(alpha = 0.72f * (1f - panelProgress * 3f).coerceIn(0f, 1f)),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.5.sp,
                            )
                        }
                    }

                    if (!artUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(artUrl)
                                .size(Size.ORIGINAL)
                                .crossfade(220)
                                .build(),
                            contentDescription = station.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // 5. Top Handle Pill & Collapse Chevron Header
        Box(
            modifier = Modifier
                .zIndex(34f)
                .fillMaxWidth()
                .padding(top = statusBarTopDp + 4.dp, start = 12.dp, end = 12.dp)
                .height(44.dp)
                .graphicsLayer { alpha = fullElementsAlpha },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .size(width = 38.dp, height = 4.5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color.White.copy(alpha = 0.40f))
                    .clickable {
                        if (isStationsDrawerOpen) isStationsDrawerOpen = false else onCollapse()
                    },
            )

            IconButton(
                onClick = onCollapse,
                enabled = panelProgress < 0.25f,
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.CenterStart)
                    .graphicsLayer { alpha = (1f - panelProgress * 2.8f).coerceIn(0f, 1f) },
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Minimize",
                    tint = Color.White.copy(alpha = 0.94f),
                    modifier = Modifier.size(32.dp),
                )
            }

            if (panelProgress > 0.8f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, start = 12.dp, end = 104.dp)
                        .height(54.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            isStationsDrawerOpen = false
                        },
                )
            }
        }

        // 6. Morphing Title, Subtitle, Star Favorite & 3-Dot More Row (slides cleanly next to 58dp thumbnail in Queue)
        val fallbackTitleTopPx = with(density) { (maxHeight - navBottomDp - 332.dp).toPx() }
        val restingTitleTopPx = if (playerTitleVisualTopPx > 0f) playerTitleVisualTopPx else fallbackTitleTopPx
        val targetPanelTitleTopPx = with(density) { (statusBarTopDp + 40.dp).toPx() }
        val animatedTitleTopPx = lerpFloatRadio(restingTitleTopPx, targetPanelTitleTopPx, panelProgress)
        val animatedTitleTopDp = with(density) { animatedTitleTopPx.toDp() }
        val titleStartPaddingDp = lerpDpRadio(24.dp, 94.dp, panelProgress)

        Row(
            modifier = Modifier
                .zIndex(36f)
                .fillMaxWidth()
                .offset { IntOffset(0, animatedTitleTopPx.roundToInt()) }
                .padding(start = titleStartPaddingDp, end = 24.dp)
                .graphicsLayer { alpha = fullElementsAlpha },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (panelProgress > 0.8f) {
                            Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isStationsDrawerOpen = false
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Text(
                    text = station.name,
                    style = TextStyle(
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = lerpFloatRadio(22f, 17f, panelProgress).sp,
                        letterSpacing = (-0.3).sp,
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = state.liveMetadataTitle ?: station.locationLabel,
                    style = TextStyle(
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Medium,
                        fontSize = lerpFloatRadio(15f, 13.5f, panelProgress).sp,
                    ),
                    color = Color.White.copy(alpha = 0.74f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
            }

            Spacer(Modifier.width(10.dp))

            // Star Favorite Circle Pill
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (isFav) 0.24f else 0.16f))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        OnlineRadioController.toggleFavorite(station)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(
                        if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
                    ),
                    contentDescription = "Favorite",
                    colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.94f)),
                    modifier = Modifier.size(19.dp),
                )
            }

            Spacer(Modifier.width(8.dp))

            // 3-Dot More Options Circle Pill
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .onGloballyPositioned { coords ->
                        val root = rootBoxCoords
                        if (root != null && root.isAttached && coords.isAttached) {
                            val c = root.localPositionOf(
                                coords,
                                Offset(coords.size.width / 2f, coords.size.height / 2f),
                            )
                            moreAnchorXPx = c.x
                            moreAnchorYPx = c.y
                        }
                    }
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        isAudioOutputPopupOpen = false
                        isAudioEnginePopupOpen = false
                        openSleepTimerDirectly = false
                        isMoreMenuOpen = !isMoreMenuOpen
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription = "More Options",
                    colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.94f)),
                    modifier = Modifier.size(21.dp),
                )
            }
        }

        // 7. Bottom Controls Dock Column (aligned identically to ITuneMusicPlayer)
        Column(
            modifier = Modifier
                .zIndex(25f)
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 18.dp)
                .graphicsLayer { alpha = fullElementsAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Layout anchor reserved for the morphing Title Row above
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .onGloballyPositioned { coords ->
                        val root = rootBoxCoords
                        if (root != null && root.isAttached && coords.isAttached) {
                            val pos = root.localPositionOf(coords, Offset.Zero)
                            if (pos.y > 0f) {
                                playerTitleVisualTopPx = pos.y
                            }
                        }
                    },
            )

            Spacer(Modifier.height(14.dp))

            // Live Broadcast Status Track + Audio Engine Badge Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .onGloballyPositioned { coords ->
                        val root = rootBoxCoords
                        if (root != null && root.isAttached && coords.isAttached) {
                            val pos = root.localPositionOf(coords, Offset.Zero)
                            if (pos.y > 0f) {
                                liveBarTopPx = pos.y
                            }
                        }
                    }
                    .clip(CircleShape)
                    .background(
                        if (state.isPlaying) {
                            Brush.horizontalGradient(
                                listOf(AppleMusicRed, Color.White.copy(alpha = 0.85f), AppleMusicRed),
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.25f)),
                            )
                        },
                    ),
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    state.streamError != null -> Color(0xFFFF453A)
                                    state.isPlaying -> AppleMusicRed
                                    else -> Color.White.copy(alpha = 0.4f)
                                },
                            ),
                    )
                    Text(
                        text = when {
                            state.streamError != null -> "OFFLINE"
                            state.isBuffering -> "BUFFERING"
                            state.isPlaying -> "LIVE"
                            else -> "PAUSED"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = SFProDisplay, fontWeight = FontWeight.Bold),
                        color = Color.White.copy(alpha = 0.86f),
                    )
                }

                // Audio Engine Pill (Dolby Atmos / 8D / EQ / iMusic)
                val pillBadge = audioEngineState.pillBadge
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val root = rootBoxCoords
                            if (root != null && root.isAttached && coords.isAttached) {
                                val c = root.localPositionOf(
                                    coords,
                                    Offset(coords.size.width / 2f, coords.size.height / 2f),
                                )
                                engineAnchorXPx = c.x
                                engineAnchorYPx = c.y
                            }
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Color.White.copy(
                                alpha = if (isAudioEnginePopupOpen || !pillBadge.isBrandedITune) 0.18f else 0.12f,
                            ),
                        )
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            isMoreMenuOpen = false
                            isAudioOutputPopupOpen = false
                            isAudioEnginePopupOpen = !isAudioEnginePopupOpen
                        }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    AnimatedContent(
                        targetState = pillBadge,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                        label = "radioEnginePill",
                    ) { badge ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Image(
                                painter = painterResource(badge.iconRes),
                                contentDescription = badge.text,
                                colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.92f)),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(14.dp),
                            )
                            if (badge.isBrandedITune) {
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(color = Color(0xFFFF1744))) { append("i") }
                                        withStyle(SpanStyle(color = Color.White)) {
                                            append("Music • ${station.qualityBadge}")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = SFProDisplay, fontWeight = FontWeight.Medium),
                                )
                            } else {
                                Text(
                                    text = badge.text,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = SFProDisplay, fontWeight = FontWeight.SemiBold),
                                )
                            }
                        }
                    }
                }

                val sleepRem = state.sleepTimerRemainingSec
                Text(
                    text = if (sleepRem != null) {
                        "${sleepRem / 60}m"
                    } else {
                        station.countryCode.ifBlank { "FM" }
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = SFProDisplay),
                    color = Color.White.copy(alpha = 0.75f),
                )
            }

            Spacer(Modifier.height(22.dp))

            // Main 3 Apple Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        OnlineRadioController.skipPreviousStation()
                    },
                    modifier = Modifier.size(72.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.skip_previous),
                        contentDescription = "Previous Station",
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(50.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            OnlineRadioController.togglePlayPause()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBuffering && !state.isPlaying) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(42.dp),
                        )
                    } else {
                        Image(
                            painter = painterResource(
                                if (state.isPlaying) R.drawable.pause else R.drawable.play,
                            ),
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size(50.dp),
                        )
                    }
                }

                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        OnlineRadioController.skipNextStation()
                    },
                    modifier = Modifier.size(72.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.skip_next),
                        contentDescription = "Next Station",
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(50.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Exact Apple Volume Slider
            IosVolumeSlider(modifier = Modifier.padding(horizontal = 4.dp))

            Spacer(Modifier.height(14.dp))

            // Bottom Action Row: Sleep Timer | Borderless Audio Output Icon ONLY | Stations Queue List
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: Sleep Timer button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (state.sleepTimerRemainingSec != null) Color.White.copy(alpha = 0.22f)
                            else Color.Transparent,
                        )
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            isAudioOutputPopupOpen = false
                            isAudioEnginePopupOpen = false
                            openSleepTimerDirectly = true
                            isMoreMenuOpen = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_menu_sleep_timer),
                        contentDescription = "Sleep Timer",
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.90f)),
                        modifier = Modifier.size(24.dp),
                    )
                }

                // Center: Pure Apple Output Device Icon (NO capsule fill/border, NO text)
                val outputIconRes = audioOutputState.capsuleIconRes
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .onGloballyPositioned { coords ->
                            val root = rootBoxCoords
                            if (root != null && root.isAttached && coords.isAttached) {
                                val c = root.localPositionOf(
                                    coords,
                                    Offset(coords.size.width / 2f, coords.size.height / 2f),
                                )
                                outputAnchorXPx = c.x
                                outputAnchorYPx = c.y
                            }
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isAudioOutputPopupOpen) Color.White.copy(alpha = 0.22f)
                            else Color.Transparent,
                        )
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            isMoreMenuOpen = false
                            isAudioEnginePopupOpen = false
                            isAudioOutputPopupOpen = !isAudioOutputPopupOpen
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(outputIconRes),
                        contentDescription = "Audio Output Devices",
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.92f)),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(if (outputIconRes == R.drawable.ic_airbuds) 28.dp else 23.dp),
                    )
                }

                // Right: Stations Queue List button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isStationsDrawerOpen) Color.White.copy(alpha = 0.22f)
                            else Color.Transparent,
                        )
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            isStationsDrawerOpen = !isStationsDrawerOpen
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = "Station List",
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.90f)),
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }

        // 8. Apple 3-Dot More Options Popup (Water-Drop Origin, sized to show all rows cleanly)
        RadioMoreOptionsPopup(
            visible = isMoreMenuOpen,
            station = station,
            isFavorite = isFav,
            isPanelOpen = isStationsDrawerOpen,
            sleepTimerRemainingSec = state.sleepTimerRemainingSec,
            startOnSleepTimer = openSleepTimerDirectly,
            anchorCenterXPx = moreAnchorXPx,
            anchorCenterYPx = moreAnchorYPx,
            statusBarTopDp = statusBarTopDp,
            titleRowTopDp = animatedTitleTopDp,
            maxHeightDp = maxHeight,
            rootCoordsProvider = { rootBoxCoords },
            onToggleFavorite = { OnlineRadioController.toggleFavorite(station) },
            onSetSleepTimer = { mins -> OnlineRadioController.setSleepTimerMinutes(mins) },
            onStopRadio = {
                isMoreMenuOpen = false
                OnlineRadioController.closeSession()
            },
            onDismiss = {
                isMoreMenuOpen = false
                openSleepTimerDirectly = false
            },
            modifier = Modifier.zIndex(80f),
        )

        // 9. Same Apple Audio Output Devices Popup
        AppleAudioOutputPopup(
            visible = isAudioOutputPopupOpen,
            artUrl = station.faviconUrl,
            anchorCenterXPx = outputAnchorXPx,
            anchorCenterYPx = outputAnchorYPx,
            statusBarTopDp = statusBarTopDp,
            navBottomDp = navBottomDp,
            maxHeightDp = maxHeight,
            rootCoordsProvider = { rootBoxCoords },
            onDismiss = { isAudioOutputPopupOpen = false },
            modifier = Modifier.zIndex(82f),
        )

        // 10. Same Apple Audio Engine & FX Popup
        AppleAudioEnginePopup(
            visible = isAudioEnginePopupOpen,
            track = null,
            artUrl = station.faviconUrl,
            anchorCenterXPx = engineAnchorXPx,
            anchorCenterYPx = engineAnchorYPx,
            statusBarTopDp = statusBarTopDp,
            navBottomDp = navBottomDp,
            maxHeightDp = maxHeight,
            rootCoordsProvider = { rootBoxCoords },
            onDismiss = { isAudioEnginePopupOpen = false },
            modifier = Modifier.zIndex(83f),
        )
    }
}

@Composable
private fun RadioInPlayerStationsList(
    stations: List<OnlineRadioStation>,
    currentStation: OnlineRadioStation,
    isPlaying: Boolean,
    onSelectStation: (OnlineRadioStation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(
            items = stations,
            key = { idx, s -> "rp_${s.uuid}_$idx" },
            contentType = { _, _ -> "radio_queue_row" },
        ) { _, item ->
            val selected = item.uuid == currentStation.uuid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onSelectStation(item) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Radio,
                        contentDescription = null,
                        tint = AppleMusicRed,
                        modifier = Modifier.size(18.dp),
                    )
                    if (!item.faviconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.faviconUrl)
                                .size(128)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        color = if (selected) AppleMusicRed else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.subtitleLine,
                        color = Color.White.copy(alpha = 0.66f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected && isPlaying) {
                    AnimatedEqualizer(isPlaying = true, color = AppleMusicRed)
                }
            }
        }
    }
}

@Composable
private fun RadioMoreOptionsPopup(
    visible: Boolean,
    station: OnlineRadioStation,
    isFavorite: Boolean,
    isPanelOpen: Boolean,
    sleepTimerRemainingSec: Int?,
    startOnSleepTimer: Boolean,
    anchorCenterXPx: Float,
    anchorCenterYPx: Float,
    statusBarTopDp: Dp,
    titleRowTopDp: Dp,
    maxHeightDp: Dp,
    rootCoordsProvider: () -> LayoutCoordinates?,
    onToggleFavorite: () -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onStopRadio: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var page by remember(visible, startOnSleepTimer) {
        mutableStateOf(if (startOnSleepTimer) RadioMorePage.SLEEP_TIMER else RadioMorePage.MAIN)
    }

    val maxClearanceAllowingFullCard = (maxHeightDp - statusBarTopDp - 440.dp).coerceAtLeast(110.dp)
    val bottomClearanceDp = if (isPanelOpen) {
        24.dp
    } else {
        (maxHeightDp - titleRowTopDp + 10.dp).coerceIn(110.dp, maxClearanceAllowingFullCard)
    }

    AppleFrostedPopupOverlay(
        visible = visible,
        title = station.name,
        subtitle = station.subtitleLine,
        artUrl = station.faviconUrl,
        anchorCenterXPx = anchorCenterXPx,
        anchorCenterYPx = anchorCenterYPx,
        maxHeightDp = maxHeightDp,
        statusBarTopDp = statusBarTopDp,
        isTopAnchored = isPanelOpen,
        bottomClearanceDp = bottomClearanceDp,
        rootCoordsProvider = rootCoordsProvider,
        onDismiss = onDismiss,
        onBack = {
            if (page == RadioMorePage.MAIN || startOnSleepTimer) onDismiss() else page = RadioMorePage.MAIN
        },
        modifier = modifier,
        headerActions = {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable { onToggleFavorite() },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(
                        if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
                    ),
                    contentDescription = "Favorite",
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    ) {
        when (page) {
                RadioMorePage.MAIN -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        RadioMenuActionRow(
                            label = if (isFavorite) "Remove from Favorite Stations" else "Favorite Station",
                            iconRes = if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
                            onClick = {
                                onToggleFavorite()
                                onDismiss()
                            },
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                        RadioMenuActionRow(
                            label = if (sleepTimerRemainingSec != null) {
                                "Sleep Timer (${sleepTimerRemainingSec / 60}m left)"
                            } else {
                                "Sleep Timer"
                            },
                            iconRes = R.drawable.ic_menu_sleep_timer,
                            onClick = { page = RadioMorePage.SLEEP_TIMER },
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                        RadioMenuActionRow(
                            label = "Station & Stream Details",
                            iconRes = R.drawable.ic_menu_details,
                            onClick = { page = RadioMorePage.DETAILS },
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                        RadioMenuActionRow(
                            label = "Copy Stream URL",
                            iconRes = R.drawable.ic_menu_copy_link,
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cb?.setPrimaryClip(ClipData.newPlainText(station.name, station.streamUrl))
                                Toast.makeText(context, "Stream URL copied", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                        )
                        if (station.homepage.startsWith("http")) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                            RadioMenuActionRow(
                                label = "Open Station Website",
                                iconRes = R.drawable.ic_menu_share,
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(station.homepage))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                    onDismiss()
                                },
                            )
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                        RadioMenuActionRow(
                            label = "Stop Radio Session",
                            iconRes = R.drawable.close,
                            tint = Color(0xFFFF453A),
                            onClick = onStopRadio,
                        )
                    }
                }

                RadioMorePage.SLEEP_TIMER -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            null to "Off",
                            15 to "15 minutes",
                            30 to "30 minutes",
                            45 to "45 minutes",
                            60 to "1 hour",
                        ).forEachIndexed { idx, (mins, label) ->
                            if (idx > 0) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                            }
                            RadioMenuActionRow(
                                label = label,
                                iconRes = R.drawable.ic_menu_sleep_timer,
                                onClick = {
                                    onSetSleepTimer(mins)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }

                RadioMorePage.DETAILS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Country: ${station.countryName} (${station.countryCode})",
                            color = Color.White,
                            fontSize = 13.5.sp,
                        )
                        if (station.cityState.isNotBlank()) {
                            Text(
                                text = "City / State: ${station.cityState}",
                                color = Color.White,
                                fontSize = 13.5.sp,
                            )
                        }
                        Text(
                            text = "Codec / Bitrate: ${station.qualityBadge}",
                            color = Color.White,
                            fontSize = 13.5.sp,
                        )
                        if (station.language.isNotBlank()) {
                            Text(
                                text = "Language: ${station.language}",
                                color = Color.White,
                                fontSize = 13.5.sp,
                            )
                        }
                        Text(
                            text = "Tags: ${station.primaryTagSummary}",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 12.5.sp,
                        )
                        Text(
                            text = "Stream: ${station.streamUrl}",
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 11.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

@Composable
private fun RadioMenuActionRow(
    label: String,
    iconRes: Int,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
        )
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            colorFilter = ColorFilter.tint(tint.copy(alpha = 0.88f)),
            modifier = Modifier.size(18.dp),
        )
    }
}
