package io.legado.app.help.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordAnnotationInflectionProbeTest {

    private fun item(text: String) = WordAnnotationItem(text = text, occurrence = 1, hint = "x")

    @Test
    fun `exact match is not counted as a miss`() {
        val report = WordAnnotationInflectionProbe.probe("home", listOf(item("home")))
        assertEquals(1, report.items)
        assertEquals(1, report.exactHits)
        assertEquals(0, report.exactMisses)
        assertEquals(0, report.inflectionHits)
    }

    @Test
    fun `a single inflected form is reported`() {
        val report = WordAnnotationInflectionProbe.probe("He ran home.", listOf(item("run")))
        assertEquals(1, report.exactMisses)
        assertEquals(1, report.inflectionHits)
        assertEquals(1, report.singleFormHits)
        assertEquals(0, report.multiFormHits)
        assertTrue(report.samples.single().found.contains("ran"))
    }

    @Test
    fun `multiple forms are reported as multi`() {
        val report = WordAnnotationInflectionProbe.probe(
            "She ran and kept running.",
            listOf(item("run")),
        )
        assertEquals(1, report.inflectionHits)
        assertEquals(0, report.singleFormHits)
        assertEquals(1, report.multiFormHits)
        assertTrue(report.samples.single().found.containsAll(listOf("ran", "running")))
    }

    @Test
    fun `case-only difference is not counted as inflection`() {
        val report = WordAnnotationInflectionProbe.probe("run home", listOf(item("Run")))
        assertEquals(1, report.caseOnlyHits)
        assertEquals(0, report.inflectionHits)
        assertTrue(report.samples.single().caseOnly)
    }

    @Test
    fun `multiword phrases are not inflected`() {
        val report = WordAnnotationInflectionProbe.probe("nothing here", listOf(item("came to terms")))
        assertEquals(1, report.phraseMisses)
        assertEquals(0, report.inflectionHits)
        assertEquals(0, report.noFormHits)
    }

    @Test
    fun `a missing function word has no candidate forms`() {
        val report = WordAnnotationInflectionProbe.probe("nothing here", listOf(item("the")))
        assertEquals(1, report.noFormHits)
        assertEquals(0, report.inflectionHits)
    }

    @Test
    fun `ambiguous surface is surfaced for sampling`() {
        // "saw" can be the past of see or a cutting tool. The probe reports it
        // so a human precision sample can reject it; it does not decide.
        val report = WordAnnotationInflectionProbe.probe("a saw on the bench", listOf(item("see")))
        assertEquals(1, report.inflectionHits)
        assertTrue(report.samples.single().found.contains("saw"))
    }

    @Test
    fun `exact hits and misses cover every item`() {
        val report = WordAnnotationInflectionProbe.probe(
            "home and ran",
            listOf(item("home"), item("run"), item("the")),
        )
        assertEquals(3, report.items)
        assertEquals(1, report.exactHits)
        assertEquals(2, report.exactMisses)
        assertEquals(1, report.inflectionHits)
        assertEquals(1, report.noFormHits)
    }

    private fun pool(surface: String) = WordAnnotationPoolItem(surface = surface, hint = "x")

    @Test
    fun `pool variants group run ran running`() {
        val report = WordAnnotationInflectionProbe.poolVariants(
            listOf(pool("run"), pool("ran"), pool("running"), pool("book")),
        )
        assertEquals(1, report.groups)
        assertEquals(2, report.extraSurfaces)
        assertEquals(listOf("ran", "run", "running"), report.samples.single().surfaces)
    }

    @Test
    fun `ambiguous see and saw are surfaced for review`() {
        val report = WordAnnotationInflectionProbe.poolVariants(
            listOf(pool("see"), pool("saw")),
        )
        assertEquals(1, report.groups)
        assertEquals(1, report.extraSurfaces)
        assertEquals(listOf("saw", "see"), report.samples.single().surfaces)
    }

    @Test
    fun `phrases do not join pool variants`() {
        val report = WordAnnotationInflectionProbe.poolVariants(
            listOf(pool("came to terms"), pool("run"), pool("ran")),
        )
        assertEquals(1, report.groups)
        assertEquals(1, report.extraSurfaces)
    }
}
