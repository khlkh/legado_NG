package io.legado.app.model.epub

import io.legado.app.help.annotation.WordAnnotation

/** One hint span in one document's native coordinates, addressed to the renderer. */
internal data class EpubSmartGlossesFragment(
    val document: Int,
    val element: String,
    val ordinal: Int,
    val nativeStart: Int,
    val nativeEnd: Int,
    val hintId: Int,
)

/**
 * Resolves pool annotations (paragraph + UTF-16 range into the joined
 * paragraph text) into per-document native fragments using the projection's
 * per-character source offset map. No text matching happens here: the map was
 * built by [EpubContentProjection.bindNativeContent].
 */
internal object EpubSmartGlossesFragments {

    fun resolve(
        paragraphs: List<String>,
        documents: List<EpubContentProjection.ProjectedDocument>,
        annotations: List<WordAnnotation>,
    ): List<EpubSmartGlossesFragment> {
        if (paragraphs.isEmpty() || annotations.isEmpty()) return emptyList()
        val base = IntArray(paragraphs.size)
        var offset = 0
        for (index in paragraphs.indices) {
            base[index] = offset
            offset += paragraphs[index].length + 1
        }

        val result = ArrayList<EpubSmartGlossesFragment>()
        annotations.forEachIndexed { hintId, annotation ->
            val paragraphBase = base.getOrNull(annotation.paragraphIndex) ?: return@forEachIndexed
            val rangeStart = paragraphBase + annotation.start
            val rangeEnd = paragraphBase + annotation.end
            if (rangeEnd <= rangeStart) return@forEachIndexed
            val length = rangeEnd - rangeStart
            val found = BooleanArray(length)
            var complete = true
            val fragments = ArrayList<EpubSmartGlossesFragment>()
            documents.forEachIndexed { documentIndex, document ->
                document.nodes.forEach { node ->
                    val size = node.sourceStarts.size
                    if (size == 0) return@forEach
                    var index = 0
                    while (index < size) {
                        val source = node.sourceStarts[index]
                        if (source !in rangeStart until rangeEnd) {
                            index++
                            continue
                        }
                        var nativeStart = Int.MAX_VALUE
                        var nativeEnd = -1
                        var cursor = index
                        while (cursor < size) {
                            val s = node.sourceStarts[cursor]
                            if (s !in rangeStart until rangeEnd) break
                            val native = node.starts[cursor]
                            if (native < 0) {
                                complete = false
                                break
                            }
                            found[s - rangeStart] = true
                            nativeStart = minOf(nativeStart, native)
                            nativeEnd = maxOf(nativeEnd, node.ends[cursor])
                            cursor++
                        }
                        if (!complete) break
                        if (nativeStart != Int.MAX_VALUE) {
                            fragments += EpubSmartGlossesFragment(
                                documentIndex, node.element, node.ordinal,
                                nativeStart, nativeEnd, hintId,
                            )
                        }
                        index = cursor
                    }
                }
            }
            // Drop any hint that is not fully represented by native content.
            if (complete && found.all { it } && fragments.isNotEmpty()) result.addAll(fragments)
        }
        return result
    }
}
