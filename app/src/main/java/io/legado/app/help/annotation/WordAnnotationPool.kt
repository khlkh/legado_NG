package io.legado.app.help.annotation

/**
 * Candidate inventory for the book-level Word Gloss glossary.
 *
 * Candidates are stored per chapter and per discovery window. A chapter entry
 * is keyed by its canonical content hash; changing the chapter text invalidates
 * only that chapter. The reader resolves hints for a chapter from that
 * chapter's candidates, not by scanning every candidate in the book.
 *
 * Latin-script books only; CJK and other non-Latin books get no hints.
 */
data class WordAnnotationPoolItem(
    val surface: String,
    val hint: String,
    val rank: Int? = null,
)

data class WordAnnotationChapterPool(
    val chapterIndex: Int,
    val contentHash: String,
    /** Discovery window indexes that returned a valid result. */
    val completedWindows: Set<Int> = emptySet(),
    val items: List<WordAnnotationPoolItem> = emptyList(),
)

data class WordAnnotationPoolState(
    val chapters: Map<Int, WordAnnotationChapterPool> = emptyMap(),
)

data class WordAnnotationPoolIdentity(
    val bookUrl: String,
    val schemaVersion: Int = WordAnnotationConfig.SCHEMA_VERSION,
    val frequencyVersion: String = WordAnnotationConfig.FREQUENCY_VERSION,
    val hintCharCap: Boolean = false,
    val providerId: String = "",
    val modelId: String = "",
)
