package no.f12.codenavigator.navigation.dsm

import java.io.File

data class MoveSuggestOutput(
    val result: MoveSuggestionResult?,
    val noResultsHints: List<String>,
    val skippedFileWarning: String?,
)

object MoveSuggestOrchestrator {

    fun run(config: MoveSuggestConfig, classDirectories: List<File>, reportFile: File): MoveSuggestOutput {
        val extraction = PackageHealthExtractor.extract(classDirectories, config.packageFilter, reportFile)
        return fromExtraction(extraction, config)
    }

    fun fromExtraction(extraction: PackageHealthExtraction, config: MoveSuggestConfig): MoveSuggestOutput {
        val result = MoveSuggester.suggest(extraction.dependencies, config.top, config.maxFanIn)

        if (result.suggestions.isEmpty()) {
            return MoveSuggestOutput(
                result = null,
                noResultsHints = listOf("No misplaced classes found — all classes have more edges to their own package than to any other."),
                skippedFileWarning = extraction.skippedFileWarning,
            )
        }

        return MoveSuggestOutput(
            result = result,
            noResultsHints = emptyList(),
            skippedFileWarning = extraction.skippedFileWarning,
        )
    }
}
