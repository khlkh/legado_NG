package io.legado.app.ui.book.read.config

import io.legado.app.ui.design.theme.NgColorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorColorEntryTest {

    @Test
    fun clusterIndexFollowsTextBackgroundAccent() {
        assertEquals(0, editorColorClusterIndex(ReadStylePage.EDIT_TEXT_COLOR))
        assertEquals(1, editorColorClusterIndex(ReadStylePage.EDIT_BACKGROUND_COLOR))
        assertEquals(2, editorColorClusterIndex(ReadStylePage.EDIT_ACCENT_COLOR))
        assertEquals(ReadStylePage.EDIT_TEXT_COLOR, editorColorPageForCluster(0))
        assertEquals(ReadStylePage.EDIT_BACKGROUND_COLOR, editorColorPageForCluster(1))
        assertEquals(ReadStylePage.EDIT_ACCENT_COLOR, editorColorPageForCluster(2))
        assertEquals(ReadStylePage.EDIT_TEXT_COLOR, editorColorPageForCluster(9))
    }

    @Test
    fun paperLooksKeepTheExamplePairs() {
        val day = ngPaperLooks(night = false)
        val night = ngPaperLooks(night = true)
        assertEquals(10, day.size)
        assertEquals(10, night.size)
        assertEquals(0xFFF6F1E7.toInt(), day.first().background)
        assertEquals(0xFF2E2A24.toInt(), day.first().text)
        assertEquals(0xFF2F5F7A.toInt(), day.first().accent)
        assertEquals(0xFF1E1D1A.toInt(), night.first().background)
        assertEquals(0xFFE8E0D4.toInt(), night.first().text)
        assertEquals(0xFF6FB0C9.toInt(), night.first().accent)
        assertEquals(0xFFB06A2E.toInt(), day[3].accent)
        (day + night).forEach { look ->
            assertTrue(NgColorMath.displayedContrast(look.text, look.background) >= 7.0)
        }
        assertEquals(10, rotatedDefaultPairs(0).size)
        assertTrue(rotatedDefaultPairs(1).first().id != day.first().id)
    }
}
