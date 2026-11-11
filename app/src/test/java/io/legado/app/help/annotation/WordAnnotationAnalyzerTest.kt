package io.legado.app.help.annotation

import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Analyzer helpers: canonical paragraph selection, windowed discovery,
 * analysis-config identity keys, and the current per-book cap default.
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
    fun `generation cap is independent from the display cap`() {
        assertEquals(60, WordAnnotationAnalyzer.generationCap(displayCap = 21))
        assertEquals(89, WordAnnotationAnalyzer.generationCap(displayCap = 89))
        assertEquals(15, WordAnnotationAnalyzer.perWindowCap(generationCap = 60, windowCount = 4))
        assertEquals(23, WordAnnotationAnalyzer.perWindowCap(generationCap = 89, windowCount = 4))
    }

    @Test
    fun `discovery windows cover the chapter and overlap at boundaries`() {
        val paragraphs = (0 until 100).map {
            WordAnnotationAnalyzer.CanonicalParagraph(it, "paragraph $it " + "x".repeat(1_000))
        }
        val windows = WordAnnotationAnalyzer.discoveryWindows(paragraphs)

        assertEquals(4, windows.size)
        assertEquals(0, windows.first().startParagraph)
        assertEquals(100, windows.last().endParagraph)
        assertEquals(0, windows.first().index)
        assertEquals(3, windows.last().index)
        windows.zipWithNext().forEach { (left, right) ->
            assertTrue(right.startParagraph < left.endParagraph)
        }
    }

    @Test
    fun `short chapter uses one discovery window`() {
        val paragraphs = (0 until 20).map {
            WordAnnotationAnalyzer.CanonicalParagraph(it, "paragraph $it " + "x".repeat(100))
        }
        val windows = WordAnnotationAnalyzer.discoveryWindows(paragraphs)

        assertEquals(1, windows.size)
        assertEquals(0, windows.single().startParagraph)
        assertEquals(20, windows.single().endParagraph)
    }

    @Test
    fun `new book defaults to the current hint cap`() {
        assertEquals(
            WordAnnotationConfig.DEFAULT_WORD_ANNOTATION_HINT_CAP,
            Book().getWordAnnotationHintCap(),
        )
    }

    @Test
    fun `word gloss is limited to latin script books`() {
        assertTrue(WordAnnotationEligibility.supports("latin"))
        assertFalse(WordAnnotationEligibility.supports("cjk"))
        assertFalse(WordAnnotationEligibility.supports("other"))
        // Unknown until the book is classified: keep the entry point visible.
        assertTrue(WordAnnotationEligibility.supports(null))

        val latin = "The quick brown fox jumps over the lazy dog."
        val chinese = "这是一段足够长的中文文字，用来测试语言分类。"
        val russian = "Это достаточно длинный русский текст для проверки сценария."

        assertTrue(WordAnnotationEligibility.supportsPassage(null, latin))
        assertFalse(WordAnnotationEligibility.supportsPassage(null, chinese))
        assertFalse(WordAnnotationEligibility.supportsPassage(null, russian))
        // A persisted non-Latin class is authoritative even if the sample is Latin.
        assertFalse(WordAnnotationEligibility.supportsPassage("other", latin))

        val otherBook = Book().apply { config.scriptClass = "other" }
        assertFalse(WordAnnotationEligibility.supports(otherBook))
    }
}
