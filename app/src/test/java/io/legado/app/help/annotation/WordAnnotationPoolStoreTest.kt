package io.legado.app.help.annotation

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class WordAnnotationPoolStoreTest {

    private lateinit var dir: File

    private val identity = WordAnnotationPoolIdentity(
        bookUrl = "https://book/1",
    )

    private val chapterOne = WordAnnotationChapterPool(
        chapterIndex = 1,
        contentHash = "hash-one",
        completedWindows = setOf(0, 1, 2, 3),
        items = listOf(
            WordAnnotationPoolItem("untenable", "impossible to defend", 12_000, difficulty = 4, contextual = true),
            WordAnnotationPoolItem("came to terms with", "accepted", null, difficulty = 2),
        ),
    )

    private val state = WordAnnotationPoolState(
        chapters = mapOf(
            1 to chapterOne,
            2 to WordAnnotationChapterPool(
                chapterIndex = 2,
                contentHash = "hash-two",
                completedWindows = setOf(0),
                items = listOf(WordAnnotationPoolItem("stagnant", "not moving", 11_889)),
            ),
        ),
    )

    @Before
    fun setUp() {
        dir = File.createTempFile("wordwise-pool-test", ".dir").apply {
            delete()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun poolFile() = File(dir, WordAnnotationPoolStore.POOL_FILE_NAME)

    @Test
    fun `write then read preserves chapters, windows, and items`() {
        val file = poolFile()
        assertTrue(WordAnnotationPoolStore.write(file, identity, state))
        assertEquals(state, WordAnnotationPoolStore.read(file, identity))
    }

    @Test
    fun `book url mismatch is a miss`() {
        val file = poolFile()
        assertTrue(WordAnnotationPoolStore.write(file, identity, state))
        assertNull(WordAnnotationPoolStore.read(file, identity.copy(bookUrl = "https://book/2")))
    }

    @Test
    fun `model mismatch is a miss`() {
        val file = poolFile()
        assertTrue(WordAnnotationPoolStore.write(file, identity, state))
        assertNull(WordAnnotationPoolStore.read(file, identity.copy(modelId = "other-model")))
    }

    @Test
    fun `empty state is stored and returned`() {
        val file = poolFile()
        assertTrue(WordAnnotationPoolStore.write(file, identity, WordAnnotationPoolState()))
        assertEquals(WordAnnotationPoolState(), WordAnnotationPoolStore.read(file, identity))
    }

    @Test
    fun `invalid json is a miss`() {
        val file = poolFile()
        file.writeText("not json")
        assertNull(WordAnnotationPoolStore.read(file, identity))
    }

    @Test
    fun `write failure leaves no final file`() {
        val file = File(dir, "occupied")
        file.mkdirs()
        File(file, "child").writeText("x")
        assertFalse(WordAnnotationPoolStore.write(file, identity, state))
        assertTrue(file.isDirectory)
    }
}
