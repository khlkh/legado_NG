package io.legado.app.help.annotation

import io.legado.app.ui.book.read.page.entities.TextHintRun
import org.junit.Assert.assertEquals
import org.junit.Test

class WordAnnotationLayoutTest {

    @Test
    fun `band height is zero without hints and a ratio of text height with hints`() {
        assertEquals(0f, WordAnnotationLayout.bandHeight(100f, hasHints = false), 0f)
        assertEquals(60f, WordAnnotationLayout.bandHeight(100f, hasHints = true), 0.001f)
        assertEquals(0f, WordAnnotationLayout.bandHeight(0f, hasHints = true), 0f)
    }

    @Test
    fun `runs for a line map annotation offsets to line local offsets`() {
        val annotations = listOf(
            WordAnnotation(paragraphIndex = 0, start = 17, end = 26, surface = "untenable", hint = "impossible"),
        )
        val runs = WordAnnotationLayout.runsForLine(annotations, lineStart = 10, lineEnd = 30)
        assertEquals(listOf(TextHintRun(7, 16, "impossible")), runs)
    }

    @Test
    fun `wrapped span intersects every line but the caller keeps only the first`() {
        val annotations = listOf(
            WordAnnotation(paragraphIndex = 0, start = 4, end = 22, surface = "came to terms with", hint = "accepted"),
        )
        // Visual lines split the span at 10 and 17.
        val first = WordAnnotationLayout.runsForLine(annotations, lineStart = 0, lineEnd = 10)
        val second = WordAnnotationLayout.runsForLine(annotations, lineStart = 10, lineEnd = 17)
        val third = WordAnnotationLayout.runsForLine(annotations, lineStart = 17, lineEnd = 30)

        assertEquals(listOf(TextHintRun(4, 10, "accepted")), first)
        assertEquals(listOf(TextHintRun(0, 7, "accepted")), second)
        assertEquals(listOf(TextHintRun(0, 5, "accepted")), third)

        // The layout loop drops placed spans after the first intersecting line.
        val pending = WordAnnotationLayout.pendingAfterLine(annotations, lineEnd = 10)
        assertEquals(emptyList<WordAnnotation>(), pending)
    }

    @Test
    fun `pending after a line keeps only spans that have not started`() {
        val annotations = listOf(
            WordAnnotation(paragraphIndex = 0, start = 4, end = 12, surface = "span-a", hint = "a"),
            WordAnnotation(paragraphIndex = 0, start = 20, end = 26, surface = "span-b", hint = "b"),
        )
        val pending = WordAnnotationLayout.pendingAfterLine(annotations, lineEnd = 15)
        assertEquals(listOf(annotations[1]), pending)
    }

    @Test
    fun `supplementary character offsets stay utf16 and are not column indexes`() {
        // "a𝔘b" is 4 UTF-16 units; the span [1,3) covers the surrogate pair only.
        val annotations = listOf(
            WordAnnotation(paragraphIndex = 0, start = 1, end = 3, surface = "𝔘", hint = "a rune"),
        )
        val runs = WordAnnotationLayout.runsForLine(annotations, lineStart = 0, lineEnd = 4)
        assertEquals(listOf(TextHintRun(1, 3, "a rune")), runs)
    }

    @Test
    fun `annotation outside the line produces no run`() {
        val annotations = listOf(
            WordAnnotation(paragraphIndex = 0, start = 40, end = 44, surface = "later", hint = "later"),
        )
        assertEquals(emptyList<TextHintRun>(), WordAnnotationLayout.runsForLine(annotations, 0, 20))
    }
}
