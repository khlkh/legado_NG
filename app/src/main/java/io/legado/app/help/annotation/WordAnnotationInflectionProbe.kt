package io.legado.app.help.annotation

import java.util.Locale

/**
 * Debug-only measurement for the inflection work, not part of the production
 * resolver or the AI selection path.
 *
 * The production contract is exact-surface matching: the prompt asks the model
 * to copy the passage surface, and the analyzer drops any item that does not
 * occur exactly. This probe reports how often that contract misses a form that
 * a conservative forward inflector could reach, so the inflection decision is
 * based on real chapters rather than the prototype's assumptions.
 *
 * It never changes the pool, the prompt, the ranks, or the resolved
 * annotations. Call it only in a debug build and only for measurement.
 */
internal object WordAnnotationInflectionProbe {

    data class Sample(
        val text: String,
        val found: List<String>,
        val caseOnly: Boolean,
    )

    data class PoolGroup(val surfaces: List<String>)

    data class PoolReport(
        val items: Int,
        val groups: Int,
        val extraSurfaces: Int,
        val samples: List<PoolGroup>,
    ) {
        fun summary(): String = "poolItems=$items variantGroups=$groups variantSurfaces=$extraSurfaces"

        fun detail(): String = samples.joinToString(separator = "; ") {
            it.surfaces.joinToString("/")
        }
    }

    data class Report(
        val items: Int,
        val exactHits: Int,
        val exactMisses: Int,
        val inflectionHits: Int,
        val singleFormHits: Int,
        val multiFormHits: Int,
        val caseOnlyHits: Int,
        val phraseMisses: Int,
        val noFormHits: Int,
        val samples: List<Sample>,
    ) {
        fun summary(): String =
            "probeItems=$items exact=$exactHits miss=$exactMisses inflection=$inflectionHits " +
                "single=$singleFormHits multi=$multiFormHits caseOnly=$caseOnlyHits " +
                "phrase=$phraseMisses noForm=$noFormHits"

        fun detail(): String = samples.joinToString(separator = "; ") { sample ->
            val kind = if (sample.caseOnly) "case" else "inflection" +
                if (sample.found.size > 1) "/multi" else ""
            "${sample.text} -> ${sample.found.joinToString("/")} ($kind)"
        }
    }

    /**
     * @param passage the canonical chapter text the analyzer validates against
     * @param items the parsed model items before cap/exact filtering
     */
    fun probe(
        passage: String,
        items: List<WordAnnotationItem>,
        maxSamples: Int = 6,
    ): Report {
        var exactHits = 0
        var exactMisses = 0
        var inflectionHits = 0
        var singleFormHits = 0
        var multiFormHits = 0
        var caseOnlyHits = 0
        var phraseMisses = 0
        var noFormHits = 0
        val samples = ArrayList<Sample>(maxSamples)

        for (item in items) {
            if (WordAnnotationResolver.findOccurrence(passage, item.text, 1) != null) {
                exactHits++
                continue
            }
            exactMisses++
            if (item.text.any { it.isWhitespace() }) {
                phraseMisses++
                continue
            }
            val forms = WordAnnotationInflector.candidateForms(item.text)
            if (forms.isEmpty()) {
                noFormHits++
                continue
            }
            val found = forms.filter { WordAnnotationResolver.findOccurrence(passage, it, 1) != null }
            if (found.isEmpty()) {
                noFormHits++
                continue
            }
            val base = item.text.lowercase(Locale.ROOT)
            val morphology = found.filter { it.lowercase(Locale.ROOT) != base }
            if (morphology.isEmpty()) {
                caseOnlyHits++
                if (samples.size < maxSamples) samples += Sample(item.text, found, caseOnly = true)
                continue
            }
            inflectionHits++
            val distinct = morphology.map { it.lowercase(Locale.ROOT) }.toSet()
            if (distinct.size == 1) singleFormHits++ else multiFormHits++
            if (samples.size < maxSamples) samples += Sample(item.text, morphology, caseOnly = false)
        }

        return Report(
            items = items.size,
            exactHits = exactHits,
            exactMisses = exactMisses,
            inflectionHits = inflectionHits,
            singleFormHits = singleFormHits,
            multiFormHits = multiFormHits,
            caseOnlyHits = caseOnlyHits,
            phraseMisses = phraseMisses,
            noFormHits = noFormHits,
            samples = samples,
        )
    }

    /**
     * Counts pool surfaces that share a generated morphological form. This is
     * a measurement of duplicate/variant pool entries, not a production
     * grouping: ambiguous families (for example see/saw) appear here and need
     * human review.
     */
    fun poolVariants(
        items: List<WordAnnotationPoolItem>,
        maxSamples: Int = 6,
    ): PoolReport {
        val parent = HashMap<String, String>()
        fun find(value: String): String {
            var root = value
            while (parent[root] != root) root = parent.getValue(root)
            var cursor = value
            while (cursor != root) {
                val next = parent.getValue(cursor)
                parent[cursor] = root
                cursor = next
            }
            return root
        }
        fun union(a: String, b: String) {
            val rootA = find(a)
            val rootB = find(b)
            if (rootA != rootB) parent[rootB] = rootA
        }

        val formSurfaces = HashMap<String, LinkedHashSet<String>>()
        for (item in items) {
            val surface = item.surface
            if (surface.isBlank() || surface.any { it.isWhitespace() }) continue
            val forms = WordAnnotationInflector.candidateForms(surface)
            if (forms.isEmpty()) continue
            parent.getOrPut(surface) { surface }
            for (form in forms) {
                val key = form.lowercase(Locale.ROOT)
                formSurfaces.getOrPut(key) { LinkedHashSet() }.add(surface)
            }
        }
        for (surfaces in formSurfaces.values) {
            val first = surfaces.firstOrNull() ?: continue
            for (surface in surfaces) {
                if (surface != first) union(first, surface)
            }
        }
        val groups = HashMap<String, MutableList<String>>()
        for (surface in parent.keys.toList()) {
            groups.getOrPut(find(surface)) { ArrayList() }.add(surface)
        }
        val variants = groups.values.filter { it.size > 1 }.sortedByDescending { it.size }
        return PoolReport(
            items = items.size,
            groups = variants.size,
            extraSurfaces = variants.sumOf { it.size - 1 },
            samples = variants.take(maxSamples).map { PoolGroup(it.sorted()) },
        )
    }
}
