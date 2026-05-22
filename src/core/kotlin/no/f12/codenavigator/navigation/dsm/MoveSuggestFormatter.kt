package no.f12.codenavigator.navigation.dsm

object MoveSuggestFormatter {

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
}
