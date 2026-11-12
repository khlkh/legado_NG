package io.legado.app.help.annotation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordAnnotationPromptTest {

    @Test
    fun `english prompt carries the contextual selection rules without an AI cap`() {
        val prompt = WordAnnotationPrompt.system(uiLocaleChinese = false)
        assertTrue(prompt.contains("Smart Glosses"))
        assertTrue(prompt.contains("educational value"))
        assertTrue(prompt.contains("\"came to terms with\""))
        assertTrue(prompt.contains("Proper names"))
        assertTrue(prompt.contains("difficulty filtering and annotation limits separately"))
        assertTrue(prompt.contains("difficulty"))
        assertTrue(prompt.contains("contextual"))
        assertTrue(prompt.contains("Each hint must be no more than 8 words."))
        assertTrue(prompt.contains("annotations\":[]"))
        assertFalse(prompt.contains("Each hint must contain"))
        assertFalse(prompt.contains("Return no more than"))
        assertFalse(prompt.contains("cap is a maximum"))
    }

    @Test
    fun `chinese hint language uses the character limit`() {
        val prompt = WordAnnotationPrompt.system(uiLocaleChinese = true)
        assertTrue(prompt.contains("Simplified Chinese"))
        assertTrue(prompt.contains("no more than 16 characters"))
        assertFalse(prompt.contains("English."))
    }

    @Test
    fun `window instruction asks for full-section coverage`() {
        val prompt = WordAnnotationPrompt.system(
            uiLocaleChinese = false,
            windowIndex = 1,
            windowCount = 4,
        )
        assertTrue(prompt.contains("section 2 of 4"))
        assertTrue(prompt.contains("middle and end"))
    }

    @Test
    fun `user message delimits the untrusted passage and requests the full schema`() {
        val user = WordAnnotationPrompt.user("A passage with \"quotes\".")
        assertTrue(user.contains("<passage>"))
        assertTrue(user.contains("</passage>"))
        assertTrue(user.contains("\"difficulty\""))
        assertTrue(user.contains("\"contextual\""))
        assertTrue(user.contains("\"occurrence\""))
    }
}
