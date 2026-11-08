package io.legado.app.help.annotation

/**
 * Book-level Word Wise pool.
 *
 * The pool is built once per book from a few sampled chapters and then every
 * chapter resolves its annotations locally from it — no per-chapter model call.
 * The reader's level floor and per-book cap filter the resolved list locally,
 * so changing either re-filters instantly across the whole book.
 */
object WordAnnotationPoolConfig {
    /** Model is asked for at most this many pool items per book. */
    const val MAX_BOOK_POOL_ITEMS = 120

    /** At most this many chapters are sampled to build the pool. */
    const val MAX_POOL_SAMPLE_CHAPTERS = 3

    /** Sampled passage char budget for the pool prompt. */
    const val MAX_POOL_SAMPLE_CHARS = 12_000
}

data class WordAnnotationPoolItem(
    val surface: String,
    val hint: String,
    val rank: Int? = null,
)

data class WordAnnotationPoolIdentity(
    val bookUrl: String,
    val sampleHash: String,
    val schemaVersion: Int = WordAnnotationConfig.SCHEMA_VERSION,
    val frequencyVersion: String = WordAnnotationConfig.FREQUENCY_VERSION,
    val hintCharCap: Boolean = false,
    val providerId: String = "",
    val modelId: String = "",
)
