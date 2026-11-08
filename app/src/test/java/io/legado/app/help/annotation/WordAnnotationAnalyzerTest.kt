package io.legado.app.help.annotation

import io.legado.app.help.book.BookContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Analyzer helpers: canonical paragraph selection, analysis-config identity
 * keys, and the fusion-resolution behavior the analyzer relies on.
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
    fun `fusion resolution recovers an inflected form`() {
        val paragraphs = listOf("He ran home.")
        val hybrid = WordAnnotationHybrid.resolveDetailed(
            paragraphs = paragraphs,
            items = listOf(WordAnnotationItem(text = "run", occurrence = 1, hint = "move fast")),
            uiLocaleChinese = false,
        )

        assertEquals(1, hybrid.results.size)
        assertEquals("ran", hybrid.results.single().annotation.surface)
        assertEquals(3, hybrid.results.single().annotation.start)
        assertEquals(6, hybrid.results.single().annotation.end)
        assertTrue(hybrid.results.single().viaLemmaFallback)
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
}
