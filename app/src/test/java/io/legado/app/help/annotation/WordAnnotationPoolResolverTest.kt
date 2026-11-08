package io.legado.app.help.annotation

import org.junit.Assert.assertEquals
import org.junit.Test

class WordAnnotationPoolResolverTest {

    private fun paragraphs(vararg texts: String): List<WordAnnotationAnalyzer.CanonicalParagraph> =
        texts.mapIndexed { index, text ->
            WordAnnotationAnalyzer.CanonicalParagraph(sourceIndex = index, text = text)
        }

    @Test
    fun `resolves the first occurrence of each pool item`() {
        val resolved = WordAnnotationPoolResolver.resolveChapter(
            paragraphs = paragraphs("The proposal was untenable. The term untenable repeats."),
            items = listOf(WordAnnotationPoolItem("untenable", "impossible to defend", 12_000)),
            level = 4,
            cap = 10,
            rankAware = true,
        )

        assertEquals(1, resolved.size)
        assertEquals("untenable", resolved.single().surface)
        assertEquals(17, resolved.single().start)
    }

    @Test
    fun `cross paragraph matches are dropped`() {
        val resolved = WordAnnotationPoolResolver.resolveChapter(
            paragraphs = paragraphs("The proposal was", "untenable from the start."),
            items = listOf(WordAnnotationPoolItem("was untenable", "could not be defended", null)),
            level = 4,
            cap = 10,
            rankAware = true,
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
            rankAware = true,
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
            rankAware = true,
        )

        assertEquals(1, resolved.single().paragraphIndex)
    }
}
