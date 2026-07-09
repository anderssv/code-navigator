package no.f12.codenavigator.formatting

/**
 * Minimal JSON-building primitives shared by [JsonFormatter] and the per-feature JSON formatters
 * it delegates to (e.g. `CyclesFormatter.formatJson`, `DsmFormatter.formatJson`). `internal` rather
 * than `private` so feature packages (e.g. `navigation.dsm`) can build the same JSON shape without
 * duplicating this string-escaping logic.
 */
@JvmInline
internal value class JsonRaw(val json: String)

internal fun <T> jsonArray(items: List<T>, render: (T) -> String): String {
    if (items.isEmpty()) return "[]"
    return items.joinToString(",", "[", "]") { render(it) }
}

internal fun jsonStringArray(items: List<String>): String {
    if (items.isEmpty()) return "[]"
    return items.joinToString(",", "[", "]") { "\"${escapeJson(it)}\"" }
}

internal fun jsonObject(vararg pairs: Pair<String, Any?>): String =
    pairs
        .filter { (_, v) -> v != null }
        .joinToString(",", "{", "}") { (k, v) ->
            "\"${escapeJson(k)}\":${jsonValue(v!!)}"
        }

internal fun jsonValue(value: Any): String = when (value) {
    is String -> "\"${escapeJson(value)}\""
    is JsonRaw -> value.json
    is Number -> value.toString()
    is Boolean -> value.toString()
    else -> "\"${escapeJson(value.toString())}\""
}

internal fun escapeJson(s: String): String =
    s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
