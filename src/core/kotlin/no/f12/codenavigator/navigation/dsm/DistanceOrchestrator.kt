package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.bytecode.RootPackageDetector
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import java.io.File

data class DistanceOutput(
    val result: PackageDistanceResult?,
    val noResultsHints: List<String>,
    val skippedFileWarning: String?,
)

object DistanceOrchestrator {

    fun run(config: PackageDistanceConfig, classDirectories: List<File>, reportFile: File): DistanceOutput {
        val projectClasses = scanProjectClasses(classDirectories)

        val packageFilter = config.packageFilter?.let { PackageName(it) }

        val extractResult = DsmDependencyExtractor.extract(
            classDirectories, projectClasses,
            packageFilter = packageFilter,
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
            return DistanceOutput(
                result = null,
                noResultsHints = PackageDistanceFormatter.noResultsHints(packageCount),
                skippedFileWarning = skippedWarning,
            )
        }

        return DistanceOutput(
            result = result,
            noResultsHints = emptyList(),
            skippedFileWarning = skippedWarning,
        )
    }
}
