package no.f12.codenavigator.navigation.dsm

object CohesionFormatter {

    fun format(result: CohesionResult): String {
        if (result.entries.isEmpty()) return "No packages with dependencies found."

        val header = String.format("%-50s %8s %8s %8s", "Package", "Internal", "External", "Cohesion")
        val separator = "-".repeat(78)
        val rows = result.entries.joinToString("\n") { entry ->
            String.format("%-50s %8d %8d %8.2f", entry.packageName, entry.internalEdges, entry.externalEdges, entry.cohesion)
        }

        return "$header\n$separator\n$rows"
    }

    fun noResultsHints(packageCount: Int): List<String> = buildList {
        if (packageCount <= 1) {
            add("All classes are in a single package. Cohesion measures intra-package relationships relative to inter-package ones.")
        }
    }
}
