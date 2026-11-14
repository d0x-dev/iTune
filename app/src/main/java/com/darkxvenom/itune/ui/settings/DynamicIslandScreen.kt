package com.darkxvenom.itune.ui.settings

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.island.DynamicIslandConfig
import com.darkxvenom.itune.island.DynamicIslandStore
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
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import android.graphics.Color as AndroidColor

/**
 * Dynamic Island configuration & live water-bubble calibration screen styled 1:1 with Apple Settings:
 * pure #000000 black background, uniform #1C1C1E grey grouped cards, #2C2C2E dividers, and white XML icons.
 * Keeps the system WindowManager overlay strictly outside the app while offering an interactive water-bubble preview.
 */
@Composable
fun DynamicIslandScreen(
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val overscrollState = rememberSpringOverscrollState()
    val appBackdrop = rememberLayerBackdrop()

    LaunchedEffect(Unit) {
        DynamicIslandStore.ensureInit(context)
    }

    val cfg by DynamicIslandStore.state.collectAsStateWithLifecycle()
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var orientationTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Portrait, 1 = Landscape

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                if (hasOverlayPermission && cfg.enabled) {
                    DynamicIslandStore.syncServiceState(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler {
        onBack()
    }

    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 96
        }
    }

    val openOverlaySettings = {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "Open system settings to grant Display over other apps", Toast.LENGTH_SHORT).show()
        }
    }

    val isLandscapeTab = orientationTab == 1
    val curX = if (isLandscapeTab) cfg.landscapeOffsetX else cfg.portraitOffsetX
    val curY = if (isLandscapeTab) cfg.landscapeOffsetY else cfg.portraitOffsetY
    val curW = if (isLandscapeTab) cfg.landscapeWidth else cfg.portraitWidth
    val curH = if (isLandscapeTab) cfg.landscapeHeight else cfg.portraitHeight

    ITubeArtistTheme {
        CompositionLocalProvider(
            LocalAppBackdrop provides appBackdrop,
            LocalLiquidGlassEnabled provides isGlassSupported(),
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(SettingsScreenBlack),
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = topBarContentPadding() + 6.dp,
                        bottom = if (hasMiniPlayer) 210.dp else 140.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .springOverscroll(overscrollState)
                        .hazeSource(state = hazeState),
                ) {
                    item(key = "di_title") {
                        Text(
                            text = "Dynamic Island",
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.6).sp,
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                    }

                    // 1. Live Interactive Water-Bubble Notch Calibration Card
                    item(key = "di_preview_card") {
                        IslandSectionColumn(title = "WATER-BUBBLE PREVIEW (TAP BUBBLE)") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            ) {
                                InteractiveWaterBubblePreview(
                                    cfg = cfg,
                                    widthDp = curW,
                                    heightDp = curH,
                                    offsetXDp = curX,
                                    offsetYDp = curY,
                                )

                                Spacer(Modifier.height(10.dp))

                                IslandBlackSegmentedSwitcher(
                                    options = listOf("Portrait", "Landscape"),
                                    selectedIndex = orientationTab,
                                    onSelect = { idx ->
                                        haptics.play(Haptic.Tap)
                                        orientationTab = idx
                                        (context as? Activity)?.requestedOrientation = if (idx == 1) {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        }
                                    },
                                )
                            }
                        }
                    }

                    // 2. Overlay Permission & Master Controls
                    item(key = "di_activation") {
                        IslandSectionColumn(title = "OVERLAY & ACTIVATION") {
                            IslandActionRow(
                                iconRes = R.drawable.ic_di_permission,
                                title = "Display Over Other Apps",
                                subtitle = if (hasOverlayPermission) {
                                    "Overlay permission active • 0-lag GPU layer"
                                } else {
                                    "Required to render Dynamic Island outside iTune"
                                },
                                trailingValue = if (hasOverlayPermission) "Granted" else "Grant",
                                trailingAccent = if (hasOverlayPermission) Color(0xFF34C759) else Color(0xFFFF453A),
                                onClick = {
                                    haptics.play(Haptic.Tap)
                                    openOverlaySettings()
                                },
                            )
                            IslandInsetDivider()
                            IslandToggleRow(
                                iconRes = R.drawable.ic_settings_dynamic_island,
                                title = "Enable Dynamic Island",
                                subtitle = "Show floating water-bubble player controller",
                                checked = cfg.enabled && hasOverlayPermission,
                                onCheckedChange = { wantEnable ->
                                    if (wantEnable && !Settings.canDrawOverlays(context)) {
                                        haptics.play(Haptic.Tap)
                                        openOverlaySettings()
                                    } else {
                                        haptics.play(if (wantEnable) Haptic.ToggleOn else Haptic.ToggleOff)
                                        DynamicIslandStore.update(context) { it.copy(enabled = wantEnable) }
                                    }
                                },
                            )
                            IslandInsetDivider()
                            IslandToggleRow(
                                iconRes = R.drawable.ic_di_outside_only,
                                title = "Only Outside App",
                                subtitle = "Hide inside iTune; appear automatically outside",
                                checked = cfg.showOutsideAppOnly,
                                onCheckedChange = { next ->
                                    haptics.play(if (next) Haptic.ToggleOn else Haptic.ToggleOff)
                                    DynamicIslandStore.update(context) { it.copy(showOutsideAppOnly = next) }
                                },
                            )
                        }
                    }

                    // 3. Position & Bubble Geometry Sliders
                    item(key = "di_geometry") {
                        IslandSectionColumn(title = "POSITION & BUBBLE ADJUSTMENT") {
                            val widthMode = when {
                                curW <= 52 -> "Mini Dot ($curW dp)"
                                curW < 115 -> "Compact ($curW dp)"
                                else -> "Water Pill ($curW dp)"
                            }
                            IslandSliderRow(
                                iconRes = R.drawable.ic_di_position,
                                title = "Horizontal Position (X)",
                                valueLabel = when {
                                    curX == 0 -> "Center (0 dp)"
                                    curX > 0 -> "+$curX dp"
                                    else -> "$curX dp"
                                },
                                value = curX.toFloat(),
                                valueRange = -200f..200f,
                                onValueChange = { v ->
                                    val iv = v.roundToInt()
                                    DynamicIslandStore.update(context) { cur ->
                                        if (isLandscapeTab) cur.copy(landscapeOffsetX = iv) else cur.copy(portraitOffsetX = iv)
                                    }
                                },
                            )
                            IslandInsetDivider()
                            IslandSliderRow(
                                iconRes = R.drawable.ic_di_position,
                                title = "Vertical Position (Y)",
                                valueLabel = "$curY dp",
                                value = curY.toFloat(),
                                valueRange = -20f..200f,
                                onValueChange = { v ->
                                    val iv = v.roundToInt()
                                    DynamicIslandStore.update(context) { cur ->
                                        if (isLandscapeTab) cur.copy(landscapeOffsetY = iv) else cur.copy(portraitOffsetY = iv)
                                    }
                                },
                            )
                            IslandInsetDivider()
                            IslandSliderRow(
                                iconRes = R.drawable.ic_di_size,
                                title = "Island Width",
                                valueLabel = widthMode,
                                value = curW.toFloat(),
                                valueRange = 38f..280f,
                                onValueChange = { v ->
                                    val iv = v.roundToInt()
                                    DynamicIslandStore.update(context) { cur ->
                                        if (isLandscapeTab) cur.copy(landscapeWidth = iv) else cur.copy(portraitWidth = iv)
                                    }
                                },
                            )
                            IslandInsetDivider()
                            IslandSliderRow(
                                iconRes = R.drawable.ic_di_size,
                                title = "Island Height",
                                valueLabel = "$curH dp",
                                value = curH.toFloat(),
                                valueRange = 22f..56f,
                                onValueChange = { v ->
                                    val iv = v.roundToInt()
                                    DynamicIslandStore.update(context) { cur ->
                                        if (isLandscapeTab) cur.copy(landscapeHeight = iv) else cur.copy(portraitHeight = iv)
                                    }
                                },
                            )
                            IslandInsetDivider()
                            IslandSliderRow(
                                iconRes = R.drawable.ic_di_roundness,
                                title = "Water-Bubble Curvature",
                                valueLabel = "${cfg.bubbleCurvatureDp} dp",
                                value = cfg.bubbleCurvatureDp.toFloat(),
                                valueRange = 18f..38f,
                                onValueChange = { v ->
                                    DynamicIslandStore.update(context) { cur -> cur.copy(bubbleCurvatureDp = v.roundToInt()) }
                                },
                            )
                            IslandInsetDivider()
                            IslandActionRow(
                                iconRes = R.drawable.ic_di_reset,
                                title = "Reset Position & Size",
                                subtitle = "Restore default camera notch alignment",
                                trailingValue = "Reset",
                                onClick = {
                                    haptics.play(Haptic.Tap)
                                    DynamicIslandStore.resetGeometry(context, isLandscapeTab)
                                    Toast.makeText(context, "Dynamic Island geometry reset", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }

                    // 4. Water-Drop Cloth Physics & Visual Effects
                    item(key = "di_water_physics") {
                        IslandSectionColumn(title = "WATER-DROP & CLOTH PHYSICS") {
                            IslandToggleRow(
                                iconRes = R.drawable.ic_di_water_drop,
                                title = "Water-Drop Ripple & Morph",
                                subtitle = "Fluid cloth stretch & double ripple wave",
                                checked = cfg.waterDropEffect,
                                onCheckedChange = { next ->
                                    haptics.play(if (next) Haptic.ToggleOn else Haptic.ToggleOff)
                                    DynamicIslandStore.update(context) { it.copy(waterDropEffect = next) }
                                },
                            )
                            IslandInsetDivider()
                            IslandSliderRow(
                                iconRes = R.drawable.ic_di_elasticity,
                                title = "Cloth Spring Elasticity",
                                valueLabel = "${"%.1f".format(cfg.bubbleElasticity)}x",
                                value = cfg.bubbleElasticity,
                                valueRange = 0.6f..1.4f,
                                onValueChange = { v ->
                                    val snapped = (v * 10f).roundToInt() / 10f
                                    DynamicIslandStore.update(context) { it.copy(bubbleElasticity = snapped) }
                                },
                            )
                            IslandInsetDivider()
                            IslandToggleRow(
                                iconRes = R.drawable.ic_di_sheen,
                                title = "Liquid Glass Bubble Sheen",
                                subtitle = "Top specular reflection & soft glass rim",
                                checked = cfg.liquidSheen,
                                onCheckedChange = { next ->
                                    haptics.play(if (next) Haptic.ToggleOn else Haptic.ToggleOff)
                                    DynamicIslandStore.update(context) { it.copy(liquidSheen = next) }
                                },
                            )
                            IslandInsetDivider()
                            IslandToggleRow(
                                iconRes = R.drawable.ic_di_visualizer,
                                title = "Live Audio Visualizer",
                                subtitle = "Synced glowing spectrum bars in pill & player",
                                checked = cfg.showVisualizer,
                                onCheckedChange = { next ->
                                    haptics.play(if (next) Haptic.ToggleOn else Haptic.ToggleOff)
                                    DynamicIslandStore.update(context) { it.copy(showVisualizer = next) }
                                },
                            )
                            IslandInsetDivider()
                            IslandToggleRow(
                                iconRes = R.drawable.ic_di_spin,
                                title = "Rotating Vinyl Artwork",
                                subtitle = "Spin circular album art during playback",
                                checked = cfg.rotatingArtwork,
                                onCheckedChange = { next ->
                                    haptics.play(if (next) Haptic.ToggleOn else Haptic.ToggleOff)
                                    DynamicIslandStore.update(context) { it.copy(rotatingArtwork = next) }
                                },
                            )
                            IslandInsetDivider()
                            IslandActionRow(
                                iconRes = R.drawable.ic_di_wave_seek,
                                title = "Seekbar Style",
                                subtitle = "Progress bar look inside expanded bubble",
                                trailingValue = if (cfg.capsuleSeekStyle) "Apple Capsule" else "Squiggly Wave",
                                onClick = {
                                    haptics.play(Haptic.Tap)
                                    DynamicIslandStore.update(context) { it.copy(capsuleSeekStyle = !it.capsuleSeekStyle) }
                                },
                            )
                        }
                    }

                    // 5. Behavior & Colors
                    item(key = "di_behavior_colors") {
                        val autoCollapseOptions = listOf(4, 6, 10, 0)
                        val pauseTimeoutOptions = listOf(0, 30, 60, 180)

                        IslandSectionColumn(title = "BEHAVIOR & CUSTOM COLORS") {
                            IslandToggleRow(
                                iconRes = R.drawable.ic_di_colors,
                                title = "Adaptive Album Glow",
                                subtitle = "Tint water bubble & spectrum from cover art",
                                checked = cfg.adaptiveAlbumTint,
                                onCheckedChange = { next ->
                                    haptics.play(if (next) Haptic.ToggleOn else Haptic.ToggleOff)
                                    DynamicIslandStore.update(context) { it.copy(adaptiveAlbumTint = next) }
                                },
                            )
                            IslandInsetDivider()
                            IslandToggleRow(
                                iconRes = R.drawable.ic_di_tap_behavior,
                                title = "Tap Pill Opens iTune",
                                subtitle = if (cfg.tapOpensApp) "Single tap opens app directly" else "Single tap blooms Water-Bubble player",
                                checked = cfg.tapOpensApp,
                                onCheckedChange = { next ->
                                    haptics.play(if (next) Haptic.ToggleOn else Haptic.ToggleOff)
                                    DynamicIslandStore.update(context) { it.copy(tapOpensApp = next) }
                                },
                            )
                            IslandInsetDivider()
                            IslandActionRow(
                                iconRes = R.drawable.ic_di_timer,
                                title = "Auto-Collapse After",
                                subtitle = "Return expanded bubble to notch automatically",
                                trailingValue = if (cfg.autoCollapseSec == 0) "Never" else "${cfg.autoCollapseSec} sec",
                                onClick = {
                                    haptics.play(Haptic.Tap)
                                    val idx = autoCollapseOptions.indexOf(cfg.autoCollapseSec)
                                    val next = autoCollapseOptions[(idx + 1) % autoCollapseOptions.size]
                                    DynamicIslandStore.update(context) { it.copy(autoCollapseSec = next) }
                                },
                            )
                            IslandInsetDivider()
                            IslandActionRow(
                                iconRes = R.drawable.ic_di_timer,
                                title = "Keep Visible When Paused",
                                subtitle = "Time before paused bubble glides away",
                                trailingValue = when (cfg.pauseKeepSec) {
                                    0 -> "Hide Now"
                                    60 -> "1 min"
                                    180 -> "3 min"
                                    else -> "${cfg.pauseKeepSec} sec"
                                },
                                onClick = {
                                    haptics.play(Haptic.Tap)
                                    val idx = pauseTimeoutOptions.indexOf(cfg.pauseKeepSec)
                                    val next = pauseTimeoutOptions[(idx + 1) % pauseTimeoutOptions.size]
                                    DynamicIslandStore.update(context) { it.copy(pauseKeepSec = next) }
                                },
                            )
                            IslandInsetDivider()
                            IslandColorPaletteRow(
                                title = "Bubble Surface Color",
                                selectedColor = cfg.bgColor,
                                swatches = listOf(
                                    AndroidColor.BLACK,
                                    AndroidColor.parseColor("#111114"),
                                    AndroidColor.parseColor("#1C1C1E"),
                                    AndroidColor.parseColor("#240A10"),
                                    AndroidColor.parseColor("#0D1522"),
                                ),
                                onSelect = { c ->
                                    haptics.play(Haptic.Tap)
                                    DynamicIslandStore.update(context) { it.copy(bgColor = c) }
                                },
                            )
                            IslandInsetDivider()
                            IslandColorPaletteRow(
                                title = "Accent & Spectrum Color",
                                selectedColor = cfg.accentColor,
                                swatches = listOf(
                                    AndroidColor.WHITE,
                                    AndroidColor.rgb(250, 45, 72),
                                    AndroidColor.parseColor("#30D158"),
                                    AndroidColor.parseColor("#FF9F0A"),
                                    AndroidColor.parseColor("#BF5AF2"),
                                ),
                                onSelect = { c ->
                                    haptics.play(Haptic.Tap)
                                    DynamicIslandStore.update(context) { it.copy(accentColor = c, adaptiveAlbumTint = false) }
                                },
                            )
                        }
                    }
                }

                TopFadeBlur(
                    hazeState = hazeState,
                    pageColor = SettingsScreenBlack,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                FrostedTopBar(
                    transparentBackdrop = true,
                    artworkPageChrome = true,
                    onBack = {
                        haptics.play(Haptic.Tap)
                        onBack()
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                    centerContent = {
                        androidx.compose.animation.AnimatedVisibility(visible = isScrolled) {
                            FrostedTopBarTitlePill(hazeState = hazeState) {
                                Text(
                                    text = "Dynamic Island",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                haptics.play(Haptic.Tap)
                                DynamicIslandStore.resetGeometry(context, isLandscapeTab)
                                Toast.makeText(context, "Geometry reset", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_di_reset),
                                contentDescription = "Reset Geometry",
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )

                BottomFadeScrim(
                    withMiniPlayer = hasMiniPlayer,
                    pageColor = SettingsScreenBlack,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun InteractiveWaterBubblePreview(
    cfg: DynamicIslandConfig,
    widthDp: Int,
    heightDp: Int,
    offsetXDp: Int,
    offsetYDp: Int,
) {
    val activeTrack = MusicPlayerState.currentTrack
    val title = activeTrack?.title ?: "Starboy (feat. Daft Punk)"
    val artist = activeTrack?.artists ?: "The Weeknd"
    val artUrl = activeTrack?.thumbnailUrl

    var isExpanded by remember { mutableStateOf(false) }
    val morph = remember { Animatable(0f) }
    val ripple = remember { Animatable(0f) }

    LaunchedEffect(isExpanded, cfg.bubbleElasticity, cfg.waterDropEffect) {
        val damping = (0.86f - (cfg.bubbleElasticity - 0.6f) * 0.25f).coerceIn(0.55f, 0.88f)
        launch {
            morph.animateTo(
                targetValue = if (isExpanded) 1f else 0f,
                animationSpec = if (cfg.waterDropEffect) {
                    spring(dampingRatio = damping, stiffness = Spring.StiffnessMediumLow)
                } else {
                    tween(240)
                },
            )
        }
        if (cfg.waterDropEffect) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(580))
        }
    }

    val spinInf = rememberInfiniteTransition(label = "diPreviewSpin")
    val spinDeg by spinInf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "deg",
    )
    val eqPhase by spinInf.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "eqPhase",
    )

    val p = morph.value.coerceIn(0f, 1.15f)
    val elasticity = cfg.bubbleElasticity.coerceIn(0.6f, 1.4f)
    val stretchFactor = 1f + sin(p.coerceIn(0f, 1f) * PI).toFloat() * 0.055f * elasticity

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(0.6.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(12.dp)),
    ) {
        val density = LocalDensity.current
        val maxW = maxWidth
        val colW = widthDp.coerceIn(38, 280).dp
        val colH = heightDp.coerceIn(22, 56).dp
        val expW = (maxW - 18.dp).coerceAtMost(330.dp)
        val expH = 148.dp

        val curW = (colW.value + (expW.value - colW.value) * p.coerceIn(0f, 1f)).dp
        val curH = ((colH.value + (expH.value - colH.value) * p.coerceIn(0f, 1f)) * stretchFactor).dp
        val curR = (colH.value / 2f + (cfg.bubbleCurvatureDp.toFloat() - colH.value / 2f) * p.coerceIn(0f, 1f)).coerceAtLeast(10f).dp

        val clampedOffsetX = (offsetXDp.toFloat() * 0.55f).coerceIn(-110f, 110f)
        val clampedOffsetY = (offsetYDp.toFloat() * 0.65f + 6f).coerceIn(4f, 44f)
        val curX = (clampedOffsetX * (1f - p.coerceIn(0f, 1f)))
        val curY = clampedOffsetY

        // Concentric water-drop ripple canvas
        if (cfg.waterDropEffect && ripple.isRunning) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rp = ripple.value.coerceIn(0f, 1f)
                val cx = size.width / 2f + with(density) { curX.dp.toPx() }
                val cy = with(density) { (curY + curH.value * 0.45f).dp.toPx() }
                val r1 = with(density) { (18f + 135f * rp).dp.toPx() }
                val a1 = ((1f - rp) * (1f - rp) * 0.48f).coerceIn(0f, 1f)
                drawCircle(
                    color = Color.White.copy(alpha = a1),
                    radius = r1,
                    center = Offset(cx, cy),
                    style = Stroke(width = with(density) { (2.6f * (1f - rp) + 0.5f).dp.toPx() }),
                )
            }
        }

        // Camera punch-hole indicator behind top bezel
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(0xFF141416))
                .border(0.6.dp, Color.White.copy(alpha = 0.12f), CircleShape),
        )

        val accentColor = if (cfg.adaptiveAlbumTint) Color(0xFFFA2D48) else Color(cfg.accentColor)
        val bubbleBg = Color(cfg.bgColor)

        // Live Water-Bubble Island preview
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset {
                    IntOffset(
                        x = with(density) { curX.dp.roundToPx() },
                        y = with(density) { curY.dp.roundToPx() },
                    )
                }
                .size(width = curW, height = curH)
                .clip(RoundedCornerShape(curR))
                .background(bubbleBg)
                .then(
                    if (cfg.liquidSheen) {
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.14f),
                                    Color.Transparent,
                                    accentColor.copy(alpha = 0.08f * p.coerceIn(0f, 1f)),
                                ),
                            ),
                        )
                    } else {
                        Modifier
                    },
                )
                .border(0.75.dp, Color.White.copy(alpha = if (cfg.liquidSheen) 0.24f else 0.12f), RoundedCornerShape(curR))
                .clickable { isExpanded = !isExpanded },
        ) {
            if (p < 0.45f) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (curW <= 54.dp) 3.dp else 6.dp)
                        .graphicsLayer { alpha = (1f - p / 0.45f).coerceIn(0f, 1f) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (curW <= 54.dp) Arrangement.Center else Arrangement.SpaceBetween,
                ) {
                    val artSize = (curH - 8.dp).coerceIn(14.dp, 40.dp)
                    Box(
                        modifier = Modifier
                            .size(artSize)
                            .graphicsLayer { if (cfg.rotatingArtwork) rotationZ = spinDeg }
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C2E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!artUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = artUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_itune_waves),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(artSize * 0.56f),
                            )
                        }
                    }

                    if (curW > 54.dp) {
                        if (curW >= 150.dp) {
                            Text(
                                text = title,
                                color = Color(cfg.textColor),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp),
                            )
                        }
                        if (cfg.showVisualizer) {
                            Canvas(modifier = Modifier.size(width = 22.dp, height = (curH - 10.dp).coerceAtLeast(10.dp))) {
                                val bars = floatArrayOf(0.45f, 0.85f, 1f, 0.6f, 0.8f)
                                val gap = size.width / 5f
                                bars.forEachIndexed { idx, base ->
                                    val pulse = 0.45f + 0.55f * abs(sin(eqPhase + idx * 1.1f))
                                    val bh = size.height * base * pulse
                                    val x = gap * idx + gap / 2f
                                    drawLine(
                                        color = accentColor,
                                        start = Offset(x, size.height / 2f - bh / 2f),
                                        end = Offset(x, size.height / 2f + bh / 2f),
                                        strokeWidth = 2.dp.toPx(),
                                        cap = StrokeCap.Round,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .graphicsLayer { alpha = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f) },
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2C2C2E)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!artUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = artUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_itune_waves),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(Color.White),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = Color(cfg.textColor),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = artist,
                                color = Color(cfg.textColor).copy(alpha = 0.68f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (cfg.showVisualizer) {
                            Canvas(modifier = Modifier.size(width = 24.dp, height = 18.dp)) {
                                val bars = floatArrayOf(0.5f, 0.9f, 1f, 0.65f, 0.85f)
                                val gap = size.width / 5f
                                bars.forEachIndexed { idx, base ->
                                    val pulse = 0.45f + 0.55f * abs(sin(eqPhase + idx * 1.1f))
                                    val bh = size.height * base * pulse
                                    val x = gap * idx + gap / 2f
                                    drawLine(
                                        color = accentColor,
                                        start = Offset(x, size.height / 2f - bh / 2f),
                                        end = Offset(x, size.height / 2f + bh / 2f),
                                        strokeWidth = 2.dp.toPx(),
                                        cap = StrokeCap.Round,
                                    )
                                }
                            }
                        }
                    }

                    // Capsule or squiggly preview scrubber
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.42f)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(if (cfg.capsuleSeekStyle) Color(cfg.textColor) else accentColor),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(painterResource(R.drawable.shuffle), null, colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.85f)), modifier = Modifier.size(16.dp))
                        Image(painterResource(R.drawable.skip_previous), null, colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.size(22.dp))
                        Image(painterResource(R.drawable.pause), null, colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.size(22.dp))
                        Image(painterResource(R.drawable.skip_next), null, colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.size(22.dp))
                        Image(painterResource(R.drawable.repeat), null, colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.85f)), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Text(
            text = if (isExpanded) "Tap bubble to collapse" else "Tap bubble to test water-drop bloom",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        )
    }
}

@Composable
private fun IslandBlackSegmentedSwitcher(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF121214))
            .padding(2.5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { idx, label ->
                val selected = idx == selectedIndex
                val bg by animateColorAsState(
                    targetValue = if (selected) Color(0xFF38383A) else Color.Transparent,
                    animationSpec = tween(160),
                    label = "diSegBg",
                )
                val fg by animateColorAsState(
                    targetValue = if (selected) Color.White else AppleTextSecondary,
                    animationSpec = tween(160),
                    label = "diSegFg",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(7.dp))
                        .background(bg)
                        .clickable { onSelect(idx) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = fg,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun IslandSectionColumn(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.65.sp,
            modifier = Modifier.padding(start = 14.dp, bottom = 7.dp),
        )
        SettingsUniformGreyCard(cornerRadius = 14.dp) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun IslandInsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 54.dp),
        thickness = 0.5.dp,
        color = SettingsDividerGrey,
    )
}

@Composable
private fun IslandIconSlot(@DrawableRes iconRes: Int) {
    Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun IslandToggleRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IslandIconSlot(iconRes)
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(1.dp))
            Text(subtitle, color = AppleTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        AppleGlassSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun IslandActionRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    trailingValue: String,
    trailingAccent: Color = AppleTextSecondary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IslandIconSlot(iconRes)
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(1.dp))
            Text(subtitle, color = AppleTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = trailingValue,
            color = trailingAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun IslandSliderRow(
    @DrawableRes iconRes: Int,
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.01f)
    val fraction by animateFloatAsState(
        targetValue = ((value - valueRange.start) / span).coerceIn(0f, 1f),
        animationSpec = tween(60),
        label = "diSliderFrac",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IslandIconSlot(iconRes)
            Spacer(Modifier.width(13.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                color = AppleTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(8.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 39.dp)
                .height(24.dp)
                .pointerInput(valueRange) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val f = (offset.x / w).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + f * span)
                    }
                }
                .pointerInput(valueRange) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val f = (change.position.x / w).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + f * span)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(SettingsDividerGrey),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
            val thumbOffsetDp = (maxWidth - 16.dp) * fraction
            Box(
                modifier = Modifier
                    .offset(x = thumbOffsetDp.coerceAtLeast(0.dp))
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun IslandColorPaletteRow(
    title: String,
    selectedColor: Int,
    swatches: List<Int>,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IslandIconSlot(R.drawable.ic_di_colors)
            Spacer(Modifier.width(13.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 39.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            swatches.forEach { colorInt ->
                val isSelected = selectedColor == colorInt
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(colorInt))
                        .border(
                            width = if (isSelected) 2.2.dp else 0.8.dp,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.28f),
                            shape = CircleShape,
                        )
                        .clickable { onSelect(colorInt) },
                )
            }
        }
    }
}
