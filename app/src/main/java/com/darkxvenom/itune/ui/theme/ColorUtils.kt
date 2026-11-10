package com.darkxvenom.itune.ui.theme

import android.graphics.Color
import androidx.annotation.ColorInt
import kotlin.math.*

/** Minimal port of AndroidX ColorUtils for the artwork palette system. */
object ColorUtils {

    fun colorToHSL(@ColorInt color: Int, outHsl: FloatArray) {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        outHsl[2] = (max + min) / 2f
        if (delta == 0f) {
            outHsl[0] = 0f
            outHsl[1] = 0f
        } else {
            outHsl[1] = delta / (1f - abs(2f * outHsl[2] - 1f))
            outHsl[0] = when (max) {
                r -> ((g - b) / delta % 6f + 6f) % 6f * 60f
                g -> ((b - r) / delta + 2f) * 60f
                else -> ((r - g) / delta + 4f) * 60f
            }
        }
    }

    @ColorInt
    fun HSLToColor(hsl: FloatArray): Int {
        val h = hsl[0]; val s = hsl[1]; val l = hsl[2]
        val c = (1f - abs(2f * l - 1f)) * s
        val m = l - c / 2f
        val x = c * (1f - abs(h / 60f % 2f - 1f))
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color.rgb(
            ((r + m) * 255f).roundToInt().coerceIn(0, 255),
            ((g + m) * 255f).roundToInt().coerceIn(0, 255),
            ((b + m) * 255f).roundToInt().coerceIn(0, 255),
        )
    }

    fun calculateLuminance(@ColorInt color: Int): Double {
        val r = sRGBToLinear(Color.red(color) / 255.0)
        val g = sRGBToLinear(Color.green(color) / 255.0)
        val b = sRGBToLinear(Color.blue(color) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun sRGBToLinear(v: Double): Double =
        if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

    /** k-means palette quantiser: returns up to [maxColors] dominant swatches. */
    fun paletteSwatches(pixels: IntArray, maxColors: Int = 6): List<Int> {
        if (pixels.isEmpty()) return emptyList()
        // Build a simplified 4-bit (16^3) color histogram
        val hist = HashMap<Int, Int>()
        for (px in pixels) {
            val a = (px ushr 24) and 0xFF
            if (a < 128) continue
            val key = ((px ushr 20) and 0xF00) or ((px ushr 12) and 0xF0) or ((px ushr 4) and 0xF)
            hist[key] = (hist[key] ?: 0) + 1
        }
        return hist.entries
            .sortedByDescending { it.value }
            .take(maxColors)
            .map { (k, _) ->
                val r = ((k ushr 8) and 0xF) * 17
                val g = ((k ushr 4) and 0xF) * 17
                val b = (k and 0xF) * 17
                (0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
    }
}
