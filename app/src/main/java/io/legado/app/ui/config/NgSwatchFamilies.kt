package io.legado.app.ui.config

import androidx.annotation.StringRes
import io.legado.app.R
import io.legado.app.ui.design.theme.NgColorMath
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Reading Color Palette System.
 * Spectrum stays an HSV rainbow plus a gray column.
 * Each reading family has its own page and text ramp, plus three accent columns:
 * theme accents, complement of the text, and triad. Accent cells are distinct hues,
 * fitted as text on the current background and ordered by distinction from the text.
 */
internal object NgSwatchFamilies {
    const val ROWS = 8
    const val COLS = 13
    /** First ten columns are the text and background fan. The last three are accent schemes. */
    const val FAN_COLUMNS = 10
    private const val HUE_COLUMNS = 12
    private const val TEXT_CONTRAST = 4.5
    private const val TICK_CONTRAST = 3.0
    const val SPECTRUM_ID = "spectrum"

    data class Family(
        val id: String,
        @StringRes val labelRes: Int,
        val targetHue: Float,
        val chromaScale: Float,
        val lightTop: Float = 0.96f,
        val lightBottom: Float = 0.16f,
        val accentChroma: Float = 0.08f,
        val accentHues: List<Float> = emptyList(),
    )

    val families: List<Family> = listOf(
        Family(SPECTRUM_ID, R.string.ng_picker_swatch_spectrum, 0f, 0f),
        Family(
            "natural", R.string.read_palette_natural, 48f, 0.032f,
            lightTop = 0.93f, lightBottom = 0.30f, accentChroma = 0.07f,
            accentHues = listOf(175f, 135f, 42f, 95f, 20f, 155f, 70f, 200f),
        ),
        Family(
            "paper", R.string.read_palette_paper, 62f, 0.020f,
            lightTop = 0.97f, lightBottom = 0.36f, accentChroma = 0.075f,
            accentHues = listOf(255f, 72f, 145f, 230f, 50f, 165f, 190f, 30f),
        ),
        Family(
            "ink", R.string.read_palette_ink, 240f, 0.008f,
            lightTop = 0.96f, lightBottom = 0.18f, accentChroma = 0.11f,
            accentHues = listOf(255f, 25f, 185f, 55f, 200f, 330f, 265f, 15f),
        ),
        Family(
            "mist", R.string.read_palette_mist, 215f, 0.016f,
            lightTop = 0.91f, lightBottom = 0.34f, accentChroma = 0.07f,
            accentHues = listOf(220f, 285f, 18f, 255f, 345f, 190f, 265f, 55f),
        ),
        Family(
            "forest", R.string.read_palette_forest, 128f, 0.028f,
            lightTop = 0.91f, lightBottom = 0.26f, accentChroma = 0.075f,
            accentHues = listOf(175f, 130f, 68f, 108f, 150f, 85f, 95f, 200f),
        ),
        Family(
            "midnight", R.string.read_palette_midnight, 248f, 0.012f,
            lightTop = 0.74f, lightBottom = 0.10f, accentChroma = 0.09f,
            accentHues = listOf(82f, 195f, 328f, 52f, 250f, 290f, 18f, 170f),
        ),
        Family(
            "sunset", R.string.read_palette_sunset, 36f, 0.034f,
            lightTop = 0.94f, lightBottom = 0.30f, accentChroma = 0.08f,
            accentHues = listOf(185f, 18f, 42f, 300f, 68f, 350f, 55f, 275f),
        ),
        Family(
            "aurora", R.string.read_palette_aurora, 220f, 0.015f,
            lightTop = 0.96f, lightBottom = 0.34f, accentChroma = 0.08f,
            accentHues = listOf(155f, 292f, 195f, 118f, 175f, 250f, 320f, 210f),
        ),
    )

    fun cells(id: String, alpha: Int = 255): IntArray {
        val family = families.firstOrNull { it.id == id } ?: families.first()
        return if (family.id == SPECTRUM_ID) spectrum(alpha) else fromSeed(family, alpha)
    }

    /**
     * Reading families only. Columns 0–9 stay the fixed fan.
     * The last three columns are accent text on [background]: theme accents,
     * complement and split-complement of the text, then triad and analogous hues.
     * Each column is eight distinct hues, not a lightness ramp. Spectrum is unchanged.
     */
    fun readingPalette(id: String, alpha: Int, foreground: Int, background: Int): IntArray {
        val base = cells(id, alpha)
        val family = families.firstOrNull { it.id == id } ?: return base
        if (family.id == SPECTRUM_ID) return base
        val reference = srgbHue(foreground) ?: family.targetHue
        writeAccent(
            base,
            FAN_COLUMNS,
            rankedAccents(family, foreground, background, alpha, themePool(family, reference), preferTheme = true),
        )
        writeAccent(
            base,
            FAN_COLUMNS + 1,
            rankedAccents(family, foreground, background, alpha, shifted(reference, COMPLEMENT_OFFSETS), preferTheme = false),
        )
        writeAccent(
            base,
            FAN_COLUMNS + 2,
            rankedAccents(family, foreground, background, alpha, shifted(reference, TRIAD_OFFSETS), preferTheme = false),
        )
        return base
    }

    private fun writeAccent(base: IntArray, column: Int, colors: IntArray) {
        colors.forEachIndexed { row, color ->
            base[row * COLS + column] = color
        }
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
        repeat(ROWS) { row ->
            val t = row.toFloat() / (ROWS - 1)
            val lightness = seed.lightTop + (seed.lightBottom - seed.lightTop) * t
            val midBoost = sin(Math.PI.toFloat() * t).coerceAtLeast(0.18f)
            repeat(FAN_COLUMNS) { column ->
                val hue = fgBgHue(seed, column)
                val chroma = fgBgChroma(seed, column, midBoost)
                out[row * COLS + column] = withAlpha(oklch(lightness, chroma, wrapHue(hue)), alpha)
            }
        }
        repeat(FAN_COLUMNS) { column ->
            val top = out[column]
            val bottomIndex = (ROWS - 1) * COLS + column
            if (NgColorMath.contrastRatio(out[bottomIndex], top) < TEXT_CONTRAST) {
                val hue = fgBgHue(seed, column)
                val chroma = fgBgChroma(seed, column, 0.18f)
                out[bottomIndex] = withAlpha(fitContrast(hue, chroma, 0.16f, top, darken = true), alpha)
            }
        }
        val lightBg = out[0]
        val darkFg = out[(ROWS - 1) * COLS]
        val schemes = listOf(
            seed.accentHues,
            shifted(seed.targetHue, COMPLEMENT_OFFSETS),
            shifted(seed.targetHue, TRIAD_OFFSETS),
        )
        schemes.forEachIndexed { offset, hues ->
            repeat(ROWS) { row ->
                val t = row.toFloat() / (ROWS - 1)
                val lightness = seed.lightTop + (seed.lightBottom - seed.lightTop) * t
                val darken = row >= ROWS / 2
                out[row * COLS + FAN_COLUMNS + offset] = withAlpha(
                    fitContrast(
                        hue = hues[row % hues.size],
                        chroma = seed.accentChroma,
                        startL = lightness,
                        against = if (darken) lightBg else darkFg,
                        darken = darken,
                    ),
                    alpha,
                )
            }
        }
        return out
    }

    private val COMPLEMENT_OFFSETS = listOf(180f, 150f, 210f, 165f, 195f, 135f, 225f, 168f)
    private val TRIAD_OFFSETS = listOf(120f, 240f, 30f, -30f, 105f, 135f, 255f, 75f)

    private fun shifted(reference: Float, offsets: List<Float>): List<Float> =
        offsets.map { wrapHue(reference + it) }

    private fun themePool(family: Family, reference: Float): List<Float> =
        family.accentHues + shifted(reference, COMPLEMENT_OFFSETS) + shifted(reference, TRIAD_OFFSETS)

    /** Narrow fan around the theme hue, so the page and text stay in one atmosphere. */
    private fun fgBgHue(family: Family, column: Int): Float =
        family.targetHue + (column - 4.5f) * (52f / 9f)

    private fun fgBgChroma(seed: Family, column: Int, midBoost: Float): Float =
        (seed.chromaScale * (1.4f + column / 9f) * (0.35f + 0.90f * midBoost))
            .coerceIn(0.004f, 0.09f)

    /**
     * Fit each hue as accent text on the page, then keep eight that differ from each other.
     * Passing 4.5:1 ranks first because accent is also read-aloud text.
     * 3:1 is the floor for a tick that is only a mark.
     * Theme columns prefer the family's accent hues. Order is usefulness, not hue angle.
     */
    private fun rankedAccents(
        family: Family,
        foreground: Int,
        background: Int,
        alpha: Int,
        hues: List<Float>,
        preferTheme: Boolean,
    ): IntArray {
        val muted = channelSpread(foreground) < 36 && channelSpread(background) < 48
        val amount = if (muted) min(family.accentChroma, 0.07f) else family.accentChroma
        val lightPage = NgColorMath.contrastRatio(0xFF000000.toInt(), background) >=
            NgColorMath.contrastRatio(0xFFFFFFFF.toInt(), background)
        val startL = if (lightPage) 0.42f else 0.74f
        val fitted = hues.map { hue ->
            val color = fitAccentOnPage(wrapHue(hue), amount, startL, background)
            AccentCandidate(color, wrapHue(hue), accentScore(color, wrapHue(hue), family, foreground, background, preferTheme))
        }
        val ordered = distinctAccents(fitted)
        return IntArray(ROWS) { row -> withAlpha(ordered[row].color, alpha) }
    }

    private data class AccentCandidate(val color: Int, val hue: Float, val score: Float)

    private fun accentScore(
        color: Int,
        hue: Float,
        family: Family,
        foreground: Int,
        background: Int,
        preferTheme: Boolean,
    ): Float {
        val contrast = NgColorMath.contrastRatio(color, background)
        val readable = when {
            contrast >= 7.0 -> 1.2f
            contrast >= TEXT_CONTRAST -> 1f
            contrast >= TICK_CONTRAST -> 0.35f
            else -> 0f
        }
        val nearest = family.accentHues.minOfOrNull { hueDistance(it, hue) } ?: 180f
        val theme = when {
            preferTheme && nearest < 24f -> 0.55f
            nearest < 24f -> 0.12f
            else -> 0f
        }
        val distinction = oklabDistance(color, foreground).toFloat().coerceAtMost(0.85f)
        return readable * 1.8f + distinction + theme
    }

    private fun distinctAccents(fitted: List<AccentCandidate>): List<AccentCandidate> {
        val ranked = fitted.sortedByDescending { it.score }
        val picked = ArrayList<AccentCandidate>(ROWS)
        for (item in ranked) {
            if (picked.size == ROWS) break
            if (picked.none { hueDistance(it.hue, item.hue) < 14f }) picked += item
        }
        for (item in ranked) {
            if (picked.size == ROWS) break
            if (picked.none { (it.color and 0xFFFFFF) == (item.color and 0xFFFFFF) }) picked += item
        }
        var index = 0
        while (picked.size < ROWS && ranked.isNotEmpty()) {
            picked += ranked[index % ranked.size]
            index++
        }
        return picked
    }

    /** Hue of a chromatic sRGB color, in degrees. Gray has no stable hue. */
    private fun srgbHue(color: Int): Float? {
        val red = (color shr 16 and 0xFF) / 255f
        val green = (color shr 8 and 0xFF) / 255f
        val blue = (color and 0xFF) / 255f
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        val delta = max - min
        if (delta < 0.06f) return null
        val sector = when (max) {
            red -> ((green - blue) / delta) % 6f
            green -> (blue - red) / delta + 2f
            else -> (red - green) / delta + 4f
        }
        return ((sector * 60f) % 360f + 360f) % 360f
    }

    private fun oklabDistance(first: Int, second: Int): Double {
        val left = oklab(first)
        val right = oklab(second)
        val dl = left[0] - right[0]
        val da = left[1] - right[1]
        val db = left[2] - right[2]
        return kotlin.math.sqrt(dl * dl + da * da + db * db)
    }

    private fun oklab(color: Int): DoubleArray {
        fun linear(channel: Int): Double {
            val encoded = channel / 255.0
            return if (encoded <= 0.04045) encoded / 12.92 else ((encoded + 0.055) / 1.055).pow(2.4)
        }
        val red = linear(color shr 16 and 0xFF)
        val green = linear(color shr 8 and 0xFF)
        val blue = linear(color and 0xFF)
        val l = kotlin.math.cbrt(0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue)
        val m = kotlin.math.cbrt(0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue)
        val s = kotlin.math.cbrt(0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private fun channelSpread(color: Int): Int {
        val red = color shr 16 and 0xFF
        val green = color shr 8 and 0xFF
        val blue = color and 0xFF
        return maxOf(red, green, blue) - minOf(red, green, blue)
    }

    /**
     * Move lightness until this accent, used as text, passes AA on the page.
     * Chroma stays put. A miss stays selectable.
     */
    private fun fitAccentOnPage(
        hue: Float,
        chroma: Float,
        startL: Float,
        background: Int,
    ): Int {
        fun at(lightness: Float, amount: Float): Pair<Int, Double> {
            val color = oklch(lightness.coerceIn(0.18f, 0.94f), amount, wrapHue(hue))
            return color to NgColorMath.contrastRatio(color, background)
        }
        val amount = chroma.coerceIn(0.02f, 0.13f)
        val probeUp = at((startL + 0.12f).coerceAtMost(0.94f), amount).second
        val probeDown = at((startL - 0.12f).coerceAtLeast(0.18f), amount).second
        val step = if (probeDown >= probeUp) -0.05f else 0.05f
        var lightness = startL
        var best = at(lightness, amount)
        for (ignored in 0 until 16) {
            val sample = at(lightness, amount)
            if (sample.second > best.second) best = sample
            if (sample.second >= TEXT_CONTRAST) return sample.first
            val next = lightness + step
            if (next !in 0.18f..0.94f) break
            lightness = next
        }
        return best.first
    }

    /**
     * Move lightness, then chroma, until sRGB relative-luminance contrast reaches AA.
     * Hue stays put. Dark rows are text-on-background; light rows are an accent surface under dark text.
     */
    private fun fitContrast(
        hue: Float,
        chroma: Float,
        startL: Float,
        against: Int,
        darken: Boolean,
    ): Int {
        var lightness = startL
        var amount = chroma
        var color = oklch(lightness, amount, wrapHue(hue))
        repeat(10) {
            if (NgColorMath.contrastRatio(color, against) >= TEXT_CONTRAST) return color
            lightness = if (darken) {
                (lightness - 0.035f).coerceAtLeast(0.16f)
            } else {
                (lightness + 0.025f).coerceAtMost(0.96f)
            }
            amount = (amount * 0.9f).coerceAtLeast(0.02f)
            color = oklch(lightness, amount, wrapHue(hue))
        }
        return color
    }

    private fun hueDistance(first: Float, second: Float): Float {
        val delta = abs(first - second) % 360f
        return min(delta, 360f - delta)
    }

    private fun wrapHue(hue: Float): Float = ((hue % 360f) + 360f) % 360f

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

internal fun readingCellIsValid(
    role: NgColorPreviewRole,
    cell: Int,
    foreground: Int,
    background: Int,
): Boolean = when (role) {
    NgColorPreviewRole.TEXT -> NgColorMath.contrastRatio(cell, background) >= 4.5
    NgColorPreviewRole.BACKGROUND -> NgColorMath.contrastRatio(foreground, cell) >= 4.5
    NgColorPreviewRole.ACCENT -> true
}

private fun hsvArgb(hue: Float, saturation: Float, value: Float, alpha: Int): Int {
    val color = NgColorPickerColors.hsvToColor(hue, saturation, value, alpha)
    return color
}
