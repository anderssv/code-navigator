package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.types.ClassName

data class DeadCodeDiff(
    val removed: List<DeadCode>,
    val remaining: List<DeadCode>,
    val new: List<DeadCode>,
)

object DeadCodeBaselineDiff {

    fun compare(baseline: List<DeadCode>, current: List<DeadCode>): DeadCodeDiff {
        val baselineKeys = baseline.map { it.identity() }.toSet()
        val currentKeys = current.map { it.identity() }.toSet()

        return DeadCodeDiff(
            removed = baseline.filter { it.identity() !in currentKeys },
            remaining = current.filter { it.identity() in baselineKeys },
            new = current.filter { it.identity() !in baselineKeys },
        )
    }

    fun parseBaseline(json: String): List<DeadCode> {
        val (result, _) = parseValue(json.trim(), 0)
        @Suppress("UNCHECKED_CAST")
        val arr = result as List<Map<String, Any?>>
        return arr.map { obj ->
            DeadCode(
                className = ClassName(obj["className"] as String),
                memberName = obj["memberName"] as String?,
                kind = DeadCodeKind.valueOf((obj["kind"] as String).uppercase()),
                sourceFile = obj["sourceFile"] as String,
                confidence = DeadCodeConfidence.valueOf((obj["confidence"] as String).uppercase()),
                reason = DeadCodeReason.valueOf((obj["reason"] as String).uppercase()),
            )
        }
    }

    private fun DeadCode.identity(): Triple<String, String?, DeadCodeKind> =
        Triple(className.toString(), memberName, kind)

    // Minimal JSON parser supporting strings, null, objects, arrays
    private fun parseValue(json: String, pos: Int): Pair<Any?, Int> {
        val i = skipWs(json, pos)
        return when {
            json[i] == '"' -> parseString(json, i)
            json[i] == '{' -> parseObj(json, i)
            json[i] == '[' -> parseArr(json, i)
            json.startsWith("null", i) -> null to i + 4
            else -> throw IllegalArgumentException("Unexpected at position $i")
        }
    }

    private fun parseString(json: String, pos: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var i = pos + 1
        while (i < json.length) {
            val c = json[i]
            if (c == '\\') { i++; sb.append(if (json[i] == 'n') '\n' else json[i]) }
            else if (c == '"') return sb.toString() to i + 1
            else sb.append(c)
            i++
        }
        throw IllegalArgumentException("Unterminated string")
    }

    private fun parseObj(json: String, pos: Int): Pair<Map<String, Any?>, Int> {
        val map = mutableMapOf<String, Any?>()
        var i = skipWs(json, pos + 1)
        if (json[i] == '}') return map to i + 1
        while (true) {
            i = skipWs(json, i)
            val (key, afterKey) = parseString(json, i)
            i = skipWs(json, afterKey)
            require(json[i] == ':'); i++
            val (value, afterValue) = parseValue(json, i)
            map[key] = value
            i = skipWs(json, afterValue)
            when (json[i]) { ',' -> i++; '}' -> return map to i + 1; else -> throw IllegalArgumentException("Expected ',' or '}'") }
        }
    }

    private fun parseArr(json: String, pos: Int): Pair<List<Any?>, Int> {
        val list = mutableListOf<Any?>()
        var i = skipWs(json, pos + 1)
        if (json[i] == ']') return list to i + 1
        while (true) {
            val (value, afterValue) = parseValue(json, i)
            list.add(value)
            i = skipWs(json, afterValue)
            when (json[i]) { ',' -> i = skipWs(json, i + 1); ']' -> return list to i + 1; else -> throw IllegalArgumentException("Expected ',' or ']'") }
        }
    }

    private fun skipWs(json: String, pos: Int): Int {
        var i = pos; while (i < json.length && json[i].isWhitespace()) i++; return i
    }
}
