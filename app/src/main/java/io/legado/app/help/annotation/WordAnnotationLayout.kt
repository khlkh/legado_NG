package io.legado.app.help.annotation

import io.legado.app.ui.book.read.page.entities.TextHintRun

/**
 * Pure geometry helpers for the canvas hint band.
 *
 * The band is reserved on the first visual line that intersects a resolved
 * annotation. Line-local hint ranges stay UTF-16 offsets into [TextLine.text];
 * they are never column indexes. The x-span on screen is computed later by
 * walking the line's columns and summing charData lengths.
 */
object WordAnnotationLayout {

    /** Hint band height as a fraction of the body line text height. */
    const val BAND_RATIO = 0.6f

    /** Hint paint text size as a fraction of the body paint text size. */
    const val HINT_TEXT_SIZE_RATIO = 0.55f

    fun bandHeight(textHeight: Float, hasHints: Boolean): Float =
        if (hasHints) textHeight * BAND_RATIO else 0f

    /**
     * Intersections of [annotations] with the visual line range [lineStart, lineEnd),
     * returned as line-local runs. A span that wraps across lines produces a run on
     * every intersecting line; the caller keeps only the first by removing placed
     * annotations from its pending list.
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

    /** Annotations that have not started by [lineEnd] and can still be placed later. */
    fun pendingAfterLine(
        annotations: List<WordAnnotation>,
        lineEnd: Int,
    ): List<WordAnnotation> = annotations.filter { it.start >= lineEnd }
}
