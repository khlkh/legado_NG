package io.legado.app.help.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordAnnotationFilterTest {

    private fun ann(
        paragraphIndex: Int = 0,
        start: Int = 0,
        end: Int = 3,
        surface: String = "abc",
        hint: String = "h",
        rank: Int? = null,
    ) = WordAnnotation(paragraphIndex, start, end, surface, hint, rank)

    @Test
    fun `cap limits the chapter and keeps reading order`() {
        val pool = listOf(
            ann(paragraphIndex = 0, start = 0, surface = "rare", rank = 20_000),
            ann(paragraphIndex = 0, start = 10, surface = "common", rank = 100),
            ann(paragraphIndex = 1, start = 0, surface = "mid", rank = 4_000),
        )
        val filtered = WordAnnotationFilter.filter(pool, level = 4, cap = 2)
        // Level 4 floor is 3000: "common"(100) gated out; rarest first among
        // survivors (mid 4000, rare 20000 -> rare first? rank asc: rare 20000
        // vs mid 4000 => mid first), then reading order, take 2.
        assertEquals(listOf("rare", "mid"), filtered.map { it.surface })
    }

    @Test
    fun `level floor gates only known single words`() {
        val pool = listOf(
            ann(surface = "common", rank = 100),
            ann(surface = "came to terms with", rank = null),
            ann(surface = "unknownword", rank = null),
        )
        val filtered = WordAnnotationFilter.filter(pool, level = 4, cap = 10)
        assertEquals(2, filtered.size)
        assertTrue(filtered.none { it.surface == "common" })
    }

    @Test
    fun `unknown ranks sort last and are kept`() {
        val pool = listOf(
            ann(paragraphIndex = 0, start = 0, surface = "unknown", rank = null),
            ann(paragraphIndex = 0, start = 5, surface = "known", rank = 50_000),
        )
        val filtered = WordAnnotationFilter.filter(pool, level = 5, cap = 10)
        // Level 5 floor 8000: known rank 50000 >= 8000 passes; unknown passes.
        assertEquals(2, filtered.size)
    }

    @Test
    fun `level one keeps common words`() {
        val pool = listOf(ann(surface = "common", rank = 100))
        val filtered = WordAnnotationFilter.filter(pool, level = 1, cap = 10)
        assertEquals(1, filtered.size)
    }

    @Test
    fun `cap one keeps the rarest survivor`() {
        // Level 4 floor is 3000; "common"(100) is gated, mid(4000) and
        // rare(20000) survive. Rarest first must keep "rare", not "mid".
        val pool = listOf(
            ann(surface = "common", rank = 100),
            ann(surface = "mid", rank = 4_000),
            ann(surface = "rare", rank = 20_000),
        )
        val filtered = WordAnnotationFilter.filter(pool, level = 4, cap = 1)
        assertEquals(listOf("rare"), filtered.map { it.surface })
    }

    @Test
    fun `unique cap does not count repeated occurrences`() {
        val pool = listOf(
            ann(paragraphIndex = 0, start = 0, surface = "repeat", rank = 20_000),
            ann(paragraphIndex = 0, start = 10, surface = "repeat", rank = 20_000),
            ann(paragraphIndex = 0, start = 20, surface = "repeat", rank = 20_000),
            ann(paragraphIndex = 1, start = 0, surface = "rare", rank = 30_000),
        )
        val filtered = WordAnnotationFilter.filter(pool, level = 4, cap = 2)
        assertEquals(2, filtered.map { it.surface }.distinct().size)
        assertEquals(3, filtered.size)
        assertEquals(listOf("repeat", "repeat", "rare"), filtered.map { it.surface })
    }

    @Test
    fun `occurrence cap keeps first and last occurrence of one surface`() {
        val pool = listOf(
            ann(paragraphIndex = 0, start = 0, surface = "repeat", rank = 20_000),
            ann(paragraphIndex = 0, start = 10, surface = "repeat", rank = 20_000),
            ann(paragraphIndex = 0, start = 20, surface = "repeat", rank = 20_000),
        )
        val filtered = WordAnnotationFilter.filter(pool, level = 4, cap = 1)
        assertEquals(listOf(0, 20), filtered.map { it.start })
    }
}
