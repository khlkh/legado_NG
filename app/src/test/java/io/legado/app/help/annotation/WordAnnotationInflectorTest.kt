package io.legado.app.help.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordAnnotationInflectorTest {

    private fun forms(base: String): List<String> =
        WordAnnotationInflector.candidateForms(base)

    @Test
    fun `irregular verb family is complete`() {
        val run = forms("run")
        assertTrue(run.containsAll(listOf("run", "ran", "runs", "running")))
    }

    @Test
    fun `irregular plural`() {
        assertTrue(forms("child").containsAll(listOf("child", "children")))
        assertTrue(forms("person").containsAll(listOf("person", "people")))
    }

    @Test
    fun `irregular adjective`() {
        assertTrue(forms("good").containsAll(listOf("good", "better", "best")))
        assertTrue(forms("bad").containsAll(listOf("bad", "worse", "worst")))
    }

    @Test
    fun `regular verb forms include past and participle`() {
        assertTrue(forms("make").containsAll(listOf("make", "makes", "made", "making")))
        assertTrue(forms("stop").containsAll(listOf("stop", "stops", "stopped", "stopping")))
        assertTrue(forms("study").containsAll(listOf("study", "studies", "studied", "studying")))
    }

    @Test
    fun `drops e and turns ie into y`() {
        assertTrue(forms("love").contains("loving"))
        assertTrue(forms("die").containsAll(listOf("dies", "died", "dying")))
        assertTrue(forms("lie").containsAll(listOf("lies", "lied", "lying")))
    }

    @Test
    fun `case variants are generated for sentence starts`() {
        val run = forms("run")
        assertTrue(run.contains("Ran"))
        assertTrue(run.contains("Running"))
        assertTrue(run.contains("ran"))
    }

    @Test
    fun `function words and pronouns are not inflected`() {
        assertTrue(forms("us").isEmpty())
        assertTrue(forms("the").isEmpty())
        assertTrue(forms("I").isEmpty())
    }

    @Test
    fun `non english and multiword bases are not inflected`() {
        assertTrue(forms("不足为奇").isEmpty())
        assertTrue(forms("came to terms").isEmpty())
        assertTrue(forms("").isEmpty())
        assertTrue(forms("x").isEmpty())
    }

    @Test
    fun `regular words do not invent comparatives`() {
        // Conservative prototype: `booker`/`bookest` are not generated.
        assertEquals(false, forms("book").contains("booker"))
        assertEquals(false, forms("book").contains("bookest"))
    }

    @Test
    fun `irregular families do not add regular past forms`() {
        // `runed` is a real word, so the irregular family must suppress it.
        assertEquals(false, forms("run").contains("runed"))
        assertEquals(false, forms("run").contains("runing"))
    }
}
