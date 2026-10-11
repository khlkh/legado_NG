package io.legado.app.help.config

import com.google.gson.annotations.SerializedName
import io.legado.app.constant.PreferKey
import io.legado.app.ui.book.read.config.AiThemePair
import io.legado.app.ui.book.read.config.NgPaperLook
import io.legado.app.ui.book.read.config.counterpart
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

/** Saved AI reading looks, kept apart from the built-in default set. Day and night are separate. */
internal object AiThemeDeckStore {

    const val MAX_DECK_SIZE = 16

    fun load(night: Boolean): List<NgPaperLook> =
        readSnapshot().deckFor(night).mapNotNull { it.toLook() }

    fun loadPairs(): List<AiThemePair> {
        val snapshot = readSnapshot()
        if (snapshot.pairs.isNotEmpty()) return snapshot.pairs.mapNotNull { it.toPair() }
        val days = snapshot.day.mapNotNull { it.toLook() }
        val nights = snapshot.night.mapNotNull { it.toLook() }
        val nightByName = nights.associateBy { it.name }
        val used = mutableSetOf<String>()
        val fromDays = days.map { day ->
            used += day.name
            AiThemePair(day.name, day, nightByName[day.name] ?: counterpart(day, toNight = true))
        }
        val fromNights = nights.filter { it.name !in used }.map { night ->
            AiThemePair(night.name, counterpart(night, toNight = false), night)
        }
        return fromDays + fromNights
    }

    fun removeAt(current: List<AiThemePair>, index: Int): List<AiThemePair> {
        val next = withoutIndex(current, index)
        if (next === current) return current
        persistPairs(next)
        return next
    }

    fun restoreAt(current: List<AiThemePair>, index: Int, pair: AiThemePair): List<AiThemePair> {
        val next = insertAt(current, index, pair)
        persistPairs(next)
        return next
    }

    fun appendPairs(current: List<AiThemePair>, incoming: List<AiThemePair>): List<AiThemePair> {
        if (incoming.isEmpty()) return current
        val merged = mergePairs(current, incoming)
        val snapshot = readSnapshot().copy(pairs = merged.map { it.toStoredPair() })
        appCtx.putPrefString(PreferKey.aiThemeDeck, GSON.toJson(snapshot))
        return merged
    }

    fun append(night: Boolean, current: List<NgPaperLook>, incoming: List<NgPaperLook>): List<NgPaperLook> {
        if (incoming.isEmpty()) return current
        val merged = mergeDeck(current, incoming)
        val snapshot = readSnapshot().withDeck(night, merged.map { it.toStored() })
        appCtx.putPrefString(PreferKey.aiThemeDeck, GSON.toJson(snapshot))
        return merged
    }

    internal fun mergeDeck(
        existing: List<NgPaperLook>,
        incoming: List<NgPaperLook>,
        maxSize: Int = MAX_DECK_SIZE,
    ): List<NgPaperLook> {
        if (incoming.isEmpty()) return existing.take(maxSize)
        val merged = incoming + existing
        return if (merged.size <= maxSize) merged else merged.take(maxSize)
    }

    internal fun insertAt(
        existing: List<AiThemePair>,
        index: Int,
        pair: AiThemePair,
    ): List<AiThemePair> {
        val at = index.coerceIn(0, existing.size)
        return buildList {
            addAll(existing)
            add(at, pair)
        }
    }

    internal fun withoutIndex(existing: List<AiThemePair>, index: Int): List<AiThemePair> {
        if (index !in existing.indices) return existing
        return existing.filterIndexed { itemIndex, _ -> itemIndex != index }
    }

    internal fun mergePairs(
        existing: List<AiThemePair>,
        incoming: List<AiThemePair>,
        maxSize: Int = MAX_DECK_SIZE,
    ): List<AiThemePair> {
        if (incoming.isEmpty()) return existing.take(maxSize)
        val merged = incoming + existing
        return if (merged.size <= maxSize) merged else merged.take(maxSize)
    }

    private fun persistPairs(pairs: List<AiThemePair>) {
        val snapshot = readSnapshot().copy(
            pairs = pairs.map { it.toStoredPair() },
            day = emptyList(),
            night = emptyList(),
        )
        appCtx.putPrefString(PreferKey.aiThemeDeck, GSON.toJson(snapshot))
    }

    private fun readSnapshot(): AiThemeDeckSnapshot {
        return runCatching {
            appCtx.getPrefString(PreferKey.aiThemeDeck)?.let { json ->
                GSON.fromJson(json, AiThemeDeckSnapshot::class.java)
            }
        }.getOrNull() ?: AiThemeDeckSnapshot()
    }
}

private data class AiThemeDeckSnapshot(
    @SerializedName("day") val day: List<StoredAiTheme> = emptyList(),
    @SerializedName("night") val night: List<StoredAiTheme> = emptyList(),
    @SerializedName("pairs") val pairs: List<StoredAiThemePair> = emptyList(),
) {
    fun deckFor(isNight: Boolean): List<StoredAiTheme> = if (isNight) night else day

    fun withDeck(isNight: Boolean, deck: List<StoredAiTheme>): AiThemeDeckSnapshot =
        if (isNight) copy(night = deck) else copy(day = deck)
}

private data class StoredAiTheme(
    @SerializedName("name") val name: String = "",
    @SerializedName("background") val background: Int = 0,
    @SerializedName("text") val text: Int = 0,
    @SerializedName("accent") val accent: Int = 0,
)

private fun NgPaperLook.toStored(): StoredAiTheme = StoredAiTheme(
    name = name,
    background = background,
    text = text,
    accent = accent,
)

private data class StoredAiThemePair(
    @SerializedName("name") val name: String = "",
    @SerializedName("day") val day: StoredAiTheme = StoredAiTheme(),
    @SerializedName("night") val night: StoredAiTheme = StoredAiTheme(),
)

private fun AiThemePair.toStoredPair(): StoredAiThemePair = StoredAiThemePair(
    name = name,
    day = day.toStored(),
    night = night.toStored(),
)

private fun StoredAiThemePair.toPair(): AiThemePair? {
    val dayLook = day.toLook() ?: return null
    val nightLook = night.toLook() ?: return null
    return AiThemePair(name.ifBlank { dayLook.name }, dayLook.copy(name = name), nightLook.copy(name = name))
}

private fun StoredAiTheme.toLook(): NgPaperLook? {
    if (background == 0 || text == 0 || accent == 0) return null
    return NgPaperLook(
        id = "ai",
        name = name.ifBlank { "Generated" },
        background = background,
        text = text,
        accent = accent,
    )
}
