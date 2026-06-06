package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName

object HintsConfigGenerator {

    private val INFRASTRUCTURE_PATTERNS = listOf(
        "*Serializer", "*Deserializer", "*Generator", "*Renderer",
        "*Config", "*Impl", "*Factory", "*Provider",
        "*Mapper", "*Converter", "*Transformer",
        "*Watcher", "*Util*",
    )

    fun generate(classRings: Map<ClassName, Int>): String {
        val ring0Classes = classRings.filter { it.value == 0 }.keys
        val byPattern = mutableMapOf<String, List<ClassName>>()

        for (pattern in INFRASTRUCTURE_PATTERNS) {
            val matching = ring0Classes.filter { cls ->
                RingsHintsConfig.matchesGlob(cls.simpleName(), pattern)
            }.sorted()
            if (matching.isNotEmpty()) {
                byPattern[pattern] = matching
            }
        }

        val sb = StringBuilder()
        sb.appendLine("// Suggested cnav-config.json — review and tweak before use")
        sb.appendLine("// This is a best-effort bootstrap based on naming patterns.")
        sb.appendLine("// Adjust patterns, remove/add entries, and add overrides as needed.")
        sb.appendLine("// Then save as cnav-config.json in the project root and re-run cnavRings.")
        sb.appendLine("{")
        sb.append("  \"ringNames\": [\"domain\", \"port\", \"application\", \"adapter\"]")
        if (byPattern.isNotEmpty()) {
            sb.appendLine(",")
            sb.appendLine("  \"hints\": {")
            sb.appendLine("    \"adapter\": [")
            val entries = byPattern.entries.toList()
            for ((i, entry) in entries.withIndex()) {
                val (pattern, classes) = entry
                val examples = classes.take(3).joinToString(", ") { it.simpleName() }
                val suffix = if (classes.size > 3) " — ${classes.size} classes" else ""
                val comma = if (i < entries.size - 1) "," else ""
                sb.appendLine("      \"$pattern\"$comma  // $examples$suffix")
            }
            sb.appendLine("    ]")
            sb.appendLine("  }")
        } else {
            sb.appendLine()
            sb.appendLine("  \"hints\": {}")
        }
        sb.appendLine("}")
        sb.appendLine()
        sb.appendLine("// Remove the // comment lines above before saving as cnav-config.json")
        return sb.toString()
    }
}
