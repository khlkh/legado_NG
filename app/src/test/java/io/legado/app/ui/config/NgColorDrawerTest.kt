package io.legado.app.ui.config

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
    fun familiesStartWithSpectrumThenNatural() {
        assertEquals(
            listOf("spectrum", "natural", "paper", "ink", "mist", "forest", "midnight"),
            NgSwatchFamilies.families.map { it.id },
        )
        NgSwatchFamilies.families.forEach { family ->
            val cells = NgSwatchFamilies.cells(family.id)
            assertEquals(NgSwatchFamilies.ROWS * NgSwatchFamilies.COLS, cells.size)
            assertTrue(cells.all { it ushr 24 == 255 })
        }
    }
}
