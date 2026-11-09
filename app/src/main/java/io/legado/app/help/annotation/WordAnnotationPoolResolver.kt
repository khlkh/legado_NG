package io.legado.app.help.annotation

/**
 * Resolves a chapter's annotations locally from the book pool.
 *
 * Every exact, case-sensitive occurrence of each pool surface is matched in the
 * chapter's canonical paragraphs, using the same boundary rules as the model
 * path. Cross-paragraph matches are dropped, the longer-wins/equal-drops
 * overlap rule is applied, and the survivors are then filtered by the reader's
 * level floor and per-book cap ([WordAnnotationFilter]).
 */
object WordAnnotationPoolResolver {

    fun resolveChapter(
        paragraphs: List<WordAnnotationAnalyzer.CanonicalParagraph>,
        items: List<WordAnnotationPoolItem>,
        level: Int,
        cap: Int,
    ): List<WordAnnotation> {
        if (paragraphs.isEmpty() || items.isEmpty()) return emptyList()
        val windows = WordAnnotationResolver.paragraphWindows(paragraphs.map { it.text })
        val joined = windows.joinToString(separator = "\n") { it.text }
        val resolved = ArrayList<WordAnnotation>(items.size * 4)
        for (item in items) {
            var occurrence = 1
            while (true) {
                val start = WordAnnotationResolver.findOccurrence(joined, item.surface, occurrence) ?: break
                occurrence++
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
        }
        // The model path's overlap rule: keep the longer span, drop both on
        // equal length, before the cap.
        val overlapDropped = WordAnnotationResolver.overlappingIndices(resolved)
        val nonOverlapping = resolved.filterIndexed { index, _ -> index !in overlapDropped }
        return WordAnnotationFilter.filter(nonOverlapping, level, cap).map { annotation ->
            annotation.copy(paragraphIndex = paragraphs[annotation.paragraphIndex].sourceIndex)
        }
    }
}
