package io.legado.app.help.annotation

/**
 * Resolves a chapter's annotations locally from the book-level pool.
 *
 * Each pool surface is matched at its first exact, case-sensitive occurrence in
 * the chapter's canonical paragraphs, using the same boundary rules as the
 * model path. Cross-paragraph matches are dropped. The resolved list is then
 * filtered by the reader's level floor and per-book cap ([WordAnnotationFilter]).
 */
object WordAnnotationPoolResolver {

    fun resolveChapter(
        paragraphs: List<WordAnnotationAnalyzer.CanonicalParagraph>,
        items: List<WordAnnotationPoolItem>,
        level: Int,
        cap: Int,
        rankAware: Boolean,
    ): List<WordAnnotation> {
        if (paragraphs.isEmpty() || items.isEmpty()) return emptyList()
        val windows = WordAnnotationResolver.paragraphWindows(paragraphs.map { it.text })
        val joined = windows.joinToString(separator = "\n") { it.text }
        val resolved = ArrayList<WordAnnotation>(items.size)
        for (item in items) {
            val start = WordAnnotationResolver.findOccurrence(joined, item.surface, 1) ?: continue
            val window = windows.firstOrNull {
                start >= it.globalStart && start + item.surface.length <= it.globalEnd
            } ?: continue // crosses a paragraph join
            resolved += WordAnnotation(
                paragraphIndex = window.index,
                start = start - window.globalStart,
                end = start - window.globalStart + item.surface.length,
                surface = item.surface,
                hint = item.hint,
                rank = item.rank,
            )
        }
        return WordAnnotationFilter.filter(resolved, level, cap, rankAware).map { annotation ->
            annotation.copy(paragraphIndex = paragraphs[annotation.paragraphIndex].sourceIndex)
        }
    }
}
