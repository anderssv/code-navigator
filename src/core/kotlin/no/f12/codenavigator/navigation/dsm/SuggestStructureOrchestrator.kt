package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import java.io.File

data class SuggestStructureOutput(
    val result: StructureResult?,
    val noResultsHints: List<String>,
    val skippedFileWarning: String?,
)

object SuggestStructureOrchestrator {

    fun run(config: SuggestStructureConfig, classDirectories: List<File>, reportFile: File): SuggestStructureOutput {
        val extraction = PackageHealthExtractor.extract(classDirectories, config.packageFilter, reportFile)
        val moveSuggestionResult = MoveSuggester.suggest(extraction.dependencies, config.top, config.maxFanIn)

        if (moveSuggestionResult.suggestions.isEmpty()) {
            return SuggestStructureOutput(
                result = null,
                noResultsHints = listOf("No misplaced classes found — all classes have more edges to their own package than to any other."),
                skippedFileWarning = extraction.skippedFileWarning,
            )
        }

        val totalClassCount = extraction.dependencies.map { it.sourceClass }.distinct().size
        val result = StructureGrouper.group(moveSuggestionResult, totalClassCount, config.minGroupSize)

        if (result.groups.isEmpty()) {
            return SuggestStructureOutput(
                result = null,
                noResultsHints = listOf("Misplaced classes found but none form groups of ${config.minGroupSize}+ targeting the same package. Try lowering min-group-size."),
                skippedFileWarning = extraction.skippedFileWarning,
            )
        }

        return SuggestStructureOutput(
            result = result,
            noResultsHints = emptyList(),
            skippedFileWarning = extraction.skippedFileWarning,
        )
    }
}
