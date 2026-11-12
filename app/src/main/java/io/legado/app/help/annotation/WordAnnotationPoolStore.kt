package io.legado.app.help.annotation

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest

/**
 * File cache for the book-level Smart Glosses candidate inventory.
 *
 * One file per book (`book_cache/<bookFolder>/smart-glosses-pool.json`). The file
 * stores the book identity and one entry per analyzed chapter:
 *
 * ```
 * {
 *   "bookUrl": "...", "schemaVersion": 9, ...,
 *   "chapters": [
 *     {
 *       "chapterIndex": 1,
 *       "contentHash": "...",
 *       "completedWindows": [0, 1, 2, 3],
 *       "items": [{"surface": "...", "hint": "...", "rank": 123}]
 *     }
 *   ]
 * }
 * ```
 *
 * A chapter's window list is the idempotency record: a completed window is
 * never sent to the model again while the chapter hash and identity match.
 * Readers resolve hints from the current chapter's items only, so a large book
 * does not make every chapter scan the whole book inventory.
 */
object WordAnnotationPoolStore {

    const val POOL_FILE_NAME = "smart-glosses-pool.json"

    /**
     * The working-name file. It is read once and migrated to [POOL_FILE_NAME]
     * so existing per-book candidate inventories are not thrown away.
     */
    const val LEGACY_POOL_FILE_NAME = "wordwise-pool.json"
    const val TEMP_SUFFIX = ".tmp"

    fun canonicalHash(
        paragraphs: List<String>,
        schemaVersion: Int = WordAnnotationConfig.SCHEMA_VERSION,
    ): String {
        val payload = buildString {
            append("schema=").append(schemaVersion).append('\n')
            paragraphs.forEach { append(it).append('\n') }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun read(file: File, identity: WordAnnotationPoolIdentity): WordAnnotationPoolState? {
        readFrom(file, identity)?.let { return it }
        val legacy = File(file.parentFile ?: return null, LEGACY_POOL_FILE_NAME)
        val state = readFrom(legacy, identity) ?: return null
        runCatching { write(file, identity, state) }
        return state
    }

    private fun readFrom(file: File, identity: WordAnnotationPoolIdentity): WordAnnotationPoolState? {
        if (!file.exists() || !file.isFile) return null
        val json = runCatching { file.readText() }.getOrNull() ?: return null
        if (json.isBlank()) return null
        val root = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return null
        if (!root.isJsonObject) return null
        val obj = root.asJsonObject
        if (obj.string("bookUrl") != identity.bookUrl) return null
        if (obj.int("schemaVersion") != identity.schemaVersion) return null
        if (obj.string("frequencyVersion") != identity.frequencyVersion) return null
        if (obj.boolean("hintCharCap") != identity.hintCharCap) return null
        if (obj.string("providerId") != identity.providerId) return null
        if (obj.string("modelId") != identity.modelId) return null

        val chapters = obj.get("chapters") ?: return null
        if (!chapters.isJsonArray) return null
        val result = LinkedHashMap<Int, WordAnnotationChapterPool>()
        for (element in chapters.asJsonArray) {
            if (!element.isJsonObject) continue
            val chapter = element.asJsonObject
            val chapterIndex = chapter.int("chapterIndex") ?: continue
            val contentHash = chapter.string("contentHash") ?: continue
            if (contentHash.isBlank()) continue
            val completed = chapter.get("completedWindows")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.mapNotNull { it.takeIf { value -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber }?.asInt }
                ?.toSet()
                ?: emptySet()
            val items = ArrayList<WordAnnotationPoolItem>()
            val itemArray = chapter.get("items")
            if (itemArray != null && itemArray.isJsonArray) {
                for (itemElement in itemArray.asJsonArray) {
                    if (!itemElement.isJsonObject) continue
                    val item = itemElement.asJsonObject
                    val surface = item.string("surface") ?: continue
                    val hint = item.string("hint") ?: continue
                    if (surface.isBlank() || hint.isBlank()) continue
                    items += WordAnnotationPoolItem(
                        surface = surface,
                        hint = hint,
                        rank = item.intOrNull("rank"),
                        difficulty = item.intOrNull("difficulty")?.coerceIn(1, 5),
                        contextual = item.boolean("contextual") == true,
                    )
                }
            }
            result[chapterIndex] = WordAnnotationChapterPool(
                chapterIndex = chapterIndex,
                contentHash = contentHash,
                completedWindows = completed,
                items = items,
            )
        }
        return WordAnnotationPoolState(result)
    }

    /** Returns true when the final file is complete; failures leave no final file. */
    fun write(
        file: File,
        identity: WordAnnotationPoolIdentity,
        state: WordAnnotationPoolState,
    ): Boolean {
        val parent = file.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        val temp = File(parent, file.name + TEMP_SUFFIX)
        return try {
            temp.writeText(toJson(identity, state))
            renameIntoPlace(temp, file)
        } catch (_: Exception) {
            runCatching { temp.delete() }
            false
        }
    }

    private fun renameIntoPlace(temp: File, file: File): Boolean {
        if (temp.renameTo(file)) return true
        if (file.exists()) file.delete()
        if (temp.renameTo(file)) return true
        temp.delete()
        return false
    }

    private fun toJson(identity: WordAnnotationPoolIdentity, state: WordAnnotationPoolState): String {
        val root = JsonObject()
        root.addProperty("bookUrl", identity.bookUrl)
        root.addProperty("schemaVersion", identity.schemaVersion)
        root.addProperty("frequencyVersion", identity.frequencyVersion)
        root.addProperty("hintCharCap", identity.hintCharCap)
        root.addProperty("providerId", identity.providerId)
        root.addProperty("modelId", identity.modelId)
        val chapters = JsonArray()
        state.chapters.values.sortedBy { it.chapterIndex }.forEach { chapter ->
            val element = JsonObject()
            element.addProperty("chapterIndex", chapter.chapterIndex)
            element.addProperty("contentHash", chapter.contentHash)
            val completed = JsonArray()
            chapter.completedWindows.sorted().forEach { completed.add(it) }
            element.add("completedWindows", completed)
            val items = JsonArray()
            chapter.items.forEach { item ->
                val itemElement = JsonObject()
                itemElement.addProperty("surface", item.surface)
                itemElement.addProperty("hint", item.hint)
                item.rank?.let { itemElement.addProperty("rank", it) }
                item.difficulty?.let { itemElement.addProperty("difficulty", it) }
                if (item.contextual) itemElement.addProperty("contextual", true)
                items.add(itemElement)
            }
            element.add("items", items)
            chapters.add(element)
        }
        root.add("chapters", chapters)
        return root.toString()
    }

    private fun JsonObject.string(name: String): String? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return null
        return element.asString
    }

    private fun JsonObject.int(name: String): Int? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) return null
        return element.asInt
    }

    private fun JsonObject.boolean(name: String): Boolean? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) return null
        return element.asBoolean
    }

    private fun JsonObject.intOrNull(name: String): Int? = int(name)
}
