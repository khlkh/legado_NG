package io.legado.app.help.annotation

/**
 * Wave 1 data model for AI Word Gloss.
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
    /**
     * Bump when the prompt, JSON contract, or candidate organization changes;
     * part of the cache key. 9 introduced per-chapter windowed discovery;
     * 10 added model difficulty/contextual metadata for level-aware selection.
     */
    const val SCHEMA_VERSION = 10

    const val MIN_WORD_ANNOTATION_HINT_CAP = 8
    const val MAX_WORD_ANNOTATION_HINT_CAP = 144
    const val DEFAULT_WORD_ANNOTATION_HINT_CAP = 34

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
 * Five reader levels for Word Gloss. The Gutenberg floor is a safety filter;
 * model difficulty (when present) is the primary level signal. Multiword
 * expressions and unknown-rank words stay eligible. The per-book display cap
 * is separate.
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
    /** Model estimate, 1 (everyday) .. 5 (exceptional/archaic). */
    val difficulty: Int? = null,
    /** True when a common surface has an unusual contextual/idiomatic meaning. */
    val contextual: Boolean = false,
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
     * Generated-frequency rank of the surface's rarest token, or null when the
     * asset does not know one of its tokens. Used only for the local level
     * floor (single words) and rarest-first ordering; layout ignores it.
     */
    val rank: Int? = null,
    /**
     * Display-only coverage bucket (0..3 quartiles of the canonical passage).
     * Layout ignores it; [WordAnnotationFilter] uses it to keep a chapter's
     * annotations spread across its sections.
     */
    val coverageBucket: Int = 0,
    val difficulty: Int? = null,
    val contextual: Boolean = false,
)
