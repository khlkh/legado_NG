package io.legado.app.ui.config

import io.legado.app.help.tts.TtsVoice
import io.legado.app.help.tts.TtsVoiceLanguage

object TtsVoiceFilterSupport {

    fun matchesName(voice: TtsVoice, query: String): Boolean {
        return voice.name.contains(query.trim(), ignoreCase = true)
    }

    fun availableLanguageLabels(voices: List<TtsVoice>): List<String> {
        return sortLanguageLabels(
            voices.flatMap { languageLabels(it.language) }.distinct()
        )
    }

    fun languageLabels(language: String?): List<String> {
        val raw = language?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val values = raw
            .replace('，', ',')
            .replace('、', ',')
            .replace(';', ',')
            .replace('；', ',')
            .replace('/', ',')
            .replace('|', ',')
            .replace('+', ',')
            .split(',', ' ', '\n', '\t')
            .mapNotNull(::languageLabel)
            .distinct()
        return values.ifEmpty {
            languageLabel(raw)?.let(::listOf).orEmpty()
        }
    }

    fun genderLabel(gender: String?): String? {
        return when (gender?.takeIf { it.isNotBlank() }?.lowercase()) {
            "male", "man" -> "男"
            "female", "woman" -> "女"
            else -> null
        }
    }

    fun sectionLabel(labels: List<String>): String? {
        return TtsVoiceLanguage.section(labels)
    }

    fun initialLanguageFilter(scriptClass: String?): Set<String> {
        return TtsVoiceLanguage.initialFilter(scriptClass)
    }

    private fun sortLanguageLabels(labels: List<String>): List<String> {
        val preferredOrder = listOf(
            TtsVoiceLanguage.CHINESE,
            TtsVoiceLanguage.EN_US,
            TtsVoiceLanguage.EN_UK,
            "日", "韩", "法", "德", "西", "葡", "粤", "吴",
        )
        return labels.sortedWith(
            compareBy<String> {
                preferredOrder.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
            }.thenBy { it }
        )
    }

    private fun languageLabel(language: String?): String? {
        return TtsVoiceLanguage.label(language)
    }
}
