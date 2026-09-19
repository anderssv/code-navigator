package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.withInterpretation

object MoveSuggestFormatter {

    internal const val MOVE_SUGGEST_INTERPRETATION = "Interpretation: ADVISORY, not a gate. Classes with more edges to another package than their own may be misplaced. own=0 rows are marked absence-driven: the verdict rests on having no ties to the current package rather than on evidence of belonging elsewhere, and confidence rounds toward 1.00 for that reason — treat them as the weakest rows, not the strongest. Gravity cannot see a deliberate convention (exception subclasses co-located with what throws them, a port kept with its service tier), so verify intent before moving — composition roots, drivers, and thin adapters are all expected to have outward edges."

    fun format(result: MoveSuggestionResult): String {
        val header = String.format("%-50s %-30s %-30s %5s %5s %10s  %s", "Class", "Current", "Suggested", "Own", "Target", "Confidence", "Evidence")
        val separator = "-".repeat(150)
        val rows = result.suggestions.joinToString("\n") { s ->
            String.format(
                "%-50s %-30s %-30s %5d %5d %10.2f  %s",
                s.className.simpleName(), s.currentPackage, s.suggestedPackage, s.edgesToCurrent, s.edgesToSuggested, s.confidence,
                if (s.absenceDriven) "absence-driven (no same-package edges) — weak" else "",
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
                "absenceDriven" to s.absenceDriven,
            )
        }

    fun formatLlm(result: MoveSuggestionResult): String =
        result.suggestions.joinToString("\n") { s ->
            "${s.className.value} current=${s.currentPackage} suggested=${s.suggestedPackage} own=${s.edgesToCurrent} target=${s.edgesToSuggested} confidence=${"%.2f".format(s.confidence)}" +
                if (s.absenceDriven) " evidence=absence-driven" else ""
        }.withInterpretation(MOVE_SUGGEST_INTERPRETATION)
}
