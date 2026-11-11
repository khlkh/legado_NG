package io.legado.app.help.annotation

/**
 * Prompt construction for the Word Gloss book glossary (Latin-script books
 * only).
 *
 * The hint language follows the app UI locale: Simplified Chinese on a
 * Chinese-UI device, English otherwise. The prompt asks for a broad pool
 * spanning common through rare; the reader filters locally by level floor and
 * per-book cap.
 */
object WordAnnotationPrompt {

    fun system(
        uiLocaleChinese: Boolean,
        cap: Int,
    ): String {
        val hintLanguage = if (uiLocaleChinese) "Simplified Chinese" else "English"
        val hintCap = if (uiLocaleChinese) {
            "no more than ${WordAnnotationConfig.MAX_HINT_CHINESE_CHARS} characters"
        } else {
            "no more than ${WordAnnotationConfig.MAX_HINT_WORDS} words"
        }
        val selectionLine = WordAnnotationLevels.supersetGuidance(uiLocaleChinese)
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
                "3. Copy each selected word or phrase exactly as it appears in the supplied passage. " +
                    "Do not normalize spelling, capitalization, punctuation, or word forms."
            )
            appendLine(
                "4. If the meaning is genuinely ambiguous or cannot be determined reliably " +
                    "from the supplied context, omit the item."
            )
            appendLine()
            appendLine(selectionLine)
            appendLine("Return up to $cap items.")
            appendLine("For each item, set \"occurrence\" to 1.")
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
