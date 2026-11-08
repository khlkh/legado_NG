package io.legado.app.help.annotation

/**
 * Resolves model items against the canonical passage text.
 *
 * The input paragraphs are the measured plain paragraph strings, in reading
 * order. They are joined with a single '\n' separator for occurrence counting
 * only; the separator belongs to neither paragraph, and a match that covers it
 * is dropped. Stored offsets are UTF-16 offsets inside one paragraph.
 *
 * Rules, in order:
 * 1. Drop items with blank text/hint, non-positive occurrence, or a hint over
 *    the locale cap (8 words, or 16 characters when the UI locale is Chinese).
 * 2. Scan for exact, case-sensitive, non-overlapping matches from the start.
 * 3. A match whose edge is a Unicode letter or decimal digit counts only when
 *    the character just outside that edge is not a letter or digit. CJK
 *    ideographs are exempt so exact Chinese spans work inside continuous text.
 * 4. Take the occurrence-th surviving match; drop the item if it does not exist.
 * 5. In one round, drop every overlapping span pair's shorter span, or both
 *    spans when lengths are equal. Independent of model order.
 * 6. Sort by paragraph index and start; keep at most MAX_HINTS_PER_CHAPTER.
 */
object WordAnnotationResolver {

    fun resolve(
        paragraphs: List<String>,
        items: List<WordAnnotationItem>,
        uiLocaleChinese: Boolean,
        maxHintsPerChapter: Int = WordAnnotationConfig.MAX_HINTS_PER_CHAPTER,
    ): List<WordAnnotation> {
        if (paragraphs.isEmpty() || items.isEmpty()) return emptyList()

        val windows = paragraphWindows(paragraphs)
        val joined = windows.joinToString(separator = "\n") { it.text }

        val accepted = ArrayList<WordAnnotation>(items.size)
        for (item in items) {
            if (!hintWithinCap(item.hint, uiLocaleChinese)) continue
            val globalStart = findOccurrence(joined, item.text, item.occurrence) ?: continue
            val window = windows.firstOrNull {
                globalStart >= it.globalStart && globalStart + item.text.length <= it.globalEnd
            } ?: continue // crosses a paragraph join, or starts inside the separator
            accepted += WordAnnotation(
                paragraphIndex = window.index,
                start = globalStart - window.globalStart,
                end = globalStart - window.globalStart + item.text.length,
                surface = item.text,
                hint = item.hint,
            )
        }

        return dropOverlaps(accepted)
            .sortedWith(compareBy({ it.paragraphIndex }, { it.start }))
            .take(maxHintsPerChapter)
    }

    fun hintWithinCap(hint: String, uiLocaleChinese: Boolean): Boolean {
        if (hint.isBlank()) return false
        return if (uiLocaleChinese) {
            hint.length <= WordAnnotationConfig.MAX_HINT_CHINESE_CHARS
        } else {
            hint.trim().split(WHITESPACE).size <= WordAnnotationConfig.MAX_HINT_WORDS
        }
    }

    private val WHITESPACE = Regex("\\s+")

    private class ParagraphWindow(val index: Int, val text: String, val globalStart: Int) {
        val globalEnd: Int get() = globalStart + text.length
    }

    private fun paragraphWindows(paragraphs: List<String>): List<ParagraphWindow> {
        val windows = ArrayList<ParagraphWindow>(paragraphs.size)
        var globalStart = 0
        for ((index, text) in paragraphs.withIndex()) {
            windows += ParagraphWindow(index, text, globalStart)
            globalStart += text.length + 1 // one '\n' separator between paragraphs
        }
        return windows
    }

    /**
     * Returns the global UTF-16 offset of the [occurrence]-th surviving match,
     * or null when that occurrence does not exist.
     */
    internal fun findOccurrence(passage: String, text: String, occurrence: Int): Int? {
        if (text.isEmpty() || occurrence <= 0) return null
        var index = 0
        var seen = 0
        while (index + text.length <= passage.length) {
            if (passage.regionMatches(index, text, 0, text.length)) {
                if (boundaryOk(passage, index, text)) {
                    seen++
                    if (seen == occurrence) return index
                    // A counted match consumes its span; matches are non-overlapping.
                    index += text.length
                } else {
                    // Not a word-boundary match; step one char to find the next start.
                    index++
                }
            } else {
                index++
            }
        }
        return null
    }

    internal fun boundaryOk(passage: String, start: Int, text: String): Boolean {
        val first = text.first()
        val last = text.last()
        if (isBoundarySensitive(first)) {
            val before = passage.getOrNull(start - 1)
            if (before != null && isBoundarySensitive(before)) return false
        }
        if (isBoundarySensitive(last)) {
            val after = passage.getOrNull(start + text.length)
            if (after != null && isBoundarySensitive(after)) return false
        }
        return true
    }

    /** Letters and digits participate in word tokens; CJK ideographs do not. */
    internal fun isBoundarySensitive(char: Char): Boolean {
        if (char.isDigit()) return true
        if (!char.isLetter()) return false
        return !isCjk(char)
    }

    private fun isCjk(char: Char): Boolean {
        val code = char.code
        return code in 0x3400..0x4DBF || // CJK Extension A
            code in 0x4E00..0x9FFF || // CJK Unified Ideographs
            code in 0xF900..0xFAFF // CJK Compatibility Ideographs
    }

    private fun dropOverlaps(annotations: List<WordAnnotation>): List<WordAnnotation> {
        val drop = HashSet<Int>()
        for (i in annotations.indices) {
            for (j in i + 1 until annotations.size) {
                val a = annotations[i]
                val b = annotations[j]
                if (a.paragraphIndex != b.paragraphIndex) continue
                if (a.start >= b.end || b.start >= a.end) continue
                val lengthA = a.end - a.start
                val lengthB = b.end - b.start
                when {
                    lengthA > lengthB -> drop += j
                    lengthB > lengthA -> drop += i
                    else -> {
                        drop += i
                        drop += j
                    }
                }
            }
        }
        return annotations.filterIndexed { index, _ -> index !in drop }
    }
}
