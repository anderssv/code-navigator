package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import java.io.File

data class CohesionOutput(
    val result: CohesionResult?,
    val noResultsHints: List<String>,
    val skippedFileWarning: String?,
)

object CohesionOrchestrator {

    fun run(config: CohesionConfig, classDirectories: List<File>, reportFile: File): CohesionOutput {
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

        val result = CohesionScorer.score(extractResult.data, config.top, config.minEdges)

        if (result.entries.isEmpty()) {
            val packageCount = projectClasses.map { it.packageName() }.distinct().size
            return CohesionOutput(
                result = null,
                noResultsHints = CohesionFormatter.noResultsHints(packageCount),
                skippedFileWarning = skippedWarning,
            )
        }

        return CohesionOutput(
            result = result,
            noResultsHints = emptyList(),
            skippedFileWarning = skippedWarning,
        )
    }
}
