package io.legado.app.help.annotation

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest

/**
 * File cache for the book-level Word Wise pool.
 *
 * One file per book (`book_cache/<bookFolder>/wordwise-pool.json`). The JSON
 * stores the book identity, the sampled-passage hash, the prompt/schema and
 * generation config, and the pool items. A read is a hit only when every
 * identity field matches and the body parses; an empty pool is a valid empty
 * success. Writes go to a sibling temp file and are renamed into place.
 */
object WordAnnotationPoolStore {

    const val POOL_FILE_NAME = "wordwise-pool.json"
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

    fun read(file: File, identity: WordAnnotationPoolIdentity): List<WordAnnotationPoolItem>? {
        if (!file.exists() || !file.isFile) return null
        val json = runCatching { file.readText() }.getOrNull() ?: return null
        if (json.isBlank()) return null
        val root = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return null
        if (!root.isJsonObject) return null
        val obj = root.asJsonObject
        if (obj.string("bookUrl") != identity.bookUrl) return null
        if (obj.string("sampleHash") != identity.sampleHash) return null
        if (obj.int("schemaVersion") != identity.schemaVersion) return null
        if (obj.string("frequencyVersion") != identity.frequencyVersion) return null
        if (obj.boolean("hintCharCap") != identity.hintCharCap) return null
        if (obj.string("providerId") != identity.providerId) return null
        if (obj.string("modelId") != identity.modelId) return null
        val items = obj.get("items") ?: return null
        if (!items.isJsonArray) return null

        val result = ArrayList<WordAnnotationPoolItem>(items.asJsonArray.size())
        for (element in items.asJsonArray) {
            if (!element.isJsonObject) continue
            val item = element.asJsonObject
            val surface = item.string("surface") ?: continue
            val hint = item.string("hint") ?: continue
            if (surface.isBlank() || hint.isBlank()) continue
            result += WordAnnotationPoolItem(
                surface = surface,
                hint = hint,
                rank = item.intOrNull("rank"),
            )
        }
        return result
    }

    /** Returns true when the final file is complete; failures leave no final file. */
    fun write(
        file: File,
        identity: WordAnnotationPoolIdentity,
        items: List<WordAnnotationPoolItem>,
    ): Boolean {
        val parent = file.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        val temp = File(parent, file.name + TEMP_SUFFIX)
        return try {
            temp.writeText(toJson(identity, items))
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

    private fun toJson(identity: WordAnnotationPoolIdentity, items: List<WordAnnotationPoolItem>): String {
        val root = JsonObject()
        root.addProperty("bookUrl", identity.bookUrl)
        root.addProperty("sampleHash", identity.sampleHash)
        root.addProperty("schemaVersion", identity.schemaVersion)
        root.addProperty("frequencyVersion", identity.frequencyVersion)
        root.addProperty("hintCharCap", identity.hintCharCap)
        root.addProperty("providerId", identity.providerId)
        root.addProperty("modelId", identity.modelId)
        val array = JsonArray()
        items.forEach { item ->
            val element = JsonObject()
            element.addProperty("surface", item.surface)
            element.addProperty("hint", item.hint)
            item.rank?.let { element.addProperty("rank", it) }
            array.add(element)
        }
        root.add("items", array)
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
