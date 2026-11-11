package io.legado.app.help.annotation

import io.legado.app.ui.book.read.page.entities.TextHintRun

/**
 * Pure geometry helpers for the canvas hint band and EPUB overlay labels.
 *
 * The band is reserved on the first visual line that intersects a resolved
 * annotation. Line-local hint ranges stay UTF-16 offsets into [TextLine.text];
 * they are never column indexes. The x-span on screen is computed later by
 * walking the line's columns and summing charData lengths.
 *
 * Labels are visual representations of one annotation: every fragment of a
 * wrapped phrase shares the same hint id. Placement never creates a second
 * annotation, AI record, or interaction state.
 */
object WordAnnotationLayout {

    /** Hint band height as a fraction of the body line text height. */
    const val BAND_RATIO = 0.6f

    /** Hint paint text size as a fraction of the body paint text size. */
    const val HINT_TEXT_SIZE_RATIO = 0.55f

    /** Gap between two labels on the same line, in px. */
    const val LABEL_GAP = 6f

    /** Labels narrower than this after packing are dropped. */
    const val LABEL_MIN_WIDTH = 4f

    /** Gap between a label's bottom and the word's line box, in px. */
    const val LABEL_ABOVE = 4f

    /** Right-edge inset so a label does not sit on the viewport border. */
    const val LABEL_COLUMN_INSET = 8f

    fun bandHeight(textHeight: Float, hasHints: Boolean): Float =
        if (hasHints) textHeight * BAND_RATIO else 0f

    /**
     * Intersections of [annotations] with the visual line range [lineStart, lineEnd),
     * returned as line-local runs. A span that wraps across lines produces a run on
     * every intersecting line; the caller keeps only the first by removing placed
     * annotations from its pending list, except when [pendingAfterLine] keeps a wrap
     * that continues on a new page.
     */
    fun runsForLine(
        annotations: List<WordAnnotation>,
        lineStart: Int,
        lineEnd: Int,
    ): List<TextHintRun> = buildList {
        for (annotation in annotations) {
            val start = maxOf(annotation.start, lineStart)
            val end = minOf(annotation.end, lineEnd)
            if (start < end) {
                add(TextHintRun(start - lineStart, end - lineStart, annotation.hint))
            }
        }
    }

    /**
     * Annotations that can still be placed after [lineEnd].
     *
     * By default only spans that have not started remain, so a wrapped phrase
     * gets one label (on its first visual line). When [keepWraps] is true the
     * caller is about to start a new page, so a still-open wrap is kept and the
     * next page's first line gets a label for the same annotation.
     */
    fun pendingAfterLine(
        annotations: List<WordAnnotation>,
        lineEnd: Int,
        keepWraps: Boolean = false,
    ): List<WordAnnotation> = annotations.filter {
        it.start >= lineEnd || (keepWraps && it.end > lineEnd)
    }

    data class HintSegment(
        val id: Int,
        val hint: String,
        val left: Float,
        val right: Float,
        val top: Float,
        val bottom: Float,
    )

    data class HintLabelPlacement(
        val id: Int,
        val hint: String,
        val left: Float,
        val maxWidth: Float,
        val segment: HintSegment,
    )

    /** Two boxes share a visual line when their vertical overlap is more than half the shorter box. */
    fun sameLine(aTop: Float, aBottom: Float, bTop: Float, bBottom: Float): Boolean {
        val overlap = minOf(aBottom, bBottom) - maxOf(aTop, bTop)
        val shorter = minOf(aBottom - aTop, bBottom - bTop)
        return shorter > 0f && overlap > shorter / 2f
    }

    /**
     * A rect belongs to this page when its center is inside the viewport.
     * Column pages share Y, so left/right (not top) is what separates them;
     * a 1px sliver from the previous column has a center < 0 and is excluded.
     */
    fun onPage(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        width: Float,
        height: Float,
    ): Boolean {
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        return cx >= 0f && cx < width && cy >= 0f && cy < height
    }

    /** Merge boxes of one hint that sit on the same visual line (inline splits). */
    fun mergeLineSegments(rects: List<HintSegment>): List<HintSegment> {
        val lines = ArrayList<HintSegment>()
        for (rect in rects) {
            val index = lines.indexOfFirst { sameLine(it.top, it.bottom, rect.top, rect.bottom) }
            if (index < 0) {
                lines += rect
            } else {
                val line = lines[index]
                lines[index] = line.copy(
                    left = minOf(line.left, rect.left),
                    right = maxOf(line.right, rect.right),
                    top = minOf(line.top, rect.top),
                    bottom = maxOf(line.bottom, rect.bottom),
                )
            }
        }
        return lines
    }

    /**
     * Horizontal placement of one label.
     *
     * Prefers [spanStart], but may slide left as far as [cursor]/[lineStart] so
     * [measuredWidth] fits. The label must still start before [spanEnd]. Returns
     * `(x, maxWidth)` where maxWidth is the leftover to [lineEnd]; the caller
     * ellipsizes when that is smaller than the measured text.
     */
    fun placeHintX(
        spanStart: Float,
        spanEnd: Float,
        measuredWidth: Float,
        lineStart: Float,
        lineEnd: Float,
        cursor: Float,
    ): Pair<Float, Float> {
        val colLeft = maxOf(lineStart, cursor)
        val colRight = lineEnd
        if (colRight - colLeft < LABEL_MIN_WIDTH || colLeft >= spanEnd) {
            return colLeft to 0f
        }
        val width = measuredWidth.coerceAtLeast(0f)
        var x = minOf(spanStart, colRight - width)
        x = maxOf(x, colLeft)
        x = minOf(x, spanEnd - 1f)
        x = maxOf(x, colLeft)
        val maxWidth = (colRight - x).coerceAtLeast(0f)
        return x to maxWidth
    }

    /**
     * One segment per hint: the first in reading order whose full label fits
     * after sliding, otherwise the one with the most room after sliding.
     */
    fun chooseAnchor(
        segments: List<HintSegment>,
        measuredWidth: Float,
        columnLeft: Float,
        columnRight: Float,
    ): HintSegment? {
        if (segments.isEmpty()) return null
        val ordered = segments.sortedWith(compareBy({ it.top }, { it.left }))
        fun fits(segment: HintSegment) = placeHintX(
            segment.left, segment.right, measuredWidth, columnLeft, columnRight, columnLeft,
        ).second >= measuredWidth - 0.5f
        fun visibleWidth(segment: HintSegment) =
            (minOf(segment.right, columnRight) - maxOf(segment.left, columnLeft)).coerceAtLeast(0f)
        return ordered.firstOrNull(::fits)
            ?: ordered.maxWithOrNull(
                compareBy<HintSegment> { visibleWidth(it) }.thenBy { -it.top }.thenBy { -it.left },
            )
    }

    /**
     * Place every chosen anchor, packing each visual line left to right.
     * A later label may steal unused space on the left of earlier labels
     * before it is pushed right or shortened.
     */
    fun packHintLabels(
        anchors: List<Pair<HintSegment, Float>>,
        columnLeft: Float,
        columnRight: Float,
        gap: Float = LABEL_GAP,
    ): List<HintLabelPlacement> {
        if (anchors.isEmpty()) return emptyList()
        val groups = ArrayList<ArrayList<Pair<HintSegment, Float>>>()
        for (anchor in anchors.sortedWith(compareBy({ it.first.top }, { it.first.left }))) {
            val group = groups.firstOrNull {
                sameLine(it[0].first.top, it[0].first.bottom, anchor.first.top, anchor.first.bottom)
            }
            if (group == null) groups += arrayListOf(anchor) else group += anchor
        }
        val result = ArrayList<HintLabelPlacement>()
        for (group in groups) {
            result += packLine(group.sortedBy { it.first.left }, columnLeft, columnRight, gap)
        }
        return result
    }

    private class Slot(
        val segment: HintSegment,
        var x: Float,
        var width: Float,
    )

    private fun packLine(
        ordered: List<Pair<HintSegment, Float>>,
        columnLeft: Float,
        columnRight: Float,
        gap: Float,
    ): List<HintLabelPlacement> {
        val slots = ArrayList<Slot>()
        for ((segment, measured) in ordered) {
            // Match placeHintX: slide left until the full label fits, even if
            // that takes it left of the word's start. An empty prefix of the
            // line is better than an unnecessary ellipsis.
            val preferred = maxOf(columnLeft, minOf(segment.left, columnRight - measured))
            var cursor = if (slots.isEmpty()) columnLeft else slots.last().x + slots.last().width + gap
            if (preferred < cursor && slots.isNotEmpty()) {
                var need = cursor - preferred
                for (j in slots.indices.reversed()) {
                    if (need <= 0f) break
                    val can = slots[j].x - columnLeft
                    if (can <= 0f) continue
                    val take = minOf(can, need)
                    for (k in j until slots.size) slots[k].x -= take
                    need -= take
                }
                cursor = slots.last().x + slots.last().width + gap
            }
            var x = maxOf(preferred, cursor)
            x = minOf(x, segment.right - 1f)
            x = maxOf(x, cursor)
            val width = minOf(measured, (columnRight - x).coerceAtLeast(0f))
            if (width < LABEL_MIN_WIDTH) continue
            slots += Slot(segment, x, width)
        }
        return slots.map {
            HintLabelPlacement(it.segment.id, it.segment.hint, it.x, it.width, it.segment)
        }
    }
}
