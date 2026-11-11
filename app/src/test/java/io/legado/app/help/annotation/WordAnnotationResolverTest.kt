package io.legado.app.help.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordAnnotationResolverTest {

    private fun resolve(
        paragraphs: List<String>,
        vararg items: WordAnnotationItem,
        uiLocaleChinese: Boolean = false,
    ): List<WordAnnotation> = WordAnnotationResolver.resolve(paragraphs, items.toList(), uiLocaleChinese)

    @Test
    fun `resolves a single word with paragraph offsets`() {
        val result = resolve(
            listOf("The proposal was untenable."),
            WordAnnotationItem("untenable", 1, "impossible to defend"),
        )
        assertEquals(1, result.size)
        assertEquals(0, result[0].paragraphIndex)
        assertEquals(17, result[0].start)
        assertEquals(26, result[0].end)
        assertEquals("untenable", result[0].surface)
    }

    @Test
    fun `occurrence selects the second exact match`() {
        val paragraph = "The proposal was untenable, and the untenable plan failed."
        val result = resolve(
            listOf(paragraph),
            WordAnnotationItem("untenable", 2, "impossible to justify"),
        )
        assertEquals(1, result.size)
        assertEquals(36, result[0].start)
        assertEquals(45, result[0].end)
    }

    @Test
    fun `missing occurrence drops the item`() {
        val result = resolve(
            listOf("The proposal was untenable."),
            WordAnnotationItem("untenable", 3, "impossible to defend"),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `art inside article does not count`() {
        val result = resolve(
            listOf("The article art is here."),
            WordAnnotationItem("art", 1, "craft"),
        )
        assertEquals(1, result.size)
        assertEquals(12, result[0].start)
        assertEquals(15, result[0].end)
    }

    @Test
    fun `overlapping candidates are still considered after a boundary miss`() {
        // "aba" at 0 is inside "ababa" as a token, so it is not a match; the
        // next start is checked without skipping a valid overlapping span.
        val passage = "x ababa aba"
        val result = resolve(
            listOf(passage),
            WordAnnotationItem("aba", 1, "a sequence"),
        )
        assertEquals(1, result.size)
        assertEquals(8, result[0].start)
        assertEquals(11, result[0].end)
    }

    @Test
    fun `digits inside a longer number do not count`() {
        val result = resolve(
            listOf("1234 123"),
            WordAnnotationItem("123", 1, "one hundred twenty three"),
        )
        assertEquals(1, result.size)
        assertEquals(5, result[0].start)
        assertEquals(8, result[0].end)
    }

    @Test
    fun `punctuation adjacent to a word still matches`() {
        val result = resolve(
            listOf("the word, again"),
            WordAnnotationItem("word", 1, "a unit of language"),
        )
        assertEquals(1, result.size)
        assertEquals(4, result[0].start)
        assertEquals(8, result[0].end)
    }

    @Test
    fun `matching is case sensitive`() {
        val result = resolve(
            listOf("word Word"),
            WordAnnotationItem("Word", 1, "a unit of language"),
        )
        assertEquals(1, result.size)
        assertEquals(5, result[0].start)
        assertEquals(9, result[0].end)
    }

    @Test
    fun `non-overlapping scan counts the second standalone occurrence`() {
        val result = resolve(
            listOf("aba aba"),
            WordAnnotationItem("aba", 2, "a sequence"),
        )
        assertEquals(1, result.size)
        assertEquals(4, result[0].start)
    }

    @Test
    fun `cjk span matches exact characters inside continuous text`() {
        val result = resolve(
            listOf("他说这不足为奇。"),
            WordAnnotationItem("不足为奇", 1, "不值得惊讶"),
            uiLocaleChinese = true,
        )
        assertEquals(1, result.size)
        assertEquals(3, result[0].start)
        assertEquals(7, result[0].end)
        assertEquals("不足为奇", result[0].surface)
    }

    @Test
    fun `cjk occurrence counts the named span`() {
        val result = resolve(
            listOf("不足为奇，他也不足为奇。"),
            WordAnnotationItem("不足为奇", 2, "不值得惊讶"),
            uiLocaleChinese = true,
        )
        assertEquals(1, result.size)
        assertEquals(7, result[0].start)
        assertEquals(11, result[0].end)
    }

    @Test
    fun `overlapping spans keep the longer phrase`() {
        val result = resolve(
            listOf("She came to terms with the decision."),
            WordAnnotationItem("came to terms", 1, "accepted"),
            WordAnnotationItem("came to terms with", 1, "accepted a difficult situation"),
        )
        assertEquals(1, result.size)
        assertEquals("came to terms with", result[0].surface)
        assertEquals(4, result[0].start)
        assertEquals(22, result[0].end)
    }

    @Test
    fun `equal length overlaps drop both`() {
        val result = resolve(
            listOf("She came to terms with the decision."),
            WordAnnotationItem("came to terms", 1, "accepted"),
            WordAnnotationItem("came to terms", 1, "accepted"),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `overlap resolution is independent of model order`() {
        val items = listOf(
            WordAnnotationItem("came to terms with", 1, "accepted a difficult situation"),
            WordAnnotationItem("came to terms", 1, "accepted"),
        )
        val result = WordAnnotationResolver.resolve(
            listOf("She came to terms with the decision."),
            items,
            uiLocaleChinese = false,
        )
        assertEquals(1, result.size)
        assertEquals("came to terms with", result[0].surface)
    }

    @Test
    fun `chapter cap keeps the default thirty-four after sorting`() {
        val words = (1..40).map { "word$it" }
        val paragraph = words.joinToString(" ")
        val items = words.map { WordAnnotationItem(it, 1, "meaning of $it") }
        val result = resolve(listOf(paragraph), *items.toTypedArray())
        assertEquals(34, result.size)
        assertEquals("word1", result.first().surface)
        assertEquals("word34", result.last().surface)
    }

    @Test
    fun `joined batch drops a match crossing the paragraph join and keeps one inside`() {
        val result = resolve(
            listOf("abc def", "x y"),
            WordAnnotationItem("def\nx", 1, "crosses the join"),
            WordAnnotationItem("abc", 1, "inside first paragraph"),
        )
        assertEquals(1, result.size)
        assertEquals("abc", result[0].surface)
        assertEquals(0, result[0].paragraphIndex)
    }

    @Test
    fun `joined batch resolves an occurrence in a later paragraph`() {
        val result = resolve(
            listOf("hello world", "world peace"),
            WordAnnotationItem("world", 2, "the earth"),
        )
        assertEquals(1, result.size)
        assertEquals(1, result[0].paragraphIndex)
        assertEquals(0, result[0].start)
        assertEquals(5, result[0].end)
    }

    @Test
    fun `english hint longer than eight words is dropped`() {
        val longHint = (1..9).joinToString(" ") { "w$it" }
        val result = resolve(
            listOf("The proposal was untenable."),
            WordAnnotationItem("untenable", 1, longHint),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `english hint of eight words is kept`() {
        val eightWords = (1..8).joinToString(" ") { "w$it" }
        val result = resolve(
            listOf("The proposal was untenable."),
            WordAnnotationItem("untenable", 1, eightWords),
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `chinese ui hint over sixteen characters is dropped`() {
        val sixteen = "一".repeat(16)
        val seventeen = "一".repeat(17)
        val kept = resolve(
            listOf("他说这不足为奇。"),
            WordAnnotationItem("不足为奇", 1, sixteen),
            uiLocaleChinese = true,
        )
        assertEquals(1, kept.size)

        val dropped = resolve(
            listOf("他说这不足为奇。"),
            WordAnnotationItem("不足为奇", 1, seventeen),
            uiLocaleChinese = true,
        )
        assertTrue(dropped.isEmpty())
    }

    @Test
    fun `hint cap helper matches the rules`() {
        assertTrue(WordAnnotationResolver.hintWithinCap("a b c", uiLocaleChinese = false))
        assertFalse(WordAnnotationResolver.hintWithinCap("", uiLocaleChinese = false))
        assertFalse(WordAnnotationResolver.hintWithinCap("   ", uiLocaleChinese = false))
        assertTrue(WordAnnotationResolver.hintWithinCap("一".repeat(16), uiLocaleChinese = true))
        assertFalse(WordAnnotationResolver.hintWithinCap("一".repeat(17), uiLocaleChinese = true))
    }
}
