package io.legado.app.help.tts

import io.legado.app.help.book.BookScriptClass

/** Locale labels and the two speaker memories used by the 发音人 picker. */
object TtsVoiceLanguage {
    const val CHINESE = "中文"
    const val EN_US = "EN US"
    const val EN_UK = "EN UK"
    const val FAMILY_ZH = "zh"
    const val FAMILY_EN = "en"

    fun label(language: String?): String? {
        val raw = language?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val primary = raw.substringBefore("-").substringBefore("_")
        val region = raw.substringAfter("-", "").substringBefore("-").substringBefore("_")
        return when (primary) {
            "zh", "cmn" -> CHINESE
            "yue" -> "粤"
            "wuu" -> "吴"
            "en" -> when (region) {
                "gb", "uk" -> EN_UK
                "us" -> EN_US
                else -> null
            }
            "ja", "jp", "jpn" -> "日"
            "ko", "kr", "kor" -> "韩"
            "fr", "fra", "fre" -> "法"
            "de", "deu", "ger" -> "德"
            "es", "esp", "spa" -> "西"
            "pt", "por" -> "葡"
            else -> primary.uppercase()
        }
    }

    fun section(labels: List<String>): String? {
        return listOf(CHINESE, EN_US, EN_UK).firstOrNull { it in labels }
    }

    fun family(language: String?, voiceId: String?): String? {
        val fromLanguage = label(language)
        when (fromLanguage) {
            CHINESE -> return FAMILY_ZH
            EN_US, EN_UK -> return FAMILY_EN
        }
        val id = voiceId?.lowercase().orEmpty()
        return when {
            id.startsWith("zh-") -> FAMILY_ZH
            id.startsWith("en-us") || id.startsWith("en-gb") || id.startsWith("en-uk") -> FAMILY_EN
            else -> null
        }
    }

    fun scriptFamily(scriptClass: String?): String? {
        return when (BookScriptClass.fromStorage(scriptClass)) {
            BookScriptClass.Cjk -> FAMILY_ZH
            BookScriptClass.Latin -> FAMILY_EN
            else -> null
        }
    }

    /** Chips to turn on when the 发音人 drawer opens for this book. */
    fun initialFilter(scriptClass: String?): Set<String> {
        return when (scriptFamily(scriptClass)) {
            FAMILY_ZH -> setOf(CHINESE)
            FAMILY_EN -> setOf(EN_US, EN_UK)
            else -> emptySet()
        }
    }

    fun remember(
        slots: Map<String, String>,
        voiceId: String?,
        language: String?,
    ): Map<String, String> {
        val family = family(language, voiceId) ?: return slots
        if (voiceId.isNullOrBlank()) return slots
        return slots + (family to voiceId)
    }

    /** Keep the current speaker in its family slot when that slot is still empty. */
    fun seed(
        slots: Map<String, String>,
        activeVoiceId: String?,
        language: String?,
    ): Map<String, String> {
        val family = family(language, activeVoiceId) ?: return slots
        if (activeVoiceId.isNullOrBlank() || slots.containsKey(family)) return slots
        return slots + (family to activeVoiceId)
    }
}
