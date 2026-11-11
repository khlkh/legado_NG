package io.legado.app.help.annotation

import kotlin.math.abs
import kotlin.math.ceil

/**
 * Local, model-free filtering of a chapter's candidate pool, English only.
 *
 * The pool stores candidate surfaces with their Gutenberg rank (1 = most
 * common, higher = rarer, null = unknown). The reader's level floor drops
 * known single words below the floor. The display cap counts **unique
 * surfaces**, not occurrences; each selected surface keeps at most two
 * occurrences. When the resolver supplies chapter quartiles, unique-surface
 * selection reserves a small quota per quartile before filling the rest by
 * rarity, so a rare cluster in the opening does not consume the whole display
 * budget.
 */
object WordAnnotationFilter {

    private val SINGLE_TOKEN = Regex("^[A-Za-z]+(?:['’][A-Za-z]+)?$")

    private data class SurfaceGroup(
        val surface: String,
        val annotations: List<WordAnnotation>,
        val buckets: Set<Int>,
        val rank: Int,
        val difficulty: Int?,
        val contextual: Boolean,
        val first: WordAnnotation,
    )

    fun filter(
        annotations: List<WordAnnotation>,
        level: Int,
        cap: Int,
        maxOccurrencesPerSurface: Int = DEFAULT_OCCURRENCES_PER_SURFACE,
        coverageBuckets: Int = 0,
    ): List<WordAnnotation> {
        if (annotations.isEmpty()) return emptyList()
        val limitedCap = cap.coerceAtLeast(1)
        val occurrenceCap = maxOccurrencesPerSurface.coerceAtLeast(1)
        val floor = WordAnnotationLevels.floorFor(level)
        val survivors = annotations.filter { passesFloor(it.surface, it.rank, floor) }
        if (survivors.isEmpty()) return emptyList()

        val groups = survivors
            .groupBy { it.surface }
            .values
            .map { group ->
                val ordered = group.sortedWith(compareBy({ it.paragraphIndex }, { it.start }))
                SurfaceGroup(
                    surface = ordered.first().surface,
                    annotations = ordered,
                    buckets = ordered.mapTo(LinkedHashSet()) { it.coverageBucket },
                    rank = group.maxOf { it.rank ?: Int.MIN_VALUE },
                    difficulty = ordered.first().difficulty,
                    contextual = ordered.any { it.contextual },
                    first = ordered.first(),
                )
            }
        val bySurface = groups.associateBy { it.surface }
        // Surface -> occurrence bucket for which it was selected.
        val selected = LinkedHashMap<String, Int>()

        // Quotas only make sense when the display budget can give every
        // bucket at least two slots. Tiny caps stay rarity-first so a later
        // common word cannot push out an opening rare word.
        if (coverageBuckets > 0 && limitedCap >= coverageBuckets * 2) {
            val quota = ceil(limitedCap.toDouble() / coverageBuckets).toInt().coerceAtLeast(1)
            for (bucket in 0 until coverageBuckets) {
                if (selected.size >= limitedCap) break
                val take = minOf(quota, limitedCap - selected.size)
                groups.asSequence()
                    .filter { it.surface !in selected && bucket in it.buckets }
                    .sortedWith(levelOrder(level))
                    .take(take)
                    .forEach { selected[it.surface] = bucket }
            }
        }
        if (selected.size < limitedCap) {
            groups.asSequence()
                .filter { it.surface !in selected }
                .sortedWith(levelOrder(level))
                .take(limitedCap - selected.size)
                .forEach { selected[it.surface] = it.first.coverageBucket }
        }

        return selected.flatMap { (surface, bucket) ->
            chooseOccurrences(bySurface.getValue(surface).annotations, occurrenceCap, bucket)
        }.sortedWith(compareBy({ it.paragraphIndex }, { it.start }))
    }

    /**
     * Model difficulty is the primary level signal; frequency rank breaks
     * ties. A contextual/idiomatic use gets one level of priority. Candidates
     * from older pools without difficulty fall back to rank order.
     */
    private fun levelOrder(level: Int): Comparator<SurfaceGroup> =
        compareBy<SurfaceGroup> { it.levelScore(level) }
            .thenByDescending { it.rank }
            .thenBy { it.first.paragraphIndex }
            .thenBy { it.first.start }

    private fun SurfaceGroup.levelScore(level: Int): Int {
        val estimated = difficulty ?: return Int.MAX_VALUE
        var score = abs(estimated - level)
        if (contextual) score = (score - 1).coerceAtLeast(0)
        return score
    }

    /**
     * Keep at most [cap] occurrences for one expression. The first occurrence
     * is chosen in the bucket that earned this surface its display slot; the
     * second prefers a different bucket.
     */
    private fun chooseOccurrences(
        group: List<WordAnnotation>,
        cap: Int,
        preferredBucket: Int,
    ): List<WordAnnotation> {
        val ordered = group.sortedWith(compareBy({ it.paragraphIndex }, { it.start }))
        if (ordered.isEmpty()) return emptyList()
        val first = ordered.firstOrNull { it.coverageBucket == preferredBucket } ?: ordered.first()
        if (cap <= 1 || ordered.size == 1) return listOf(first)
        val second = ordered.firstOrNull {
            it !== first && it.coverageBucket != first.coverageBucket
        } ?: ordered.lastOrNull { it !== first }
        return listOfNotNull(first, second)
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
