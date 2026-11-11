package io.legado.app.help.annotation

/**
 * Local, model-free filtering of a chapter's candidate pool, English only.
 *
 * The pool stores candidate surfaces with their Gutenberg rank (1 = most
 * common, higher = rarer, null = unknown). The reader's level floor drops
 * known single words below the floor; the survivors are ordered rarest first
 * (descending rank, unknown last) and trimmed to the per-book unique-surface
 * cap; each unique surface keeps at most
 * [DEFAULT_OCCURRENCES_PER_SURFACE] occurrences.
 */
object WordAnnotationFilter {

    private val SINGLE_TOKEN = Regex("^[A-Za-z]+(?:['’][A-Za-z]+)?$")

    fun filter(
        annotations: List<WordAnnotation>,
        level: Int,
        cap: Int,
        maxOccurrencesPerSurface: Int = DEFAULT_OCCURRENCES_PER_SURFACE,
    ): List<WordAnnotation> {
        if (annotations.isEmpty()) return emptyList()
        val limitedCap = cap.coerceAtLeast(1)
        val occurrenceCap = maxOccurrencesPerSurface.coerceAtLeast(1)
        val floor = WordAnnotationLevels.floorFor(level)
        val survivors = annotations.filter { passesFloor(it.surface, it.rank, floor) }
        // The display cap applies to unique expressions, never to raw
        // occurrences. A repeated surface must not consume multiple slots
        // that could belong to a different expression.
        val unique = survivors
            .groupBy { it.surface }
            .values
            .sortedWith(
                compareByDescending<List<WordAnnotation>> { group ->
                    group.maxOf { it.rank ?: Int.MIN_VALUE }
                }.thenBy { group ->
                    group.minOf { it.paragraphIndex }
                }.thenBy { group ->
                    group.minOf { it.start }
                },
            )
            .take(limitedCap)
            .flatMap { chooseOccurrences(it, occurrenceCap) }
        return unique.sortedWith(compareBy({ it.paragraphIndex }, { it.start }))
    }

    /**
     * Keep at most [cap] occurrences for one expression. Taking the first and
     * last occurrence spreads a repeated expression across the chapter instead
     * of spending every slot on the opening.
     */
    private fun chooseOccurrences(
        group: List<WordAnnotation>,
        cap: Int,
    ): List<WordAnnotation> {
        val ordered = group.sortedWith(compareBy({ it.paragraphIndex }, { it.start }))
        if (cap <= 1 || ordered.size == 1) return ordered.take(cap)
        return listOf(ordered.first(), ordered.last()).distinctBy { it.paragraphIndex to it.start }
    }

    const val DEFAULT_OCCURRENCES_PER_SURFACE = 2

    fun passesFloor(surface: String, rank: Int?, floor: Int): Boolean {
        if (floor <= 0) return true
        // Phrases pass; only single-word hints with a known rank are gated.
        if (!SINGLE_TOKEN.matches(surface)) return true
        val known = rank ?: return true
        return known >= floor
    }
}
