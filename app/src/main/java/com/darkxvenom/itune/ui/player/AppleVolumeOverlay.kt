package com.darkxvenom.itune.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.darkxvenom.itune.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Renders an Apple SF-style speaker icon that dynamically adapts to volume levels:
 * - 0% (mute): speaker with an "x"
 * - 1%..33%: speaker with 1 sound wave
 * - 34%..66%: speaker with 2 sound waves
 * - 67%..100%: speaker with 3 sound waves
 */
@Composable
fun AppleSpeakerIcon(
    volumeFraction: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val px = w / 24f

        // Speaker horn body
        val bodyPath = Path().apply {
            moveTo(3.5f * px, 9.5f * px)
            lineTo(7f * px, 9.5f * px)
            lineTo(11.5f * px, 5.5f * px)
            lineTo(11.5f * px, 18.5f * px)
            lineTo(7f * px, 14.5f * px)
            lineTo(3.5f * px, 14.5f * px)
            close()
        }
        drawPath(
            path = bodyPath,
            color = tint,
            style = Fill,
        )

        val strokeW = 1.8f * px
        val stroke = Stroke(
            width = strokeW,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )

        if (volumeFraction <= 0.001f) {
            // Mute "x"
            drawLine(
                color = tint,
                start = Offset(14.5f * px, 9.5f * px),
                end = Offset(19.5f * px, 14.5f * px),
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = tint,
                start = Offset(14.5f * px, 14.5f * px),
                end = Offset(19.5f * px, 9.5f * px),
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )
        } else {
            val cx = 9f * px
            val cy = 12f * px

            // Wave 1
            val r1 = 5f * px
            drawArc(
                color = tint,
                startAngle = -36f,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = Offset(cx - r1, cy - r1),
                size = Size(r1 * 2, r1 * 2),
                style = stroke,
            )

            // Wave 2
            if (volumeFraction > 0.33f) {
                val r2 = 8.5f * px
                drawArc(
                    color = tint,
                    startAngle = -38f,
                    sweepAngle = 76f,
                    useCenter = false,
                    topLeft = Offset(cx - r2, cy - r2),
                    size = Size(r2 * 2, r2 * 2),
                    style = stroke,
                )
            }

            // Wave 3
            if (volumeFraction > 0.66f) {
                val r3 = 12f * px
                drawArc(
                    color = tint,
                    startAngle = -40f,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = Offset(cx - r3, cy - r3),
                    size = Size(r3 * 2, r3 * 2),
                    style = stroke,
                )
            }
        }
    }
}

/**
 * 100% authentic Apple iOS volume capsule HUD anchored to the right screen edge.
 * Slides in with an organic spring animation from the right edge when hardware volume keys are pressed or volume is scrubbed,
 * features exact rounded rectangle corners (18.dp) and proportional height (~120.dp) matching Apple iOS / Sing slider,
 * inverted-contrast masking, direct vertical drag adjustment, and automatically hides after 3.5s.
 */
@Composable
fun AppleVolumeOverlay(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val isVisible by AppleVolumeManager.isVisible.collectAsStateWithLifecycle()
    val rawFraction by AppleVolumeManager.volumeFraction.collectAsStateWithLifecycle()
    val isDragging by AppleVolumeManager.isDragging.collectAsStateWithLifecycle()

    val animatedFraction by animateFloatAsState(
        targetValue = rawFraction,
        animationSpec = if (isDragging) {
            snap()
        } else {
            spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "appleVolumeFraction",
    )

    val capsuleWidth by animateDpAsState(
        targetValue = if (isDragging) 58.dp else 54.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium),
        label = "appleVolumeCapsuleWidth",
    )

    val isCoveredByFill = animatedFraction >= 0.22f
    val iconTint by animateColorAsState(
        targetValue = if (isCoveredByFill) Color(0xFF1E1E20) else Color.White,
        animationSpec = tween(
            durationMillis = 260,
            easing = FastOutSlowInEasing,
        ),
        label = "appleVolumeIconTint",
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInHorizontally(
                initialOffsetX = { it + 80 },
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 380f,
                ),
            ) + fadeIn(
                animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
            ) + scaleIn(
                initialScale = 0.88f,
                transformOrigin = TransformOrigin(1f, 0.5f),
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { it + 80 },
                animationSpec = spring(
                    dampingRatio = 0.90f,
                    stiffness = 450f,
                ),
            ) + fadeOut(
                animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing),
            ) + scaleOut(
                targetScale = 0.88f,
                transformOrigin = TransformOrigin(1f, 0.5f),
                animationSpec = tween(durationMillis = 220),
            ),
        ) {
            val capsuleHeight = 120.dp
            val cornerRadius = 18.dp

            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .offset(y = (-32).dp)
                    .width(capsuleWidth)
                    .height(capsuleHeight)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(cornerRadius),
                        ambientColor = Color.Black.copy(alpha = 0.6f),
                        spotColor = Color.Black.copy(alpha = 0.85f),
                    )
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color(0x55303032))
                    .background(Color.White.copy(alpha = 0.28f))
                    .border(
                        width = 0.75.dp,
                        color = Color.White.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(cornerRadius),
                    )
                    .pointerInput(context) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                AppleVolumeManager.onUserInteractionStart()
                                val frac = (1f - (offset.y / size.height.toFloat())).coerceIn(0f, 1f)
                                AppleVolumeManager.setVolumeFraction(context, frac)
                            },
                            onDragEnd = {
                                AppleVolumeManager.onUserInteractionEnd()
                            },
                            onDragCancel = {
                                AppleVolumeManager.onUserInteractionEnd()
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                AppleVolumeManager.onUserInteractionOngoing()
                                val frac = (1f - (change.position.y / size.height.toFloat())).coerceIn(0f, 1f)
                                if (frac <= 0.001f || frac >= 0.999f) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                AppleVolumeManager.setVolumeFraction(context, frac)
                            },
                        )
                    },
            ) {
                // Layer 1: Solid white fill from bottom up
                val fillHeightDp = capsuleHeight * animatedFraction.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(fillHeightDp)
                        .align(Alignment.BottomCenter)
                        .background(Color.White),
                )

                // Layer 2: Single icon on top with smooth color transition
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_magic_wand),
                        contentDescription = "Volume",
                        tint = iconTint,
                        modifier = Modifier
                            .padding(bottom = 14.dp)
                            .size(28.dp),
                    )
                }
            }
        }
    }
}
