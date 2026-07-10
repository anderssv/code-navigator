package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.withInterpretation

object StrengthFormatter {

    internal const val STRENGTH_INTERPRETATION = "Interpretation: Integration strength levels — MODEL: only data classes cross the boundary (loosest). CONTRACT: interfaces/abstractions cross. FUNCTIONAL: concrete implementations cross (tightest). Higher strength at greater distance is a modularity concern."

    fun noResultsHints(packageCount: Int): List<String> = buildList {
        if (packageCount <= 1) {
            add("All classes are in a single package. Integration strength measures inter-package relationships, so there is nothing to display.")
        }
    }

    fun format(result: StrengthResult): String {
        if (result.entries.isEmpty()) return "No inter-package dependencies found."

        return result.entries.joinToString("\n") { entry ->
            buildString {
                append("${entry.source} → ${entry.target}  strength=${entry.strength}  contract=${entry.contractCount} model=${entry.modelCount} functional=${entry.functionalCount}")
                if (entry.unknownCount > 0) {
                    append(" unknown=${entry.unknownCount}")
                }
            }
        }
    }

    fun formatJson(result: StrengthResult): String =
        jsonArray(result.entries) { entry ->
            jsonObject(
                "source" to entry.source.toString(),
                "target" to entry.target.toString(),
                "strength" to entry.strength.name,
                "contract" to entry.contractCount,
                "model" to entry.modelCount,
                "functional" to entry.functionalCount,
                "unknown" to entry.unknownCount,
                "totalDeps" to entry.totalDeps,
            )
        }

    fun formatLlm(result: StrengthResult): String =
        result.entries.joinToString("\n") { entry ->
            buildString {
                append("${entry.source}->${entry.target} strength=${entry.strength} contract=${entry.contractCount} model=${entry.modelCount} functional=${entry.functionalCount}")
                if (entry.unknownCount > 0) {
                    append(" unknown=${entry.unknownCount}")
                }
            }
        }.withInterpretation(STRENGTH_INTERPRETATION)
}
