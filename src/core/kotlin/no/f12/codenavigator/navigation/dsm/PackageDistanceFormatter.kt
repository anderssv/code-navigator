package no.f12.codenavigator.navigation.dsm

object PackageDistanceFormatter {

    fun noResultsHints(packageCount: Int): List<String> = buildList {
        if (packageCount <= 1) {
            add("All classes are in a single package. Package distance measures inter-package relationships, so there is nothing to display.")
        }
    }

    fun format(result: PackageDistanceResult): String {
        if (result.entries.isEmpty()) return "No inter-package dependencies found."

        return buildString {
            if (result.displayPrefix.isNotEmpty()) {
                appendLine("Common prefix: ${result.displayPrefix} (stripped for readability)")
                appendLine()
            }
            append(result.entries.joinToString("\n") { entry ->
                "${entry.source} → ${entry.target}  distance=${entry.distance}  deps=${entry.dependencyCount}"
            })
        }.trimEnd()
    }
}
