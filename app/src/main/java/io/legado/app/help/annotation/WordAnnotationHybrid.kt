package io.legado.app.help.annotation

/**
 * Fusion prototype: exact LLM anchoring, a rule-based lemma fallback, and an
 * optional frequency-band prior.
 *
 * This is intentionally standalone. It does not read a word list, call a model,
 * or change the production analyzer. The caller supplies the model items and,
 * if it has one, a [WordFrequencyIndex]; the prototype returns resolved hints
 * with their band and whether the lemma fallback was used.
 *
 * Order of resolution for every item:
 * 1. Exact, case-sensitive surface match (the production rule).
 * 2. If the exact text does not occur at all, try the inflected forms of the
 *    returned item as a base form ([WordAnnotationInflector]).
 * 3. If the exact text occurs but the requested occurrence does not, drop the
 *    item. A wrong occurrence count is not a reason to reinterpret it.
 * 4. Deduplicate identical spans, then apply the production overlap rule.
 *
 * The frequency band is computed from the actual matched surface, not from the
 * model's base form, so `ran` is ranked as `ran`.
 */
object WordAnnotationHybrid {

    data class Options(
        val lemmaFallback: Boolean = true,
        /**
         * Prototype experiment: when true, the chapter cap keeps the hardest
         * bands first instead of the first hints in reading order. The Wave 1
         * spec keeps reading order, so this defaults to false.
         */
        val frequencyFirst: Boolean = false,
        /**
         * Identical spans can arise when the model returns both a base form and
         * its inflected surface. Prefer the exact match and keep one hint.
         * Equal-length overlaps would otherwise delete both.
         */
        val dedupeIdenticalSpans: Boolean = true,
        val maxHintsPerChapter: Int = WordAnnotationConfig.MAX_HINTS_PER_CHAPTER,
        /**
         * Soft frequency gate. Only single-word hints whose rank is known and
         * below this floor are dropped; multiword expressions and unknown-rank
         * words stay eligible. 0 disables the gate.
         */
        val minRankFloor: Int = 0,
    )

    data class Result(
        val annotation: WordAnnotation,
        val frequencyBand: FrequencyBand,
        val viaLemmaFallback: Boolean,
    )

    data class Resolution(
        val results: List<Result>,
        val gatedByFloor: Int,
    )

    fun resolve(
        paragraphs: List<String>,
        items: List<WordAnnotationItem>,
        uiLocaleChinese: Boolean,
        frequencyIndex: WordFrequencyIndex? = null,
        options: Options = Options(),
        maxHintWords: Int = WordAnnotationConfig.MAX_HINT_WORDS,
    ): List<Result> = resolveDetailed(
        paragraphs, items, uiLocaleChinese, frequencyIndex, options, maxHintWords,
    ).results

    fun resolveDetailed(
        paragraphs: List<String>,
        items: List<WordAnnotationItem>,
        uiLocaleChinese: Boolean,
        frequencyIndex: WordFrequencyIndex? = null,
        options: Options = Options(),
        maxHintWords: Int = WordAnnotationConfig.MAX_HINT_WORDS,
    ): Resolution {
        if (paragraphs.isEmpty() || items.isEmpty()) return Resolution(emptyList(), 0)

        val windows = WordAnnotationResolver.paragraphWindows(paragraphs)
        val joined = windows.joinToString(separator = "\n") { it.text }
        val candidates = ArrayList<Candidate>(items.size)
        var gatedByFloor = 0

        items.forEachIndexed { itemIndex, item ->
            if (!WordAnnotationResolver.hintWithinCap(item.hint, uiLocaleChinese, maxHintWords)) {
                return@forEachIndexed
            }

            val exactStart = WordAnnotationResolver.findOccurrence(joined, item.text, item.occurrence)
            if (exactStart != null) {
                if (!passesFloorGate(joined, exactStart, item.text.length, frequencyIndex, options)) {
                    gatedByFloor++
                    return@forEachIndexed
                }
                addCandidate(
                    candidates, windows, joined, exactStart, item.text.length,
                    item.hint, viaLemmaFallback = false, sourceOrder = itemIndex,
                )
                return@forEachIndexed
            }

            // The exact surface exists, but not at the occurrence the model
            // named. Do not reinterpret the item through its inflections.
            if (WordAnnotationResolver.findOccurrence(joined, item.text, 1) != null) {
                return@forEachIndexed
            }
            if (!options.lemmaFallback) return@forEachIndexed

            val forms = WordAnnotationInflector.candidateForms(item.text)
            if (forms.isEmpty()) return@forEachIndexed
            val match = findFormOccurrence(joined, forms, item.occurrence) ?: return@forEachIndexed
            if (!passesFloorGate(joined, match.start, match.length, frequencyIndex, options)) {
                gatedByFloor++
                return@forEachIndexed
            }
            addCandidate(
                candidates, windows, joined, match.start, match.length,
                item.hint, viaLemmaFallback = true, sourceOrder = itemIndex,
            )
        }

        val deduped = if (options.dedupeIdenticalSpans) dedupe(candidates) else candidates
        val overlapDropped = WordAnnotationResolver.overlappingIndices(
            deduped.map { it.annotation }
        )
        val survivors = deduped.filterIndexed { index, _ -> index !in overlapDropped }

        val results = survivors.map { candidate ->
            Result(
                annotation = candidate.annotation,
                frequencyBand = frequencyIndex?.bandOf(candidate.annotation.surface)
                    ?: FrequencyBand.UNKNOWN,
                viaLemmaFallback = candidate.viaLemmaFallback,
            )
        }

        val readingOrder = compareBy<Result>(
            { it.annotation.paragraphIndex },
            { it.annotation.start },
        )
        val selected = if (options.frequencyFirst) {
            results.sortedWith(
                compareByDescending<Result> { it.frequencyBand.ordinal }.then(readingOrder)
            ).take(options.maxHintsPerChapter)
        } else {
            results.sortedWith(readingOrder).take(options.maxHintsPerChapter)
        }
        return Resolution(selected.sortedWith(readingOrder), gatedByFloor)
    }

    private val SINGLE_TOKEN = Regex("^[A-Za-z]+(?:['’][A-Za-z]+)?$")

    private fun passesFloorGate(
        joined: String,
        start: Int,
        length: Int,
        frequencyIndex: WordFrequencyIndex?,
        options: Options,
    ): Boolean {
        if (options.minRankFloor <= 0 || frequencyIndex == null) return true
        val surface = joined.substring(start, start + length)
        // Phrases pass; only single-word hints are gated, and only when their
        // rank is known. Unknown words are eligible everywhere.
        if (!SINGLE_TOKEN.matches(surface)) return true
        val minRank = frequencyIndex.minRankOf(surface) ?: return true
        return minRank >= options.minRankFloor
    }

    private fun addCandidate(
        into: MutableList<Candidate>,
        windows: List<WordAnnotationResolver.ParagraphWindow>,
        joined: String,
        start: Int,
        length: Int,
        hint: String,
        viaLemmaFallback: Boolean,
        sourceOrder: Int,
    ) {
        val window = windows.firstOrNull {
            start >= it.globalStart && start + length <= it.globalEnd
        } ?: return // crosses a paragraph join
        into += Candidate(
            annotation = WordAnnotation(
                paragraphIndex = window.index,
                start = start - window.globalStart,
                end = start - window.globalStart + length,
                surface = joined.substring(start, start + length),
                hint = hint,
            ),
            viaLemmaFallback = viaLemmaFallback,
            sourceOrder = sourceOrder,
        )
    }

    private fun findFormOccurrence(
        passage: String,
        forms: List<String>,
        occurrence: Int,
    ): FormMatch? {
        if (occurrence <= 0) return null
        var index = 0
        var seen = 0
        while (index < passage.length) {
            var matched: String? = null
            for (form in forms) {
                if (form.isNotEmpty() &&
                    index + form.length <= passage.length &&
                    passage.regionMatches(index, form, 0, form.length) &&
                    WordAnnotationResolver.boundaryOk(passage, index, form)
                ) {
                    matched = form
                    break
                }
            }
            if (matched == null) {
                index++
                continue
            }
            seen++
            if (seen == occurrence) return FormMatch(index, matched.length)
            index += matched.length
        }
        return null
    }

    /** Prefer the exact match, then the earliest model item, for identical spans. */
    private fun dedupe(candidates: List<Candidate>): List<Candidate> {
        val seen = HashSet<SpanKey>()
        val kept = ArrayList<Candidate>(candidates.size)
        candidates
            .sortedWith(compareBy({ it.viaLemmaFallback }, { it.sourceOrder }))
            .forEach { candidate ->
                val key = SpanKey(
                    candidate.annotation.paragraphIndex,
                    candidate.annotation.start,
                    candidate.annotation.end,
                )
                if (seen.add(key)) kept += candidate
            }
        return kept.sortedBy { it.sourceOrder }
    }

    private data class Candidate(
        val annotation: WordAnnotation,
        val viaLemmaFallback: Boolean,
        val sourceOrder: Int,
    )

    private data class FormMatch(val start: Int, val length: Int)

    private data class SpanKey(val paragraphIndex: Int, val start: Int, val end: Int)
}
