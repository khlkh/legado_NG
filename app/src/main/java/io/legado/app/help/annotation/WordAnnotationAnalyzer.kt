package io.legado.app.help.annotation

import android.util.Log
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiManager
import io.legado.app.help.ai.AiMessage
import io.legado.app.help.ai.AiProviderStore
import io.legado.app.help.ai.AiTextParams
import io.legado.app.help.book.BookContent
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.BookLanguageDetector
import io.legado.app.help.book.BookScriptClass
import io.legado.app.help.book.isEpub
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.entities.TextChapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Wave 1 analysis pipeline: canonical paragraphs -> cache -> model -> resolve
 * -> cache -> publish. The cache-hit path runs before the first layout; the
 * network path runs in the background and re-renders only after a publish
 * check confirms the book, chapter, flag, and canonical hash are still current.
 *
 * The cache file stores annotations with the layout's source paragraph index,
 * so [attachCached] assigns them directly without a second index mapping.
 */
object WordAnnotationAnalyzer {

    const val FAILURE_COOLDOWN_MILLIS = 60_000L
    private const val MAX_FAILURE_ENTRIES = 64

    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val lastFailureAt = ConcurrentHashMap<String, Long>()

    data class CanonicalParagraph(val sourceIndex: Int, val text: String)

    /** Plain paragraphs only; titles, [newpage], <usehtml> blocks, and image paragraphs are skipped. */
    fun canonicalParagraphs(bookContent: BookContent): List<CanonicalParagraph> =
        bookContent.textList.mapIndexedNotNull { index, content ->
            val trimmed = content.trim()
            if (trimmed == "[newpage]") return@mapIndexedNotNull null
            if (trimmed.startsWith("<usehtml>")) return@mapIndexedNotNull null
            if (AppPattern.imgPattern.matcher(content).find()) return@mapIndexedNotNull null
            CanonicalParagraph(index, content)
        }

    fun annotationFile(book: Book, bookChapter: BookChapter): File =
        File(
            File(BookHelp.cachePath, book.getFolderName()),
            WordAnnotationStore.annotationFileName(bookChapter.getFileName()),
        )

    /** Attaches a cache hit before the first layout; a miss leaves annotations empty. */
    fun attachCached(
        textChapter: TextChapter,
        book: Book,
        bookChapter: BookChapter,
        bookContent: BookContent,
    ) {
        if (!book.getWordAnnotationEnabled() || book.isEpub) return
        val paragraphs = canonicalParagraphs(bookContent)
        if (paragraphs.isEmpty()) return
        val hash = WordAnnotationStore.canonicalHash(paragraphs.map { it.text })
        val identity = WordAnnotationIdentity(book.bookUrl, bookChapter.url, bookChapter.index, hash)
        val cached = WordAnnotationStore.read(annotationFile(book, bookChapter), identity) ?: return
        // The file already stores layout source paragraph indices.
        textChapter.wordAnnotations = cached
    }

    /**
     * Resolves model items against the canonical paragraphs. Returns null when
     * the model body was not valid JSON (never cache that as empty success);
     * returns an empty list for a valid body that resolves to nothing.
     */
    internal fun resolveItems(
        paragraphs: List<CanonicalParagraph>,
        items: List<WordAnnotationItem>?,
        uiLocaleChinese: Boolean,
    ): List<WordAnnotation>? {
        if (items == null) return null
        return WordAnnotationResolver.resolve(paragraphs.map { it.text }, items, uiLocaleChinese)
            .map { annotation ->
                annotation.copy(paragraphIndex = paragraphs[annotation.paragraphIndex].sourceIndex)
            }
    }

    suspend fun analyzeAndPublish(
        book: Book,
        bookChapter: BookChapter,
        bookContent: BookContent,
        textChapter: TextChapter,
    ) {
        if (!book.getWordAnnotationEnabled()) {
            Log.d("WordAnnotation", "skip flag off chapter=${bookChapter.index}")
            AppLog.put("WordAnnotation skip: flag off chapter=${bookChapter.index}")
            return
        }
        if (book.isEpub) {
            Log.d("WordAnnotation", "skip epub chapter=${bookChapter.index}")
            return
        }
        val paragraphs = canonicalParagraphs(bookContent)
        if (paragraphs.isEmpty()) {
            Log.d("WordAnnotation", "skip no paragraphs chapter=${bookChapter.index}")
            AppLog.put("WordAnnotation skip: no eligible paragraphs chapter=${bookChapter.index}")
            return
        }
        val hash = WordAnnotationStore.canonicalHash(paragraphs.map { it.text })
        val identity = WordAnnotationIdentity(book.bookUrl, bookChapter.url, bookChapter.index, hash)
        val file = annotationFile(book, bookChapter)

        // An existing hit (including an empty success) never calls the model again.
        if (WordAnnotationStore.read(file, identity) != null) {
            Log.d("WordAnnotation", "skip cache hit chapter=${bookChapter.index}")
            AppLog.put("WordAnnotation skip: cache hit chapter=${bookChapter.index}")
            return
        }

        val key = "${book.bookUrl}|${bookChapter.index}|$hash"
        val now = System.currentTimeMillis()
        val lastFailure = lastFailureAt[key]
        if (lastFailure != null && now - lastFailure < FAILURE_COOLDOWN_MILLIS) {
            Log.d("WordAnnotation", "skip cooldown chapter=${bookChapter.index}")
            AppLog.put("WordAnnotation skip: cooldown chapter=${bookChapter.index}")
            return
        }
        if (!inFlight.add(key)) {
            Log.d("WordAnnotation", "skip in-flight chapter=${bookChapter.index}")
            AppLog.put("WordAnnotation skip: already in flight chapter=${bookChapter.index}")
            return
        }

        Log.d("WordAnnotation", "analyze start chapter=${bookChapter.index} paragraphs=${paragraphs.size}")
        AppLog.put("WordAnnotation analyze start chapter=${bookChapter.index} paragraphs=${paragraphs.size}")
        try {
            val uiLocaleChinese = Locale.getDefault().language == "zh"
            val passage = paragraphs.joinToString("\n") { it.text }
            val language = when (book.config.scriptClass) {
                "cjk" -> WordAnnotationLanguage.CHINESE
                "latin" -> WordAnnotationLanguage.ENGLISH
                else -> if (BookLanguageDetector.classifyScript(passage) == BookScriptClass.Cjk) {
                    WordAnnotationLanguage.CHINESE
                } else {
                    WordAnnotationLanguage.ENGLISH
                }
            }
            // Dedicated Word Wise model when configured; otherwise the active provider.
            val providerId = AiConfig.wordAnnotationProviderId.ifBlank { AiProviderStore.activeProviderId() }
            val modelId = AiConfig.wordAnnotationModelId.ifBlank {
                AiProviderStore.provider(providerId)?.model.orEmpty()
            }
            val result = withContext(Dispatchers.IO) {
                AiManager.generateText(
                    messages = listOf(
                        AiMessage(AiMessage.Role.SYSTEM, WordAnnotationPrompt.system(language, uiLocaleChinese)),
                        AiMessage(AiMessage.Role.USER, WordAnnotationPrompt.user(passage)),
                    ),
                    params = AiTextParams(temperature = 0f, disableThinking = true, jsonResponse = true),
                    providerId = providerId,
                    modelId = modelId.ifBlank { null },
                )
            }
            val items = WordAnnotationParser.parse(result.content)
            val resolved = resolveItems(paragraphs, items, uiLocaleChinese)
            if (resolved == null) {
                // Invalid model output must not be cached as an empty success.
                recordFailure(key)
                Log.e("WordAnnotation", "invalid json body chapter=${bookChapter.index}")
                AppLog.put("WordAnnotation invalid json chapter=${bookChapter.index}")
                return
            }
            AppLog.put(
                "WordAnnotation tokens=${result.promptTokens ?: -1}/${result.completionTokens ?: -1} " +
                    "items=${items?.size ?: 0} resolved=${resolved.size} chapter=${bookChapter.index}",
            )

            // Publish only when the open book, chapter, flag, and hash still match.
            if (!isCurrent(book, bookChapter, textChapter, hash)) {
                AppLog.put("WordAnnotation publish rejected: not current chapter=${bookChapter.index}")
                return
            }
            if (!WordAnnotationStore.write(file, identity, resolved)) {
                AppLog.put("WordAnnotation write failed chapter=${bookChapter.index}")
                return
            }

            if (resolved.isNotEmpty()) {
                // Re-render the in-memory chapter so the freshly written cache is laid out.
                ReadBook.loadContent(false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailure(key)
            Log.e("WordAnnotation", "failed chapter=${bookChapter.index}", e)
            AppLog.put("WordAnnotation failed chapter=${bookChapter.index}: ${e.message}", e)
        } finally {
            inFlight.remove(key)
        }
    }

    private fun recordFailure(key: String) {
        lastFailureAt[key] = System.currentTimeMillis()
        if (lastFailureAt.size > MAX_FAILURE_ENTRIES) {
            val oldest = lastFailureAt.entries
                .sortedBy { it.value }
                .take(lastFailureAt.size / 2)
            oldest.forEach { lastFailureAt.remove(it.key) }
        }
    }

    private fun isCurrent(
        book: Book,
        bookChapter: BookChapter,
        textChapter: TextChapter,
        hash: String,
    ): Boolean {
        val currentBook = ReadBook.book ?: return false
        if (currentBook.bookUrl != book.bookUrl) return false
        if (!currentBook.getWordAnnotationEnabled()) return false
        if (ReadBook.durChapterIndex != bookChapter.index) return false
        // The in-memory chapter must still be the text we analyzed.
        val currentBookContent = BookContent(
            sameTitleRemoved = textChapter.sameTitleRemoved,
            textList = textChapter.sourceParagraphs,
            effectiveReplaceRules = textChapter.effectiveReplaceRules,
        )
        val currentHash = WordAnnotationStore.canonicalHash(
            canonicalParagraphs(currentBookContent).map { it.text },
        )
        return currentHash == hash
    }
}
