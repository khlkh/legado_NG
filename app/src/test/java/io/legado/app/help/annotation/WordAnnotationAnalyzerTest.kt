package io.legado.app.help.annotation

import io.legado.app.help.book.BookContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 1 analyzer helpers: canonical paragraph selection and the
 * resolve-items contract (invalid JSON body is null, never an empty success).
 */
class WordAnnotationAnalyzerTest {

    private fun content(texts: List<String>): BookContent =
        BookContent(
            sameTitleRemoved = false,
            textList = texts,
            effectiveReplaceRules = null,
        )

    @Test
    fun `canonical paragraphs skip control, html, and image lines`() {
        val paragraphs = WordAnnotationAnalyzer.canonicalParagraphs(
            content(
                listOf(
                    "[newpage]",
                    "first paragraph",
                    "<usehtml><p>raw html</p></usehtml>",
                    "a <img src=\"x.png\"> b",
                    "last paragraph",
                ),
            ),
        )

        assertEquals(
            listOf(
                WordAnnotationAnalyzer.CanonicalParagraph(1, "first paragraph"),
                WordAnnotationAnalyzer.CanonicalParagraph(4, "last paragraph"),
            ),
            paragraphs,
        )
    }

    @Test
    fun `resolveItems maps resolver indices to source indices`() {
        val paragraphs = listOf(
            WordAnnotationAnalyzer.CanonicalParagraph(sourceIndex = 3, text = "untenable"),
            WordAnnotationAnalyzer.CanonicalParagraph(sourceIndex = 7, text = "the proposal"),
        )
        val resolved = WordAnnotationAnalyzer.resolveItems(
            paragraphs = paragraphs,
            items = listOf(WordAnnotationItem(text = "untenable", occurrence = 1, hint = "无法防守")),
            uiLocaleChinese = true,
        )!!

        assertEquals(1, resolved.size)
        assertEquals(3, resolved.single().paragraphIndex)
        assertEquals("无法防守", resolved.single().hint)
    }

    @Test
    fun `resolveItems returns null for an invalid model body`() {
        val paragraphs = listOf(WordAnnotationAnalyzer.CanonicalParagraph(0, "untenable"))

        assertNull(
            WordAnnotationAnalyzer.resolveItems(
                paragraphs = paragraphs,
                items = null,
                uiLocaleChinese = false,
            ),
        )
    }

    @Test
    fun `resolveItems returns empty list for a valid body that places nothing`() {
        val paragraphs = listOf(WordAnnotationAnalyzer.CanonicalParagraph(0, "untenable"))

        val resolved = WordAnnotationAnalyzer.resolveItems(
            paragraphs = paragraphs,
            items = emptyList(),
            uiLocaleChinese = false,
        )!!

        assertTrue(resolved.isEmpty())
    }

    @Test
    fun `fusion option recovers an inflected form and keeps the source index`() {
        val paragraphs = listOf(
            WordAnnotationAnalyzer.CanonicalParagraph(sourceIndex = 3, text = "He ran home."),
        )

        val resolved = WordAnnotationAnalyzer.resolveItems(
            paragraphs = paragraphs,
            items = listOf(WordAnnotationItem(text = "run", occurrence = 1, hint = "move fast")),
            uiLocaleChinese = false,
            hybridOptions = WordAnnotationHybrid.Options(),
        )!!

        assertEquals(1, resolved.size)
        assertEquals(3, resolved.single().paragraphIndex)
        assertEquals("ran", resolved.single().surface)
        assertEquals(3, resolved.single().start)
        assertEquals(6, resolved.single().end)
    }
}
