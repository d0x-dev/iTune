package com.darkxvenom.itune.ui.artist.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkxvenom.itune.R
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun stableStatusBarInsets(): WindowInsets = WindowInsets.statusBarsIgnoringVisibility

private val GESTURE_BAR_FLOOR = 15.dp

internal val floatingBarInsets: WindowInsets
    @Composable get() = WindowInsets.navigationBars.union(WindowInsets(bottom = GESTURE_BAR_FLOOR))

val TopBarContentHeight = 52.dp
val TopBarContentGap = 12.dp

@Composable
fun topBarHeight(): Dp =
    stableStatusBarInsets().asPaddingValues().calculateTopPadding() + TopBarContentHeight

@Composable
fun topBarContentPadding(): Dp = topBarHeight() + TopBarContentGap

private val FADE_RUN = 16.dp
private val BLUR_MAX = 24.dp
private const val FOOT_FADE_START = 0.6f
private const val FOOT_FADE_STOPS = 8

private val FootFade = Brush.verticalGradient(
    colorStops = Array(FOOT_FADE_STOPS + 1) { i ->
        val t = i / FOOT_FADE_STOPS.toFloat()
        val at = FOOT_FADE_START + (1f - FOOT_FADE_START) * t
        at to Color.Black.copy(alpha = 1f - EaseInOut.transform(t))
    },
)

@OptIn(ExperimentalHazeApi::class)
@Composable
fun TopFadeBlur(
    hazeState: HazeState,
    pageColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topBarHeight() + FADE_RUN)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(FootFade, blendMode = BlendMode.DstIn)
            }
            .hazeEffect(state = hazeState) {
                backgroundColor = pageColor
                tints = emptyList()
                blurRadius = BLUR_MAX
                noiseFactor = 0f
                inputScale = HazeInputScale.Fixed(0.33f)
                progressive = HazeProgressive.verticalGradient(
                    easing = EaseInOut,
                    startIntensity = 1f,
                    endIntensity = 0f,
                )
            },
    )
}

private val BOTTOM_FADE_HEIGHT = 180.dp
private val BOTTOM_FADE_HEIGHT_WITH_MINI = 254.dp
private const val BOTTOM_STOPS = 16

@Composable
fun BottomFadeScrim(
    modifier: Modifier = Modifier,
    withMiniPlayer: Boolean = false,
    pageColor: Color = MaterialTheme.colorScheme.background,
) {
    val inset = floatingBarInsets.asPaddingValues().calculateBottomPadding()
    val height by animateDpAsState(
        targetValue = inset + if (withMiniPlayer) BOTTOM_FADE_HEIGHT_WITH_MINI else BOTTOM_FADE_HEIGHT,
        animationSpec = tween(220),
        label = "bottomScrimHeight",
    )

    val brush = remember(pageColor) {
        Brush.verticalGradient(
            colorStops = Array(BOTTOM_STOPS) { i ->
                val t = i / (BOTTOM_STOPS - 1f)
                t to pageColor.copy(alpha = EaseInCubic.transform(t))
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(brush),
    )
}

@Composable
fun FrostedTopBar(
    modifier: Modifier = Modifier,
    transparentBackdrop: Boolean = true,
    artworkPageChrome: Boolean = true,
    backButtonHazeState: HazeState? = null,
    onBack: (() -> Unit)? = null,
    centerContent: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    val reduceDynamicBlur = LocalReduceDynamicBlur.current
    val useFloatingChrome = artworkPageChrome || (LocalLiquidGlassEnabled.current && isGlassSupported())
    val dividerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.outline.copy(
            alpha = if (transparentBackdrop) 0f else 0.6f,
        ),
        animationSpec = tween(220),
        label = "topBarDivider",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (reduceDynamicBlur && !transparentBackdrop) {
                    Modifier.background(MaterialTheme.colorScheme.surface)
                } else Modifier,
            ),
    ) {
        val edge = if (useFloatingChrome) BAR_GUTTER else 4.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(stableStatusBarInsets())
                .height(TopBarContentHeight)
                .padding(horizontal = edge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                if (useFloatingChrome) {
                    ArtworkPageBackButton(
                        onClick = onBack,
                        hazeState = backButtonHazeState,
                    )
                } else {
                    IconButton(onClick = onBack) {
                        Icon(
                            SfIcons.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                centerContent()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (useFloatingChrome) {
                    ArtworkPageActions(hazeState = backButtonHazeState, content = actions)
                } else {
                    actions()
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
    }
}

@Composable
private fun ArtworkPageBackButton(
    onClick: () -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val reduceDynamicBlur = LocalReduceDynamicBlur.current
    val useLiquidGlass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val contentColor = if (useLiquidGlass && !reduceDynamicBlur) {
        glassContentColor()
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(TOP_CONTROL_SIZE)
            .then(artworkPageSurface(shape = shape, hazeState = hazeState)),
    ) {
        Icon(
            SfIcons.ArrowBack,
            contentDescription = stringResource(R.string.back),
            tint = contentColor,
        )
    }
}

@Composable
private fun ArtworkPageActions(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ActionsRow(
        modifier = modifier
            .then(artworkPageSurface(shape = CircleShape, hazeState = hazeState))
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            .artworkActionEdgePadding(),
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides TOP_CONTROL_SIZE) {
            content()
        }
    }
}

@Composable
private fun ActionsRow(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val target = TOP_CONTROL_SIZE.roundToPx()
        val gap = ACTION_GAP.toPx()
        val gaps = placeables.mapIndexed { index, placeable ->
            if (index == 0) 0 else (gap * (placeable.width.toFloat() / target).coerceIn(0f, 1f)).roundToInt()
        }
        val width = placeables.sumOf { it.width } + gaps.sum()
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            var x = 0
            placeables.forEachIndexed { index, placeable ->
                x += gaps[index]
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width
            }
        }
    }
}

private fun Modifier.artworkActionEdgePadding(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0))
    val oneActionWidth = TOP_CONTROL_SIZE.roundToPx()
    val extraActionFraction = ((placeable.width - oneActionWidth).toFloat() / oneActionWidth)
        .coerceIn(0f, 1f)
    val edgePadding = (ACTION_EDGE_INSET.roundToPx() * extraActionFraction).roundToInt()
    val width = (placeable.width + edgePadding * 2).coerceIn(constraints.minWidth, constraints.maxWidth)

    layout(width, placeable.height) {
        placeable.placeRelative(edgePadding, 0)
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun artworkPageSurface(
    shape: CornerBasedShape,
    hazeState: HazeState?,
): Modifier {
    val container = MaterialTheme.colorScheme.surface
    val reduceDynamicBlur = LocalReduceDynamicBlur.current
    val useLiquidGlass = LocalLiquidGlassEnabled.current && isGlassSupported()

    return Modifier
        .clip(shape)
        .then(
            when {
                useLiquidGlass -> Modifier.liquidGlass(shape)
                reduceDynamicBlur || hazeState == null -> Modifier.background(container)
                else -> Modifier.optimizedHazeEffect(
                    state = hazeState,
                    style = HazeMaterials.regular(container),
                )
            },
        )
        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
}

private val TOP_CONTROL_SIZE = 44.dp
private val ACTION_GAP = 10.dp
private val ACTION_EDGE_INSET = 1.5.dp
