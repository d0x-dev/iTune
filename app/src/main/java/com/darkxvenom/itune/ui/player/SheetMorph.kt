package com.darkxvenom.itune.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * 1:1 Gesture-driven Sheet Morph replicated directly from iTube / AirBeats.
 * Pinned precisely to the tip of the finger with zero latency, using GPU graphicsLayer.
 */
@Stable
class SheetMorph(
    private val scope: CoroutineScope,
    private val onSettled: (expanded: Boolean) -> Unit
) {
    val progress = Animatable(0f, visibilityThreshold = 0.0005f)

    var dragging by mutableStateOf(false)
        private set

    private var startedExpanded = false
    var travel: Float = 1800f

    /**
     * Applies raw vertical delta (in pixels).
     * Upward drag (deltaY < 0) expands sheet (increases progress towards 1.0f).
     * Downward drag (deltaY > 0) collapses sheet (decreases progress towards 0.0f).
     */
    fun dragBy(deltaY: Float): Float {
        if (!dragging) {
            dragging = true
            startedExpanded = progress.value >= 0.5f
        }
        val safeTravel = travel.coerceAtLeast(100f)
        val before = progress.value.coerceIn(0f, 1f)
        val after = (before - deltaY / safeTravel).coerceIn(0f, 1f)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            progress.snapTo(after)
        }
        return (before - after) * safeTravel
    }

    /**
     * Settle gesture release based on velocity and distance traveled.
     */
    fun settle(velocityY: Float) {
        if (!dragging) return
        dragging = false
        val p = progress.value
        val safeTravel = travel.coerceAtLeast(100f)

        val expand = when {
            // velocityY > 0 is downward fling -> collapse
            velocityY > FLING_PX_PER_S -> false
            // velocityY < 0 is upward fling -> expand
            velocityY < -FLING_PX_PER_S -> true
            startedExpanded -> p > 1f - DISMISS_FROM_FULL
            else -> p > EXPAND_FROM_MINI
        }

        val target = if (expand) 1f else 0f
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            progress.animateTo(
                targetValue = target,
                animationSpec = if (expand) settleSpec else closeSpec,
                initialVelocity = -velocityY / safeTravel
            )
        }
        onSettled(expand)
    }

    suspend fun animateTo(expanded: Boolean) {
        if (dragging) return
        val target = if (expanded) 1f else 0f
        if (progress.targetValue == target && (progress.isRunning || progress.value == target)) return
        progress.animateTo(
            targetValue = target,
            animationSpec = if (expanded) settleSpec else closeSpec
        )
    }

    companion object {
        const val FLING_PX_PER_S = 350f
        const val DISMISS_FROM_FULL = 0.15f
        const val EXPAND_FROM_MINI = 0.15f
        val settleSpec = spring<Float>(dampingRatio = 0.86f, stiffness = 650f, visibilityThreshold = 0.0005f)
        val closeSpec = spring<Float>(dampingRatio = 1f, stiffness = 650f, visibilityThreshold = 0.0005f)
    }
}

@Composable
fun rememberSheetMorph(
    onSettled: (expanded: Boolean) -> Unit
): SheetMorph {
    val scope = rememberCoroutineScope()
    return remember { SheetMorph(scope, onSettled) }
}

/**
 * Modifier to bind vertical touch drag gestures to the SheetMorph with 1:1 tip-of-finger tracking.
 */
fun Modifier.sheetMorphDrag(morph: SheetMorph): Modifier = this.pointerInput(morph) {
    val velocityTracker = VelocityTracker()
    detectVerticalDragGestures(
        onDragStart = {
            velocityTracker.resetTracking()
        },
        onVerticalDrag = { change, dragAmount ->
            velocityTracker.addPointerInputChange(change)
            morph.dragBy(dragAmount)
            change.consume()
        },
        onDragCancel = {
            velocityTracker.resetTracking()
            morph.settle(0f)
        },
        onDragEnd = {
            val velocityY = velocityTracker.calculateVelocity().y
            velocityTracker.resetTracking()
            morph.settle(velocityY)
        }
    )
}

/**
 * Modifier for FloatingMiniPlayer with 1:1 tip-of-finger physics:
 * - Upward drag: expands full player via SheetMorph
 * - Downward drag: moves mini player down and dismisses + stops song when released past threshold
 */
fun Modifier.miniPlayerGestures(
    morph: SheetMorph,
    dismissOffset: Animatable<Float, *>,
    scope: CoroutineScope,
    dismissThresholdPx: Float,
    dismissTargetPx: Float,
    onDismiss: () -> Unit
): Modifier = this.pointerInput(morph) {
    val velocityTracker = VelocityTracker()
    detectVerticalDragGestures(
        onDragStart = {
            velocityTracker.resetTracking()
        },
        onVerticalDrag = { change, dragAmount ->
            velocityTracker.addPointerInputChange(change)
            if (dragAmount > 0f) {
                // Dragging DOWN
                if (morph.progress.value > 0.001f) {
                    morph.dragBy(dragAmount)
                } else {
                    val newDismiss = dismissOffset.value + dragAmount
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        dismissOffset.snapTo(newDismiss)
                    }
                }
            } else if (dragAmount < 0f) {
                // Dragging UP
                if (dismissOffset.value > 0.001f) {
                    val newDismiss = (dismissOffset.value + dragAmount).coerceAtLeast(0f)
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        dismissOffset.snapTo(newDismiss)
                    }
                } else {
                    morph.dragBy(dragAmount)
                }
            }
            change.consume()
        },
        onDragCancel = {
            velocityTracker.resetTracking()
            if (dismissOffset.value > 0f) {
                scope.launch {
                    dismissOffset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 600f))
                }
            }
            morph.settle(0f)
        },
        onDragEnd = {
            val velocityY = velocityTracker.calculateVelocity().y
            velocityTracker.resetTracking()
            if (dismissOffset.value > 0.001f) {
                val shouldDismiss = velocityY > 350f || dismissOffset.value > dismissThresholdPx
                scope.launch {
                    if (shouldDismiss) {
                        dismissOffset.animateTo(
                            targetValue = dismissTargetPx,
                            animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing)
                        )
                        onDismiss()
                        dismissOffset.snapTo(0f)
                    } else {
                        dismissOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 600f)
                        )
                    }
                }
            } else {
                morph.settle(velocityY)
            }
        }
    )
}
