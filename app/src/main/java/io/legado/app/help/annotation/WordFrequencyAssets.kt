package io.legado.app.help.annotation

import android.content.Context
import android.util.Log

/**
 * Loads the generated Project Gutenberg rank asset used by the Word Gloss fusion
 * prototype.
 *
 * The asset is generated from the canonical Wiktionary 2006/04 frequency list
 * by `tools/word-annotation/generate_frequency_rank.py`. Its header carries the
 * source, snapshot date, and the CC BY-SA 4.0 notice. The raw source pages are
 * not part of the app.
 *
 * A missing or corrupt asset is not fatal: [load] returns null, the fusion
 * resolver still runs, and every hint gets the [FrequencyBand.UNKNOWN] band.
 */
object WordFrequencyAssets {

    const val VERSION = "gutenberg-2006-04"
    const val ASSET_PATH = "word_annotation/frequency_rank_2006_04.txt"

    private const val TAG = "WordFrequency"

    @Volatile
    private var cached: WordFrequencyIndex? = null

    fun load(context: Context): WordFrequencyIndex? {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val index = runCatching {
                context.assets.open(ASSET_PATH).bufferedReader().useLines { lines ->
                    WordFrequencyIndex.fromRankAssetLines(lines)
                }
            }.onFailure {
                Log.w(TAG, "failed to load $ASSET_PATH", it)
                io.legado.app.constant.AppLog.put("WordFrequency load failed: ${it.message}")
            }.getOrNull() ?: return null
            Log.i(TAG, "loaded $VERSION: ${index.size} tokens")
            io.legado.app.constant.AppLog.put("WordFrequency loaded $VERSION: ${index.size} tokens")
            cached = index
            return index
        }
    }
}
