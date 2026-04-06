package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.core.ClassName

data class Layer(
    val name: String,
    val patterns: List<String>,
    val peerLimit: Int = 0,
    val testInfrastructure: Boolean = false,
)

data class LayerConfig(
    val layers: List<Layer>,
) {

    fun layerIndexOf(className: ClassName): Int? {
        for (candidate in className.candidateNames()) {
            val index = findMatchingLayerIndex(candidate)
            if (index != null) return index
        }

        return findMatchingLayerIndex(className.simpleName())
    }

    fun layerNameOf(className: ClassName): String? {
        val index = layerIndexOf(className) ?: return null
        return layers[index].name
    }

    fun peerLimitOf(className: ClassName): Int {
        val index = layerIndexOf(className) ?: return 0
        return layers[index].peerLimit
    }

    fun arePeers(a: ClassName, b: ClassName): Boolean {
        val indexA = layerIndexOf(a) ?: return false
        val indexB = layerIndexOf(b) ?: return false
        return indexA == indexB
    }

    private fun findMatchingLayerIndex(simpleName: String): Int? =
        layers.indexOfFirst { layer ->
            layer.patterns.any { pattern -> matchesGlob(simpleName, pattern) }
        }.takeIf { it >= 0 }

    companion object {

        fun parse(json: String): LayerConfig {
            val root = SimpleJson.parseObject(json)
            val layersArray = root["layers"] as? List<*>
                ?: throw IllegalArgumentException("Config must have a 'layers' array")

            val layers = layersArray.map { element ->
                val obj = element as? Map<*, *>
                    ?: throw IllegalArgumentException("Each layer entry must be an object")
                parseLayer(obj)
            }

            return LayerConfig(layers = layers)
        }

        private fun parseLayer(obj: Map<*, *>): Layer {
            val name = obj["name"] as? String
                ?: throw IllegalArgumentException("Layer must have a 'name' field")
            val patterns = (obj["patterns"] as? List<*>)
                ?.map { it as String }
                ?: throw IllegalArgumentException("Layer '$name' must have a 'patterns' array")
            val peerLimit = when (val raw = obj["peerLimit"]) {
                is Number -> raw.toInt()
                is String -> raw.toInt()
                null -> 0
                else -> throw IllegalArgumentException("Layer '$name' peerLimit must be a number")
            }
            val testInfrastructure = when (val raw = obj["testInfrastructure"]) {
                is Boolean -> raw
                null -> false
                else -> throw IllegalArgumentException("Layer '$name' testInfrastructure must be a boolean")
            }
            return Layer(name = name, patterns = patterns, peerLimit = peerLimit, testInfrastructure = testInfrastructure)
        }

        internal fun matchesGlob(simpleName: String, pattern: String): Boolean = when {
            pattern == "*" -> true
            pattern.startsWith("*") && pattern.endsWith("*") -> {
                val middle = pattern.substring(1, pattern.length - 1)
                simpleName.contains(middle)
            }
            pattern.startsWith("*") -> simpleName.endsWith(pattern.removePrefix("*"))
            pattern.endsWith("*") -> simpleName.startsWith(pattern.removeSuffix("*"))
            else -> simpleName == pattern
        }
    }
}

/**
 * Minimal JSON parser that handles the subset needed for .cnav-layers.json:
 * objects, arrays, strings, and non-negative integers.
 */
internal object SimpleJson {

    fun parseObject(json: String): Map<String, Any?> {
        val (result, _) = parseValue(json.trim(), 0)
        val map = result as? Map<*, *>
            ?: throw IllegalArgumentException("Expected JSON object at top level")
        @Suppress("UNCHECKED_CAST")
        return map as Map<String, Any?>
    }

    private fun parseValue(json: String, pos: Int): Pair<Any?, Int> {
        val i = skipWhitespace(json, pos)
        return when {
            json[i] == '"' -> parseString(json, i)
            json[i] == '{' -> parseObj(json, i)
            json[i] == '[' -> parseArr(json, i)
            json[i].isDigit() || json[i] == '-' -> parseNumber(json, i)
            json.startsWith("true", i) -> true to i + 4
            json.startsWith("false", i) -> false to i + 5
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
                sb.append(json[i])
            } else if (c == '"') {
                return sb.toString() to i + 1
            } else {
                sb.append(c)
            }
            i++
        }
        throw IllegalArgumentException("Unterminated string starting at position $pos")
    }

    private fun parseNumber(json: String, pos: Int): Pair<Int, Int> {
        var i = pos
        if (json[i] == '-') i++
        while (i < json.length && json[i].isDigit()) i++
        return json.substring(pos, i).toInt() to i
    }

    private fun parseObj(json: String, pos: Int): Pair<Map<String, Any?>, Int> {
        require(json[pos] == '{')
        val map = mutableMapOf<String, Any?>()
        var i = skipWhitespace(json, pos + 1)
        if (json[i] == '}') return map to i + 1

        while (true) {
            i = skipWhitespace(json, i)
            val (key, afterKey) = parseString(json, i)
            i = skipWhitespace(json, afterKey)
            require(json[i] == ':') { "Expected ':' at position $i" }
            i++
            val (value, afterValue) = parseValue(json, i)
            map[key] = value
            i = skipWhitespace(json, afterValue)
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
        var i = skipWhitespace(json, pos + 1)
        if (json[i] == ']') return list to i + 1

        while (true) {
            val (value, afterValue) = parseValue(json, i)
            list.add(value)
            i = skipWhitespace(json, afterValue)
            when (json[i]) {
                ',' -> i = skipWhitespace(json, i + 1)
                ']' -> return list to i + 1
                else -> throw IllegalArgumentException("Expected ',' or ']' at position $i")
            }
        }
    }

    private fun skipWhitespace(json: String, pos: Int): Int {
        var i = pos
        while (i < json.length && json[i].isWhitespace()) i++
        return i
    }
}
