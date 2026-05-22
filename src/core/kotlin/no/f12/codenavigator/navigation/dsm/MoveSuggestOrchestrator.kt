package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import java.io.File

data class MoveSuggestOutput(
    val result: MoveSuggestionResult?,
    val noResultsHints: List<String>,
    val skippedFileWarning: String?,
)

object MoveSuggestOrchestrator {

    fun run(config: MoveSuggestConfig, classDirectories: List<File>, reportFile: File): MoveSuggestOutput {
        val projectClasses = scanProjectClasses(classDirectories)

        val packageFilter = config.packageFilter?.let { PackageName(it) }

        val extractResult = DsmDependencyExtractor.extract(
            classDirectories, projectClasses,
            packageFilter = packageFilter,
            includeExternal = false,
            filterTargets = false,
            includeSamePackage = true,
        )
        val skippedWarning = SkippedFileReporter.report(extractResult.skippedFiles, reportFile)

        val result = MoveSuggester.suggest(extractResult.data, config.top, config.maxFanIn)

        if (result.suggestions.isEmpty()) {
            return MoveSuggestOutput(
                result = null,
                noResultsHints = listOf("No misplaced classes found — all classes have more edges to their own package than to any other."),
                skippedFileWarning = skippedWarning,
            )
        }

        return MoveSuggestOutput(
            result = result,
            noResultsHints = emptyList(),
            skippedFileWarning = skippedWarning,
        )
    }
}
