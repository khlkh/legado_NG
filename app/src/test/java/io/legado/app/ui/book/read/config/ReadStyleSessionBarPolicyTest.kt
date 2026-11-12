package io.legado.app.ui.book.read.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadStyleSessionBarPolicyTest {

    @Test
    fun focusedEditorsHideThePresetSessionBar() {
        listOf(
            ReadStylePage.EDIT_TEXT_COLOR,
            ReadStylePage.EDIT_BACKGROUND_COLOR,
            ReadStylePage.EDIT_ACCENT_COLOR,
            ReadStylePage.EDIT_AI_THEME,
            ReadStylePage.EDIT_UNDERLINE_COLOR,
            ReadStylePage.HIGHLIGHT_EDIT,
            ReadStylePage.HIGHLIGHT_NINE_SLICE,
            ReadStylePage.HIGHLIGHT_TEXT_COLOR,
            ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR,
            ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR,
        ).forEach { page ->
            assertEquals(page.name, ReadStyleSessionBarMode.HIDDEN, page.sessionBarMode())
        }
    }

    @Test
    fun highlightRootOnlyOffersDone() {
        assertEquals(
            ReadStyleSessionBarMode.DONE_ONLY,
            ReadStylePage.HIGHLIGHT.sessionBarMode(),
        )
    }

    @Test
    fun sessionPagesKeepDiscardAndDone() {
        listOf(
            ReadStylePage.PRESET,
            ReadStylePage.ADJUST,
            ReadStylePage.EDIT,
            ReadStylePage.EDIT_UNDERLINE,
            ReadStylePage.APP_DEFAULTS,
            ReadStylePage.LANGUAGE_FONTS,
            ReadStylePage.NEW_BOOK_PRESET,
            ReadStylePage.FLOATING_WINDOWS,
        ).forEach { page ->
            assertEquals(page.name, ReadStyleSessionBarMode.FULL, page.sessionBarMode())
        }
    }
}
