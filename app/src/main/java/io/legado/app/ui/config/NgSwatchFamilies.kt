package io.legado.app.ui.config

import androidx.annotation.StringRes
import io.legado.app.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Grid families: spectrum, then the reading-paper seeds (自然, 纸张, …). */
internal object NgSwatchFamilies {
    const val ROWS = 8
    const val COLS = 13
    private const val HUE_COLUMNS = 12
    const val SPECTRUM_ID = "spectrum"

    data class Family(
        val id: String,
        @StringRes val labelRes: Int,
        val targetHue: Float,
        val chromaScale: Float,
    )

    val families: List<Family> = listOf(
        Family(SPECTRUM_ID, R.string.ng_picker_swatch_spectrum, 0f, 0f),
        Family("natural", R.string.read_palette_natural, 75f, 0.015f),
        Family("paper", R.string.read_palette_paper, 65f, 0.03f),
        Family("ink", R.string.read_palette_ink, 60f, 0.005f),
        Family("mist", R.string.read_palette_mist, 220f, 0.01f),
        Family("forest", R.string.read_palette_forest, 135f, 0.022f),
        Family("midnight", R.string.read_palette_midnight, 240f, 0.018f),
    )

    fun cells(id: String, alpha: Int = 255): IntArray {
        val family = families.firstOrNull { it.id == id } ?: families.first()
        return if (family.id == SPECTRUM_ID) spectrum(alpha) else fromSeed(family, alpha)
    }

    private fun spectrum(alpha: Int): IntArray {
        val out = IntArray(ROWS * COLS)
        var index = 0
        repeat(ROWS) { row ->
            repeat(COLS) { column ->
                out[index++] = spectrumCell(row, column, alpha)
            }
        }
        return out
    }

    private fun spectrumCell(row: Int, column: Int, alpha: Int): Int {
        if (column == HUE_COLUMNS) {
            val channel = ((1f - row.toFloat() / (ROWS - 1)) * 255).roundToInt().coerceIn(0, 255)
            return packArgb(alpha, channel, channel, channel)
        }
        val hue = column * (360f / HUE_COLUMNS)
        val saturation = when (row) {
            0 -> 0.12f
            1 -> 0.36f
            2 -> 0.62f
            else -> 0.92f
        }
        val value = when (row) {
            0, 1, 2 -> 1f
            else -> 1f - (row - 2) * 0.145f
        }.coerceAtLeast(0.20f)
        return hsvArgb(hue, saturation, value, alpha)
    }

    private fun fromSeed(seed: Family, alpha: Int): IntArray {
        val out = IntArray(ROWS * COLS)
        var index = 0
        repeat(ROWS) { row ->
            val t = row.toFloat() / (ROWS - 1)
            val lightness = 0.96f - t * 0.80f
            val midBoost = sin(Math.PI.toFloat() * t).coerceAtLeast(0.18f)
            repeat(COLS) { column ->
                out[index++] = if (column == HUE_COLUMNS) {
                    withAlpha(oklch(lightness, 0f, seed.targetHue), alpha)
                } else {
                    val hue = seed.targetHue + (column - 5.5f) * 8f
                    val chroma = (seed.chromaScale * (1.4f + column / 11f) * (0.35f + 0.90f * midBoost))
                        .coerceIn(0.004f, 0.09f)
                    withAlpha(oklch(lightness, chroma, ((hue % 360f) + 360f) % 360f), alpha)
                }
            }
        }
        return out
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}

internal fun oklch(l: Float, c: Float, hDeg: Float): Int {
    val h = Math.toRadians(hDeg.toDouble())
    val a = c.toDouble() * cos(h)
    val b = c.toDouble() * sin(h)
    val l_ = l + 0.3963377774 * a + 0.2158037573 * b
    val m_ = l - 0.1055613458 * a - 0.0638541728 * b
    val s_ = l - 0.0894841775 * a - 1.2914855480 * b
    val l3 = l_ * l_ * l_
    val m3 = m_ * m_ * m_
    val s3 = s_ * s_ * s_
    val rLin = +4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3
    val gLin = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3
    val bLin = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3
    return packArgb(255, srgbChannel(rLin), srgbChannel(gLin), srgbChannel(bLin))
}

internal fun packArgb(alpha: Int, red: Int, green: Int, blue: Int): Int =
    (alpha.coerceIn(0, 255) shl 24) or
        (red.coerceIn(0, 255) shl 16) or
        (green.coerceIn(0, 255) shl 8) or
        blue.coerceIn(0, 255)

private fun srgbChannel(linear: Double): Int {
    val clamped = linear.coerceIn(0.0, 1.0)
    val encoded = if (clamped <= 0.0031308) 12.92 * clamped else 1.055 * clamped.pow(1.0 / 2.4) - 0.055
    return (encoded * 255.0).roundToInt().coerceIn(0, 255)
}

private fun hsvArgb(hue: Float, saturation: Float, value: Float, alpha: Int): Int {
    val color = NgColorPickerColors.hsvToColor(hue, saturation, value, alpha)
    return color
}
