package io.legado.app.help.annotation

import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookLanguageDetector
import io.legado.app.help.book.BookScriptClass
import io.legado.app.help.book.isImage

/**
 * Word Gloss is limited to Latin-script books.
 *
 * The prompt, the Gutenberg rank gate, and the word-boundary rules are
 * English/Latin-specific. CJK is excluded, and other scripts (Cyrillic,
 * Arabic, Hebrew, ...) are not supported either. A null
 * [Book.ReadConfig.scriptClass] means the book has not been classified yet:
 * keep the entry points visible in that case and verify the passage before
 * analyzing it.
 */
object WordAnnotationEligibility {

    fun supports(book: Book): Boolean {
        if (book.isImage) return false
        return supports(book.config.scriptClass)
    }

    /**
     * Whether the stored script class can use Word Gloss. `null` is
     * "unknown, keep visible"; the analyzer still checks the passage.
     */
    fun supports(scriptClass: String?): Boolean =
        when (BookScriptClass.fromStorage(scriptClass)) {
            BookScriptClass.Latin -> true
            BookScriptClass.Cjk, BookScriptClass.Other -> false
            null -> true
        }

    /** Whether the actual passage is Latin script when the class is unknown. */
    fun supportsPassage(scriptClass: String?, passage: String): Boolean =
        when (BookScriptClass.fromStorage(scriptClass)) {
            BookScriptClass.Latin -> true
            BookScriptClass.Cjk, BookScriptClass.Other -> false
            null -> BookLanguageDetector.classifyScript(passage) == BookScriptClass.Latin
        }
}
