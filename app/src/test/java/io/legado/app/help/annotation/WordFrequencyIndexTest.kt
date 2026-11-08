package io.legado.app.help.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class WordFrequencyIndexTest {

    private val index = WordFrequencyIndex.fromRankMap(
        mapOf(
            "the" to 1,
            "to" to 8,
            "he" to 10,
            "home" to 400,
            "terms" to 229,
            "committee" to 643,
            "run" to 672,
            "came" to 1_097,
            "reader" to 500,
            "ran" to 4_286,
            "uncommon" to 8_000,
            "rare" to 15_000,
            "zoetrope" to 36_663,
        )
    )

    @Test
    fun `rank bands follow the configured thresholds`() {
        assertEquals(FrequencyBand.VERY_COMMON, index.bandOf("the"))
        assertEquals(FrequencyBand.VERY_COMMON, index.bandOf("committee"))
        assertEquals(FrequencyBand.COMMON, index.bandOf("came"))
        assertEquals(FrequencyBand.LESS_COMMON, index.bandOf("ran"))
        assertEquals(FrequencyBand.UNCOMMON, index.bandOf("uncommon"))
        assertEquals(FrequencyBand.RARE, index.bandOf("rare"))
        assertEquals(FrequencyBand.VERY_RARE, index.bandOf("zoetrope"))
        assertEquals(FrequencyBand.UNKNOWN, index.bandOf("untenable"))
    }

    @Test
    fun `lookup is case insensitive and strips possessives`() {
        assertEquals(FrequencyBand.VERY_COMMON, index.bandOf("The"))
        assertEquals(FrequencyBand.VERY_COMMON, index.bandOf("reader's"))
        assertEquals(FrequencyBand.VERY_COMMON, index.bandOf("reader’s"))
    }

    @Test
    fun `a phrase uses its hardest known token`() {
        // came (COMMON) + to (VERY_COMMON) + terms (VERY_COMMON)
        assertEquals(FrequencyBand.COMMON, index.bandOf("came to terms"))
    }

    @Test
    fun `an unknown content word makes the whole surface unknown`() {
        assertEquals(FrequencyBand.UNKNOWN, index.bandOf("untenable situation"))
        assertEquals(FrequencyBand.UNKNOWN, index.bandOf("不足为奇"))
    }

    @Test
    fun `rank lookup only works for a single token`() {
        assertEquals(4_286, index.rankOf("ran"))
        assertEquals(4_286, index.rankOf("Ran"))
        assertNull(index.rankOf("came to terms"))
        assertNull(index.rankOf("untenable"))
    }

    @Test
    fun `from ranked words assigns one based ranks`() {
        val built = WordFrequencyIndex.fromRankedWords(
            sequenceOf("the", "of", "and", "run")
        )
        assertEquals(1, built.rankOf("the"))
        assertEquals(3, built.rankOf("and"))
        assertEquals(FrequencyBand.VERY_COMMON, built.bandOf("run"))
    }

    @Test
    fun `from rank map keeps the lowest rank for duplicate spellings`() {
        val built = WordFrequencyIndex.fromRankMap(
            mapOf("word" to 5_000, "WORD" to 3_000)
        )
        assertEquals(3_000, built.rankOf("word"))
    }

    @Test
    fun `invalid ranks and tokens are ignored`() {
        val built = WordFrequencyIndex.fromRankMap(
            mapOf("word" to 0, "two words" to 10, "ok" to -1)
        )
        assertNull(built.rankOf("word"))
        assertNull(built.rankOf("two words"))
        assertNull(built.rankOf("ok"))
    }

    @Test
    fun `rank asset lines skip comments and blanks but keep rank positions`() {
        val index = WordFrequencyIndex.fromRankAssetLines(
            sequenceOf("# header", "", "the", "of", "of.", "Gutenberg")
        )
        assertEquals(1, index.rankOf("the"))
        assertEquals(2, index.rankOf("of"))
        // of. is not a token the index can use, but it still consumes rank 3.
        assertNull(index.rankOf("of."))
        assertEquals(4, index.rankOf("Gutenberg"))
    }

    @Test
    fun `generated gutenberg asset has the expected known ranks`() {
        val asset = listOf(
            File("src/main/assets/word_annotation/frequency_rank_2006_04.txt"),
            File("app/src/main/assets/word_annotation/frequency_rank_2006_04.txt"),
        ).firstOrNull { it.isFile }
        assumeTrue("generated frequency asset is missing", asset != null)

        val index = asset!!.useLines { WordFrequencyIndex.fromRankAssetLines(it) }
        assertEquals(1, index.rankOf("the"))
        assertEquals(256, index.rankOf("Gutenberg"))
        assertEquals(280, index.rankOf("thy"))
        assertEquals(14_737, index.rankOf("untenable"))
        assertEquals(20_000, index.rankOf("ussr"))
        assertEquals(36_663, index.rankOf("zoetrope"))
    }
}
