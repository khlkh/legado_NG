package io.legado.app.help.annotation

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Reader-facing annotation density. */
enum class WordAnnotationDensity(
    val storageValue: Int,
    val multiplier: Double,
) {
    SPARSE(0, 0.75),
    STANDARD(1, 1.0),
    DENSE(2, 1.25);

    companion object {
        fun fromStorage(value: Int?): WordAnnotationDensity =
            entries.firstOrNull { it.storageValue == value } ?: STANDARD
    }
}

/**
 * Pure density policy for Word Gloss.
 *
 * Display density is content-proportional:
 * `words / 350 * 1.5 * levelMultiplier * densityMultiplier`.
 *
 * Discovery is deliberately larger than display and independent of the
 * current level/density, so changing either setting can re-select from the
 * existing candidates without another model call. Windows are word-based and
 * capped for request cost; the safety ceilings are engineering guards, not
 * reader-facing density settings.
 */
object WordAnnotationTargets {

    const val WORDS_PER_HINT = 350.0
    const val BASE_HINTS_PER_350 = 1.5
    const val DISCOVERY_FACTOR = 1.75

    const val MIN_DISCOVERY = 30
    const val MAX_DISCOVERY = 600
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

    fun discoveryTarget(words: Int): Int {
        if (words <= 0) return 0
        val base = words / WORDS_PER_HINT * BASE_HINTS_PER_350
        return ceil(base * DISCOVERY_FACTOR)
            .toInt()
            .coerceIn(MIN_DISCOVERY, MAX_DISCOVERY)
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
