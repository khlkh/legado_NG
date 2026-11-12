package io.legado.app.help.annotation

import com.google.gson.JsonParser

/**
 * Parses and structurally validates the model's JSON response.
 *
 * Returns null when the body is not valid JSON, is not an object, or has no
 * `annotations` array — a null result writes nothing to the cache.
 *
 * Returns an empty list for a valid body whose `annotations` array is empty;
 * that is a successful empty annotation run.
 *
 * Structurally invalid elements (non-object, blank text or hint, missing or
 * non-positive occurrence) are dropped individually. The hint length cap and
 * all text-alignment rules live in [WordAnnotationResolver].
 */
object WordAnnotationParser {

    data class ParsedAnnotations(
        val items: List<WordAnnotationItem>,
        val rawCount: Int,
        val invalidCount: Int,
    )

    fun parse(content: String): List<WordAnnotationItem>? =
        parseWithStats(content)?.items

    fun parseWithStats(content: String): ParsedAnnotations? {
        val root = runCatching { JsonParser.parseString(content) }.getOrNull() ?: return null
        if (!root.isJsonObject) return null
        val annotations = root.asJsonObject.get("annotations") ?: return null
        if (!annotations.isJsonArray) return null

        val array = annotations.asJsonArray
        val items = ArrayList<WordAnnotationItem>(array.size())
        for (element in array) {
            if (!element.isJsonObject) continue
            val obj = element.asJsonObject
            val text = obj.stringField("text") ?: continue
            val hint = obj.stringField("hint") ?: continue
            val occurrence = obj.positiveIntField("occurrence") ?: continue
            if (text.isBlank() || hint.isBlank()) continue
            items += WordAnnotationItem(
                text = text,
                occurrence = occurrence,
                hint = hint,
                difficulty = obj.intField("difficulty")?.takeIf { it in 1..5 },
                contextual = obj.booleanField("contextual") == true,
            )
        }
        return ParsedAnnotations(
            items = items,
            rawCount = array.size(),
            invalidCount = array.size() - items.size,
        )
    }

    private fun com.google.gson.JsonObject.stringField(name: String): String? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return null
        return element.asString
    }

    private fun com.google.gson.JsonObject.intField(name: String): Int? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) return null
        return element.asInt
    }

    private fun com.google.gson.JsonObject.booleanField(name: String): Boolean? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) return null
        return element.asBoolean
    }

    private fun com.google.gson.JsonObject.positiveIntField(name: String): Int? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) return null
        val value = element.asInt
        return value.takeIf { it > 0 }
    }
}
