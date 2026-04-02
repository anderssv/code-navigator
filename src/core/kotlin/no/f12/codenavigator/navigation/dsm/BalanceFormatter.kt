package no.f12.codenavigator.navigation.dsm

object BalanceFormatter {

    fun noResultsHints(packageCount: Int): List<String> = buildList {
        if (packageCount <= 1) {
            add("All classes are in a single package. Balanced coupling measures inter-package relationships, so there is nothing to display.")
        }
    }

    fun format(result: BalanceResult): String {
        if (result.entries.isEmpty()) return "No balance findings."

        return result.entries.joinToString("\n") { entry ->
            buildString {
                append("${entry.source} → ${entry.target}  verdict=${entry.verdict}  strength=${entry.strength}  distance=${entry.distance}  volatility=${entry.sourceVolatility}/${entry.targetVolatility}")
                if (entry.suggestion.isNotEmpty()) {
                    append("\n  → ${entry.suggestion}")
                }
            }
        }
    }
}
