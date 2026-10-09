package io.legado.app.ui.design.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NgColorMathContrastTest {

    @Test
    fun blackOnWhiteIsAaaAndEqualColorsAreUnrated() {
        val ratio = NgColorMath.displayedContrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        assertTrue(ratio > 20.0)
        assertEquals("AAA", NgColorMath.wcagGrade(ratio))
        assertEquals("WCAG 21.0:1  AAA", NgColorMath.wcagContrastLabel(21.0))
        assertEquals("AA", NgColorMath.wcagGrade(4.5))
        assertEquals("—", NgColorMath.wcagGrade(4.49))
        assertEquals(1.0, NgColorMath.displayedContrast(0xFF336699.toInt(), 0xFF336699.toInt()), 0.001)
    }

    @Test
    fun flattenCompositesHalfBlackOverWhite() {
        assertEquals(
            0xFF7F7F7F.toInt(),
            NgColorMath.flatten(0x80000000.toInt(), 0xFFFFFFFF.toInt()),
        )
    }
}
