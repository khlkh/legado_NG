package io.legado.app.help.annotation

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class WordAnnotationStoreTest {

    private lateinit var dir: File

    private val identity = WordAnnotationIdentity(
        bookUrl = "https://book/1",
        chapterUrl = "https://book/1/chapter/3",
        chapterIndex = 2,
        canonicalHash = "hash-abc",
    )

    private val annotations = listOf(
        WordAnnotation(paragraphIndex = 0, start = 17, end = 26, surface = "untenable", hint = "impossible to defend"),
        WordAnnotation(paragraphIndex = 1, start = 4, end = 22, surface = "came to terms with", hint = "accepted"),
    )

    @Before
    fun setUp() {
        dir = File.createTempFile("wordwise-test", ".dir").apply {
            delete()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun cacheFile(name: String = "00002-abc.wordwise.json") = File(dir, name)

    @Test
    fun `write then read is a hit with the same annotations`() {
        val file = cacheFile()
        assertTrue(WordAnnotationStore.write(file, identity, annotations))
        assertEquals(annotations, WordAnnotationStore.read(file, identity))
    }

    @Test
    fun `canonical hash mismatch is a miss`() {
        val file = cacheFile()
        assertTrue(WordAnnotationStore.write(file, identity, annotations))
        assertNull(WordAnnotationStore.read(file, identity.copy(canonicalHash = "hash-other")))
    }

    @Test
    fun `book url mismatch is a miss`() {
        val file = cacheFile()
        assertTrue(WordAnnotationStore.write(file, identity, annotations))
        assertNull(WordAnnotationStore.read(file, identity.copy(bookUrl = "https://book/2")))
    }

    @Test
    fun `chapter url mismatch is a miss`() {
        val file = cacheFile()
        assertTrue(WordAnnotationStore.write(file, identity, annotations))
        assertNull(WordAnnotationStore.read(file, identity.copy(chapterUrl = "https://book/1/chapter/4")))
    }

    @Test
    fun `chapter index mismatch is a miss`() {
        val file = cacheFile()
        assertTrue(WordAnnotationStore.write(file, identity, annotations))
        assertNull(WordAnnotationStore.read(file, identity.copy(chapterIndex = 3)))
    }

    @Test
    fun `schema version mismatch is a miss`() {
        val file = cacheFile()
        assertTrue(WordAnnotationStore.write(file, identity, annotations))
        assertNull(WordAnnotationStore.read(file, identity.copy(schemaVersion = 99)))
    }

    @Test
    fun `hint cap or model mismatch is a miss`() {
        val file = cacheFile()
        assertTrue(WordAnnotationStore.write(file, identity, annotations))
        assertNull(WordAnnotationStore.read(file, identity.copy(hintCap = 21)))
        assertNull(WordAnnotationStore.read(file, identity.copy(modelId = "other-model")))
        assertNull(WordAnnotationStore.read(file, identity.copy(hintCharCap = true)))
    }

    @Test
    fun `empty success is stored and returned as empty not null`() {
        val file = cacheFile()
        assertTrue(WordAnnotationStore.write(file, identity, emptyList()))
        val read = WordAnnotationStore.read(file, identity)
        assertTrue(read != null && read.isEmpty())
    }

    @Test
    fun `missing file is a miss`() {
        assertNull(WordAnnotationStore.read(cacheFile(), identity))
    }

    @Test
    fun `truncated file is a miss not an empty success`() {
        val file = cacheFile()
        val json = """{"bookUrl":"https://book/1","annotations":[]}"""
        file.writeText(json.substring(0, json.length / 2))
        assertNull(WordAnnotationStore.read(file, identity))
    }

    @Test
    fun `invalid json is a miss`() {
        val file = cacheFile()
        file.writeText("not json")
        assertNull(WordAnnotationStore.read(file, identity))
    }

    @Test
    fun `empty file is a miss`() {
        val file = cacheFile()
        file.writeText("")
        assertNull(WordAnnotationStore.read(file, identity))
    }

    @Test
    fun `annotations not an array is a miss`() {
        val file = cacheFile()
        file.writeText(
            """
            {"bookUrl":"https://book/1","chapterUrl":"https://book/1/chapter/3",
             "chapterIndex":2,"canonicalHash":"hash-abc","schemaVersion":7,
             "level":4,"frequencyVersion":"gutenberg-2006-04",
             "hintCap":13,"hintCharCap":false,"providerId":"","modelId":"",
             "annotations":"[]"}
            """.trimIndent(),
        )
        assertNull(WordAnnotationStore.read(file, identity))
    }

    @Test
    fun `invalid annotation elements are dropped while valid ones survive`() {
        val file = cacheFile()
        file.writeText(
            """
            {
              "bookUrl": "https://book/1",
              "chapterUrl": "https://book/1/chapter/3",
              "chapterIndex": 2,
              "canonicalHash": "hash-abc",
              "schemaVersion": 7,
              "level": 4,
              "frequencyVersion": "gutenberg-2006-04",
              "hintCap": 13,
              "hintCharCap": false,
              "providerId": "",
              "modelId": "",
              "annotations": [
                {"paragraphIndex":0,"start":17,"end":26,"surface":"untenable","hint":"impossible to defend"},
                {"paragraphIndex":0,"start":-1,"end":26,"surface":"bad","hint":"bad"},
                "not an object"
              ]
            }
            """.trimIndent(),
        )
        val read = WordAnnotationStore.read(file, identity)
        assertEquals(1, read?.size)
        assertEquals("untenable", read?.single()?.surface)
    }

    @Test
    fun `write failure leaves no final file`() {
        // The final name is a non-empty directory: renameIntoPlace cannot replace it.
        val file = File(dir, "occupied")
        file.mkdirs()
        File(file, "child").writeText("x")
        assertFalse(WordAnnotationStore.write(file, identity, annotations))
        assertTrue(file.isDirectory)
        assertNull(WordAnnotationStore.read(File(dir, "occupied"), identity))
        // No stray temp file beside it.
        assertEquals(0, dir.listFiles { f -> f.name.endsWith(WordAnnotationStore.TEMP_SUFFIX) }?.size ?: 0)
    }

    @Test
    fun `write replaces an existing final file`() {
        val file = cacheFile()
        assertTrue(WordAnnotationStore.write(file, identity, emptyList()))
        assertTrue(WordAnnotationStore.write(file, identity, annotations))
        assertEquals(annotations, WordAnnotationStore.read(file, identity))
    }

    @Test
    fun `annotation file name derives from the chapter body name`() {
        assertEquals("00001-abc.wordwise.json", WordAnnotationStore.annotationFileName("00001-abc.nb"))
        assertEquals("00001-abc.wordwise.json", WordAnnotationStore.annotationFileName("00001-abc.wordwise.json"))
    }

    @Test
    fun `canonical hash is stable for the same paragraphs and version`() {
        val paragraphs = listOf("The proposal was untenable.", "She came to terms with it.")
        val first = WordAnnotationStore.canonicalHash(paragraphs)
        val second = WordAnnotationStore.canonicalHash(paragraphs)
        assertEquals(first, second)
        assertNotEquals(first, WordAnnotationStore.canonicalHash(paragraphs + "extra"))
        assertNotEquals(first, WordAnnotationStore.canonicalHash(paragraphs, schemaVersion = 1))
        assertNotEquals(first, WordAnnotationStore.canonicalHash(emptyList()))
    }
}
