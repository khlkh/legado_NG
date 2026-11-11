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

    @Test
    fun `pending keeps a wrap only when the next line is a new page`() {
        val annotations = listOf(
            WordAnnotation(paragraphIndex = 0, start = 4, end = 22, surface = "came to terms with", hint = "accepted"),
            WordAnnotation(paragraphIndex = 0, start = 40, end = 48, surface = "later", hint = "later"),
        )
        assertEquals(listOf(annotations[1]), WordAnnotationLayout.pendingAfterLine(annotations, lineEnd = 10))
        assertEquals(annotations, WordAnnotationLayout.pendingAfterLine(annotations, lineEnd = 10, keepWraps = true))
    }

    @Test
    fun `end of line label slides left to keep full width`() {
        val (x, maxWidth) = WordAnnotationLayout.placeHintX(
            spanStart = 300f, spanEnd = 340f, measuredWidth = 80f,
            lineStart = 0f, lineEnd = 360f, cursor = 0f,
        )
        assertEquals(280f, x, 0.01f)
        assertEquals(80f, maxWidth, 0.01f)
    }

    @Test
    fun `start of line label stays at the word`() {
        val (x, maxWidth) = WordAnnotationLayout.placeHintX(
            spanStart = 20f, spanEnd = 60f, measuredWidth = 80f,
            lineStart = 0f, lineEnd = 360f, cursor = 0f,
        )
        assertEquals(20f, x, 0.01f)
        assertEquals(340f, maxWidth, 0.01f)
    }

    @Test
    fun `cursor blocks a label from sliding through the previous one`() {
        val (x, maxWidth) = WordAnnotationLayout.placeHintX(
            spanStart = 200f, spanEnd = 240f, measuredWidth = 80f,
            lineStart = 0f, lineEnd = 360f, cursor = 120f,
        )
        assertEquals(200f, x, 0.01f)
        assertEquals(160f, maxWidth, 0.01f)
    }

    @Test
    fun `on-page uses the rect center so a next-column sliver is excluded`() {
        assertEquals(true, WordAnnotationLayout.onPage(10f, 10f, 40f, 30f, 360f, 640f))
        // Previous CSS column: same Y, negative X. Center is < 0.
        assertEquals(false, WordAnnotationLayout.onPage(-80f, 400f, 2f, 420f, 360f, 640f))
        // Next CSS column: center is past the viewport width.
        assertEquals(false, WordAnnotationLayout.onPage(358f, 400f, 440f, 420f, 360f, 640f))
        // First-line word clipped at y=0 still has its center on the page.
        assertEquals(true, WordAnnotationLayout.onPage(20f, -4f, 80f, 16f, 360f, 640f))
    }

    @Test
    fun `inline splits on one line merge into a single segment`() {
        val merged = WordAnnotationLayout.mergeLineSegments(
            listOf(
                WordAnnotationLayout.HintSegment(1, "accepted", 200f, 240f, 40f, 60f),
                WordAnnotationLayout.HintSegment(1, "accepted", 240f, 310f, 41f, 59f),
                WordAnnotationLayout.HintSegment(1, "accepted", 20f, 90f, 64f, 84f),
            ),
        )
        assertEquals(2, merged.size)
        assertEquals(200f, merged[0].left, 0f)
        assertEquals(310f, merged[0].right, 0f)
        assertEquals(20f, merged[1].left, 0f)
    }

    @Test
    fun `wrapped phrase picks the first segment when it can slide to fit`() {
        val first = WordAnnotationLayout.HintSegment(1, "accepted", 300f, 340f, 40f, 60f)
        val second = WordAnnotationLayout.HintSegment(1, "accepted", 20f, 180f, 64f, 84f)
        val chosen = WordAnnotationLayout.chooseAnchor(listOf(first, second), measuredWidth = 80f, columnLeft = 0f, columnRight = 360f)
        assertEquals(first, chosen)
    }

    @Test
    fun `wrapped phrase picks the roomier segment when the first cannot fit`() {
        val sliver = WordAnnotationLayout.HintSegment(1, "accepted", 358f, 360f, 40f, 60f)
        val rest = WordAnnotationLayout.HintSegment(1, "accepted", 20f, 180f, 64f, 84f)
        val chosen = WordAnnotationLayout.chooseAnchor(
            listOf(sliver, rest),
            measuredWidth = 400f,
            columnLeft = 0f,
            columnRight = 360f,
        )
        assertEquals(rest, chosen)
    }

    @Test
    fun `pack slides an end of line label left instead of ellipsizing`() {
        val segment = WordAnnotationLayout.HintSegment(1, "impossible", 300f, 340f, 40f, 60f)
        val placed = WordAnnotationLayout.packHintLabels(
            listOf(segment to 80f),
            columnLeft = 0f,
            columnRight = 360f,
        )
        assertEquals(1, placed.size)
        assertEquals(280f, placed[0].left, 0.01f)
        assertEquals(80f, placed[0].maxWidth, 0.01f)
    }

    @Test
    fun `pack slides the earlier label left to keep both labels full width`() {
        val first = WordAnnotationLayout.HintSegment(1, "a", 80f, 100f, 40f, 60f)
        val second = WordAnnotationLayout.HintSegment(2, "b", 80f, 100f, 40f, 60f)
        val placed = WordAnnotationLayout.packHintLabels(
            listOf(first to 40f, second to 40f),
            columnLeft = 0f,
            columnRight = 100f,
        )
        assertEquals(2, placed.size)
        assertEquals(14f, placed[0].left, 0.01f)
        assertEquals(40f, placed[0].maxWidth, 0.01f)
        assertEquals(60f, placed[1].left, 0.01f)
        assertEquals(40f, placed[1].maxWidth, 0.01f)
    }

    @Test
    fun `pack slides a right edge label left instead of ellipsizing`() {
        val segment = WordAnnotationLayout.HintSegment(1, "极其恶劣的", 330f, 360f, 40f, 60f)
        val placed = WordAnnotationLayout.packHintLabels(
            listOf(segment to 80f),
            columnLeft = 0f,
            columnRight = 325f,
        )
        assertEquals(1, placed.size)
        assertEquals(245f, placed[0].left, 0.01f)
        assertEquals(80f, placed[0].maxWidth, 0.01f)
    }
}
