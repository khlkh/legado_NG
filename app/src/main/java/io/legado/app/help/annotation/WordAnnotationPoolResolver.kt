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

    /** Canonical chapter quartiles used only for display coverage. */
    const val COVERAGE_BUCKETS = 4

    data class Stats(
        val poolItems: Int = 0,
        val floorRejected: Int = 0,
        /** Exact matches before overlap resolution. */
        val matchedOccurrences: Int = 0,
        /** Exact matches surviving overlap resolution, before display filtering. */
        val resolvedOccurrences: Int = 0,
        val overlapRejected: Int = 0,
        val filter: WordAnnotationFilter.Stats = WordAnnotationFilter.Stats(),
    )

    data class Result(
        val annotations: List<WordAnnotation>,
        val stats: Stats,
    )

    fun resolveChapter(
        paragraphs: List<WordAnnotationAnalyzer.CanonicalParagraph>,
        items: List<WordAnnotationPoolItem>,
        level: Int,
        cap: Int,
        coverageBuckets: Int = COVERAGE_BUCKETS,
    ): List<WordAnnotation> = resolveChapterWithStats(
        paragraphs = paragraphs,
        items = items,
        level = level,
        cap = cap,
        coverageBuckets = coverageBuckets,
    ).annotations

    fun resolveChapterWithStats(
        paragraphs: List<WordAnnotationAnalyzer.CanonicalParagraph>,
        items: List<WordAnnotationPoolItem>,
        level: Int,
        cap: Int,
        coverageBuckets: Int = COVERAGE_BUCKETS,
    ): Result {
        val emptyStats = Stats(poolItems = items.size)
        if (paragraphs.isEmpty() || items.isEmpty()) return Result(emptyList(), emptyStats)
        val floor = WordAnnotationLevels.floorFor(level)
        // Expand rarest first so the expansion budget cannot be consumed by a
        // common surface that happens to sit early in pool order.
        val eligible = items
            .filter { WordAnnotationFilter.passesFloor(it.surface, it.rank, floor) }
            .sortedWith(compareByDescending<WordAnnotationPoolItem> { it.rank ?: Int.MIN_VALUE })
        val floorRejected = items.size - eligible.size
        if (eligible.isEmpty()) {
            return Result(emptyList(), emptyStats.copy(floorRejected = floorRejected))
        }
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
                    coverageBucket = ((start.toLong() * COVERAGE_BUCKETS) / joined.length.coerceAtLeast(1))
                        .toInt()
                        .coerceIn(0, COVERAGE_BUCKETS - 1),
                    difficulty = item.difficulty,
                    contextual = item.contextual,
                )
                if (resolved.size >= expandLimit) break@outer
            }
        }
        // The model path's overlap rule: keep the longer span, drop both on
        // equal length, before the cap.
        val overlapDropped = WordAnnotationResolver.overlappingIndices(resolved)
        val nonOverlapping = resolved.filterIndexed { index, _ -> index !in overlapDropped }
        val filtered = WordAnnotationFilter.filterWithStats(
            annotations = nonOverlapping,
            level = level,
            cap = cap,
            coverageBuckets = coverageBuckets,
        )
        return Result(
            annotations = filtered.annotations.map { annotation ->
                annotation.copy(paragraphIndex = paragraphs[annotation.paragraphIndex].sourceIndex)
            },
            stats = Stats(
                poolItems = items.size,
                floorRejected = floorRejected,
                matchedOccurrences = resolved.size,
                resolvedOccurrences = nonOverlapping.size,
                overlapRejected = overlapDropped.size,
                filter = filtered.stats,
            ),
        )
    }
}
