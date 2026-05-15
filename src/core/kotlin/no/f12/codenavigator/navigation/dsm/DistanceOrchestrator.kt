package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.core.PackageName
import no.f12.codenavigator.navigation.core.RootPackageDetector
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import no.f12.codenavigator.navigation.core.scanProjectClasses
import java.io.File

data class DistanceOutput(
    val formatted: String?,
    val skippedFileWarning: String?,
)

object DistanceOrchestrator {

    fun run(config: PackageDistanceConfig, classDirectories: List<File>, reportFile: File): DistanceOutput {
        val projectClasses = scanProjectClasses(classDirectories)

        val packageFilter = config.packageFilter?.let { PackageName(it) }

        val extractResult = DsmDependencyExtractor.extract(
            classDirectories, projectClasses,
            packageFilter = packageFilter ?: PackageName(""),
            includeExternal = config.includeExternal,
            filterTargets = false,
        )
        val skippedWarning = SkippedFileReporter.report(extractResult.skippedFiles, reportFile)

        val dependencies = extractResult.data.filterByPackage(packageFilter)

        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())
        val matrix = DsmMatrixBuilder.build(dependencies, displayPrefix, config.depth)

        val result = PackageDistanceBuilder.build(matrix, config.top)

        if (result.entries.isEmpty()) {
            val packageCount = projectClasses.map { it.packageName() }.distinct().size
            val hints = PackageDistanceFormatter.noResultsHints(packageCount)
            return DistanceOutput(
                formatted = OutputWrapper.emptyResult(config.format, "No inter-package dependencies found.", hints),
                skippedFileWarning = skippedWarning,
            )
        }

        return DistanceOutput(
            formatted = OutputWrapper.formatAndWrap(config.format,
                text = { PackageDistanceFormatter.format(result) },
                json = { JsonFormatter.formatDistance(result) },
                llm = { LlmFormatter.formatDistance(result) },
            ),
            skippedFileWarning = skippedWarning,
        )
    }
}
