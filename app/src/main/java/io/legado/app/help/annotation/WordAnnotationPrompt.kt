package io.legado.app.help.annotation

/**
 * Prompt construction for Wave 1.
 *
 * One shared contract, with a language-specific selection line chosen from the
 * book's script class (English vs Chinese). The hint language is the app UI
 * locale, not the book's languageHint setting.
 */
enum class WordAnnotationLanguage { ENGLISH, CHINESE }

object WordAnnotationPrompt {

    fun system(
        language: WordAnnotationLanguage,
        uiLocaleChinese: Boolean,
        level: Int = WordAnnotationConfig.DEFAULT_WORD_ANNOTATION_LEVEL,
        cap: Int = WordAnnotationConfig.DEFAULT_WORD_ANNOTATION_HINT_CAP,
        superset: Boolean = false,
    ): String {
        val selectionLine = when (language) {
            WordAnnotationLanguage.ENGLISH ->
                "For English text, target advanced vocabulary, idioms, phrasal verbs, " +
                    "and expressions whose contextual meanings are not obvious."
            WordAnnotationLanguage.CHINESE ->
                "For Chinese text, target meaningful 成语, literary expressions, uncommon words, " +
                    "and context-dependent meanings in continuous text. Do not split on spaces."
        }
        // Chinese books always get English hints; English books follow the app UI locale.
        val hintLanguage = if (language == WordAnnotationLanguage.CHINESE) {
            "English"
        } else if (uiLocaleChinese) {
            "Simplified Chinese"
        } else {
            "English"
        }
        val hintCap = if (language == WordAnnotationLanguage.CHINESE) {
            "no more than ${WordAnnotationConfig.MAX_HINT_WORDS_CHINESE_BOOK} words"
        } else if (uiLocaleChinese) {
            "no more than ${WordAnnotationConfig.MAX_HINT_CHINESE_CHARS} characters"
        } else {
            "no more than ${WordAnnotationConfig.MAX_HINT_WORDS} words"
        }
        // Level guidance must match the hint language; for Chinese books the
        // guidance stays English so it cannot contradict the English-hint rule.
        // Superset analysis uses a level-independent broad pool instead.
        val levelGuidance = if (superset) {
            WordAnnotationLevels.supersetGuidance(
                uiLocaleChinese && language != WordAnnotationLanguage.CHINESE,
            )
        } else {
            WordAnnotationLevels.promptGuidance(
                level,
                uiLocaleChinese && language != WordAnnotationLanguage.CHINESE,
            )
        }
        return buildString {
            appendLine("You are a vocabulary assistant for a long-form ebook reader.")
            appendLine()
            appendLine(
                "Identify words and multiword expressions that a fluent but non-native reader " +
                    "may find difficult to understand in the supplied passage."
            )
            appendLine()
            appendLine("Selection rules:")
            appendLine(
                "1. Select words, idioms, and fixed multiword phrases whose contextual meaning " +
                    "is critical and non-obvious."
            )
            appendLine(
                "2. Prefer meaningful expressions when their combined meaning is more useful " +
                    "than separate word definitions."
            )
            appendLine(
                "3. Skip ordinary words, transparent compounds, and words the passage already makes clear."
            )
            appendLine("4. Stay sparse. Do not annotate every potentially unfamiliar word.")
            appendLine(
                "5. Copy each selected word or phrase exactly as it appears in the supplied passage. " +
                    "Do not normalize spelling, capitalization, punctuation, or word forms."
            )
            appendLine("6. Explain the meaning in this passage, not a dictionary list.")
            appendLine(
                "7. If the meaning is genuinely ambiguous or cannot be determined reliably " +
                    "from the supplied context, omit the item."
            )
            appendLine(
                "8. Do not annotate the same occurrence more than once. " +
                    "Avoid redundant and overlapping annotations."
            )
            appendLine()
            appendLine("Reader level guidance (this overrides rules 3 and 4):")
            appendLine(levelGuidance)
            appendLine("Return up to $cap items.")
            appendLine()
            appendLine(selectionLine)
            if (superset) {
                appendLine("For each item, set \"occurrence\" to 1.")
            } else {
                appendLine(
                    "For each item, set \"occurrence\" to the 1-based count of that exact text in the passage, " +
                        "counting only occurrences that are not part of a longer word."
                )
            }
            appendLine("Write each hint in $hintLanguage, using $hintCap.")
            appendLine()
            appendLine(
                "Return only valid JSON in the requested schema. " +
                    "Do not include Markdown, commentary, or text outside the JSON."
            )
        }.trimEnd()
    }

    fun user(passage: String): String = buildString {
        appendLine("Passage:")
        appendLine(passage)
        appendLine()
        appendLine("Return a JSON object of this shape:")
        append("""{"annotations":[{"text":"<exact text>","occurrence":1,"hint":"<short explanation>"}]}""")
    }
}
