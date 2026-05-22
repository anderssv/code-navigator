package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.FrameworkPresets
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import java.io.File
import java.nio.file.Path

data class StrengthOutput(
    val result: StrengthResult?,
    val noResultsHints: List<String>,
    val skippedFileWarning: String?,
)

object StrengthOrchestrator {

    fun run(config: StrengthConfig, classDirectories: List<File>, reportFile: File, classpath: List<Path> = emptyList()): StrengthOutput {
        val projectClasses = scanProjectClasses(classDirectories)

        val modelAnnotations = FrameworkPresets.resolveAllModelAnnotations()
        val classTypeRegistry = ClassTypeCollector.collect(classDirectories, modelAnnotations).toMutableMap()

        val packageFilter = config.packageFilter?.let { PackageName(it) }

        val extractResult = DsmDependencyExtractor.extract(
            classDirectories, projectClasses,
            packageFilter = packageFilter,
            includeExternal = config.includeExternal,
            filterTargets = false,
        )
        val skippedWarning = SkippedFileReporter.report(extractResult.skippedFiles, reportFile)

        // Lazily resolve external classes from classpath
        if (config.includeExternal && classpath.isNotEmpty()) {
            val unknownTargets = extractResult.data
                .map { it.targetClass }
                .filter { it !in classTypeRegistry }
                .toSet()
            if (unknownTargets.isNotEmpty()) {
                val resolved = ClassTypeCollector.resolveFromClasspath(unknownTargets, classpath, modelAnnotations)
                classTypeRegistry.putAll(resolved)
            }
        }

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
