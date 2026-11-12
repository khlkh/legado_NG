package io.legado.app.help.annotation

import org.junit.Assert.assertEquals
import org.junit.Test

class WordAnnotationPoolResolverTest {

    private fun paragraphs(vararg texts: String): List<WordAnnotationAnalyzer.CanonicalParagraph> =
        texts.mapIndexed { index, text ->
            WordAnnotationAnalyzer.CanonicalParagraph(sourceIndex = index, text = text)
        }

    @Test
    fun `resolves every occurrence of each pool item`() {
        val resolved = WordAnnotationPoolResolver.resolveChapter(
            paragraphs = paragraphs("The proposal was untenable. The term untenable repeats."),
            items = listOf(WordAnnotationPoolItem("untenable", "impossible to defend", 12_000)),
            level = 4,
            cap = 10,
        )

        assertEquals(2, resolved.size)
        assertEquals(listOf(17, 37), resolved.map { it.start })
    }

    @Test
    fun `cross paragraph matches are dropped`() {
        val resolved = WordAnnotationPoolResolver.resolveChapter(
            paragraphs = paragraphs("The proposal was", "untenable from the start."),
            items = listOf(WordAnnotationPoolItem("was untenable", "could not be defended", null)),
            level = 4,
            cap = 10,
        )

        assertEquals(0, resolved.size)
    }

    @Test
    fun `level floor and cap apply to the resolved chapter`() {
        val resolved = WordAnnotationPoolResolver.resolveChapter(
            paragraphs = paragraphs("A common word and a rare one."),
            items = listOf(
                WordAnnotationPoolItem("common", "ordinary", 100),
                WordAnnotationPoolItem("rare", "uncommon", 20_000),
            ),
            level = 4,
            cap = 1,
        )

        // Floor 3000 drops "common"(100); rarest first keeps "rare" under cap 1.
        assertEquals(listOf("rare"), resolved.map { it.surface })
    }

    @Test
    fun `source paragraph index is restored after filtering`() {
        val resolved = WordAnnotationPoolResolver.resolveChapter(
            paragraphs = paragraphs("zero", "one rare"),
            items = listOf(WordAnnotationPoolItem("rare", "uncommon", 20_000)),
            level = 4,
            cap = 10,
        )

        assertEquals(1, resolved.single().paragraphIndex)
    }

    @Test
    fun `resolver stats separate pool floor and overlap losses`() {
        val result = WordAnnotationPoolResolver.resolveChapterWithStats(
            paragraphs = paragraphs("A common word and a rare one."),
            items = listOf(
                WordAnnotationPoolItem("common", "ordinary", 100),
                WordAnnotationPoolItem("rare", "uncommon", 20_000),
            ),
            level = 4,
            cap = 1,
        )
        assertEquals(2, result.stats.poolItems)
        assertEquals(1, result.stats.floorRejected)
        assertEquals(1, result.stats.matchedOccurrences)
        assertEquals(1, result.stats.resolvedOccurrences)
        assertEquals(1, result.stats.filter.selectedSurfaces)
        assertEquals(1, result.annotations.size)
    }

    @Test
    fun `resolver stats count overlap rejections`() {
        val result = WordAnnotationPoolResolver.resolveChapterWithStats(
            paragraphs = paragraphs("They came to terms with it."),
            items = listOf(
                WordAnnotationPoolItem("came to terms with", "accepted", null),
                WordAnnotationPoolItem("terms", "conditions", null),
            ),
            level = 4,
            cap = 10,
        )
        assertEquals(2, result.stats.matchedOccurrences)
        assertEquals(1, result.stats.resolvedOccurrences)
        assertEquals(1, result.stats.overlapRejected)
        assertEquals(1, result.annotations.size)
        assertEquals("came to terms with", result.annotations.single().surface)
    }

    @Test
    fun `late rare item survives a budget filled by an early common item`() {
        val text = "the " .repeat(80) + "a rare word"
        val resolved = WordAnnotationPoolResolver.resolveChapter(
            paragraphs = paragraphs(text),
            items = listOf(
                WordAnnotationPoolItem("the", "definite article", 100),
                WordAnnotationPoolItem("rare", "uncommon", 20_000),
            ),
            level = 1,
            cap = 1,
        )

        assertEquals(listOf("rare"), resolved.map { it.surface })
    }
}
