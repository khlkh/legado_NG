package io.legado.app.ui.book.read.config

import io.legado.app.help.config.AiThemeDeckStore
import io.legado.app.ui.design.theme.NgColorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiThemePromptTest {

    @Test
    fun codeLineOmitsOpaqueAlphaAndAbbreviatesAccent() {
        assertEquals(
            "FG: #3A3228 / BG: #F4EDE0 / AC: #2255AA",
            aiThemeCodeLine(0xFF3A3228.toInt(), 0xFFF4EDE0.toInt(), 0xFF2255AA.toInt()),
        )
    }

    @Test
    fun promptAsksForAccentInkOnThePage() {
        assertTrue(AI_THEME_SYSTEM_PROMPT.contains("4.5:1"))
        assertTrue(AI_THEME_SYSTEM_PROMPT.contains("7.0:1"))
        assertTrue(AI_THEME_SYSTEM_PROMPT.contains("6.0:1"))
        assertTrue(AI_THEME_SYSTEM_PROMPT.contains("120"))
        assertTrue(AI_THEME_SYSTEM_PROMPT.contains("Day-mode accent quality"))
        assertTrue(AI_THEME_SYSTEM_PROMPT.contains("pale"))
        assertTrue(AI_THEME_SYSTEM_PROMPT.contains("do not require it to reach 4.5:1"))
        assertTrue(AI_THEME_SYSTEM_PROMPT.contains("Do NOT create separate objects for day and night"))
        assertTrue(AI_THEME_SYSTEM_PROMPT.contains("same name"))
        assertTrue(AI_THEME_SYSTEM_PROMPT.contains("distinct accent hue families"))
        assertTrue(AI_THEME_SYSTEM_PROMPT.contains("no mechanical lightening"))
        assertFalse(AI_THEME_SYSTEM_PROMPT.contains("only the requested polarity"))
        assertFalse(AI_THEME_SYSTEM_PROMPT.contains("hue+180"))
        assertTrue(buildAiThemeUserPrompt("warm paper", uiLanguageTag = "zh-CN").contains("CURRENT_MODE: both"))
    }

    @Test
    fun parseKeepsReturnedColorsEvenWhenContrastIsLow() {
        val looks = parseAiPaperLooks(
            raw = """
                [
                  {"name":"Warm paper","bg":"#F4EDE0","text":"#3A3228","accent":"#F4EDE0"},
                  {"name":"Pale ink","bg":"#F4EDE0","text":"#3A3228","accent":"#E7D3B0"},
                  {"name":"Whimsical Meadow","bg":"#F3EED9","text":"#C4B89A","accent":"#E4D7A8"}
                ]
            """.trimIndent(),
            night = false,
        )
        assertEquals(3, looks.size)
        assertEquals(0xFFF4EDE0.toInt(), looks[0].accent)
        assertEquals(0xFFE7D3B0.toInt(), looks[1].accent)
        assertEquals(0xFFC4B89A.toInt(), looks[2].text)
        assertEquals(0xFFF3EED9.toInt(), looks[2].background)
        assertEquals(0xFFE4D7A8.toInt(), looks[2].accent)
    }

    @Test
    fun parseKeepsAutumnMapleColorsThatAlreadyPassContrast() {
        val pairs = parseAiThemePairs(
            """
            {"themes":[{
              "name":"Autumn Maple",
              "day":{"bg":"#F5E6D3","text":"#3B2F2A","accent":"#A0522D"},
              "night":{"bg":"#2B1E16","text":"#E8D5C4","accent":"#D98C5F"}
            }]}
            """.trimIndent(),
        )
        assertEquals(1, pairs.size)
        assertEquals("Autumn Maple", pairs[0].name)
        assertEquals("FF3B2F2A", "%08X".format(pairs[0].day.text))
        assertEquals("FFF5E6D3", "%08X".format(pairs[0].day.background))
        assertEquals("FFA0522D", "%08X".format(pairs[0].day.accent))
        assertEquals("FFE8D5C4", "%08X".format(pairs[0].night.text))
        assertEquals("FF2B1E16", "%08X".format(pairs[0].night.background))
        assertEquals("FFD98C5F", "%08X".format(pairs[0].night.accent))
    }

    @Test
    fun parseKeepsDayAndNightUnderOneName() {
        val pairs = parseAiThemePairs(
            """
            [{
              "name":"Summer heat",
              "day":{"bg":"#F4EDE0","text":"#3A3228","accent":"#1F6B62"},
              "night":{"bg":"#1A1612","text":"#E8DCC8","accent":"#8FD0C4"}
            }]
            """.trimIndent(),
        )
        assertEquals(1, pairs.size)
        assertEquals("Summer heat", pairs[0].name)
        assertEquals("Summer heat", pairs[0].day.name)
        assertEquals("Summer heat", pairs[0].night.name)
        assertTrue(NgColorMath.displayedContrast(pairs[0].day.text, pairs[0].day.background) >= 4.5)
        assertTrue(NgColorMath.displayedContrast(pairs[0].night.text, pairs[0].night.background) >= 4.5)
        assertTrue(NgColorMath.displayedContrast(pairs[0].day.accent, pairs[0].day.background) >= 4.5)
        assertTrue(NgColorMath.displayedContrast(pairs[0].night.accent, pairs[0].night.background) >= 4.5)
    }

    @Test
    fun parseJoinsFlatDayAndNightThatShareAName() {
        val pairs = parseAiThemePairs(
            """
            [
              {"name":"河畔暮色","bg":"#F7EDE2","text":"#3A2E2A","accent":"#B4532A"},
              {"name":"河畔暮色","bg":"#292A32","text":"#E8DFD5","accent":"#E6A477"}
            ]
            """.trimIndent(),
        )
        assertEquals(1, pairs.size)
        assertEquals("河畔暮色", pairs[0].name)
        assertEquals("河畔暮色", pairs[0].day.name)
        assertEquals("河畔暮色", pairs[0].night.name)
        assertTrue(NgColorMath.displayedContrast(pairs[0].day.text, pairs[0].day.background) >= 4.5)
        assertTrue(NgColorMath.displayedContrast(pairs[0].night.text, pairs[0].night.background) >= 4.5)
    }

    @Test
    fun parseDropsObjectsWithoutColors() {
        assertTrue(parseAiPaperLooks("not json", night = false).isEmpty())
        assertTrue(parseAiPaperLooks("""[{"name":"only"}]""", night = false).isEmpty())
    }

    @Test
    fun swipeRemovesOnlyTheChosenGeneratedTheme() {
        val pairs = List(3) { index ->
            val look = NgPaperLook(
                id = "ai",
                name = "t$index",
                background = 0xFFF4EDE0.toInt(),
                text = 0xFF3A3228.toInt(),
                accent = 0xFF2255AA.toInt() + index,
            )
            AiThemePair("t$index", look, look)
        }
        val next = AiThemeDeckStore.withoutIndex(pairs, 1)
        assertEquals(listOf("t0", "t2"), next.map { it.name })
        assertEquals(pairs, AiThemeDeckStore.withoutIndex(pairs, -1))
        val restored = AiThemeDeckStore.insertAt(next, 1, pairs[1])
        assertEquals(listOf("t0", "t1", "t2"), restored.map { it.name })
    }

    @Test
    fun savedDeckDropsTheOldestWhenItPassesSixteen() {
        val existing = List(14) { index ->
            NgPaperLook(id = "ai", name = "e$index", background = 0xFF101010.toInt() + index, text = 0xFFE0E0E0.toInt(), accent = 0xFF4488AA.toInt())
        }
        val incoming = List(4) { index ->
            NgPaperLook(id = "ai", name = "n$index", background = 0xFFF0F0F0.toInt(), text = 0xFF222222.toInt(), accent = 0xFF2255AA.toInt())
        }
        val merged = AiThemeDeckStore.mergeDeck(existing, incoming)
        assertEquals(16, merged.size)
        assertEquals("n0", merged.first().name)
        assertEquals("e11", merged.last().name)
    }
}
