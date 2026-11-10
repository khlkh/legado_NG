package io.legado.app.help.annotation

import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Analyzer helpers: canonical paragraph selection, analysis-config identity
 * keys, pool sizing, and the current per-book cap default.
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
    fun `analysis config keys differ when hint language or model changes`() {
        val base = WordAnnotationAnalyzer.AnalysisConfig(
            hintCharCap = false,
            providerId = "p",
            modelId = "m",
        )
        val charCap = base.copy(hintCharCap = true)
        val otherModel = base.copy(modelId = "other")

        assertTrue(base.key != charCap.key)
        assertTrue(base.key != otherModel.key)
    }

    @Test
    fun `chinese book max hint words rejects a seven-word english hint`() {
        val sevenWords = List(7) { "word" }.joinToString(" ")
        assertTrue(!WordAnnotationResolver.hintWithinCap(sevenWords, uiLocaleChinese = false, maxHintWords = 6))
        assertTrue(WordAnnotationResolver.hintWithinCap(sevenWords, uiLocaleChinese = false, maxHintWords = 8))
    }

    @Test
    fun `pool target respects formula and cost cap`() {
        assertEquals(120, WordAnnotationAnalyzer.poolTarget(chapterCount = 10, cap = 13))
        assertEquals(260, WordAnnotationAnalyzer.poolTarget(chapterCount = 100, cap = 13))
        assertEquals(500, WordAnnotationAnalyzer.poolTarget(chapterCount = 5_000, cap = 55))
    }

    @Test
    fun `new book defaults to the current hint cap`() {
        assertEquals(
            WordAnnotationConfig.DEFAULT_WORD_ANNOTATION_HINT_CAP,
            Book().getWordAnnotationHintCap(),
        )
    }
}
