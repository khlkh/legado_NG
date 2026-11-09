package io.legado.app.help.annotation

import android.util.Log
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.PreferKey
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
import io.legado.app.utils.getPrefString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Word Wise book glossary, English/Latin books only.
 *
 * Each chapter is analyzed with a small per-chapter call and its items are
 * appended to the book pool (deduped by surface, first hint wins, each item
 * validated against the chapter text). Chapters resolve their annotations
 * locally from the pool; the reader's level floor and per-book cap filter
 * locally. This is deliberately a glossary trade-off: a surface gets the same
 * hint anywhere in the book, at its first occurrence per chapter.
 */
object WordAnnotationAnalyzer {

    const val FAILURE_COOLDOWN_MILLIS = 60_000L
    private const val MAX_FAILURE_ENTRIES = 64
    private const val MAX_ANALYZED_ENTRIES = 256

    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val lastFailureAt = ConcurrentHashMap<String, Long>()
    private val analyzedChapters = ConcurrentHashMap.newKeySet<String>()
    private val poolLocks = ConcurrentHashMap<String, Any>()

    data class CanonicalParagraph(val sourceIndex: Int, val text: String)

    data class AnalysisConfig(
        val hintCharCap: Boolean,
        val providerId: String,
        val modelId: String,
    ) {
        val key: String =
            "char=$hintCharCap;provider=$providerId;model=$modelId;" +
                "freq=${WordAnnotationConfig.FREQUENCY_VERSION}"
    }

    /** Plain paragraphs only; titles, [newpage], <usehtml> blocks, and image paragraphs are skipped. */
    fun canonicalParagraphs(bookContent: BookContent): List<CanonicalParagraph> =
        bookContent.textList.mapIndexedNotNull { index, content ->
            val trimmed = content.trim()
            if (trimmed == "[newpage]") return@mapIndexedNotNull null
            if (trimmed.startsWith("<usehtml>")) return@mapIndexedNotNull null
            if (AppPattern.imgPattern.matcher(content).find()) return@mapIndexedNotNull null
            CanonicalParagraph(index, content)
        }

    fun poolFile(book: Book): File =
        File(
            File(BookHelp.cachePath, book.getFolderName()),
            WordAnnotationPoolStore.POOL_FILE_NAME,
        )

    /** The app UI language, following the explicit app setting like AppContextWrapper does. */
    fun appUiLocaleChinese(): Boolean {
        val set = appCtx.getPrefString(PreferKey.language)
        return when (set) {
            "zh" -> true
            "en" -> false
            else -> Locale.getDefault().language == "zh"
        }
    }

    /** True only for Latin-script books; CJK books get no hints in this slice. */
    fun bookLanguage(passage: String, scriptClass: String?): WordAnnotationLanguage =
        when (scriptClass) {
            "cjk" -> WordAnnotationLanguage.CHINESE
            "latin" -> WordAnnotationLanguage.ENGLISH
            else -> if (BookLanguageDetector.classifyScript(passage) == BookScriptClass.Cjk) {
                WordAnnotationLanguage.CHINESE
            } else {
                WordAnnotationLanguage.ENGLISH
            }
        }

    fun analysisConfig(book: Book, uiLocaleChinese: Boolean): AnalysisConfig {
        val providerId = AiConfig.wordAnnotationProviderId.ifBlank { AiProviderStore.activeProviderId() }
        val modelId = AiConfig.wordAnnotationModelId.ifBlank {
            AiProviderStore.provider(providerId)?.model.orEmpty()
        }
        return AnalysisConfig(
            hintCharCap = uiLocaleChinese,
            providerId = providerId,
            modelId = modelId,
        )
    }

    /**
     * Pool target = max(120, chapterCount * cap * 20%), bounded by the cost
     * guard and by the number of items the book could ever contribute
     * (chapterCount * cap), so short books stop instead of calling forever.
     */
    fun poolTarget(chapterCount: Int, cap: Int): Int {
        val byLength = (chapterCount.toLong() * cap * WordAnnotationPoolConfig.POOL_TARGET_RATIO).toLong()
        val byBookMax = chapterCount.toLong() * cap
        return byLength.coerceAtLeast(WordAnnotationPoolConfig.MIN_POOL_TARGET.toLong())
            .coerceAtMost(WordAnnotationPoolConfig.MAX_POOL_TARGET.toLong())
            .coerceAtMost(byBookMax)
            .toInt()
    }

    private fun identityFor(book: Book, config: AnalysisConfig) = WordAnnotationPoolIdentity(
        bookUrl = book.bookUrl,
        hintCharCap = config.hintCharCap,
        providerId = config.providerId,
        modelId = config.modelId,
    )

    private fun readPool(book: Book, config: AnalysisConfig): List<WordAnnotationPoolItem>? =
        WordAnnotationPoolStore.read(poolFile(book), identityFor(book, config))

    /** True when the book has a readable pool (including an empty success). */
    fun hasPool(book: Book): Boolean {
        if (!book.getWordAnnotationEnabled()) return false
        val config = analysisConfig(book, appUiLocaleChinese())
        return readPool(book, config) != null
    }

    /**
     * Attaches pool annotations before the first layout; returns true when the
     * pool exists (even if it resolved to zero hints), false on a pool miss.
     */
    fun attachPool(
        textChapter: TextChapter,
        book: Book,
        bookChapter: BookChapter,
        bookContent: BookContent,
    ): Boolean {
        if (!book.getWordAnnotationEnabled()) return false
        val paragraphs = canonicalParagraphs(bookContent)
        if (paragraphs.isEmpty()) return false
        val passage = paragraphs.joinToString("\n") { it.text }
        if (bookLanguage(passage, book.config.scriptClass) != WordAnnotationLanguage.ENGLISH) return false
        val config = analysisConfig(book, appUiLocaleChinese())
        val items = readPool(book, config) ?: return false
        textChapter.wordAnnotationPoolAttached = true
        val resolved = WordAnnotationPoolResolver.resolveChapter(
            paragraphs = paragraphs,
            items = items,
            level = book.getWordAnnotationLevel(),
            cap = book.getWordAnnotationHintCap(),
        )
        if (book.isEpub) {
            // The EPUB renderer draws hints from native fragments; the native
            // layout must not reserve hint bands for them.
            textChapter.epubWordAnnotations = resolved
            textChapter.wordAnnotations = emptyList()
        } else {
            textChapter.wordAnnotations = resolved
        }
        return true
    }

    /**
     * Per-chapter analysis that appends to the book pool. One small model call
     * per chapter; once a chapter is analyzed (or the pool target is reached)
     * the call is a no-op. Merge and write happen under one book lock.
     */
    suspend fun analyzeChapterAndAppendPool(
        book: Book,
        bookChapter: BookChapter,
        bookContent: BookContent,
        textChapter: TextChapter,
    ) {
        if (!book.getWordAnnotationEnabled()) {
            Log.d("WordAnnotation", "skip flag off")
            AppLog.put("WordAnnotation skip: flag off")
            return
        }
        val paragraphs = canonicalParagraphs(bookContent)
        if (paragraphs.isEmpty()) {
            Log.d("WordAnnotation", "skip no paragraphs")
            AppLog.put("WordAnnotation skip: no eligible paragraphs")
            return
        }
        val passage = paragraphs.joinToString("\n") { it.text }
        // English/Latin books only; CJK books get no hints.
        if (bookLanguage(passage, book.config.scriptClass) != WordAnnotationLanguage.ENGLISH) {
            Log.d("WordAnnotation", "skip non-english book")
            return
        }
        val uiLocaleChinese = appUiLocaleChinese()
        val config = analysisConfig(book, uiLocaleChinese)
        val cap = book.getWordAnnotationHintCap().coerceIn(
            WordAnnotationConfig.MIN_WORD_ANNOTATION_HINT_CAP,
            WordAnnotationConfig.MAX_WORD_ANNOTATION_HINT_CAP,
        )
        val target = poolTarget(ReadBook.chapterSize, cap)
        val file = poolFile(book)
        val identity = identityFor(book, config)
        val hash = WordAnnotationPoolStore.canonicalHash(paragraphs.map { it.text })
        val chapterKey = "${book.bookUrl}|${bookChapter.index}|$hash|${config.key}"

        // A chapter is analyzed once per process; the append-triggered reload
        // and navigation re-launches must not call the model again.
        if (analyzedChapters.contains(chapterKey)) {
            Log.d("WordAnnotation", "skip chapter already analyzed ${bookChapter.index}")
            return
        }

        val lock = poolLocks.computeIfAbsent(book.bookUrl) { Any() }
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val existing = WordAnnotationPoolStore.read(file, identity) ?: emptyList()
            if (existing.size >= target) {
                AppLog.put("WordAnnotation pool complete size=${existing.size} target=$target")
                return
            }
            val lastFailure = lastFailureAt[chapterKey]
            if (lastFailure != null && now - lastFailure < FAILURE_COOLDOWN_MILLIS) {
                Log.d("WordAnnotation", "skip chapter cooldown ${bookChapter.index}")
                return
            }
            if (!inFlight.add(chapterKey)) {
                Log.d("WordAnnotation", "skip chapter in-flight ${bookChapter.index}")
                return
            }
        }
        try {
            AppLog.put("WordAnnotation analyze chapter=${bookChapter.index}")
            val result = withContext(Dispatchers.IO) {
                AiManager.generateText(
                    messages = listOf(
                        AiMessage(
                            AiMessage.Role.SYSTEM,
                            WordAnnotationPrompt.system(uiLocaleChinese, cap),
                        ),
                        AiMessage(AiMessage.Role.USER, WordAnnotationPrompt.user(passage)),
                    ),
                    params = AiTextParams(temperature = 0f, disableThinking = true, jsonResponse = true),
                    providerId = config.providerId,
                    modelId = config.modelId.ifBlank { null },
                )
            }
            val items = WordAnnotationParser.parse(result.content)
            var added = 0
            var mergedSize = -1
            if (items == null) {
                recordFailure(chapterKey)
                Log.e("WordAnnotation", "invalid chapter json ${bookChapter.index}")
                AppLog.put("WordAnnotation invalid chapter json chapter=${bookChapter.index}")
                return
            }
            // Merge and write under one lock so a concurrent chapter append
            // cannot be overwritten.
            synchronized(lock) {
                val current = WordAnnotationPoolStore.read(file, identity) ?: emptyList()
                val frequencyIndex = WordFrequencyAssets.load(appCtx)
                val known = current.mapTo(HashSet()) { it.surface }
                val next = ArrayList(current)
                for (item in items) {
                    if (!WordAnnotationResolver.hintWithinCap(
                            item.hint,
                            config.hintCharCap,
                            WordAnnotationConfig.MAX_HINT_WORDS,
                        )
                    ) continue
                    // Glossary guard: only pool a surface that actually occurs
                    // in this chapter's canonical text with boundary rules.
                    if (WordAnnotationResolver.findOccurrence(passage, item.text, 1) == null) continue
                    if (!known.add(item.text)) continue
                    next += WordAnnotationPoolItem(
                        surface = item.text,
                        hint = item.hint,
                        rank = frequencyIndex?.maxRankOf(item.text),
                    )
                    added++
                }
                val merged = next.take(target).toList()
                mergedSize = merged.size
                if (merged != current && !WordAnnotationPoolStore.write(file, identity, merged)) {
                    AppLog.put("WordAnnotation pool write failed")
                    return
                }
                markAnalyzed(chapterKey)
            }
            AppLog.put(
                "WordAnnotation chapter tokens=${result.promptTokens ?: -1}/${result.completionTokens ?: -1} " +
                    "items=${items.size} added=$added pool=$mergedSize/$target chapter=${bookChapter.index}",
            )
            val currentBook = ReadBook.book ?: return
            if (currentBook.bookUrl != book.bookUrl || !currentBook.getWordAnnotationEnabled()) {
                AppLog.put("WordAnnotation pool append rejected: book changed")
                return
            }
            if (added > 0) {
                ReadBook.loadContent(false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailure(chapterKey)
            Log.e("WordAnnotation", "chapter analysis failed ${bookChapter.index}", e)
            AppLog.put("WordAnnotation chapter analysis failed chapter=${bookChapter.index}: ${e.message}", e)
        } finally {
            inFlight.remove(chapterKey)
        }
    }

    private fun markAnalyzed(key: String) {
        analyzedChapters.add(key)
        if (analyzedChapters.size > MAX_ANALYZED_ENTRIES) {
            val oldest = analyzedChapters.take(analyzedChapters.size / 2)
            analyzedChapters.removeAll(oldest)
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
}
