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
    const val SCHEMA_VERSION = 1

    /** Wave 1 keeps at most this many resolved hints per chapter, after all other rules. */
    const val MAX_HINTS_PER_CHAPTER = 12

    /** Hint length cap for non-Chinese UI locales, in whitespace-separated words. */
    const val MAX_HINT_WORDS = 8

    /** Hint length cap when the app UI locale is Chinese, in characters. */
    const val MAX_HINT_CHINESE_CHARS = 16

    /**
     * Prototype switch for the fusion resolver: after exact matching fails,
     * expand a returned base form through the English inflection engine, rank
     * hints by the generated Gutenberg frequency band before the chapter cap,
     * and load the rank asset. Not a user setting; off keeps the Wave 1
     * exact-surface contract and reading-order cap.
     */
    const val USE_FUSION_PROTOTYPE = false
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
)
