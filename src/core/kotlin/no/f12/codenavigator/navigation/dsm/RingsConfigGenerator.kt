package no.f12.codenavigator.navigation.dsm

object RingsConfigGenerator {

    fun generate(graph: RingGraph, layering: RingLayering): String = buildString {
        appendLine("{")
        appendLine("  \"rings\": {")
        appendLine("    \"expected\": ${layering.ringCount},")
        appendLine("    \"compositionRoots\": ${jsonArray(graph.compositionRoots.map { it.value })},")
        appendLine("    \"adapters\": [],")
        appendLine("    \"notAdapters\": []")
        appendLine("  }")
        append("}")
    }

    private fun jsonArray(values: Collection<String>): String =
        values.sorted().joinToString(", ", "[", "]") { "\"$it\"" }
}
