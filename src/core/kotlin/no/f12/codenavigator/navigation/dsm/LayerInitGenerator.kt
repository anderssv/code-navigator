package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.core.ClassName

object LayerInitGenerator {

    fun generateConfigJson(classes: List<ClassName>): String {
        val suffixes = detectCommonSuffixes(classes)
        return buildConfigJson(suffixes)
    }

    internal fun detectCommonSuffixes(classes: List<ClassName>): Map<String, List<String>> {
        val knownLayers = listOf(
            "http" to listOf("*Controller", "*Routes", "*Endpoint", "*Resource"),
            "service" to listOf("*Service"),
            "adapter" to listOf("*Repository", "*Client", "*Cache", "*Sender", "*Dao"),
        )

        val result = mutableMapOf<String, List<String>>()
        val matched = mutableSetOf<String>()

        for ((layerName, patterns) in knownLayers) {
            val matchingPatterns = patterns.filter { pattern ->
                classes.any { cls ->
                    LayerConfig.matchesGlob(cls.simpleName(), pattern)
                }
            }
            if (matchingPatterns.isNotEmpty()) {
                result[layerName] = matchingPatterns
                matched.addAll(matchingPatterns)
            }
        }

        result["domain"] = listOf("*")
        return result
    }

    private fun buildConfigJson(layers: Map<String, List<String>>): String = buildString {
        appendLine("{")
        appendLine("  \"layers\": [")
        val entries = layers.entries.toList()
        entries.forEachIndexed { index, (name, patterns) ->
            val comma = if (index < entries.size - 1) "," else ""
            val patternsJson = patterns.joinToString(", ") { "\"$it\"" }
            appendLine("    { \"name\": \"$name\", \"patterns\": [$patternsJson] }$comma")
        }
        appendLine("  ]")
        append("}")
    }
}
