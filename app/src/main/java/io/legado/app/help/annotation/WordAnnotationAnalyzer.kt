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
 * Word Wise analysis pipeline: canonical paragraphs -> cache -> model -> resolve
 * -> cache -> publish. The cache-hit path runs before the first layout; the
 * network path runs in the background and re-renders only after a publish
 * check confirms the book, chapter, flag, canonical hash, and the full
 * analysis config are still current.
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

    data class AnalysisConfig(
        val level: Int,
        val hintCap: Int,
        val hintCharCap: Boolean,
        val maxHintWords: Int,
        val providerId: String,
        val modelId: String,
    ) {
        val key: String =
            "level=$level;cap=$hintCap;char=$hintCharCap;words=$maxHintWords;" +
                "provider=$providerId;model=$modelId;freq=${WordAnnotationConfig.FREQUENCY_VERSION}"
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

    fun annotationFile(book: Book, bookChapter: BookChapter): File =
        File(
            File(BookHelp.cachePath, book.getFolderName()),
            WordAnnotationStore.annotationFileName(bookChapter.getFileName()),
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
            level = WordAnnotationLevels.normalize(book.getWordAnnotationLevel()),
            hintCap = book.getWordAnnotationHintCap().coerceIn(
                WordAnnotationConfig.MIN_WORD_ANNOTATION_HINT_CAP,
                WordAnnotationConfig.MAX_WORD_ANNOTATION_HINT_CAP,
            ),
            hintCharCap = hintCharCap,
            maxHintWords = maxHintWords,
            providerId = providerId,
            modelId = modelId,
        )
    }

    private fun identityFor(
        book: Book,
        bookChapter: BookChapter,
        hash: String,
        config: AnalysisConfig,
    ) = WordAnnotationIdentity(
        bookUrl = book.bookUrl,
        chapterUrl = bookChapter.url,
        chapterIndex = bookChapter.index,
        canonicalHash = hash,
        level = config.level,
        hintCap = config.hintCap,
        hintCharCap = config.hintCharCap,
        providerId = config.providerId,
        modelId = config.modelId,
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
        val uiLocaleChinese = appUiLocaleChinese()
        val language = bookLanguage(paragraphs.joinToString("\n") { it.text }, book.config.scriptClass)
        val config = analysisConfig(book, language, uiLocaleChinese)
        val cached = WordAnnotationStore.read(
            annotationFile(book, bookChapter),
            identityFor(book, bookChapter, hash, config),
        ) ?: return
        // The file already stores layout source paragraph indices.
        textChapter.wordAnnotations = cached
    }

    /** True when this chapter already has a readable cache (including an empty success). */
    fun hasCached(book: Book, bookChapter: BookChapter, bookContent: BookContent): Boolean {
        if (!book.getWordAnnotationEnabled() || book.isEpub) return false
        val paragraphs = canonicalParagraphs(bookContent)
        if (paragraphs.isEmpty()) return false
        val hash = WordAnnotationStore.canonicalHash(paragraphs.map { it.text })
        val uiLocaleChinese = appUiLocaleChinese()
        val language = bookLanguage(paragraphs.joinToString("\n") { it.text }, book.config.scriptClass)
        val config = analysisConfig(book, language, uiLocaleChinese)
        return WordAnnotationStore.read(
            annotationFile(book, bookChapter),
            identityFor(book, bookChapter, hash, config),
        ) != null
    }

    suspend fun analyzeAndPublish(
        book: Book,
        bookChapter: BookChapter,
        bookContent: BookContent,
        textChapter: TextChapter,
        prefetch: Boolean = false,
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
        val passage = paragraphs.joinToString("\n") { it.text }
        val uiLocaleChinese = appUiLocaleChinese()
        val language = bookLanguage(passage, book.config.scriptClass)
        val config = analysisConfig(book, language, uiLocaleChinese)
        val identity = identityFor(book, bookChapter, hash, config)
        val file = annotationFile(book, bookChapter)

        // An existing hit (including an empty success) never calls the model again.
        if (WordAnnotationStore.read(file, identity) != null) {
            Log.d("WordAnnotation", "skip cache hit chapter=${bookChapter.index}")
            AppLog.put("WordAnnotation skip: cache hit chapter=${bookChapter.index}")
            if (!prefetch) ReadBook.prefetchWordAnnotationAhead()
            return
        }

        val key = "${book.bookUrl}|${bookChapter.index}|$hash|${config.key}"
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
            val result = withContext(Dispatchers.IO) {
                AiManager.generateText(
                    messages = listOf(
                        AiMessage(
                            AiMessage.Role.SYSTEM,
                            WordAnnotationPrompt.system(language, uiLocaleChinese, config.level, config.hintCap),
                        ),
                        AiMessage(AiMessage.Role.USER, WordAnnotationPrompt.user(passage)),
                    ),
                    params = AiTextParams(temperature = 0f, disableThinking = true, jsonResponse = true),
                    providerId = config.providerId,
                    modelId = config.modelId.ifBlank { null },
                )
            }
            val items = WordAnnotationParser.parse(result.content)
            val resolved: List<WordAnnotation>?
            var stats: String? = null
            if (items == null) {
                resolved = null
            } else {
                // The rank asset is a soft prior in both paths; the prototype
                // flag only controls the inflection fallback. Level floor and
                // the per-book cap therefore apply in every build.
                val frequencyIndex = WordFrequencyAssets.load(appCtx)
                val hybrid = WordAnnotationHybrid.resolveDetailed(
                    paragraphs = paragraphs.map { it.text },
                    items = items,
                    uiLocaleChinese = config.hintCharCap,
                    frequencyIndex = frequencyIndex,
                    options = WordAnnotationHybrid.Options(
                        frequencyFirst = true,
                        lemmaFallback = WordAnnotationConfig.USE_FUSION_PROTOTYPE,
                        maxHintsPerChapter = config.hintCap,
                        minRankFloor = WordAnnotationLevels.floorFor(config.level),
                    ),
                    maxHintWords = config.maxHintWords,
                )
                stats = hybridStats(hybrid.results) +
                    " level=${config.level} floor=${WordAnnotationLevels.floorFor(config.level)}" +
                    " cap=${config.hintCap} gated=${hybrid.gatedByFloor}"
                resolved = hybrid.results.map { result ->
                    result.annotation.copy(
                        paragraphIndex = paragraphs[result.annotation.paragraphIndex].sourceIndex,
                    )
                }
            }
            if (resolved == null) {
                // Invalid model output must not be cached as an empty success.
                recordFailure(key)
                Log.e("WordAnnotation", "invalid json body chapter=${bookChapter.index}")
                AppLog.put("WordAnnotation invalid json chapter=${bookChapter.index}")
                return
            }
            AppLog.put(
                "WordAnnotation tokens=${result.promptTokens ?: -1}/${result.completionTokens ?: -1} " +
                    "items=${items?.size ?: 0} resolved=${resolved.size} chapter=${bookChapter.index}" +
                    " level=${config.level} prefetch=$prefetch" +
                    (stats?.let { " $it" } ?: ""),
            )

            // Publish only when the open book, chapter, flag, hash, and the
            // live analysis config still match the ones we analyzed with.
            if (!isPublishable(book, bookChapter, textChapter, identity, prefetch)) {
                AppLog.put("WordAnnotation publish rejected: not current chapter=${bookChapter.index}")
                return
            }
            if (!WordAnnotationStore.write(file, identity, resolved)) {
                AppLog.put("WordAnnotation write failed chapter=${bookChapter.index}")
                return
            }

            if (resolved.isNotEmpty() && !prefetch) {
                // Re-render the in-memory chapter so the freshly written cache is laid out.
                ReadBook.loadContent(false)
            }
            if (!prefetch) {
                // After the current chapter is cached, warm the chapters ahead
                // in the background so their hints are ready on arrival.
                ReadBook.prefetchWordAnnotationAhead()
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

    private fun hybridStats(results: List<WordAnnotationHybrid.Result>): String {
        val viaLemma = results.count { it.viaLemmaFallback }
        val bands = results.groupingBy { it.frequencyBand }.eachCount()
            .entries.joinToString(",") { "${it.key.name}=${it.value}" }
        val preview = results.take(8).joinToString(",") {
            "${it.annotation.surface}:${it.frequencyBand.name}"
        }
        val more = if (results.size > 8) ",+${results.size - 8}" else ""
        return "hybrid viaLemma=$viaLemma bands={$bands} selected=[$preview$more]"
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

    private fun isPublishable(
        book: Book,
        bookChapter: BookChapter,
        textChapter: TextChapter,
        identity: WordAnnotationIdentity,
        prefetch: Boolean,
    ): Boolean {
        val currentBook = ReadBook.book ?: return false
        if (currentBook.bookUrl != book.bookUrl) return false
        if (!currentBook.getWordAnnotationEnabled()) return false
        if (prefetch) {
            // A prefetch result is only cached while its chapter is still ahead
            // of the current chapter and within the configured prefetch window.
            val prefetchN = AiConfig.wordAnnotationPrefetchChapters
            val offset = bookChapter.index - ReadBook.durChapterIndex
            if (offset < 1 || offset > prefetchN) return false
        } else if (ReadBook.durChapterIndex != bookChapter.index) {
            return false
        }
        // The in-memory chapter must still be the text we analyzed.
        val currentBookContent = BookContent(
            sameTitleRemoved = textChapter.sameTitleRemoved,
            textList = textChapter.sourceParagraphs,
            effectiveReplaceRules = textChapter.effectiveReplaceRules,
        )
        val currentHash = WordAnnotationStore.canonicalHash(
            canonicalParagraphs(currentBookContent).map { it.text },
        )
        if (currentHash != identity.canonicalHash) return false
        // The live analysis config must still be the one we analyzed with.
        val currentLanguage = bookLanguage(
            textChapter.sourceParagraphs.joinToString("\n"),
            currentBook.config.scriptClass,
        )
        val currentConfig = analysisConfig(currentBook, currentLanguage, appUiLocaleChinese())
        return currentConfig.level == identity.level &&
            currentConfig.hintCap == identity.hintCap &&
            currentConfig.hintCharCap == identity.hintCharCap &&
            currentConfig.providerId == identity.providerId &&
            currentConfig.modelId == identity.modelId
    }
}
