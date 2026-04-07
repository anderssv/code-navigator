package no.f12.codenavigator.navigation.refactor

fun jsonEscape(s: String): String =
    s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

fun changesToJson(changes: List<RenameChange>): String =
    if (changes.isEmpty()) "[]"
    else changes.joinToString(",", "[", "]") { change ->
        val escapedPath = jsonEscape(change.filePath)
        val escapedBefore = jsonEscape(change.before)
        val escapedAfter = jsonEscape(change.after)
        """{"filePath":"$escapedPath","before":"$escapedBefore","after":"$escapedAfter"}"""
    }

fun changesFromJson(obj: Map<String, Any?>): List<RenameChange> {
    val changesArr = obj["changes"] as? List<*> ?: return emptyList()
    return changesArr.map { item ->
        @Suppress("UNCHECKED_CAST")
        val map = item as Map<String, Any?>
        RenameChange(
            filePath = map["filePath"] as String,
            before = map["before"] as String,
            after = map["after"] as String,
        )
    }
}

fun parseJsonObject(json: String): Map<String, Any?> {
    val (result, _) = parseValue(json.trim(), 0)
    @Suppress("UNCHECKED_CAST")
    return result as Map<String, Any?>
}

private fun parseValue(json: String, pos: Int): Pair<Any?, Int> {
    val i = skipWs(json, pos)
    return when (json[i]) {
        '"' -> parseString(json, i)
        '{' -> parseObj(json, i)
        '[' -> parseArr(json, i)
        else -> throw IllegalArgumentException("Unexpected character '${json[i]}' at position $i")
    }
}

private fun parseString(json: String, pos: Int): Pair<String, Int> {
    require(json[pos] == '"')
    val sb = StringBuilder()
    var i = pos + 1
    while (i < json.length) {
        val c = json[i]
        if (c == '\\') {
            i++
            when (json[i]) {
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                '"' -> sb.append('"')
                '\\' -> sb.append('\\')
                else -> { sb.append('\\'); sb.append(json[i]) }
            }
        } else if (c == '"') {
            return sb.toString() to i + 1
        } else {
            sb.append(c)
        }
        i++
    }
    throw IllegalArgumentException("Unterminated string at position $pos")
}

private fun parseObj(json: String, pos: Int): Pair<Map<String, Any?>, Int> {
    require(json[pos] == '{')
    val map = mutableMapOf<String, Any?>()
    var i = skipWs(json, pos + 1)
    if (json[i] == '}') return map to i + 1

    while (true) {
        i = skipWs(json, i)
        val (key, afterKey) = parseString(json, i)
        i = skipWs(json, afterKey)
        require(json[i] == ':')
        i++
        val (value, afterValue) = parseValue(json, i)
        map[key] = value
        i = skipWs(json, afterValue)
        when (json[i]) {
            ',' -> i++
            '}' -> return map to i + 1
            else -> throw IllegalArgumentException("Expected ',' or '}' at position $i")
        }
    }
}

private fun parseArr(json: String, pos: Int): Pair<List<Any?>, Int> {
    require(json[pos] == '[')
    val list = mutableListOf<Any?>()
    var i = skipWs(json, pos + 1)
    if (json[i] == ']') return list to i + 1

    while (true) {
        val (value, afterValue) = parseValue(json, i)
        list.add(value)
        i = skipWs(json, afterValue)
        when (json[i]) {
            ',' -> i = skipWs(json, i + 1)
            ']' -> return list to i + 1
            else -> throw IllegalArgumentException("Expected ',' or ']' at position $i")
        }
    }
}

private fun skipWs(json: String, pos: Int): Int {
    var i = pos
    while (i < json.length && json[i].isWhitespace()) i++
    return i
}
