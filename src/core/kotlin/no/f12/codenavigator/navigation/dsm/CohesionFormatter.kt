package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.withInterpretation

object CohesionFormatter {

    internal const val COHESION_INTERPRETATION = "Interpretation: Cohesion ratio = internal edges / total edges. COHESIVE (>0.5) = classes collaborate more with each other than with outsiders. REVIEW (<0.5) = package may contain unrelated classes. THIN_LAYER (0.0) = no internal collaboration, consider merging into a neighbor."

    fun format(result: CohesionResult): String {
        if (result.entries.isEmpty()) return "No packages with dependencies found."

        val header = String.format("%-50s %7s %8s %8s %8s  %s", "Package", "Classes", "Internal", "External", "Cohesion", "Verdict")
        val separator = "-".repeat(100)
        val rows = result.entries.joinToString("\n") { entry ->
            String.format(
                "%-50s %7d %8d %8d %8.2f  %s",
                entry.packageName, entry.classCount, entry.internalEdges, entry.externalEdges, entry.cohesion, entry.verdict,
            )
        }

        return "$header\n$separator\n$rows"
    }

    fun noResultsHints(packageCount: Int): List<String> = buildList {
        if (packageCount <= 1) {
            add("All classes are in a single package. Cohesion measures intra-package relationships relative to inter-package ones.")
        }
    }

    fun formatJson(result: CohesionResult): String =
        jsonArray(result.entries) { entry ->
            jsonObject(
                "package" to entry.packageName.toString(),
                "classes" to entry.classCount,
                "internalEdges" to entry.internalEdges,
                "externalEdges" to entry.externalEdges,
                "cohesion" to entry.cohesion,
                "verdict" to entry.verdict.name,
            )
        }

    fun formatLlm(result: CohesionResult): String =
        result.entries.joinToString("\n") { entry ->
            "${entry.packageName} classes=${entry.classCount} internal=${entry.internalEdges} external=${entry.externalEdges} cohesion=${"%.2f".format(entry.cohesion)} verdict=${entry.verdict}"
        }.withInterpretation(COHESION_INTERPRETATION)
}
