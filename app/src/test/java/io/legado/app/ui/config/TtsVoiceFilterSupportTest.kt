package io.legado.app.ui.config

import io.legado.app.help.book.BookScriptClass
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsVoiceLanguage
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoiceFilterSupportTest {

    @Test
    fun `normalizes and orders language labels`() {
        val voices = listOf(
            TtsVoice(id = "1", name = "English", language = "en-US"),
            TtsVoice(id = "2", name = "Chinese", language = "zh-CN/yue"),
            TtsVoice(id = "3", name = "Japanese", language = "ja-JP")
        )

        assertEquals(
            listOf("中文", "EN US", "日", "粤"),
            TtsVoiceFilterSupport.availableLanguageLabels(voices)
        )
    }

    @Test
    fun `normalizes known genders`() {
        assertEquals("男", TtsVoiceFilterSupport.genderLabel("male"))
        assertEquals("女", TtsVoiceFilterSupport.genderLabel("Woman"))
        assertEquals(null, TtsVoiceFilterSupport.genderLabel("neutral"))
    }

    @Test
    fun `voice search matches display name only`() {
        val voice = TtsVoice(
            id = "voice-001",
            name = "书卷文雅",
            language = "zh-CN",
            gender = "female",
            style = "高冷",
            tags = listOf("青年")
        )
        val option = TtsVoiceOption(
            engine = TtsEngineSetting(
                id = "mossland",
                name = "Mossland",
                type = TtsEngineType.SCRIPT
            ),
            voice = voice,
            systemDefault = false
        )

        assertTrue(TtsVoiceFilterSupport.matchesName(voice, "书卷"))
        assertFalse(TtsVoiceFilterSupport.matchesName(voice, "高冷"))
        assertFalse(TtsVoiceFilterSupport.matchesName(voice, "voice-001"))
        assertTrue(option.matchesName("书卷"))
        assertFalse(option.matchesName("高冷"))
        assertFalse(option.matchesName("青年"))
        assertFalse(option.matchesName("Mossland"))
        assertFalse(option.matchesName("voice-001"))
    }

    @Test
    fun `book language preset selects chinese or both english groups`() {
        assertEquals(
            setOf(TtsVoiceLanguage.CHINESE),
            TtsVoiceFilterSupport.initialLanguageFilter(BookScriptClass.Cjk.storageValue),
        )
        assertEquals(
            setOf(TtsVoiceLanguage.EN_US, TtsVoiceLanguage.EN_UK),
            TtsVoiceFilterSupport.initialLanguageFilter(BookScriptClass.Latin.storageValue),
        )
        assertEquals(emptySet<String>(), TtsVoiceFilterSupport.initialLanguageFilter(null))
    }

    @Test
    fun `chinese and english speakers stay in separate slots`() {
        val chinese = TtsVoiceLanguage.remember(emptyMap(), "zh-CN-YunxiNeural", "zh-CN")
        val both = TtsVoiceLanguage.remember(chinese, "en-US-JennyNeural", "en-US")
        val stillBoth = TtsVoiceLanguage.remember(both, "en-GB-SoniaNeural", "en-GB")

        assertEquals("zh-CN-YunxiNeural", stillBoth[TtsVoiceLanguage.FAMILY_ZH])
        assertEquals("en-GB-SoniaNeural", stillBoth[TtsVoiceLanguage.FAMILY_EN])
        val seeded = TtsVoiceLanguage.seed(stillBoth, "zh-CN-XiaoxiaoNeural", "zh-CN")
        assertEquals("zh-CN-YunxiNeural", seeded[TtsVoiceLanguage.FAMILY_ZH])
        assertEquals("en-GB-SoniaNeural", seeded[TtsVoiceLanguage.FAMILY_EN])
    }
}
