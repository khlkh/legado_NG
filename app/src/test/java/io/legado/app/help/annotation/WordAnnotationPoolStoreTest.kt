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
        sampleHash = "sample-abc",
    )

    private val items = listOf(
        WordAnnotationPoolItem("untenable", "impossible to defend", 12_000),
        WordAnnotationPoolItem("came to terms with", "accepted", null),
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
    fun `write then read is a hit with the same items`() {
        val file = poolFile()
        assertTrue(WordAnnotationPoolStore.write(file, identity, items))
        assertEquals(items, WordAnnotationPoolStore.read(file, identity))
    }

    @Test
    fun `sample hash mismatch is a miss`() {
        val file = poolFile()
        assertTrue(WordAnnotationPoolStore.write(file, identity, items))
        assertNull(WordAnnotationPoolStore.read(file, identity.copy(sampleHash = "other")))
    }

    @Test
    fun `model mismatch is a miss`() {
        val file = poolFile()
        assertTrue(WordAnnotationPoolStore.write(file, identity, items))
        assertNull(WordAnnotationPoolStore.read(file, identity.copy(modelId = "other-model")))
    }

    @Test
    fun `empty pool is stored and returned as empty not null`() {
        val file = poolFile()
        assertTrue(WordAnnotationPoolStore.write(file, identity, emptyList()))
        val read = WordAnnotationPoolStore.read(file, identity)
        assertTrue(read != null && read.isEmpty())
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
        assertFalse(WordAnnotationPoolStore.write(file, identity, items))
        assertTrue(file.isDirectory)
    }
}
