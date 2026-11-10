package com.darkxvenom.itune.ui.artist.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Stable
class HeldItem internal constructor(
    val bounds: Rect,
) {
    var image: ImageBitmap? by mutableStateOf(null)
        internal set
}

object LongPressOrigin {
    private var held: HeldItem? = null
    private var recordedAt = 0L

    internal var capturing: Rect? by mutableStateOf(null)

    internal fun record(item: HeldItem) {
        held = item
        recordedAt = System.nanoTime()
    }

    fun consume(): HeldItem? {
        val item = held?.takeIf { System.nanoTime() - recordedAt < FRESH_NANOS }
        held = null
        return item
    }

    private const val FRESH_NANOS = 500_000_000L
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.longPressMenuClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    enabled: Boolean = true,
    liftPadding: PaddingValues = NoLiftPadding,
): Modifier {
    val capture = remember { HoldCapture() }
    val graphicsContext = LocalGraphicsContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val longClick = onLongClick?.let { held ->
        {
            capture.coordinates?.takeIf { it.isAttached }?.let { coordinates ->
                val whole = Rect(coordinates.positionInWindow(), coordinates.size.toSize())
                with(density) {
                    capture.trimLeft = liftPadding.calculateLeftPadding(layoutDirection).toPx()
                    capture.trimRight = liftPadding.calculateRightPadding(layoutDirection).toPx()
                }
                val item = HeldItem(
                    Rect(whole.left + capture.trimLeft, whole.top, whole.right - capture.trimRight, whole.bottom),
                )
                LongPressOrigin.record(item)
                LongPressOrigin.capturing = item.bounds
                capture.pending = item
                capture.requests++
            }
            held()
        }
    }
    return this
        .onPlaced { capture.coordinates = it }
        .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = longClick)
        .drawWithContent {
            drawContent()
            val request = capture.requests
            if (request == capture.handled) return@drawWithContent
            capture.handled = request
            val item = capture.pending ?: return@drawWithContent
            capture.pending = null
            val layer = graphicsContext.createGraphicsLayer()
            val trimLeft = capture.trimLeft
            val width = (size.width - trimLeft - capture.trimRight).roundToInt().coerceAtLeast(1)
            layer.record(size = IntSize(width, size.height.roundToInt())) {
                translate(left = -trimLeft) { this@drawWithContent.drawContent() }
            }
            scope.launch {
                try {
                    item.image = layer.toImageBitmap()
                } finally {
                    graphicsContext.releaseGraphicsLayer(layer)
                    if (LongPressOrigin.capturing == item.bounds) LongPressOrigin.capturing = null
                }
            }
        }
}

private class HoldCapture {
    var coordinates: LayoutCoordinates? = null
    var pending: HeldItem? = null
    var trimLeft = 0f
    var trimRight = 0f
    var requests by mutableIntStateOf(0)
    var handled = 0
}

private val NoLiftPadding = PaddingValues()

object MenuAnchor {
    private var bounds: Rect? = null
    private var recordedAt = 0L

    internal fun record(bounds: Rect) {
        this.bounds = bounds
        recordedAt = System.nanoTime()
    }

    fun consume(): Rect? {
        val anchor = bounds?.takeIf { System.nanoTime() - recordedAt < FRESH_NANOS }
        bounds = null
        return anchor
    }

    private const val FRESH_NANOS = 3_000_000_000L
}

@Composable
fun Modifier.menuAnchor(): Modifier {
    val anchor = remember { AnchorCoordinates() }
    return this
        .onPlaced { anchor.coordinates = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val down = event.changes.firstOrNull { it.pressed && !it.previousPressed }
                    if (down != null) {
                        anchor.coordinates?.takeIf { it.isAttached }?.let { MenuAnchor.record(it.boundsInWindow()) }
                    }
                }
            }
        }
}

private class AnchorCoordinates {
    var coordinates: LayoutCoordinates? = null
}

@Composable
fun Modifier.leftOutOfHeldPicture(): Modifier {
    val position = remember { HeldPosition() }
    return this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val capturing = LongPressOrigin.capturing
            if (capturing != null && position.center?.let(capturing::contains) == true) {
                layout(0, placeable.height) {}
            } else {
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
        }
        .onPlaced { position.center = it.boundsInWindow().center }
}

private class HeldPosition {
    var center: Offset? = null
}
