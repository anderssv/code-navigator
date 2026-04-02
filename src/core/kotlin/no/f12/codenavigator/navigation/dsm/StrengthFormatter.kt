package no.f12.codenavigator.navigation.dsm

object StrengthFormatter {

    fun noResultsHints(packageCount: Int): List<String> = buildList {
        if (packageCount <= 1) {
            add("All classes are in a single package. Integration strength measures inter-package relationships, so there is nothing to display.")
        }
    }

    fun format(result: StrengthResult): String {
        if (result.entries.isEmpty()) return "No inter-package dependencies found."

        return result.entries.joinToString("\n") { entry ->
            "${entry.source} → ${entry.target}  strength=${entry.strength}  contract=${entry.contractCount} model=${entry.modelCount} functional=${entry.functionalCount}"
        }
    }
}
