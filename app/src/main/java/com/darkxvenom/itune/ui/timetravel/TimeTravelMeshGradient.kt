package com.darkxvenom.itune.ui.timetravel

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

val FallbackMeshColors = listOf(
    Color(0xFF3A1C71),
    Color(0xFFD76D77),
    Color(0xFF2B5876),
    Color(0xFFFFAF7B),
)

/** The four mesh colours, wrapped so the backdrop can skip recomposition. */
@Immutable
data class MeshPalette(val colors: List<Color>)

/**
 * The Apple Music / BitChord backdrop: four luminous colour blobs sampled
 * from album art, drawn as soft radial gradients and blurred into a mesh.
 */
@Composable
fun MeshGradientBackground(
    palette: MeshPalette,
    modifier: Modifier = Modifier,
    trackKey: Any? = null,
    driftMillis: Int = 8_000,
    continuous: Boolean = false,
    blurRadius: Dp = 64.dp,
    animated: Boolean = true,
    colorFadeMillis: Int = 1400,
    paused: Boolean = false,
) {
    val tuned = (palette.colors.ifEmpty { FallbackMeshColors } + FallbackMeshColors)
        .take(4)
        .map { it.tuned() }

    val colorSpec: AnimationSpec<Color> = if (!animated) snap() else tween(colorFadeMillis)
    val animatedColors = tuned.mapIndexed { index, color ->
        animateColorAsState(color, colorSpec, label = "meshColor$index").value
    }
    val baseColor by animateColorAsState(tuned.first().dimmed(), colorSpec, label = "meshBase")

    val phase = remember { Animatable(0f) }
    LaunchedEffect(trackKey, continuous, animated, paused) {
        when {
            !animated -> phase.snapTo(0f)
            paused -> Unit
            continuous -> while (isActive) {
                phase.animateTo(
                    targetValue = phase.value + (2 * PI).toFloat(),
                    animationSpec = tween(driftMillis * 4, easing = LinearEasing),
                )
            }
            else -> phase.animateTo(
                targetValue = phase.value + (PI * 0.45f).toFloat(),
                animationSpec = tween(driftMillis, easing = FastOutSlowInEasing),
            )
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                scaleX = 1.3f
                scaleY = 1.3f
            }
            .background(baseColor)
            .blur(blurRadius),
    ) {
        val anchors = listOf(
            Offset(0.20f, 0.25f),
            Offset(0.80f, 0.20f),
            Offset(0.75f, 0.80f),
            Offset(0.25f, 0.75f),
        )
        val speeds = listOf(1f, -0.7f, 0.85f, -1.15f)
        val drift = phase.value

        animatedColors.forEachIndexed { index, color ->
            val anchor = anchors[index]
            val center = Offset(
                x = (anchor.x + 0.16f * cos(drift * speeds[index] + index * 1.7f)) * size.width,
                y = (anchor.y + 0.16f * sin(drift * speeds[index] * 0.9f + index * 2.3f)) * size.height,
            )
            val radius = size.maxDimension * 0.62f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = Array(FADE_CURVE_STOPS + 1) { i ->
                        val t = i / FADE_CURVE_STOPS.toFloat()
                        t to color.copy(alpha = 0.85f * (1f - smootherStep(t)))
                    },
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.10f),
                    Color.Black.copy(alpha = 0.38f),
                ),
            ),
        )
    }
}

/**
 * Loads artwork with Coil and pulls a 4-colour palette out of it.
 */
@Composable
fun rememberArtworkColors(imageUrl: String?): MeshPalette {
    val context = LocalContext.current
    var palette by remember(imageUrl) { mutableStateOf(MeshPalette(FallbackMeshColors)) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(128)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.drawable.toBitmap(128, 128, Bitmap.Config.ARGB_8888)
                    val extracted = paletteFromBitmap(bitmap)
                    withContext(Dispatchers.Main) {
                        palette = MeshPalette(extracted)
                    }
                }
            }
        }
    }
    return palette
}

private fun paletteFromBitmap(bitmap: Bitmap): List<Color> {
    val p = Palette.from(bitmap).maximumColorCount(24).generate()
    val swatches = p.swatches.sortedByDescending { it.population }.map { Color(it.rgb) }
    val distinct = mutableListOf<Color>()
    swatches.forEach { color ->
        if (distinct.none { it.isCloseTo(color) }) distinct += color
    }
    return when {
        distinct.isEmpty() -> FallbackMeshColors
        distinct.size >= 4 -> distinct.take(4)
        else -> {
            val out = distinct.toMutableList()
            var step = 1
            while (out.size < 4) {
                out += distinct[(out.size - distinct.size) % distinct.size].shifted(24f * step, 0.12f * step)
                step++
            }
            out
        }
    }
}

fun Color.isCloseTo(other: Color): Boolean {
    val a = hsl()
    val b = other.hsl()
    val hueGap = abs(a[0] - b[0]).let { min(it, 360f - it) }
    return hueGap < 15f && abs(a[2] - b[2]) < 0.12f
}

fun Color.shifted(hue: Float, lightness: Float): Color {
    val h = hsl()
    h[0] = (h[0] + hue) % 360f
    h[2] = (h[2] + lightness).coerceIn(0.2f, 0.7f)
    return Color(ColorUtilsHsl.HSLToColor(h))
}

fun Color.hsl(): FloatArray =
    FloatArray(3).also { ColorUtilsHsl.colorToHSL(toArgb(), it) }

fun Color.tuned(): Color {
    val h = FloatArray(3)
    ColorUtilsHsl.colorToHSL(toArgb(), h)
    h[1] = (h[1] * 1.35f).coerceAtMost(1f)
    h[2] = h[2].coerceIn(0.28f, 0.58f)
    return Color(ColorUtilsHsl.HSLToColor(h))
}

fun Color.dimmed(): Color {
    val h = FloatArray(3)
    ColorUtilsHsl.colorToHSL(toArgb(), h)
    h[2] = 0.12f
    return Color(ColorUtilsHsl.HSLToColor(h))
}

fun smootherStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

const val FADE_CURVE_STOPS = 16

object ColorUtilsHsl {
    fun colorToHSL(color: Int, outHsl: FloatArray) {
        val rf = (color shr 16) and 0xFF
        val gf = (color shr 8) and 0xFF
        val bf = color and 0xFF
        val r = rf / 255f
        val g = gf / 255f
        val b = bf / 255f
        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        val delta = max - min
        val l = (max + min) / 2f
        var h = 0f
        val s = if (max == min) 0f else {
            h = when (max) {
                r -> ((g - b) / delta) % 6f
                g -> ((b - r) / delta) + 2f
                else -> ((r - g) / delta) + 4f
            }
            delta / (1f - abs(2f * l - 1f))
        }
        h = (h * 60f) % 360f
        if (h < 0) h += 360f
        outHsl[0] = h.coerceIn(0f, 360f)
        outHsl[1] = s.coerceIn(0f, 1f)
        outHsl[2] = l.coerceIn(0f, 1f)
    }

    fun HSLToColor(hsl: FloatArray): Int {
        val h = hsl[0]
        val s = hsl[1]
        val l = hsl[2]
        val c = (1f - abs(2 * l - 1f)) * s
        val m = l - 0.5f * c
        val x = c * (1f - abs((h / 60f % 2f) - 1f))
        val seg = h.toInt() / 60
        var r = 0; var g = 0; var b = 0
        when (seg) {
            0 -> { r = (255 * (c + m)).roundToInt(); g = (255 * (x + m)).roundToInt(); b = (255 * m).roundToInt() }
            1 -> { r = (255 * (x + m)).roundToInt(); g = (255 * (c + m)).roundToInt(); b = (255 * m).roundToInt() }
            2 -> { r = (255 * m).roundToInt(); g = (255 * (c + m)).roundToInt(); b = (255 * (x + m)).roundToInt() }
            3 -> { r = (255 * m).roundToInt(); g = (255 * (x + m)).roundToInt(); b = (255 * (c + m)).roundToInt() }
            4 -> { r = (255 * (x + m)).roundToInt(); g = (255 * m).roundToInt(); b = (255 * (c + m)).roundToInt() }
            else -> { r = (255 * (c + m)).roundToInt(); g = (255 * m).roundToInt(); b = (255 * (x + m)).roundToInt() }
        }
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
