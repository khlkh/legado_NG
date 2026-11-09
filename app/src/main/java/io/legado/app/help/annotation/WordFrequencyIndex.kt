package io.legado.app.help.annotation

import java.util.Locale

/**
 * Fusion prototype: a coarse frequency/difficulty prior for resolved hints.
 *
 * This class deliberately embeds no word list. The caller injects ranks, so a
 * deployment can choose a data source (and its license) independently of the
 * resolver. Rank convention matches a newline-separated frequency list:
 * rank 1 is the most frequent word.
 *
 * For a local experiment:
 * ```
 * val index = File("path/to/ranked-words.txt")
 *     .useLines { WordFrequencyIndex.fromRankedWords(it) }
 * ```
 * The reference lists in the developer's Downloads folder are not copied into
 * the repository or the APK.
 *
 * The bands are only a prior. They must not decide whether a word receives a
 * hint: context decides that, which is the LLM's job. They are useful for
 * ordering hints before a chapter cap, for a future density slider, and for
 * offline evaluation of selection quality.
 */
enum class FrequencyBand {
    /** rank <= 1,000 */
    VERY_COMMON,

    /** rank <= 3,000 */
    COMMON,

    /** rank <= 5,000 */
    LESS_COMMON,

    /** rank <= 10,000 */
    UNCOMMON,

    /** rank <= 20,000 */
    RARE,

    /** Known token with rank > 20,000 */
    VERY_RARE,

    /** Absent from the index, or contains an unknown word. The hardest bucket. */
    UNKNOWN,
}

class WordFrequencyIndex private constructor(
    private val rankByWord: Map<String, Int>,
) {

    /** Number of distinct tokens the index knows. */
    val size: Int get() = rankByWord.size

    /** Rank of a single token, or null when the index does not know it. */
    fun rankOf(word: String): Int? {
        val token = normalizeToken(word) ?: return null
        return rankByWord[token]
    }

    /**
     * Minimum rank across the tokens of a surface, or null when the surface
     * has no letters or any token is absent from the index. Unknown words make
     * the whole surface unknown, matching [bandOf].
     */
    fun minRankOf(surface: String): Int? {
        val tokens = TOKEN.findAll(surface)
            .mapNotNull { normalizeToken(it.value) }
            .toList()
        if (tokens.isEmpty()) return null
        var min = Int.MAX_VALUE
        for (token in tokens) {
            val rank = rankByWord[token] ?: return null
            if (rank < min) min = rank
        }
        return min
    }

    /**
     * Maximum (rarest) token rank of a surface, or null when any token is
     * unknown. Used for pool ordering so a phrase ranks by its hardest token
     * instead of its most common one.
     */
    fun maxRankOf(surface: String): Int? {
        val tokens = TOKEN.findAll(surface)
            .mapNotNull { normalizeToken(it.value) }
            .toList()
        if (tokens.isEmpty()) return null
        var max = Int.MIN_VALUE
        for (token in tokens) {
            val rank = rankByWord[token] ?: return null
            if (rank > max) max = rank
        }
        return max
    }

    /**
     * Band for a word or multiword surface. A phrase is only banded when every
     * token is known; one unknown token makes the whole phrase [FrequencyBand.UNKNOWN],
     * which is the conservative choice for a hint candidate.
     */
    fun bandOf(surface: String): FrequencyBand {
        val tokens = TOKEN.findAll(surface)
            .mapNotNull { normalizeToken(it.value) }
            .toList()
        if (tokens.isEmpty()) return FrequencyBand.UNKNOWN

        var hardest = FrequencyBand.VERY_COMMON
        for (token in tokens) {
            val rank = rankByWord[token] ?: return FrequencyBand.UNKNOWN
            val band = bandForRank(rank)
            if (band.ordinal > hardest.ordinal) hardest = band
        }
        return hardest
    }

    companion object {
        private val TOKEN = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")

        /**
         * Build an index from a frequency-ordered list. The first occurrence of
         * a token wins; blank entries still consume a rank, matching line
         * numbers in a rank file.
         */
        fun fromRankedWords(words: Sequence<String>): WordFrequencyIndex {
            val ranks = HashMap<String, Int>()
            var rank = 0
            for (raw in words) {
                rank++
                val token = normalizeToken(raw) ?: continue
                ranks.putIfAbsent(token, rank)
            }
            return WordFrequencyIndex(ranks)
        }

        /**
         * Build an index from the generated rank asset: comment lines start
         * with '#' and blank lines are ignored; every other line consumes a
         * rank, including tokens the index itself will skip (abbreviations,
         * numbers), so the canonical rank numbers stay stable.
         */
        fun fromRankAssetLines(lines: Sequence<String>): WordFrequencyIndex =
            fromRankedWords(
                lines.filterNot { it.isBlank() || it.trimStart().startsWith("#") }
            )

        /** Build an index directly from word-to-rank pairs; the lowest rank wins. */
        fun fromRankMap(rankByWord: Map<String, Int>): WordFrequencyIndex {
            val ranks = HashMap<String, Int>()
            for ((raw, rank) in rankByWord) {
                if (rank <= 0) continue
                val token = normalizeToken(raw) ?: continue
                val previous = ranks[token]
                if (previous == null || rank < previous) {
                    ranks[token] = rank
                }
            }
            return WordFrequencyIndex(ranks)
        }

        private fun bandForRank(rank: Int): FrequencyBand = when {
            rank <= 1_000 -> FrequencyBand.VERY_COMMON
            rank <= 3_000 -> FrequencyBand.COMMON
            rank <= 5_000 -> FrequencyBand.LESS_COMMON
            rank <= 10_000 -> FrequencyBand.UNCOMMON
            rank <= 20_000 -> FrequencyBand.RARE
            else -> FrequencyBand.VERY_RARE
        }

        private fun normalizeToken(raw: String): String? {
            var token = raw.trim().lowercase(Locale.ROOT)
            when {
                token.endsWith("'s") || token.endsWith("’s") -> token = token.dropLast(2)
                token.endsWith("'") || token.endsWith("’") -> token = token.dropLast(1)
            }
            token = token.replace('’', '\'')
            if (token.isEmpty()) return null
            if (token.any { it !in 'a'..'z' && it != '\'' }) return null
            return token
        }
    }
}
