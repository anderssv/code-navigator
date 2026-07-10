package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.withInterpretation

object BalanceFormatter {

    internal const val BALANCE_INTERPRETATION = "Interpretation: distance = number of architectural rings the edge crosses (not package-name nesting). BALANCED = coupling strength matches ring separation. TOLERABLE = suboptimal but low volatility reduces risk. DANGER = tight coupling across rings in volatile code — highest priority for refactoring. Composition roots (DI/wiring) are never DANGER. Focus on DANGER entries first."

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

    fun formatJson(result: BalanceResult): String =
        jsonArray(result.entries) { entry ->
            jsonObject(
                "source" to entry.source.toString(),
                "target" to entry.target.toString(),
                "strength" to entry.strength.name,
                "distance" to entry.distance,
                "sourceVolatility" to entry.sourceVolatility,
                "targetVolatility" to entry.targetVolatility,
                "verdict" to entry.verdict.name,
                "suggestion" to entry.suggestion,
            )
        }

    fun formatLlm(result: BalanceResult): String =
        result.entries.joinToString("\n") { entry ->
            buildString {
                append("${entry.source}->${entry.target} verdict=${entry.verdict} strength=${entry.strength} distance=${entry.distance} volatility=${entry.sourceVolatility}/${entry.targetVolatility}")
                if (entry.suggestion.isNotEmpty()) {
                    append(" | ${entry.suggestion}")
                }
            }
        }.withInterpretation(BALANCE_INTERPRETATION)
}
