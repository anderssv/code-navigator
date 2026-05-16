package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.types.FrameworkPresets
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import java.io.File

data class StrengthOutput(
    val formatted: String?,
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
            val hints = StrengthFormatter.noResultsHints(packageCount)
            return StrengthOutput(
                formatted = OutputWrapper.emptyResult(config.format, "No inter-package dependencies found.", hints),
                skippedFileWarning = skippedWarning,
            )
        }

        return StrengthOutput(
            formatted = OutputWrapper.formatAndWrap(config.format,
                text = { StrengthFormatter.format(result) },
                json = { JsonFormatter.formatStrength(result) },
                llm = { LlmFormatter.formatStrength(result) },
            ),
            skippedFileWarning = skippedWarning,
        )
    }
}
