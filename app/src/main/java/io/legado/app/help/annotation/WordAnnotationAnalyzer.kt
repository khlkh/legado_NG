package io.legado.app.help.annotation

import android.util.Log
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
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
import io.legado.app.help.book.ContentProcessor
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
 * Book-level Word Wise pool.
 *
 * The pool is built once per book from a few sampled chapters and cached in a
 * single book-level file. Every chapter then resolves its annotations locally
 * from the pool ([WordAnnotationPoolResolver]), filtered by the reader's level
 * floor and per-book cap ([WordAnnotationFilter]) — changing either re-filters
 * instantly and never re-calls the model.
 */
object WordAnnotationAnalyzer {

    const val FAILURE_COOLDOWN_MILLIS = 60_000L
    private const val MAX_FAILURE_ENTRIES = 64
    private const val FALLBACK_SAMPLE_HASH = "current-chapter-only"

    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val lastFailureAt = ConcurrentHashMap<String, Long>()

    data class CanonicalParagraph(val sourceIndex: Int, val text: String)

    data class AnalysisConfig(
        val hintCharCap: Boolean,
        val maxHintWords: Int,
        val providerId: String,
        val modelId: String,
    ) {
        val key: String =
            "char=$hintCharCap;words=$maxHintWords;provider=$providerId;model=$modelId;" +
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

    fun analysisConfig(book: Book, language: WordAnnotationLanguage, uiLocaleChinese: Boolean): AnalysisConfig {
        val hintCharCap = uiLocaleChinese && language != WordAnnotationLanguage.CHINESE
        val maxHintWords = if (language == WordAnnotationLanguage.CHINESE) {
            WordAnnotationConfig.MAX_HINT_WORDS_CHINESE_BOOK
        } else {
            WordAnnotationConfig.MAX_HINT_WORDS
        }
        val providerId = AiConfig.wordAnnotationProviderId.ifBlank { AiProviderStore.activeProviderId() }
        val modelId = AiConfig.wordAnnotationModelId.ifBlank {
            AiProviderStore.provider(providerId)?.model.orEmpty()
        }
        return AnalysisConfig(
            hintCharCap = hintCharCap,
            maxHintWords = maxHintWords,
            providerId = providerId,
            modelId = modelId,
        )
    }

    /** True when the book has a readable pool (including an empty success). */
    fun hasPool(book: Book, bookChapter: BookChapter, bookContent: BookContent): Boolean {
        if (!book.getWordAnnotationEnabled() || book.isEpub) return false
        val paragraphs = canonicalParagraphs(bookContent)
        if (paragraphs.isEmpty()) return false
        val uiLocaleChinese = appUiLocaleChinese()
        val language = bookLanguage(paragraphs.joinToString("\n") { it.text }, book.config.scriptClass)
        val config = analysisConfig(book, language, uiLocaleChinese)
        val hash = sampleHashFor(book, bookChapter, bookContent)
        return readPool(book, hash, config) != null
    }

    /** Attaches pool annotations before the first layout; a pool miss leaves annotations empty. */
    fun attachPool(
        textChapter: TextChapter,
        book: Book,
        bookChapter: BookChapter,
        bookContent: BookContent,
    ) {
        if (!book.getWordAnnotationEnabled() || book.isEpub) return
        val paragraphs = canonicalParagraphs(bookContent)
        if (paragraphs.isEmpty()) return
        val uiLocaleChinese = appUiLocaleChinese()
        val language = bookLanguage(paragraphs.joinToString("\n") { it.text }, book.config.scriptClass)
        val config = analysisConfig(book, language, uiLocaleChinese)
        val hash = sampleHashFor(book, bookChapter, bookContent)
        val items = readPool(book, hash, config) ?: return
        textChapter.wordAnnotations = WordAnnotationPoolResolver.resolveChapter(
            paragraphs = paragraphs,
            items = items,
            level = book.getWordAnnotationLevel(),
            cap = book.getWordAnnotationHintCap(),
            rankAware = language != WordAnnotationLanguage.CHINESE,
        )
    }

    /** Builds (or reuses) the book pool, then re-renders the current chapter from it. */
    suspend fun ensureBookPoolAndPublish(
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
        if (book.isEpub) {
            Log.d("WordAnnotation", "skip epub")
            return
        }
        val paragraphs = canonicalParagraphs(bookContent)
        if (paragraphs.isEmpty()) {
            Log.d("WordAnnotation", "skip no paragraphs")
            AppLog.put("WordAnnotation skip: no eligible paragraphs")
            return
        }
        val uiLocaleChinese = appUiLocaleChinese()
        val language = bookLanguage(paragraphs.joinToString("\n") { it.text }, book.config.scriptClass)
        val config = analysisConfig(book, language, uiLocaleChinese)
        val samples = sampleSet(book, bookChapter, bookContent)
        if (samples.texts.isEmpty()) {
            AppLog.put("WordAnnotation skip: no pool samples")
            return
        }
        val hash = if (samples.fallback) {
            FALLBACK_SAMPLE_HASH
        } else {
            WordAnnotationPoolStore.canonicalHash(samples.texts)
        }
        sampleHashCache[book.bookUrl] = hash
        val identity = WordAnnotationPoolIdentity(
            bookUrl = book.bookUrl,
            sampleHash = hash,
            hintCharCap = config.hintCharCap,
            providerId = config.providerId,
            modelId = config.modelId,
        )
        val file = poolFile(book)

        if (WordAnnotationPoolStore.read(file, identity) != null) {
            Log.d("WordAnnotation", "skip pool cache hit")
            AppLog.put("WordAnnotation skip: pool cache hit")
            return
        }

        val key = "${book.bookUrl}|$hash|${config.key}"
        val now = System.currentTimeMillis()
        val lastFailure = lastFailureAt[key]
        if (lastFailure != null && now - lastFailure < FAILURE_COOLDOWN_MILLIS) {
            Log.d("WordAnnotation", "skip pool cooldown")
            AppLog.put("WordAnnotation skip: pool cooldown")
            return
        }
        if (!inFlight.add(key)) {
            Log.d("WordAnnotation", "skip pool in-flight")
            AppLog.put("WordAnnotation skip: pool already in flight")
            return
        }

        val sampleTexts = samples.texts
        Log.d("WordAnnotation", "pool build start chapters=${sampleTexts.size} chars=${sampleTexts.sumOf { it.length }}")
        AppLog.put("WordAnnotation pool build start samples=${sampleTexts.size}")
        try {
            val result = withContext(Dispatchers.IO) {
                AiManager.generateText(
                    messages = listOf(
                        AiMessage(
                            AiMessage.Role.SYSTEM,
                            WordAnnotationPrompt.system(
                                language = language,
                                uiLocaleChinese = uiLocaleChinese,
                                cap = WordAnnotationPoolConfig.MAX_BOOK_POOL_ITEMS,
                                superset = true,
                            ),
                        ),
                        AiMessage(
                            AiMessage.Role.USER,
                            WordAnnotationPrompt.user(sampleTexts.joinToString("\n")),
                        ),
                    ),
                    params = AiTextParams(temperature = 0f, disableThinking = true, jsonResponse = true),
                    providerId = config.providerId,
                    modelId = config.modelId.ifBlank { null },
                )
            }
            val items = WordAnnotationParser.parse(result.content)
            val pool: List<WordAnnotationPoolItem>?
            if (items == null) {
                pool = null
            } else {
                // Pool items have no occurrence meaning; validate hint length,
                // dedupe surfaces (first wins), and attach English ranks.
                val frequencyIndex = WordFrequencyAssets.load(appCtx)
                val deduped = LinkedHashMap<String, WordAnnotationPoolItem>()
                for (item in items) {
                    if (!WordAnnotationResolver.hintWithinCap(
                            item.hint,
                            config.hintCharCap,
                            config.maxHintWords,
                        )
                    ) continue
                    if (deduped.containsKey(item.text)) continue
                    deduped[item.text] = WordAnnotationPoolItem(
                        surface = item.text,
                        hint = item.hint,
                        rank = frequencyIndex?.minRankOf(item.text),
                    )
                }
                pool = deduped.values.take(WordAnnotationPoolConfig.MAX_BOOK_POOL_ITEMS).toList()
            }
            if (pool == null) {
                recordFailure(key)
                Log.e("WordAnnotation", "invalid pool json")
                AppLog.put("WordAnnotation invalid pool json")
                return
            }
            AppLog.put(
                "WordAnnotation pool tokens=${result.promptTokens ?: -1}/${result.completionTokens ?: -1} " +
                    "items=${items?.size ?: 0} pooled=${pool.size}",
            )

            // Publish only when the same book is still open with the flag on.
            val currentBook = ReadBook.book ?: return
            if (currentBook.bookUrl != book.bookUrl || !currentBook.getWordAnnotationEnabled()) {
                AppLog.put("WordAnnotation pool publish rejected: book changed")
                return
            }
            if (!WordAnnotationPoolStore.write(file, identity, pool)) {
                AppLog.put("WordAnnotation pool write failed")
                return
            }
            if (pool.isNotEmpty()) {
                ReadBook.loadContent(false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailure(key)
            Log.e("WordAnnotation", "pool build failed", e)
            AppLog.put("WordAnnotation pool build failed: ${e.message}", e)
        } finally {
            inFlight.remove(key)
        }
    }

    private data class SampleSet(val texts: List<String>, val fallback: Boolean)

    private val sampleHashCache = ConcurrentHashMap<String, String>()

    /**
     * Deterministic sample: first, middle, and last chapters when their content
     * is available; only when none is available does it fall back to the current
     * chapter (hash fixed to a constant in that case, so later chapters reuse
     * the pool instead of rebuilding it).
     */
    private fun sampleSet(
        book: Book,
        currentChapter: BookChapter,
        currentContent: BookContent,
    ): SampleSet {
        val chapters = ArrayList<Pair<BookChapter, BookContent>>()
        val total = ReadBook.chapterSize
        val candidates = linkedSetOf(0, total / 2, total - 1).filter { it in 0 until total }
        for (index in candidates) {
            if (chapters.size >= WordAnnotationPoolConfig.MAX_POOL_SAMPLE_CHAPTERS) break
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, index) ?: continue
            val raw = BookHelp.getContent(book, chapter) ?: continue
            val processor = ContentProcessor.get(book.name, book.origin)
            val contents = processor.getContent(book, chapter, raw, includeTitle = false)
            if (contents.textList.isEmpty()) continue
            chapters += chapter to contents
        }
        val fallback = chapters.isEmpty()
        if (fallback) {
            val currentParagraphs = canonicalParagraphs(currentContent)
            if (currentParagraphs.isEmpty()) return SampleSet(emptyList(), true)
            chapters += currentChapter to currentContent
        }
        // Bound the prompt by characters across all samples.
        val budget = WordAnnotationPoolConfig.MAX_POOL_SAMPLE_CHARS
        val texts = ArrayList<String>()
        var used = 0
        for ((_, contents) in chapters) {
            for (paragraph in canonicalParagraphs(contents)) {
                if (used >= budget) break
                val text = paragraph.text
                val remaining = budget - used
                texts += if (text.length > remaining) text.substring(0, remaining) else text
                used += texts.last().length
            }
            if (used >= budget) break
        }
        return SampleSet(texts, fallback)
    }

    private fun sampleHashFor(
        book: Book,
        currentChapter: BookChapter,
        currentContent: BookContent,
    ): String = sampleHashCache[book.bookUrl] ?: run {
        val set = sampleSet(book, currentChapter, currentContent)
        val hash = if (set.fallback) {
            FALLBACK_SAMPLE_HASH
        } else {
            WordAnnotationPoolStore.canonicalHash(set.texts)
        }
        sampleHashCache[book.bookUrl] = hash
        hash
    }

    private fun readPool(
        book: Book,
        hash: String,
        config: AnalysisConfig,
    ): List<WordAnnotationPoolItem>? = WordAnnotationPoolStore.read(
        poolFile(book),
        WordAnnotationPoolIdentity(
            bookUrl = book.bookUrl,
            sampleHash = hash,
            hintCharCap = config.hintCharCap,
            providerId = config.providerId,
            modelId = config.modelId,
        ),
    )

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
