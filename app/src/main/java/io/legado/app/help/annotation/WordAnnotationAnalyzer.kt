package io.legado.app.help.annotation

import android.util.Log
import io.legado.app.BuildConfig
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
import kotlin.math.ceil

/**
 * Smart Glosses candidate discovery and chapter attachment.
 *
 * Discovery is windowed: a chapter is split into paragraph-aligned,
 * overlapping sections, and each section gets its own model budget. The
 * per-window results are merged into that chapter's candidate entry. Window
 * completion is persisted, so restarting the app does not re-analyze a
 * section. Chapters resolve hints from their own entry, not from a global
 * book pool; later chapters therefore do not make earlier chapters scan an
 * ever-growing inventory.
 *
 * Latin-script books only; CJK and other non-Latin books get no hints.
 */
object WordAnnotationAnalyzer {

    const val FAILURE_COOLDOWN_MILLIS = 60_000L
    private const val MAX_FAILURE_ENTRIES = 64


    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val lastFailureAt = ConcurrentHashMap<String, Long>()
    private val poolLocks = ConcurrentHashMap<String, Any>()

    data class CanonicalParagraph(val sourceIndex: Int, val text: String)

    data class DiscoveryWindow(
        val index: Int,
        val text: String,
        val startParagraph: Int,
        val endParagraph: Int,
    )

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

    fun perWindowCap(discoveryTarget: Int, windowCount: Int): Int =
        ceil(discoveryTarget.toDouble() / windowCount.coerceAtLeast(1)).toInt().coerceAtLeast(1)

    /**
     * Split canonical paragraphs into paragraph-aligned, overlapping windows.
     * Windows are word-based, so a very long chapter gets more independent
     * discovery budgets without cutting a phrase at an arbitrary character.
     */
    fun discoveryWindows(paragraphs: List<CanonicalParagraph>): List<DiscoveryWindow> {
        if (paragraphs.isEmpty()) return emptyList()
        val paragraphWords = paragraphs.map { WordAnnotationTargets.countWords(it.text).coerceAtLeast(1) }
        val totalWords = paragraphWords.sum()
        val count = WordAnnotationTargets.windowCount(totalWords)
        if (count <= 1) {
            return listOf(
                DiscoveryWindow(
                    index = 0,
                    text = paragraphs.joinToString("\n") { it.text },
                    startParagraph = 0,
                    endParagraph = paragraphs.size,
                ),
            )
        }
        val targetWords = ceil(totalWords.toDouble() / count).toInt().coerceAtLeast(1)
        val overlap = (targetWords / 20).coerceAtLeast(1)
        val ends = IntArray(paragraphs.size)
        var accumulated = 0
        paragraphWords.forEachIndexed { index, words ->
            accumulated += words
            ends[index] = accumulated
        }
        val result = ArrayList<DiscoveryWindow>(count)
        for (i in 0 until count) {
            val startWord = (i * targetWords - overlap).coerceAtLeast(0)
            val endWord = if (i == count - 1) totalWords
            else ((i + 1) * targetWords + overlap).coerceAtMost(totalWords)
            val start = ends.indexOfFirst { it > startWord }.takeIf { it >= 0 } ?: (paragraphs.size - 1)
            val end = if (i == count - 1) paragraphs.size
            else ends.indexOfFirst { it >= endWord }.let { if (it < 0) paragraphs.size else it + 1 }
            if (end <= start) continue
            result += DiscoveryWindow(
                index = i,
                text = paragraphs.subList(start, end).joinToString("\n") { it.text },
                startParagraph = start,
                endParagraph = end,
            )
        }
        return result
            .distinctBy { it.startParagraph to it.endParagraph }
            .mapIndexed { index, window -> window.copy(index = index) }
    }

    private fun identityFor(book: Book, config: AnalysisConfig) = WordAnnotationPoolIdentity(
        bookUrl = book.bookUrl,
        hintCharCap = config.hintCharCap,
        providerId = config.providerId,
        modelId = config.modelId,
    )

    private fun readState(book: Book, config: AnalysisConfig): WordAnnotationPoolState? =
        WordAnnotationPoolStore.read(poolFile(book), identityFor(book, config))

    /**
     * Attaches the current chapter's candidates before layout. Returns false
     * when this chapter has no analysis entry yet; the caller then runs
     * discovery.
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
        if (!WordAnnotationEligibility.supportsPassage(book.config.scriptClass, passage)) return false
        val config = analysisConfig(book, appUiLocaleChinese())
        val state = readState(book, config) ?: return false
        val hash = WordAnnotationPoolStore.canonicalHash(paragraphs.map { it.text })
        val chapter = state.chapters[bookChapter.index]?.takeIf { it.contentHash == hash }
            ?: return false
        // The entry exists: mark attached even when it contains no candidates,
        // so cover/nav chapters and empty successes do not trigger reload loops.
        textChapter.wordAnnotationPoolAttached = true
        if (chapter.items.isEmpty()) {
            textChapter.epubWordAnnotations = emptyList()
            textChapter.wordAnnotations = emptyList()
            return true
        }
        val words = WordAnnotationTargets.wordCount(paragraphs.map { it.text })
        val density = WordAnnotationDensity.fromStorage(book.getWordAnnotationDensity())
        val target = WordAnnotationTargets.displayTarget(
            words = words,
            level = book.getWordAnnotationLevel(),
            density = density,
        )
        val resolved = WordAnnotationPoolResolver.resolveChapter(
            paragraphs = paragraphs,
            items = chapter.items,
            level = book.getWordAnnotationLevel(),
            cap = target,
            coverageBuckets = WordAnnotationTargets.coverageBuckets(target),
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
     * Discover candidates window by window. Each successful window is written
     * immediately, including its completion marker. A failure only prevents
     * that window from being marked complete; later opens retry just the
     * unfinished windows.
     */
    suspend fun analyzeChapterAndAppendPool(
        book: Book,
        bookChapter: BookChapter,
        bookContent: BookContent,
        textChapter: TextChapter,
    ) {
        if (!book.getWordAnnotationEnabled()) {
            Log.d("WordAnnotation", "skip flag off")
            return
        }
        if (!WordAnnotationEligibility.supports(book)) {
            Log.d("WordAnnotation", "skip unsupported script")
            return
        }
        val paragraphs = canonicalParagraphs(bookContent)
        if (paragraphs.isEmpty()) {
            Log.d("WordAnnotation", "skip no paragraphs")
            return
        }
        val passage = paragraphs.joinToString("\n") { it.text }
        if (!WordAnnotationEligibility.supportsPassage(book.config.scriptClass, passage)) {
            Log.d("WordAnnotation", "skip non-latin book")
            return
        }
        val uiLocaleChinese = appUiLocaleChinese()
        val config = analysisConfig(book, uiLocaleChinese)
        val windows = discoveryWindows(paragraphs)
        if (windows.isEmpty()) return
        val discoveryTarget = WordAnnotationTargets.discoveryTarget(
            WordAnnotationTargets.wordCount(paragraphs.map { it.text }),
        )
        if (discoveryTarget <= 0) return
        val perWindowCap = perWindowCap(discoveryTarget, windows.size)
        val file = poolFile(book)
        val identity = identityFor(book, config)
        val hash = WordAnnotationPoolStore.canonicalHash(paragraphs.map { it.text })
        val chapterKey = "${book.bookUrl}|${bookChapter.index}|$hash|${config.key}"

        val initial = readState(book, config)
        val initialChapter = initial?.chapters?.get(bookChapter.index)?.takeIf { it.contentHash == hash }
        val pending = windows.filter { it.index !in initialChapter?.completedWindows.orEmpty() }
        if (pending.isEmpty()) {
            Log.d("WordAnnotation", "skip chapter windows complete ${bookChapter.index}")
            return
        }
        if (!inFlight.add(chapterKey)) {
            Log.d("WordAnnotation", "skip chapter in-flight ${bookChapter.index}")
            return
        }

        val lock = poolLocks.computeIfAbsent(book.bookUrl) { Any() }
        var added = 0
        var promptTokens = 0
        var completionTokens = 0
        try {
            AppLog.put(
                "WordAnnotation discover chapter=${bookChapter.index} windows=${windows.size} " +
                    "pending=${pending.size} discovery=$discoveryTarget perWindow=$perWindowCap",
            )
            for (window in pending) {
                val windowKey = "$chapterKey|window=${window.index}"
                val now = System.currentTimeMillis()
                if (lastFailureAt[windowKey]?.let { now - it < FAILURE_COOLDOWN_MILLIS } == true) {
                    Log.d("WordAnnotation", "skip window cooldown ${bookChapter.index}/${window.index}")
                    continue
                }
                val result = try {
                    withContext(Dispatchers.IO) {
                        AiManager.generateText(
                            messages = listOf(
                                AiMessage(
                                    AiMessage.Role.SYSTEM,
                                    WordAnnotationPrompt.system(
                                        uiLocaleChinese = uiLocaleChinese,
                                        cap = perWindowCap,
                                        windowIndex = window.index,
                                        windowCount = windows.size,
                                    ),
                                ),
                                AiMessage(AiMessage.Role.USER, WordAnnotationPrompt.user(window.text)),
                            ),
                            params = AiTextParams(temperature = 0f, disableThinking = true, jsonResponse = true),
                            providerId = config.providerId,
                            modelId = config.modelId.ifBlank { null },
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    recordFailure(windowKey)
                    Log.e("WordAnnotation", "window analysis failed ${bookChapter.index}/${window.index}", e)
                    AppLog.put(
                        "WordAnnotation window failed chapter=${bookChapter.index} window=${window.index}: ${e.message}",
                    )
                    continue
                }
                promptTokens += result.promptTokens ?: 0
                completionTokens += result.completionTokens ?: 0
                val items = WordAnnotationParser.parse(result.content)
                if (items == null) {
                    recordFailure(windowKey)
                    Log.e("WordAnnotation", "invalid window json ${bookChapter.index}/${window.index}")
                    AppLog.put("WordAnnotation invalid window json chapter=${bookChapter.index} window=${window.index}")
                    continue
                }
                val inflectionProbe = if (BuildConfig.DEBUG) {
                    val measured = items.filter {
                        WordAnnotationResolver.hintWithinCap(
                            it.hint,
                            config.hintCharCap,
                            WordAnnotationConfig.MAX_HINT_WORDS,
                        )
                    }
                    WordAnnotationInflectionProbe.probe(window.text, measured)
                } else null
                if (inflectionProbe != null && inflectionProbe.exactMisses > 0) {
                    Log.i("WordAnnotation", "inflection samples: ${inflectionProbe.detail()}")
                }
                var windowAdded = 0
                synchronized(lock) {
                    val state = readState(book, config) ?: WordAnnotationPoolState()
                    val chapter = state.chapters[bookChapter.index]?.takeIf { it.contentHash == hash }
                        ?: WordAnnotationChapterPool(bookChapter.index, hash)
                    if (window.index in chapter.completedWindows) {
                        // Another process completed it while this call was in flight.
                        return@synchronized
                    }
                    val frequencyIndex = WordFrequencyAssets.load(appCtx)
                    val known = chapter.items.mapTo(HashSet()) { it.surface }
                    val nextItems = ArrayList(chapter.items)
                    val remaining = (discoveryTarget - chapter.items.size).coerceAtLeast(0)
                    val windowLimit = minOf(perWindowCap, remaining)
                    var acceptedForWindow = 0
                    for (item in items) {
                        if (acceptedForWindow >= windowLimit) break
                        if (!WordAnnotationResolver.hintWithinCap(
                                item.hint,
                                config.hintCharCap,
                                WordAnnotationConfig.MAX_HINT_WORDS,
                            )
                        ) continue
                        // Only keep a surface that occurs exactly in this window;
                        // the pool is still surface-keyed and layout resolves all
                        // occurrences later.
                        if (WordAnnotationResolver.findOccurrence(window.text, item.text, 1) == null) continue
                        if (!known.add(item.text)) continue
                        nextItems += WordAnnotationPoolItem(
                            surface = item.text,
                            hint = item.hint,
                            rank = frequencyIndex?.maxRankOf(item.text),
                            difficulty = item.difficulty?.coerceIn(1, 5),
                            contextual = item.contextual,
                        )
                        acceptedForWindow++
                        windowAdded++
                    }
                    val nextChapter = chapter.copy(
                        completedWindows = chapter.completedWindows + window.index,
                        items = nextItems,
                    )
                    val nextState = WordAnnotationPoolState(
                        state.chapters + (bookChapter.index to nextChapter),
                    )
                    if (!WordAnnotationPoolStore.write(file, identity, nextState)) {
                        AppLog.put("WordAnnotation pool write failed")
                        return@synchronized
                    }
                    added += windowAdded
                    AppLog.put(
                        "WordAnnotation window chapter=${bookChapter.index} window=${window.index} " +
                            "items=${items.size} accepted=$acceptedForWindow added=$windowAdded " +
                            "chapterPool=${nextItems.size}",
                    )
                }
            }

            val finalState = readState(book, config)
            val finalChapter = finalState?.chapters?.get(bookChapter.index)?.takeIf { it.contentHash == hash }
            val allCompleted = finalChapter != null &&
                windows.all { it.index in finalChapter.completedWindows }
            if (finalChapter != null && BuildConfig.DEBUG) {
                WordAnnotationInflectionProbe.poolVariants(finalChapter.items).takeIf { it.groups > 0 }?.let { report ->
                    AppLog.put("WordAnnotation inflection pool ${report.summary()}")
                    Log.i("WordAnnotation", "inflection pool samples: ${report.detail()}")
                }
            }
            AppLog.put(
                "WordAnnotation chapter tokens=$promptTokens/$completionTokens " +
                    "windows=${windows.size} completed=${finalChapter?.completedWindows?.size ?: 0} " +
                    "items=${finalChapter?.items?.size ?: 0} added=$added chapter=${bookChapter.index}",
            )
            val currentBook = ReadBook.book
            if (currentBook?.bookUrl == book.bookUrl && currentBook.getWordAnnotationEnabled()) {
                if (added > 0 || allCompleted) {
                    ReadBook.loadContent(false)
                }
            } else if (added > 0) {
                AppLog.put("WordAnnotation pool append not reloaded: book changed")
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
