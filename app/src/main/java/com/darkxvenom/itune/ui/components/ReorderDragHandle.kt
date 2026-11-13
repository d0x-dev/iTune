package com.darkxvenom.itune.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A compact handle that reorders its item after a vertical drag. */
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
    Icon(
        imageVector = Icons.Rounded.DragHandle,
        contentDescription = contentDescription,
        tint = tint,
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
    )
}
