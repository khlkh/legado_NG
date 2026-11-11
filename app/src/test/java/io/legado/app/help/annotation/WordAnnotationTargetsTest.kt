package io.legado.app.help.annotation

import org.junit.Assert.assertEquals
import org.junit.Test

class WordAnnotationTargetsTest {

    @Test
    fun `display target follows word count and level multiplier`() {
        assertEquals(9, WordAnnotationTargets.displayTarget(2_000, 3, WordAnnotationDensity.STANDARD))
        assertEquals(43, WordAnnotationTargets.displayTarget(10_000, 3, WordAnnotationDensity.STANDARD))
        assertEquals(34, WordAnnotationTargets.displayTarget(10_000, 4, WordAnnotationDensity.STANDARD))
        assertEquals(56, WordAnnotationTargets.displayTarget(10_000, 1, WordAnnotationDensity.STANDARD))
        assertEquals(26, WordAnnotationTargets.displayTarget(10_000, 5, WordAnnotationDensity.STANDARD))
        assertEquals(171, WordAnnotationTargets.displayTarget(50_000, 4, WordAnnotationDensity.STANDARD))
    }

    @Test
    fun `density profile changes quantity independently of level`() {
        val sparse = WordAnnotationTargets.displayTarget(10_000, 4, WordAnnotationDensity.SPARSE)
        val standard = WordAnnotationTargets.displayTarget(10_000, 4, WordAnnotationDensity.STANDARD)
        val dense = WordAnnotationTargets.displayTarget(10_000, 4, WordAnnotationDensity.DENSE)
        assertEquals(standard, 34)
        assertEquals(sparse, 26)
        assertEquals(dense, 43)
    }

    @Test
    fun `discovery target is larger than display and independent of level`() {
        assertEquals(30, WordAnnotationTargets.discoveryTarget(2_000))
        assertEquals(75, WordAnnotationTargets.discoveryTarget(10_000))
        assertEquals(375, WordAnnotationTargets.discoveryTarget(50_000))
    }

    @Test
    fun `word windows scale and cap request count`() {
        assertEquals(1, WordAnnotationTargets.windowCount(2_000))
        assertEquals(4, WordAnnotationTargets.windowCount(10_000))
        assertEquals(16, WordAnnotationTargets.windowCount(50_000))
    }

    @Test
    fun `coverage buckets grow for larger display targets`() {
        assertEquals(4, WordAnnotationTargets.coverageBuckets(34))
        assertEquals(8, WordAnnotationTargets.coverageBuckets(64))
    }

    @Test
    fun `word count ignores blank whitespace`() {
        assertEquals(0, WordAnnotationTargets.countWords("   "))
        assertEquals(3, WordAnnotationTargets.countWords("one  two\nthree"))
        assertEquals(5, WordAnnotationTargets.wordCount(listOf("one two", "  ", "three four five")))
    }
}
