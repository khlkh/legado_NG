package io.legado.app.help.annotation

/**
 * Wave 1 data model for AI Word Wise.
 *
 * [WordAnnotationItem] is what the model returns: an exact surface string, the
 * 1-based occurrence of that string inside the requested passage, and a short
 * contextual hint. The model never returns offsets.
 *
 * [WordAnnotation] is the app-resolved result: one paragraph and a UTF-16 range
 * inside that paragraph's measured string. It is intentionally not tied to
 * screen coordinates, fonts, or pagination.
 */
object WordAnnotationConfig {
    /** Bump when the prompt or the JSON contract changes; part of the cache key. */
    const val SCHEMA_VERSION = 8

    /** Wave 1 keeps at most this many resolved hints per chapter, after all other rules. */
    const val MAX_HINTS_PER_CHAPTER = 12

    const val MIN_WORD_ANNOTATION_HINT_CAP = 5
    const val MAX_WORD_ANNOTATION_HINT_CAP = 55
    const val DEFAULT_WORD_ANNOTATION_HINT_CAP = 13

    /** Hint length cap for non-Chinese UI locales, in whitespace-separated words. */
    const val MAX_HINT_WORDS = 8

    /** Hint length cap when the app UI locale is Chinese, in characters. */
    const val MAX_HINT_CHINESE_CHARS = 16

    /** Version of the generated frequency rank asset; part of the cache key. */
    const val FREQUENCY_VERSION = "gutenberg-2006-04"

    const val MIN_WORD_ANNOTATION_LEVEL = 1
    const val MAX_WORD_ANNOTATION_LEVEL = 5
    const val DEFAULT_WORD_ANNOTATION_LEVEL = 4
}

/**
 * Five reader levels for Word Wise. One slider drives the soft floor: only
 * single-word hints whose rank is known and below the floor are dropped;
 * multiword expressions and unknown-rank words always stay eligible. The
 * per-chapter hint cap is a separate per-book setting.
 */
object WordAnnotationLevels {

    private val FLOORS = mapOf(
        1 to 100,
        2 to 300,
        3 to 1_000,
        4 to 3_000,
        5 to 8_000,
    )

    fun normalize(level: Int): Int = level.coerceIn(
        WordAnnotationConfig.MIN_WORD_ANNOTATION_LEVEL,
        WordAnnotationConfig.MAX_WORD_ANNOTATION_LEVEL,
    )

    fun floorFor(level: Int): Int = FLOORS[normalize(level)] ?: FLOORS.getValue(4)

    /**
     * Level-independent selection for the book glossary: the model returns a
     * broad pool spanning common through rare; the reader filters locally by
     * level floor and per-book cap, so the prompt must not pre-filter by level.
     */
    fun supersetGuidance(uiLocaleChinese: Boolean): String = if (uiLocaleChinese) {
        "覆盖从常见日常词汇到高级、生僻词汇、习语和短语动词的广泛范围。" +
            "读者稍后会按难度筛选，因此不要因为一个词太常见就跳过它。"
    } else {
        "Cover a broad range from common everyday words through advanced and rare " +
            "vocabulary, idioms, and phrasal verbs. The reader filters by difficulty " +
            "later, so do not skip an item only because it is common."
    }
}

data class WordAnnotationItem(
    val text: String,
    val occurrence: Int,
    val hint: String,
)

data class WordAnnotation(
    val paragraphIndex: Int,
    /** Inclusive UTF-16 offset into the paragraph's measured string. */
    val start: Int,
    /** Exclusive UTF-16 offset into the paragraph's measured string. */
    val end: Int,
    /** Equal to paragraph.substring(start, end); kept for cache/verification. */
    val surface: String,
    val hint: String,
    /**
     * Generated-frequency rank of the surface, or null when the asset does not
     * know it (phrase or out-of-vocabulary word). Used only for the local level
     * floor and frequency-first ordering; layout ignores it.
     */
    val rank: Int? = null,
)
