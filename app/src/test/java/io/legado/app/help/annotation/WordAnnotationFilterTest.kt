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
        bucket: Int = 0,
        difficulty: Int? = null,
        contextual: Boolean = false,
    ) = WordAnnotation(paragraphIndex, start, end, surface, hint, rank, bucket, difficulty, contextual)

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

    @Test
    fun `coverage quota reserves a slot for a later quartile`() {
        val opening = (1..8).map { i ->
            ann(paragraphIndex = 0, start = i, surface = "opening-$i", rank = 50_000 + i, bucket = 0)
        }
        val later = ann(paragraphIndex = 3, start = 0, surface = "later-mid", rank = 20_000, bucket = 3)
        val filtered = WordAnnotationFilter.filter(
            opening + later,
            level = 4,
            cap = 8,
            coverageBuckets = 4,
        )
        assertEquals(8, filtered.size)
        assertTrue(filtered.any { it.surface == "later-mid" })
    }

    @Test
    fun `tiny cap stays rarity first instead of forcing an early bucket quota`() {
        val pool = listOf(
            ann(paragraphIndex = 0, start = 0, surface = "early-common", rank = 100, bucket = 0),
            ann(paragraphIndex = 3, start = 0, surface = "late-rare", rank = 50_000, bucket = 3),
        )
        val filtered = WordAnnotationFilter.filter(
            pool,
            level = 1,
            cap = 1,
            coverageBuckets = 4,
        )
        assertEquals(listOf("late-rare"), filtered.map { it.surface })
    }

    @Test
    fun `coverage occurrence choice prefers the selected quartile`() {
        val pool = listOf(
            ann(paragraphIndex = 0, start = 0, surface = "repeat", rank = 20_000, bucket = 0),
            ann(paragraphIndex = 3, start = 0, surface = "repeat", rank = 20_000, bucket = 3),
        )
        val filtered = WordAnnotationFilter.filter(
            pool,
            level = 4,
            cap = 1,
            coverageBuckets = 4,
        )
        assertEquals(2, filtered.size)
        assertEquals(listOf(0, 3), filtered.map { it.coverageBucket })
    }

    @Test
    fun `level-aware ordering prefers difficulty near the configured level`() {
        val pool = listOf(
            ann(surface = "easy", rank = 20_000, difficulty = 1),
            ann(surface = "hard", rank = 20_000, difficulty = 5),
        )
        assertEquals(
            listOf("easy"),
            WordAnnotationFilter.filter(pool, level = 1, cap = 1).map { it.surface },
        )
        assertEquals(
            listOf("hard"),
            WordAnnotationFilter.filter(pool, level = 5, cap = 1).map { it.surface },
        )
    }

    @Test
    fun `contextual meaning gets priority over a literal use at the same level`() {
        val pool = listOf(
            ann(surface = "literal", rank = 20_000, difficulty = 3),
            ann(surface = "contextual", rank = 20_000, difficulty = 3, contextual = true),
        )
        val filtered = WordAnnotationFilter.filter(pool, level = 4, cap = 1)
        assertEquals(listOf("contextual"), filtered.map { it.surface })
    }
}
