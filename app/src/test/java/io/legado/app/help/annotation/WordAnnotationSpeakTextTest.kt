package io.legado.app.help.annotation

import android.app.Application
import io.legado.app.help.tts.ReadAloudParagraph
import io.legado.app.ui.book.read.page.entities.TextHintRun
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.TextParagraph
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * Wave 1 contract: hint runs are presentation only. They live on [TextLine]
 * beside the body text but are never concatenated into [TextLine.text], and so
 * never into [TextParagraph.text] — the same property the native read-aloud
 * source reads through [ReadAloudParagraph].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE, application = WordAnnotationSpeakTextTestApplication::class)
class WordAnnotationSpeakTextTest {

    private val body = "The proposal was untenable."

    private fun bodyLinesWithHints(): Pair<TextLine, TextLine> {
        val first = TextLine(
            text = "The proposal was ",
            paragraphNum = 1,
            chapterPosition = 0,
        ).apply {
            hintRuns = listOf(TextHintRun(start = 4, end = 12, hint = "a plan put forward"))
        }
        val second = TextLine(
            text = "untenable.",
            paragraphNum = 1,
            chapterPosition = 17,
            isParagraphEnd = true,
        ).apply {
            hintRuns = listOf(TextHintRun(start = 0, end = 9, hint = "impossible to defend"))
        }
        return first to second
    }

    @Test
    fun `paragraph text equals body when hint runs are present`() {
        val (first, second) = bodyLinesWithHints()
        val paragraph = TextParagraph(num = 1)
        paragraph.textLines.add(first)
        paragraph.textLines.add(second)

        assertEquals(body, paragraph.text)
        assertEquals(body.length, paragraph.length)
        assertEquals("a plan put forward", first.hintRuns.single().hint)
        assertEquals("impossible to defend", second.hintRuns.single().hint)
    }

    @Test
    fun `native aloud paragraph contract still sees body only`() {
        val (first, second) = bodyLinesWithHints()
        val paragraph = TextParagraph(num = 1)
        paragraph.textLines.add(first)
        paragraph.textLines.add(second)

        val aloud: ReadAloudParagraph = paragraph
        assertEquals(body, aloud.text)
        assertEquals(body.length, aloud.length)
        assertEquals(0, aloud.chapterPosition)
        assertEquals(0..body.length, aloud.chapterIndices)
        assertTrue(aloud.isParagraphEnd)
    }

    @Test
    fun `text page paragraph grouping keeps body and drops no hint into text`() {
        val (first, second) = bodyLinesWithHints()
        val page = TextPage(text = "", title = "")
        page.addLine(first)
        page.addLine(second)

        val paragraph = page.paragraphsInternal.single()
        assertEquals(body, paragraph.text)
        assertEquals(body.length, paragraph.length)
        assertFalse(paragraph.text.contains("a plan put forward"))
        assertFalse(paragraph.text.contains("impossible to defend"))
    }

    @Test
    fun `hint runs do not change the line body strings`() {
        val (first, second) = bodyLinesWithHints()
        assertEquals("The proposal was ", first.text)
        assertEquals("untenable.", second.text)
        assertEquals(17, first.charSize)
        assertEquals(10, second.charSize)
    }

    @Test
    fun `hint x-span walks charData columns and maps source offsets`() {
        val line = TextLine(
            text = "hello world",
            paragraphNum = 1,
            chapterPosition = 0,
        )
        line.addColumn(TextColumn(start = 0f, end = 10f, charData = "hello"))
        line.addColumn(TextColumn(start = 10f, end = 20f, charData = " "))
        line.addColumn(TextColumn(start = 20f, end = 40f, charData = "world"))

        val span = line.hintRunXSpan(TextHintRun(start = 6, end = 11, hint = "x"))!!

        assertEquals(20f, span.first, 0f)
        assertEquals(40f, span.second, 0f)
    }

    @Test
    fun `hint x-span with synthetic indent columns maps source offsets`() {
        val line = TextLine(
            text = "你好",
            paragraphNum = 1,
            chapterPosition = 0,
            indentWidth = 30f,
            indentSize = 2,
        )
        line.addColumn(TextColumn(start = 0f, end = 15f, charData = "　"))
        line.addColumn(TextColumn(start = 15f, end = 30f, charData = "　"))
        line.addColumn(TextColumn(start = 30f, end = 60f, charData = "你好"))

        val span = line.hintRunXSpan(TextHintRun(start = 0, end = 2, hint = "x"))!!

        assertEquals(30f, span.first, 0f)
        assertEquals(60f, span.second, 0f)
    }
}

class WordAnnotationSpeakTextTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        injectAsAppCtx()
    }
}
