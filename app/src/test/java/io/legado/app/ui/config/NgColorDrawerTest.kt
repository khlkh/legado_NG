package io.legado.app.ui.config

import io.legado.app.ui.design.theme.NgColorMath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NgColorDrawerTest {

    @Test
    fun portraitCapIsHalfAndLandscapeCapIsSeventyPercent() {
        assertEquals(0.5f, colorDrawerHeightFraction(portrait = true))
        assertEquals(0.7f, colorDrawerHeightFraction(portrait = false))
    }

    @Test
    fun drawerShrinksToContentWhenTheGraphicFits() {
        val plan = measureColorDrawer(
            capPx = 800,
            headerPx = 80,
            naturalBodyPx = 400,
            preferredVisualPx = 168,
            minVisualPx = 96,
            graphic = true,
        )
        assertFalse(plan.scroll)
        assertEquals(480, plan.heightPx)
        assertEquals(168, plan.visualPx)
    }

    @Test
    fun graphicShrinksSoChromeStaysInsideTheCap() {
        val plan = measureColorDrawer(
            capPx = 500,
            headerPx = 80,
            naturalBodyPx = 500,
            preferredVisualPx = 168,
            minVisualPx = 80,
            graphic = true,
        )
        assertFalse(plan.scroll)
        assertEquals(500, plan.heightPx)
        assertEquals(88, plan.visualPx)
    }

    @Test
    fun overflowScrollsInsteadOfClippingWhenTheMinimumGraphicDoesNotFit() {
        val plan = measureColorDrawer(
            capPx = 300,
            headerPx = 80,
            naturalBodyPx = 500,
            preferredVisualPx = 168,
            minVisualPx = 96,
            graphic = true,
        )
        assertTrue(plan.scroll)
        assertEquals(300, plan.heightPx)
        assertEquals(96, plan.visualPx)
    }

    @Test
    fun slidersKeepTheirHeightAndScrollInsideTheCap() {
        val plan = measureColorDrawer(
            capPx = 400,
            headerPx = 80,
            naturalBodyPx = 420,
            preferredVisualPx = 0,
            minVisualPx = 96,
            graphic = false,
        )
        assertTrue(plan.scroll)
        assertEquals(400, plan.heightPx)
    }
}

class NgSwatchFamiliesTest {

    @Test
    fun familiesStartWithSpectrumThenReadingPalettes() {
        assertEquals(
            listOf(
                "spectrum",
                "natural", "paper", "ink", "mist", "forest", "midnight", "sunset", "aurora",
            ),
            NgSwatchFamilies.families.map { it.id },
        )
        NgSwatchFamilies.families.forEach { family ->
            val cells = NgSwatchFamilies.cells(family.id)
            assertEquals(NgSwatchFamilies.ROWS * NgSwatchFamilies.COLS, cells.size)
            assertTrue(cells.all { it ushr 24 == 255 })
        }
    }

    @Test
    fun spectrumKeepsANeutralLastColumn() {
        val cells = NgSwatchFamilies.cells(NgSwatchFamilies.SPECTRUM_ID)
        repeat(NgSwatchFamilies.ROWS) { row ->
            assertFalse(isChromatic(cells[row * NgSwatchFamilies.COLS + 12]))
        }
    }

    @Test
    fun otherFamiliesUseLastColumnAsAccentAndPassTextContrast() {
        NgSwatchFamilies.families
            .filter { it.id != NgSwatchFamilies.SPECTRUM_ID }
            .forEach { family ->
                val cells = NgSwatchFamilies.cells(family.id)
                assertTrue(isChromatic(cells[3 * NgSwatchFamilies.COLS + 12]))
                val lightBg = cells[0]
                val darkFg = cells[(NgSwatchFamilies.ROWS - 1) * NgSwatchFamilies.COLS]
                assertTrue(NgColorMath.contrastRatio(cells[NgSwatchFamilies.FAN_COLUMNS], darkFg) >= 4.5)
                assertTrue(
                    NgColorMath.contrastRatio(
                        cells[(NgSwatchFamilies.ROWS - 1) * NgSwatchFamilies.COLS + 12],
                        lightBg,
                    ) >= 4.5,
                )
                repeat(NgSwatchFamilies.FAN_COLUMNS) { column ->
                    val top = cells[column]
                    val bottom = cells[(NgSwatchFamilies.ROWS - 1) * NgSwatchFamilies.COLS + column]
                    assertTrue(NgColorMath.contrastRatio(bottom, top) >= 4.5)
                    assertFalse((top and 0xFFFFFF) == 0xFFFFFF || (bottom and 0xFFFFFF) == 0)
                }
            }
    }

    @Test
    fun readingAccentColumnFollowsTheReadingPair() {
        val text = 0xFF222222.toInt()
        val day = 0xFFF7F3EA.toInt()
        val night = 0xFF161616.toInt()
        val dayAccent = accentColumn("paper", text, day)
        val nightAccent = accentColumn("paper", text, night)
        val mistAccent = accentColumn("mist", text, day)
        assertTrue(dayAccent.zip(nightAccent).any { (left, right) -> left != right })
        assertTrue(dayAccent.zip(mistAccent).any { (left, right) -> left != right })
        assertTrue(dayAccent.all { NgColorMath.contrastRatio(it, day) >= 4.5 })
        assertTrue(nightAccent.all { NgColorMath.contrastRatio(it, night) >= 4.5 })
        val blue = 0xFF2450B0.toInt()
        val live = NgSwatchFamilies.readingPalette("paper", 255, blue, day)
        val complement = schemeColumn(live, NgSwatchFamilies.FAN_COLUMNS + 1)
        val triad = schemeColumn(live, NgSwatchFamilies.FAN_COLUMNS + 2)
        assertTrue(complement.zip(dayAccent).any { (left, right) -> left != right })
        assertTrue(triad.zip(dayAccent).any { (left, right) -> left != right })
        assertTrue(complement.all { NgColorMath.contrastRatio(it, day) >= 4.5 })
        assertTrue(triad.all { NgColorMath.contrastRatio(it, day) >= 4.5 })
        assertTrue(readingCellIsValid(NgColorPreviewRole.TEXT, text, text, day))
        assertFalse(readingCellIsValid(NgColorPreviewRole.TEXT, day, text, day))
        assertTrue(readingCellIsValid(NgColorPreviewRole.BACKGROUND, day, text, day))
    }

    @Test
    fun readingPaletteKeepsTheFixedGridAndLeavesSpectrumAlone() {
        val text = 0xFF222222.toInt()
        val page = 0xFFF7F3EA.toInt()
        listOf("natural", "paper", "ink", "mist", "forest", "midnight", "sunset", "aurora").forEach { id ->
            val base = NgSwatchFamilies.cells(id)
            val live = NgSwatchFamilies.readingPalette(id, 255, text, page)
            repeat(NgSwatchFamilies.ROWS) { row ->
                repeat(NgSwatchFamilies.FAN_COLUMNS) { column ->
                    assertEquals(base[row * NgSwatchFamilies.COLS + column], live[row * NgSwatchFamilies.COLS + column])
                }
            }
        }
        assertArrayEquals(
            NgSwatchFamilies.cells("spectrum"),
            NgSwatchFamilies.readingPalette("spectrum", 255, text, 0xFF101010.toInt()),
        )
    }

    @Test
    fun accentColumnIsTextOnTheReadingBackground() {
        val page = 0xFFF7F3EA.toInt()
        val column = accentColumn("paper", 0xFF222222.toInt(), page)
        assertEquals(8, column.toSet().size)
        column.forEach { color ->
            assertTrue(NgColorMath.contrastRatio(color, page) >= 4.5)
        }
    }

    @Test
    fun themesKeepTheirOwnPageAndAccentCharacter() {
        val page = 0xFFF7F3EA.toInt()
        val text = 0xFF5C5346.toInt()
        val naturalTop = NgSwatchFamilies.cells("natural")[0]
        val mistTop = NgSwatchFamilies.cells("mist")[0]
        val paperTop = NgSwatchFamilies.cells("paper")[0]
        val midnightTop = NgSwatchFamilies.cells("midnight")[0]
        val forestTop = NgSwatchFamilies.cells("forest")[0]
        assertTrue(channel(naturalTop, 16) > channel(naturalTop, 0))
        assertTrue(channel(mistTop, 0) > channel(mistTop, 16))
        assertTrue(
            channel(forestTop, 8) - channel(forestTop, 16) >
                channel(paperTop, 8) - channel(paperTop, 16),
        )
        assertTrue(channelSum(midnightTop) < channelSum(paperTop) - 40)
        val naturalAccent = accentColumn("natural", text, page)
        val paperAccent = accentColumn("paper", text, page)
        assertTrue(naturalAccent.zip(paperAccent).count { (left, right) -> left != right } >= 6)
        assertTrue(distinctHueCount(naturalAccent) >= 4)
        assertTrue(naturalAccent.all { NgColorMath.contrastRatio(it, page) >= 4.5 })
    }

    @Test
    fun mutedPairKeepsHueWhileCappingChroma() {
        val paleText = 0xFFF4EFE6.toInt()
        val palePage = 0xFFFAF6F0.toInt()
        val muted = accentColumn("paper", paleText, palePage)
        val vivid = accentColumn("paper", 0xFF222222.toInt(), 0xFFF7F3EA.toInt())
        val mutedSpread = muted.map(::channelSpread)
        assertTrue(muted.all { NgColorMath.contrastRatio(it, palePage) >= 4.5 })
        assertTrue(mutedSpread.all { it >= 8 })
        assertTrue(muted.toSet().size >= 4)
        assertTrue(vivid.toSet().size >= 4)
    }

    private fun channelSpread(color: Int): Int {
        val red = color shr 16 and 0xFF
        val green = color shr 8 and 0xFF
        val blue = color and 0xFF
        return maxOf(red, green, blue) - minOf(red, green, blue)
    }

    private fun accentColumn(id: String, foreground: Int, background: Int): List<Int> =
        schemeColumn(NgSwatchFamilies.readingPalette(id, 255, foreground, background), NgSwatchFamilies.FAN_COLUMNS)

    private fun schemeColumn(cells: IntArray, column: Int): List<Int> {
        return List(NgSwatchFamilies.ROWS) { row -> cells[row * NgSwatchFamilies.COLS + column] }
    }

    private fun channel(color: Int, shift: Int): Int = color shr shift and 0xFF

    private fun channelSum(color: Int): Int = channel(color, 16) + channel(color, 8) + channel(color, 0)

    private fun distinctHueCount(colors: List<Int>): Int {
        val buckets = colors.map { color ->
            val red = channel(color, 16) / 255f
            val green = channel(color, 8) / 255f
            val blue = channel(color, 0) / 255f
            val max = maxOf(red, green, blue)
            val min = minOf(red, green, blue)
            val delta = max - min
            if (delta < 0.04f) return@map -1
            val sector = when (max) {
                red -> ((green - blue) / delta) % 6f
                green -> (blue - red) / delta + 2f
                else -> (red - green) / delta + 4f
            }
            (((sector * 60f) % 360f + 360f) % 360f).toInt() / 24
        }
        return buckets.filter { it >= 0 }.toSet().size
    }

    private fun isChromatic(color: Int): Boolean {
        val red = color shr 16 and 0xFF
        val green = color shr 8 and 0xFF
        val blue = color and 0xFF
        return maxOf(red, green, blue) - minOf(red, green, blue) >= 12
    }
}
