package io.legado.app.help.annotation

/**
 * Local, model-free filtering of the superset cache.
 *
 * The cache stores one broad candidate pool per chapter (up to
 * [WordAnnotationConfig.MAX_SUPERSET_ITEMS] items spanning all difficulty
 * levels). The reader's level floor and per-book cap are applied here at attach
 * time, so changing either re-filters instantly and never re-calls the model.
 *
 * The frequency floor and rank ordering are English-only and must never be
 * mixed into a CJK pool. For Chinese books ([rankAware] false) the pool is kept
 * in reading order and only the per-book cap applies.
 */
object WordAnnotationFilter {

    private val SINGLE_TOKEN = Regex("^[A-Za-z]+(?:['’][A-Za-z]+)?$")

    fun filter(
        annotations: List<WordAnnotation>,
        level: Int,
        cap: Int,
        rankAware: Boolean = true,
    ): List<WordAnnotation> {
        if (annotations.isEmpty()) return emptyList()
        val limitedCap = cap.coerceAtLeast(1)
        if (!rankAware) {
            // CJK pool: no English frequency floor, no rank ordering.
            return annotations
                .sortedWith(compareBy({ it.paragraphIndex }, { it.start }))
                .take(limitedCap)
        }
        val floor = WordAnnotationLevels.floorFor(level)
        val survivors = annotations.filter { passesFloor(it.surface, it.rank, floor) }
        // Rarest first (lower rank = rarer), unknown-rank last, then reading order.
        return survivors
            .sortedWith(
                compareBy<WordAnnotation> { it.rank ?: Int.MAX_VALUE }
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
