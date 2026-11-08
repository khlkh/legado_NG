package io.legado.app.help.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordAnnotationHybridTest {

    private val frequency = WordFrequencyIndex.fromRankMap(
        mapOf(
            "the" to 1,
            "to" to 8,
            "he" to 10,
            "was" to 20,
            "terms" to 229,
            "child" to 300,
            "children" to 354,
            "home" to 400,
            "better" to 495,
            "committee" to 643,
            "run" to 672,
            "came" to 1_097,
            "running" to 1_103,
            "ran" to 4_286,
            "runs" to 4_500,
            "good" to 155,
        )
    )

    private fun resolve(
        paragraphs: List<String>,
        items: List<WordAnnotationItem>,
        options: WordAnnotationHybrid.Options = WordAnnotationHybrid.Options(),
        frequencyIndex: WordFrequencyIndex? = frequency,
    ): List<WordAnnotationHybrid.Result> = WordAnnotationHybrid.resolve(
        paragraphs = paragraphs,
        items = items,
        uiLocaleChinese = false,
        frequencyIndex = frequencyIndex,
        options = options,
    )

    @Test
    fun `lemma fallback resolves an inflected form`() {
        val result = resolve(
            listOf("He ran home."),
            listOf(WordAnnotationItem("run", 1, "move fast")),
        )

        assertEquals(1, result.size)
        val annotation = result.single().annotation
        assertEquals("ran", annotation.surface)
        assertEquals(0, annotation.paragraphIndex)
        assertEquals(3, annotation.start)
        assertEquals(6, annotation.end)
        assertEquals("move fast", annotation.hint)
        assertTrue(result.single().viaLemmaFallback)
        assertEquals(FrequencyBand.LESS_COMMON, result.single().frequencyBand)
    }

    @Test
    fun `exact match still wins over the fallback`() {
        val result = resolve(
            listOf("He run home."),
            listOf(WordAnnotationItem("run", 1, "move fast")),
        )

        assertEquals(1, result.size)
        assertEquals("run", result.single().annotation.surface)
        assertEquals(3, result.single().annotation.start)
        assertFalse(result.single().viaLemmaFallback)
        assertEquals(FrequencyBand.VERY_COMMON, result.single().frequencyBand)
    }

    @Test
    fun `occurrence counts across the inflection family`() {
        val result = resolve(
            listOf("He ran, then runs."),
            listOf(WordAnnotationItem("run", 2, "move fast")),
        )

        assertEquals(1, result.size)
        assertEquals("runs", result.single().annotation.surface)
        assertEquals(13, result.single().annotation.start)
        assertEquals(17, result.single().annotation.end)
        assertTrue(result.single().viaLemmaFallback)
    }

    @Test
    fun `capitalized forms at a sentence start are matched`() {
        val result = resolve(
            listOf("Ran home."),
            listOf(WordAnnotationItem("run", 1, "move fast")),
        )

        assertEquals(1, result.size)
        assertEquals("Ran", result.single().annotation.surface)
        assertEquals(0, result.single().annotation.start)
        assertEquals(3, result.single().annotation.end)
    }

    @Test
    fun `a base form inside a longer word is not matched`() {
        val result = resolve(
            listOf("The article is here."),
            listOf(WordAnnotationItem("art", 1, "craft")),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a missing exact occurrence is not reinterpreted through inflections`() {
        val result = resolve(
            listOf("He ran."),
            listOf(WordAnnotationItem("ran", 2, "past of run")),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `lemma fallback can be disabled`() {
        val result = resolve(
            listOf("He ran home."),
            listOf(WordAnnotationItem("run", 1, "move fast")),
            options = WordAnnotationHybrid.Options(lemmaFallback = false),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `frequency first keeps the harder word under a tight cap`() {
        val paragraphs = listOf("The committee was untenable.")
        val items = listOf(
            WordAnnotationItem("committee", 1, "a group"),
            WordAnnotationItem("untenable", 1, "not defensible"),
        )

        val reading = resolve(
            paragraphs,
            items,
            options = WordAnnotationHybrid.Options(maxHintsPerChapter = 1),
        )
        assertEquals("committee", reading.single().annotation.surface)

        val hardest = resolve(
            paragraphs,
            items,
            options = WordAnnotationHybrid.Options(
                frequencyFirst = true,
                maxHintsPerChapter = 1,
            ),
        )
        assertEquals("untenable", hardest.single().annotation.surface)
        assertEquals(FrequencyBand.UNKNOWN, hardest.single().frequencyBand)
    }

    @Test
    fun `identical exact and fallback spans are deduplicated`() {
        val paragraphs = listOf("He ran.")
        val items = listOf(
            WordAnnotationItem("ran", 1, "past of run"),
            WordAnnotationItem("run", 1, "move fast"),
        )

        val deduped = resolve(paragraphs, items)
        assertEquals(1, deduped.size)
        assertEquals("ran", deduped.single().annotation.surface)
        assertFalse(deduped.single().viaLemmaFallback)

        val noDedupe = resolve(
            paragraphs,
            items,
            options = WordAnnotationHybrid.Options(dedupeIdenticalSpans = false),
        )
        // Equal-length overlapping spans delete each other in the production rule.
        assertTrue(noDedupe.isEmpty())
    }

    @Test
    fun `chapter cap keeps reading order by default`() {
        val paragraph = (1..13).joinToString(" ") { "word$it" }
        val items = (1..13).map { WordAnnotationItem("word$it", 1, "meaning") }

        val result = resolve(
            listOf(paragraph),
            items,
            frequencyIndex = null,
        )

        assertEquals(12, result.size)
        assertEquals("word1", result.first().annotation.surface)
        assertEquals("word12", result.last().annotation.surface)
    }

    @Test
    fun `chinese exact spans are unaffected`() {
        val result = WordAnnotationHybrid.resolve(
            paragraphs = listOf("他说这不足为奇。"),
            items = listOf(WordAnnotationItem("不足为奇", 1, "不值得惊讶")),
            uiLocaleChinese = true,
            frequencyIndex = frequency,
        )

        assertEquals(1, result.size)
        assertEquals("不足为奇", result.single().annotation.surface)
        assertEquals(3, result.single().annotation.start)
        assertEquals(7, result.single().annotation.end)
        assertFalse(result.single().viaLemmaFallback)
        assertEquals(FrequencyBand.UNKNOWN, result.single().frequencyBand)
    }

    @Test
    fun `multiword bases do not get an inflection fallback`() {
        val result = resolve(
            listOf("She came to terms with it."),
            listOf(WordAnnotationItem("come to terms", 1, "accept")),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `hint length cap still applies`() {
        val nineWords = (1..9).joinToString(" ") { "w$it" }
        val result = resolve(
            listOf("The proposal was untenable."),
            listOf(WordAnnotationItem("untenable", 1, nineWords)),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `unknown surface gets the unknown band`() {
        val result = resolve(
            listOf("The proposal was untenable."),
            listOf(WordAnnotationItem("untenable", 1, "not defensible")),
        )

        assertEquals(1, result.size)
        assertEquals(FrequencyBand.UNKNOWN, result.single().frequencyBand)
        assertFalse(result.single().viaLemmaFallback)
    }
}
