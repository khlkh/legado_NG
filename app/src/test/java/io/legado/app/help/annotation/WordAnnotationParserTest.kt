package io.legado.app.help.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordAnnotationParserTest {

    @Test
    fun `parses valid response items`() {
        val parsed = WordAnnotationParser.parse(
            """{"annotations":[{"text":"untenable","occurrence":1,"hint":"impossible to defend"}]}"""
        )
        assertEquals(
            listOf(WordAnnotationItem("untenable", 1, "impossible to defend")),
            parsed,
        )
    }

    @Test
    fun `empty annotations array is an empty success not null`() {
        assertEquals(emptyList<WordAnnotationItem>(), WordAnnotationParser.parse("""{"annotations":[]}"""))
    }

    @Test
    fun `invalid json is null`() {
        assertNull(WordAnnotationParser.parse("not json"))
        assertNull(WordAnnotationParser.parse(""))
    }

    @Test
    fun `missing annotations field is null`() {
        assertNull(WordAnnotationParser.parse("""{"items":[]}"""))
    }

    @Test
    fun `annotations not an array is null`() {
        assertNull(WordAnnotationParser.parse("""{"annotations":"[]"}"""))
        assertNull(WordAnnotationParser.parse("""{"annotations":{}}"""))
    }

    @Test
    fun `root not an object is null`() {
        assertNull(WordAnnotationParser.parse("""[{"text":"a","occurrence":1,"hint":"b"}]"""))
    }

    @Test
    fun `drops structurally invalid elements`() {
        val parsed = WordAnnotationParser.parse(
            """
            {
              "annotations": [
                {"text":"ok","occurrence":1,"hint":"fine"},
                "not an object",
                {"hint":"no text","occurrence":1},
                {"text":"no hint","occurrence":1},
                {"text":"bad occurrence","occurrence":0,"hint":"x"},
                {"text":"negative occurrence","occurrence":-1,"hint":"x"},
                {"text":"string occurrence","occurrence":"1","hint":"x"},
                {"text":"","occurrence":1,"hint":"x"},
                {"text":"blank hint","occurrence":1,"hint":"  "}
              ]
            }
            """
        )
        assertEquals(listOf(WordAnnotationItem("ok", 1, "fine")), parsed)
    }

    @Test
    fun `numeric occurrence one point zero parses as one`() {
        val parsed = WordAnnotationParser.parse("""{"annotations":[{"text":"a","occurrence":1.0,"hint":"x"}]}""")
        assertEquals(listOf(WordAnnotationItem("a", 1, "x")), parsed)
    }

    @Test
    fun `extra fields are ignored`() {
        val parsed = WordAnnotationParser.parse(
            """{"annotations":[{"text":"a","occurrence":1,"hint":"x","confidence":0.9,"start":4}]}"""
        )
        assertEquals(listOf(WordAnnotationItem("a", 1, "x")), parsed)
    }

    @Test
    fun `hint longer than locale cap still parses and is resolved away later`() {
        val longHint = (1..9).joinToString(" ") { "w$it" }
        val parsed = WordAnnotationParser.parse(
            """{"annotations":[{"text":"a","occurrence":1,"hint":"$longHint"}]}"""
        )
        assertEquals(listOf(WordAnnotationItem("a", 1, longHint)), parsed)
        assertTrue(WordAnnotationResolver.resolve(listOf("a"), parsed!!, uiLocaleChinese = false).isEmpty())
    }

    @Test
    fun `parse stats report raw and invalid item counts`() {
        val parsed = WordAnnotationParser.parseWithStats(
            """
            {
              "annotations": [
                {"text":"ok","occurrence":1,"hint":"fine"},
                {"text":"","occurrence":1,"hint":"blank"}
              ]
            }
            """
        )
        assertEquals(2, parsed!!.rawCount)
        assertEquals(1, parsed.invalidCount)
        assertEquals(1, parsed.items.size)
    }

    @Test
    fun `parses optional difficulty and contextual metadata`() {
        val parsed = WordAnnotationParser.parse(
            """{"annotations":[{"text":"a","occurrence":1,"hint":"x","difficulty":4,"contextual":true}]}"""
        )
        assertEquals(listOf(WordAnnotationItem("a", 1, "x", 4, true)), parsed)
    }

    @Test
    fun `invalid difficulty is ignored but the item survives`() {
        val parsed = WordAnnotationParser.parse(
            """{"annotations":[{"text":"a","occurrence":1,"hint":"x","difficulty":9,"contextual":"yes"}]}"""
        )
        assertEquals(listOf(WordAnnotationItem("a", 1, "x")), parsed)
    }
}
