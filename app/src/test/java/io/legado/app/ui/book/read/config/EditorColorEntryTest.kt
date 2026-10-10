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
    fun paperLooksKeepReadableTextAndTheDayAccent() {
        val dayAccent = 0xFF2B579A.toInt()
        ngPaperLooks(night = false).forEach { look ->
            assertEquals(dayAccent, look.accent)
            assertTrue(NgColorMath.displayedContrast(look.text, look.background) >= 7.0)
        }
        ngPaperLooks(night = true).forEach { look ->
            assertTrue(NgColorMath.displayedContrast(look.text, look.background) >= 7.0)
        }
        assertEquals(3, ngPaperLooks(night = false).size)
        assertEquals(3, ngPaperLooks(night = true).size)
    }
}
