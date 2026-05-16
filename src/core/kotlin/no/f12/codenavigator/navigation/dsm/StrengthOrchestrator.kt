package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.FrameworkPresets
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import java.io.File

data class StrengthOutput(
    val result: StrengthResult?,
    val noResultsHints: List<String>,
    val skippedFileWarning: String?,
)

object StrengthOrchestrator {

    fun run(config: StrengthConfig, classDirectories: List<File>, reportFile: File): StrengthOutput {
        val projectClasses = scanProjectClasses(classDirectories)

        val classTypeRegistry = ClassTypeCollector.collect(classDirectories, FrameworkPresets.resolveAllModelAnnotations())

        val packageFilter = config.packageFilter?.let { PackageName(it) }

        val extractResult = DsmDependencyExtractor.extract(
            classDirectories, projectClasses,
            packageFilter = packageFilter,
            includeExternal = config.includeExternal,
            filterTargets = false,
        )
        val skippedWarning = SkippedFileReporter.report(extractResult.skippedFiles, reportFile)

        val result = StrengthClassifier.classify(extractResult.data, classTypeRegistry, config.top, packageFilter)

        if (result.entries.isEmpty()) {
            val packageCount = projectClasses.map { it.packageName() }.distinct().size
            return StrengthOutput(
                result = null,
                noResultsHints = StrengthFormatter.noResultsHints(packageCount),
                skippedFileWarning = skippedWarning,
            )
        }

        return StrengthOutput(
            result = result,
            noResultsHints = emptyList(),
            skippedFileWarning = skippedWarning,
        )
    }
}
