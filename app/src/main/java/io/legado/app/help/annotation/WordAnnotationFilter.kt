package io.legado.app.help.annotation

/**
 * Local, model-free filtering of the book pool, English only.
 *
 * The pool stores candidate surfaces with their Gutenberg rank (1 = most
 * common, higher = rarer, null = unknown). The reader's level floor drops
 * known single words below the floor; the survivors are ordered rarest first
 * (descending rank, unknown last) and trimmed to the per-book cap.
 */
object WordAnnotationFilter {

    private val SINGLE_TOKEN = Regex("^[A-Za-z]+(?:['’][A-Za-z]+)?$")

    fun filter(
        annotations: List<WordAnnotation>,
        level: Int,
        cap: Int,
    ): List<WordAnnotation> {
        if (annotations.isEmpty()) return emptyList()
        val limitedCap = cap.coerceAtLeast(1)
        val floor = WordAnnotationLevels.floorFor(level)
        val survivors = annotations.filter { passesFloor(it.surface, it.rank, floor) }
        // Rarest first: rank 1 is the most common, higher ranks are rarer;
        // unknown-rank surfaces sort last, then reading order.
        return survivors
            .sortedWith(
                compareByDescending<WordAnnotation> { it.rank ?: Int.MIN_VALUE }
                    .thenBy { it.paragraphIndex }
                    .thenBy { it.start },
            )
            .take(limitedCap)
            .sortedWith(compareBy({ it.paragraphIndex }, { it.start }))
    }

    private fun passesFloor(surface: String, rank: Int?, floor: Int): Boolean {
        if (floor <= 0) return true
        // Phrases pass; only single-word hints with a known rank are gated.
        if (!SINGLE_TOKEN.matches(surface)) return true
        val known = rank ?: return true
        return known >= floor
    }
}
