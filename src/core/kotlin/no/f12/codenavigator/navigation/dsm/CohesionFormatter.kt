package no.f12.codenavigator.navigation.dsm

object CohesionFormatter {

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
}
