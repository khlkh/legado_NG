package io.legado.app.help.annotation

/**
 * Book-level Word Gloss pool.
 *
 * The pool grows incrementally: each chapter is analyzed with a small
 * per-chapter call and its items are appended to the book pool. Chapters then
 * resolve their annotations locally from the pool — no per-chapter cache. The
 * reader's level floor and per-book cap filter the resolved list locally.
 *
 * Latin-script books only; CJK and other non-Latin books get no hints.
 */
object WordAnnotationPoolConfig {
    /** The pool target is at least this many items. */
    const val MIN_POOL_TARGET = 120

    /**
     * Cost guard: the target formula is `max(MIN_POOL_TARGET,
     * chapterCount * cap * 0.2)`, capped at this value so very long books
     * cannot balloon the pool (and its token cost).
     */
    const val MAX_POOL_TARGET = 500

    /** chapterCount * cap * this fraction contributes to the target. */
    const val POOL_TARGET_RATIO = 0.2
}

data class WordAnnotationPoolItem(
    val surface: String,
    val hint: String,
    val rank: Int? = null,
)

data class WordAnnotationPoolIdentity(
    val bookUrl: String,
    val schemaVersion: Int = WordAnnotationConfig.SCHEMA_VERSION,
    val frequencyVersion: String = WordAnnotationConfig.FREQUENCY_VERSION,
    val hintCharCap: Boolean = false,
    val providerId: String = "",
    val modelId: String = "",
)
