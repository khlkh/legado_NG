package io.legado.app.help.annotation

/**
 * Resolves a chapter's annotations locally from that chapter's candidate pool.
 *
 * Every exact, case-sensitive occurrence of each pool surface is matched in the
 * chapter's canonical paragraphs, using the same boundary rules as the model
 * path. Cross-paragraph matches are dropped, the longer-wins/equal-drops
 * overlap rule is applied, and the survivors are then filtered by the reader's
 * level floor, unique-surface cap, and occurrence cap ([WordAnnotationFilter]).
 */
object WordAnnotationPoolResolver {

    fun resolveChapter(
        paragraphs: List<WordAnnotationAnalyzer.CanonicalParagraph>,
        items: List<WordAnnotationPoolItem>,
        level: Int,
        cap: Int,
    ): List<WordAnnotation> {
        if (paragraphs.isEmpty() || items.isEmpty()) return emptyList()
        val floor = WordAnnotationLevels.floorFor(level)
        // Expand rarest first so the expansion budget cannot be consumed by a
        // common surface that happens to sit early in pool order.
        val eligible = items
            .filter { WordAnnotationFilter.passesFloor(it.surface, it.rank, floor) }
            .sortedWith(compareByDescending<WordAnnotationPoolItem> { it.rank ?: Int.MIN_VALUE })
        if (eligible.isEmpty()) return emptyList()
        val windows = WordAnnotationResolver.paragraphWindows(paragraphs.map { it.text })
        val joined = windows.joinToString(separator = "\n") { it.text }
        val resolved = ArrayList<WordAnnotation>(eligible.size * 4)
        // Per-chapter candidates are bounded, but a single common candidate
        // can still have many occurrences. Keep enough slots for the unique
        // surface cap and its occurrence cap without letting one surface
        // consume the whole expansion.
        val expandLimit = maxOf(cap * 8, 256)
        outer@ for (item in eligible) {
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
                if (resolved.size >= expandLimit) break@outer
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
