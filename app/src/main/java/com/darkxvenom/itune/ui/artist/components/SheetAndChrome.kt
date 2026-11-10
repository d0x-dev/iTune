package com.darkxvenom.itune.ui.artist.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity

val LocalDisableBlendEffects = staticCompositionLocalOf { false }

fun Modifier.additiveChrome(enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        drawWithCache {
            val layer = obtainGraphicsLayer().apply { blendMode = BlendMode.Plus }
            onDrawWithContent {
                layer.record { this@onDrawWithContent.drawContent() }
                drawLayer(layer)
            }
        }
    }

fun Modifier.containSheetGestures(): Modifier =
    nestedScroll(SwallowNestedScroll).pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                ?: return@awaitEachGesture
            drag(start.id) { it.consume() }
        }
    }

private object SwallowNestedScroll : NestedScrollConnection {
    override fun onPostScroll(consumed: OffsetOrZero, available: OffsetOrZero, source: NestedScrollSource) = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
}

private typealias OffsetOrZero = androidx.compose.ui.geometry.Offset
