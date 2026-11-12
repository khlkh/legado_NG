package io.legado.app.model.epub

import io.legado.app.help.annotation.WordAnnotation
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubSmartGlossesFragmentsTest {

    private fun node(
        element: String,
        ordinal: Int,
        text: String,
        sourceStart: Int,
        nativeStart: Int,
    ): EpubContentProjection.ProjectedNode {
        val size = text.length
        return EpubContentProjection.ProjectedNode(
            element = element,
            ordinal = ordinal,
            original = text,
            text = text,
            starts = IntArray(size) { nativeStart + it },
            ends = IntArray(size) { nativeStart + it + 1 },
            sourceStarts = IntArray(size) { sourceStart + it },
        )
    }

    private fun doc(index: Int, vararg nodes: EpubContentProjection.ProjectedNode) =
        EpubContentProjection.ProjectedDocument(
            EpubSourceDocument(
                href = "doc$index.xhtml",
                html = "",
                nodes = emptyList(),
                media = emptyList(),
                excludedElements = emptySet(),
                spineOccurrence = index,
            ),
            nodes.toList(),
            emptyList(),
        )

    @Test
    fun `annotation across inline nodes is split per node`() {
        val paragraphs = listOf("She came to terms with it.")
        // "came to terms with" spans two inline nodes; source starts at 4.
        val nodes = listOf(
            node("em", 0, "came to", sourceStart = 4, nativeStart = 10),
            node("em", 1, " terms with", sourceStart = 11, nativeStart = 20),
        )
        val fragments = EpubSmartGlossesFragments.resolve(
            paragraphs,
            listOf(doc(0, *nodes.toTypedArray())),
            listOf(WordAnnotation(paragraphIndex = 0, start = 4, end = 21, surface = "came to terms with", hint = "accepted")),
        )
        assertEquals(2, fragments.size)
        assertEquals("em", fragments[0].element)
        assertEquals(10, fragments[0].nativeStart)
        assertEquals(20, fragments[1].nativeStart)
    }

    @Test
    fun `annotation only resolves its exact source range`() {
        val paragraphs = listOf("untenable and untenable")
        val fragments = EpubSmartGlossesFragments.resolve(
            paragraphs,
            listOf(
                doc(
                    0,
                    node("span", 0, "untenable", sourceStart = 0, nativeStart = 0),
                    node("span", 1, " and ", sourceStart = 9, nativeStart = 9),
                    node("span", 2, "untenable", sourceStart = 14, nativeStart = 14),
                ),
            ),
            listOf(WordAnnotation(paragraphIndex = 0, start = 14, end = 23, surface = "untenable", hint = "x")),
        )
        assertEquals(1, fragments.size)
        assertEquals(14, fragments.single().nativeStart)
        assertEquals(2, fragments.single().ordinal)
    }

    @Test
    fun `partially represented annotation is dropped`() {
        val paragraphs = listOf("the quick brown fox")
        // "brown fox" (10..18) is missing the "fox" tail.
        val fragments = EpubSmartGlossesFragments.resolve(
            paragraphs,
            listOf(
                doc(0, node("span", 0, "brown", sourceStart = 10, nativeStart = 5)),
            ),
            listOf(WordAnnotation(paragraphIndex = 0, start = 10, end = 19, surface = "brown fox", hint = "x")),
        )
        assertEquals(0, fragments.size)
    }

    @Test
    fun `surrogate pair spans two utf16 units and resolves`() {
        val paragraphs = listOf("a 𐍈 b")
        // "a" at 0, space 1, "𐍈" at 2..3, space 4, "b" 5
        val fragments = EpubSmartGlossesFragments.resolve(
            paragraphs,
            listOf(
                doc(0, node("span", 0, "𐍈", sourceStart = 2, nativeStart = 7)),
            ),
            listOf(WordAnnotation(paragraphIndex = 0, start = 2, end = 4, surface = "𐍈", hint = "x")),
        )
        assertEquals(1, fragments.size)
        assertEquals(7, fragments.single().nativeStart)
    }
}
