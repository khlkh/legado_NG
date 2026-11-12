package io.legado.app.help.annotation

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Reader-facing annotation density. */
enum class WordAnnotationDensity(
    val storageValue: Int,
    val multiplier: Double,
) {
    SPARSE(0, 0.75),
    LIGHT(1, 1.00),
    STANDARD(2, 1.25),
    DENSE(3, 1.50),
    RICH(4, 1.75);

    companion object {
        val DEFAULT = DENSE

        fun fromStorage(value: Int?): WordAnnotationDensity =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * Pure density policy for Smart Glosses.
 *
 * Display density is content-proportional:
 * `words / 350 * 1.5 * levelMultiplier * densityMultiplier`.
 *
 * Discovery retains a broad, reusable candidate pool up to a fixed safety
 * ceiling. It is independent of the current reading level and display density,
 * so changing either setting re-selects from the existing pool without another
 * model call. Windows are word-based and capped for request cost; the safety
 * ceiling is an engineering guard, not a reader-facing density setting.
 */
object WordAnnotationTargets {

    const val WORDS_PER_HINT = 350.0
    const val BASE_HINTS_PER_350 = 1.5

    /** Safety ceiling for one chapter's retained candidate pool. */
    const val MAX_POOL_CANDIDATES = 600
    const val MAX_DISPLAY = 300

    const val TARGET_WORDS_PER_WINDOW = 3_000
    const val MAX_WINDOWS = 16
    const val COVERAGE_BUCKETS_LARGE = 8
    const val COVERAGE_BUCKETS_SMALL = 4
    const val LARGE_COVERAGE_THRESHOLD = 64

    private val LEVEL_MULTIPLIERS = doubleArrayOf(1.30, 1.15, 1.00, 0.80, 0.60)
    private val WHITESPACE = Regex("\\s+")

    fun levelMultiplier(level: Int): Double =
        LEVEL_MULTIPLIERS[WordAnnotationLevels.normalize(level) - 1]

    fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split(WHITESPACE).count { it.isNotBlank() }
    }

    fun wordCount(texts: Iterable<String>): Int = texts.sumOf(::countWords)

    fun displayTarget(
        words: Int,
        level: Int,
        density: WordAnnotationDensity,
    ): Int {
        if (words <= 0) return 0
        val base = words / WORDS_PER_HINT * BASE_HINTS_PER_350
        return (base * levelMultiplier(level) * density.multiplier)
            .roundToInt()
            .coerceIn(1, MAX_DISPLAY)
    }

    fun windowCount(words: Int): Int =
        ceil(words.toDouble() / TARGET_WORDS_PER_WINDOW)
            .toInt()
            .coerceIn(1, MAX_WINDOWS)

    /** More buckets for long chapters; small targets stay simple. */
    fun coverageBuckets(displayTarget: Int): Int =
        if (displayTarget >= LARGE_COVERAGE_THRESHOLD) {
            COVERAGE_BUCKETS_LARGE
        } else {
            COVERAGE_BUCKETS_SMALL
        }
}
