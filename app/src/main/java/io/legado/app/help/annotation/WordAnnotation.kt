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

    /**
     * Superset analysis: the model returns this many candidates spanning all
     * difficulty levels; the reader's level floor and per-book cap then filter
     * locally, so changing them never re-calls the model.
     */
    const val MAX_SUPERSET_ITEMS = 21

    const val MIN_WORD_ANNOTATION_HINT_CAP = 5
    const val MAX_WORD_ANNOTATION_HINT_CAP = 55
    const val DEFAULT_WORD_ANNOTATION_HINT_CAP = 13

    /** Hint length cap for non-Chinese UI locales, in whitespace-separated words. */
    const val MAX_HINT_WORDS = 8

    /** English-hint length cap when the book text is Chinese, in words. */
    const val MAX_HINT_WORDS_CHINESE_BOOK = 6

    /** Hint length cap when the app UI locale is Chinese, in characters. */
    const val MAX_HINT_CHINESE_CHARS = 16

    /** Version of the generated frequency rank asset; part of the cache key. */
    const val FREQUENCY_VERSION = "gutenberg-2006-04"

    const val MIN_WORD_ANNOTATION_LEVEL = 1
    const val MAX_WORD_ANNOTATION_LEVEL = 5
    const val DEFAULT_WORD_ANNOTATION_LEVEL = 4

    /**
     * Prototype switch for the fusion resolver: after exact matching fails,
     * expand a returned base form through the English inflection engine, rank
     * hints by the generated Gutenberg frequency band before the chapter cap,
     * and load the rank asset. Not a user setting; off keeps the Wave 1
     * exact-surface contract and reading-order cap.
     */
    const val USE_FUSION_PROTOTYPE = false
}

/**
 * Five reader levels for Word Wise. One slider drives three things:
 *
 * - [floorFor]: soft gate. Only single-word hints whose every token rank is
 *   below the floor are dropped; multiword expressions and unknown-rank words
 *   always stay eligible (the model already selected them).
 * - [promptGuidance]: level-specific selection/density instruction appended to
 *   the shared prompt; it overrides the general "stay sparse" rules.
 *
 * The per-chapter hint cap is a separate per-book setting
 * (WordAnnotationConfig.DEFAULT_WORD_ANNOTATION_HINT_CAP).
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

    fun promptGuidance(level: Int, uiLocaleChinese: Boolean): String {
        val (en, zh) = when (normalize(level)) {
            1 -> (
                "Target words and expressions beyond basic everyday vocabulary, " +
                    "including common content words. Do not skip a word only because it is common. " +
                    "Explain in very short, simple sentences; do not use idioms in the hint."
                ) to
                ("只注释超出基础日常词汇的词和表达，包括常见实词；不要仅仅因为一个词常见就跳过它。" +
                    "用非常简短、简单的句子解释；提示中不要使用习语。")
            2 -> (
                "Target words and expressions beyond everyday vocabulary. " +
                    "Explain in simple English."
                ) to
                ("只注释超出日常词汇的词和表达。用简单的中文解释。")
            3 -> (
                "Target intermediate and advanced vocabulary and useful expressions. " +
                    "Explain in clear, accessible language."
                ) to
                ("注释中高级词汇和有价值的表达。用清晰易懂的中文解释。")
            4 -> (
                "Target advanced vocabulary, idioms, phrasal verbs, and expressions whose " +
                    "contextual meanings are not obvious. Explain concisely and precisely."
                ) to
                ("注释高级词汇、习语、短语动词和语境含义不明显的表达。用简洁准确的中文解释。")
            else -> (
                "Target rare, literary, or specialist vocabulary only. Skip anything a fluent " +
                    "reader would already know. Explain precisely and sophisticatedly."
                ) to
                ("只注释生僻、文学性或专业词汇。跳过流利读者已掌握的词汇。用精准、讲究的中文解释。")
        }
        return if (uiLocaleChinese) zh else en
    }

    /**
     * Level-independent selection for the superset cache: the model returns a
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
