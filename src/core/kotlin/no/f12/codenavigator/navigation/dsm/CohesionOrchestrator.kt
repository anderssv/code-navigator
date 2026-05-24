package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import java.io.File

data class CohesionOutput(
    val result: CohesionResult?,
    val noResultsHints: List<String>,
    val skippedFileWarning: String?,
)

object CohesionOrchestrator {

    fun run(config: CohesionConfig, classDirectories: List<File>, reportFile: File): CohesionOutput {
        val extraction = PackageHealthExtractor.extract(classDirectories, config.packageFilter, reportFile)
        return fromExtraction(extraction, config, classDirectories)
    }

    fun fromExtraction(extraction: PackageHealthExtraction, config: CohesionConfig, classDirectories: List<File>): CohesionOutput {
        val result = CohesionScorer.score(extraction.dependencies, config.top, config.minEdges)

        if (result.entries.isEmpty()) {
            val projectClasses = scanProjectClasses(classDirectories)
            val packageCount = projectClasses.map { it.packageName() }.distinct().size
            return CohesionOutput(
                result = null,
                noResultsHints = CohesionFormatter.noResultsHints(packageCount),
                skippedFileWarning = extraction.skippedFileWarning,
            )
        }

        return CohesionOutput(
            result = result,
            noResultsHints = emptyList(),
            skippedFileWarning = extraction.skippedFileWarning,
        )
    }
}
