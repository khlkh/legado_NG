package io.legado.app.help.annotation

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest

/**
 * File cache for one chapter's resolved word annotations.
 *
 * The file is a sibling of the chapter body file
 * (`book_cache/<bookFolder>/<index>-<titleMd5>.wordwise.json`). The JSON stores
 * the book/chapter identity, the canonical-text hash, the prompt/schema version,
 * and the resolved annotation list.
 *
 * A read is a hit only when every identity field matches and the body parses.
 * An empty, truncated, or invalid file is a miss — never an empty success. An
 * empty success is a complete file whose annotation list is empty.
 *
 * Writes go to a sibling temp file and are renamed onto the final name, so a
 * reader never opens a half-written final file.
 */
object WordAnnotationStore {

    const val FILE_SUFFIX = ".wordwise.json"
    const val TEMP_SUFFIX = ".tmp"

    fun annotationFileName(chapterFileName: String): String {
        if (chapterFileName.endsWith(FILE_SUFFIX)) return chapterFileName
        val base = chapterFileName.substringBeforeLast('.', chapterFileName)
        return "$base$FILE_SUFFIX"
    }

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

    fun read(file: File, identity: WordAnnotationIdentity): List<WordAnnotation>? {
        if (!file.exists() || !file.isFile) return null
        val json = runCatching { file.readText() }.getOrNull() ?: return null
        if (json.isBlank()) return null
        val root = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return null
        if (!root.isJsonObject) return null
        val obj = root.asJsonObject
        if (obj.string("bookUrl") != identity.bookUrl) return null
        if (obj.string("chapterUrl") != identity.chapterUrl) return null
        if (obj.int("chapterIndex") != identity.chapterIndex) return null
        if (obj.string("canonicalHash") != identity.canonicalHash) return null
        if (obj.int("schemaVersion") != identity.schemaVersion) return null
        if (obj.int("level") != identity.level) return null
        if (obj.string("frequencyVersion") != identity.frequencyVersion) return null
        if (obj.int("hintCap") != identity.hintCap) return null
        if (obj.boolean("hintCharCap") != identity.hintCharCap) return null
        if (obj.string("providerId") != identity.providerId) return null
        if (obj.string("modelId") != identity.modelId) return null
        val annotations = obj.get("annotations") ?: return null
        if (!annotations.isJsonArray) return null

        val result = ArrayList<WordAnnotation>(annotations.asJsonArray.size())
        for (element in annotations.asJsonArray) {
            if (!element.isJsonObject) continue
            val item = element.asJsonObject
            val paragraphIndex = item.intOrNull("paragraphIndex") ?: continue
            val start = item.intOrNull("start") ?: continue
            val end = item.intOrNull("end") ?: continue
            val surface = item.string("surface") ?: continue
            val hint = item.string("hint") ?: continue
            if (start < 0 || end <= start || hint.isBlank()) continue
            result += WordAnnotation(
                paragraphIndex = paragraphIndex,
                start = start,
                end = end,
                surface = surface,
                hint = hint,
            )
        }
        return result
    }

    /** Returns true when the final file is complete; failures leave no final file. */
    fun write(
        file: File,
        identity: WordAnnotationIdentity,
        annotations: List<WordAnnotation>,
    ): Boolean {
        val parent = file.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        val temp = File(parent, file.name + TEMP_SUFFIX)
        return try {
            val json = toJson(identity, annotations)
            temp.writeText(json)
            renameIntoPlace(temp, file)
        } catch (_: Exception) {
            runCatching { temp.delete() }
            false
        }
    }

    private fun renameIntoPlace(temp: File, file: File): Boolean {
        if (temp.renameTo(file)) return true
        // renameTo may refuse to replace an existing file on some platforms.
        if (file.exists()) file.delete()
        if (temp.renameTo(file)) return true
        temp.delete()
        return false
    }

    private fun toJson(identity: WordAnnotationIdentity, annotations: List<WordAnnotation>): String {
        val root = JsonObject()
        root.addProperty("bookUrl", identity.bookUrl)
        root.addProperty("chapterUrl", identity.chapterUrl)
        root.addProperty("chapterIndex", identity.chapterIndex)
        root.addProperty("canonicalHash", identity.canonicalHash)
        root.addProperty("schemaVersion", identity.schemaVersion)
        root.addProperty("level", identity.level)
        root.addProperty("frequencyVersion", identity.frequencyVersion)
        root.addProperty("hintCap", identity.hintCap)
        root.addProperty("hintCharCap", identity.hintCharCap)
        root.addProperty("providerId", identity.providerId)
        root.addProperty("modelId", identity.modelId)
        val array = JsonArray()
        annotations.forEach { annotation ->
            val item = JsonObject()
            item.addProperty("paragraphIndex", annotation.paragraphIndex)
            item.addProperty("start", annotation.start)
            item.addProperty("end", annotation.end)
            item.addProperty("surface", annotation.surface)
            item.addProperty("hint", annotation.hint)
            array.add(item)
        }
        root.add("annotations", array)
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

data class WordAnnotationIdentity(
    val bookUrl: String,
    val chapterUrl: String,
    val chapterIndex: Int,
    val canonicalHash: String,
    val schemaVersion: Int = WordAnnotationConfig.SCHEMA_VERSION,
    val level: Int = WordAnnotationConfig.DEFAULT_WORD_ANNOTATION_LEVEL,
    val frequencyVersion: String = WordAnnotationConfig.FREQUENCY_VERSION,
    val hintCap: Int = WordAnnotationConfig.DEFAULT_WORD_ANNOTATION_HINT_CAP,
    val hintCharCap: Boolean = false,
    val providerId: String = "",
    val modelId: String = "",
)
