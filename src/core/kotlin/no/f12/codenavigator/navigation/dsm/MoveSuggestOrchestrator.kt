package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.Scope
import java.io.File

data class MoveSuggestOutput(
    val result: MoveSuggestionResult?,
    val noResultsHints: List<String>,
    val skippedFileWarning: String?,
)

object MoveSuggestOrchestrator {

    fun run(
        config: MoveSuggestConfig,
        classDirectories: List<File>,
        reportFile: File,
        testClassDirectories: List<File> = emptyList(),
    ): MoveSuggestOutput {
        val extraction = PackageHealthExtractor.extract(classDirectories, config.packageFilter, reportFile)
        return fromExtraction(extraction, config, testClassDirectories)
    }

    /**
     * Test classes are never move *subjects* unless the run is explicitly scoped to tests. Their
     * gravity is dominated by object mothers and fixtures, which live with the data they build, so
     * scoring a test by where its references land says nothing about where the test belongs — see
     * `cnavTestAlignment` for the check that does answer that question.
     */
    fun testSubjectExclusions(config: MoveSuggestConfig, testClassDirectories: List<File>): Set<ClassName> =
        if (config.scope == Scope.TEST || testClassDirectories.isEmpty()) emptySet()
        else scanProjectClasses(testClassDirectories)

    fun fromExtraction(
        extraction: PackageHealthExtraction,
        config: MoveSuggestConfig,
        testClassDirectories: List<File> = emptyList(),
    ): MoveSuggestOutput {
        val result = MoveSuggester.suggest(
            extraction.dependencies,
            extraction.structuralSupertypes,
            config.top,
            config.maxFanIn,
            extraction.receiverTypes,
            extraction.projectClasses,
            testSubjectExclusions(config, testClassDirectories),
        )

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
