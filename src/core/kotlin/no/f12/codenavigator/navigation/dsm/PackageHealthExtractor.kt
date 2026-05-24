package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import java.io.File

/**
 * Shared extraction for tasks that need the same DsmDependencyExtractor call with
 * includeSamePackage=true (cohesion, move-suggest).
 */
data class PackageHealthExtraction(
    val dependencies: List<PackageDependency>,
    val skippedFileWarning: String?,
)

object PackageHealthExtractor {

    fun extract(
        classDirectories: List<File>,
        packageFilter: String?,
        reportFile: File,
    ): PackageHealthExtraction {
        val projectClasses = scanProjectClasses(classDirectories)
        val filter = packageFilter?.let { PackageName(it) }

        val extractResult = DsmDependencyExtractor.extract(
            classDirectories, projectClasses,
            packageFilter = filter,
            includeExternal = false,
            filterTargets = false,
            includeSamePackage = true,
        )
        val skippedWarning = SkippedFileReporter.report(extractResult.skippedFiles, reportFile)

        return PackageHealthExtraction(
            dependencies = extractResult.data,
            skippedFileWarning = skippedWarning,
        )
    }
}
