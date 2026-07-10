package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.withInterpretation

object MoveSuggestFormatter {

    internal const val MOVE_SUGGEST_INTERPRETATION = "Interpretation: Classes with more edges to another package than their own are potentially misplaced. High confidence + low own-edges = strong signal. Verify intent before moving — composition roots, drivers, and thin adapters are expected to have outward edges."

    fun format(result: MoveSuggestionResult): String {
        val header = String.format("%-50s %-30s %-30s %5s %5s %10s", "Class", "Current", "Suggested", "Own", "Target", "Confidence")
        val separator = "-".repeat(135)
        val rows = result.suggestions.joinToString("\n") { s ->
            String.format(
                "%-50s %-30s %-30s %5d %5d %10.2f",
                s.className.simpleName(), s.currentPackage, s.suggestedPackage, s.edgesToCurrent, s.edgesToSuggested, s.confidence,
            )
        }
        return "$header\n$separator\n$rows"
    }

    fun formatJson(result: MoveSuggestionResult): String =
        jsonArray(result.suggestions) { s ->
            jsonObject(
                "class" to s.className.value,
                "currentPackage" to s.currentPackage.toString(),
                "suggestedPackage" to s.suggestedPackage.toString(),
                "edgesToCurrent" to s.edgesToCurrent,
                "edgesToSuggested" to s.edgesToSuggested,
                "confidence" to s.confidence,
            )
        }

    fun formatLlm(result: MoveSuggestionResult): String =
        result.suggestions.joinToString("\n") { s ->
            "${s.className.value} current=${s.currentPackage} suggested=${s.suggestedPackage} own=${s.edgesToCurrent} target=${s.edgesToSuggested} confidence=${"%.2f".format(s.confidence)}"
        }.withInterpretation(MOVE_SUGGEST_INTERPRETATION)
}
