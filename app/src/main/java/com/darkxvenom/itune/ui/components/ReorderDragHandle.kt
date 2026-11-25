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

package com.darkxvenom.itune.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

/** A compact 3-line handle matching iOS Apple Music that reorders its item after a vertical drag. */
@Composable
fun ReorderDragHandle(
    index: Int,
    itemCount: Int,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    itemStep: Dp = 64.dp,
    tint: Color = Color.White.copy(alpha = 0.55f),
    contentDescription: String = "Reorder",
    onDragDistance: (Float) -> Unit = {},
    onDragFinished: () -> Unit = {},
) {
    val density = LocalDensity.current
    Canvas(
        modifier = modifier.pointerInput(index, itemCount, itemStep) {
            var dragDistance = 0f
            detectDragGestures(
                onDragStart = { dragDistance = 0f },
                onDragEnd = {
                    if (itemCount > 1) {
                        val stepPx = with(density) { itemStep.toPx() }.coerceAtLeast(1f)
                        val stepCount = (dragDistance / stepPx).roundToInt()
                        val targetIndex = (index + stepCount).coerceIn(0, itemCount - 1)
                        if (targetIndex != index) onMove(index, targetIndex)
                    }
                    onDragFinished()
                },
                onDragCancel = {
                    dragDistance = 0f
                    onDragFinished()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragDistance += dragAmount.y
                    onDragDistance(dragAmount.y)
                },
            )
        },
    ) {
        val barWidth = size.width * 0.72f
        val barHeight = 1.6.dp.toPx().coerceAtLeast(1f)
        val startX = (size.width - barWidth) / 2f
        val spacing = 4.2.dp.toPx()
        val centerY = size.height / 2f
        val cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
        for (i in -1..1) {
            val y = centerY + i * spacing - barHeight / 2f
            drawRoundRect(
                color = tint,
                topLeft = Offset(startX, y),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius,
            )
        }
    }
}
