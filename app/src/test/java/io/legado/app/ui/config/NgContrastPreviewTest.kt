package io.legado.app.ui.config

import io.legado.app.ui.design.theme.NgColorMath
import org.junit.Assert.assertEquals
import org.junit.Test

class NgContrastPreviewTest {

    private val text = 0xFF222222.toInt()
    private val page = 0xFFF7F3EA.toInt()
    private val accent = 0xFF2B579A.toInt()
    private val picked = 0xFF98652E.toInt()

    @Test
    fun textRoleReplacesInkAndKeepsThePageAndMark() {
        val preview = ngContrastPreview(NgColorPreviewRole.TEXT, picked, page, text, accent)
        assertEquals(picked, preview.foreground)
        assertEquals(accent, preview.accent)
        assertEquals(page, preview.background)
        assertEquals(NgColorMath.displayedContrast(picked, page), preview.bodyContrast, 0.01)
    }

    @Test
    fun backgroundRoleReplacesThePageOnly() {
        val preview = ngContrastPreview(NgColorPreviewRole.BACKGROUND, picked, page, text, accent)
        assertEquals(text, preview.foreground)
        assertEquals(accent, preview.accent)
        assertEquals(picked, preview.background)
        assertEquals(NgColorMath.displayedContrast(text, picked), preview.bodyContrast, 0.01)
    }

    @Test
    fun accentRoleRecolorsOnlyTheSecondPhrase() {
        val first = ngContrastPreview(NgColorPreviewRole.ACCENT, picked, page, text, accent)
        val second = ngContrastPreview(NgColorPreviewRole.ACCENT, accent, page, text, accent)
        assertEquals(text, first.foreground)
        assertEquals(text, second.foreground)
        assertEquals(picked, first.accent)
        assertEquals(accent, second.accent)
        assertEquals(page, first.background)
        assertEquals(NgColorMath.displayedContrast(text, page), first.bodyContrast, 0.01)
        assertEquals(NgColorMath.displayedContrast(picked, page), first.accentContrast, 0.01)
        assertEquals(NgColorMath.displayedContrast(accent, page), second.accentContrast, 0.01)
    }
}
