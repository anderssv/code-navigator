package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.withInterpretation

object CohesionFormatter {

    internal const val COHESION_INTERPRETATION = "Interpretation: Cohesion ratio = internal edges / total edges; edges into a parent or child package count as internal. Feature = the same ratio counting every package under the same feature (first package below the project root) as internal. COHESIVE (>0.5) = classes collaborate more with each other than with outsiders. REVIEW (<0.5) = package may contain unrelated classes. THIN_LAYER (0.0) = no internal collaboration, consider merging into a neighbor. LAYERED = weak alone but cohesive within its feature: a ring of a feature (web, store, adapters depending inward), not a problem."

    fun format(result: CohesionResult): String {
        if (result.entries.isEmpty()) return "No packages with dependencies found."

        val header = String.format("%-50s %7s %8s %8s %8s %8s  %s", "Package", "Classes", "Internal", "External", "Cohesion", "Feature", "Verdict")
        val separator = "-".repeat(109)
        val rows = result.entries.joinToString("\n") { entry ->
            String.format(
                "%-50s %7d %8d %8d %8.2f %8.2f  %s",
                entry.packageName, entry.classCount, entry.internalEdges, entry.externalEdges, entry.cohesion, entry.featureCohesion, entry.verdict,
            )
        }

        if (result.features.isEmpty()) return "$header\n$separator\n$rows"
        val features = result.features.joinToString("\n") {
            String.format("%-50s %7d %8d %8d %8.2f", it.feature, it.packageCount, it.internalEdges, it.externalEdges, it.cohesion)
        }
        val featureHeader = String.format("%-50s %7s %8s %8s %8s", "Feature (all its packages as one)", "Pkgs", "Internal", "External", "Cohesion")
        return "$header\n$separator\n$rows\n\n$featureHeader\n${"-".repeat(84)}\n$features"
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
                "feature" to entry.feature?.toString(),
                "featureCohesion" to entry.featureCohesion,
                "verdict" to entry.verdict.name,
            )
        }

    fun formatLlm(result: CohesionResult): String =
        result.entries.joinToString("\n") { entry ->
            "${entry.packageName} classes=${entry.classCount} internal=${entry.internalEdges} external=${entry.externalEdges} cohesion=${"%.2f".format(entry.cohesion)} feature-cohesion=${"%.2f".format(entry.featureCohesion)} verdict=${entry.verdict}"
        }.plus(result.features.joinToString("") { "\nfeature ${it.feature} packages=${it.packageCount} internal=${it.internalEdges} external=${it.externalEdges} cohesion=${"%.2f".format(it.cohesion)}" })
            .withInterpretation(COHESION_INTERPRETATION)
}
