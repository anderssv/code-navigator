package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File

data class TypeAffinityOutput(
    val result: TypeAffinityResult,
    val skippedFileWarning: String?,
)

/** Shared by TypeAffinityTask (Gradle) and TypeAffinityMojo (Maven) so both build tools run the exact same pipeline. */
object TypeAffinityOrchestrator {

    fun run(
        config: TypeAffinityConfig,
        taggedDirs: List<Pair<File, SourceSet>>,
        scope: Scope,
        reportFile: File,
    ): TypeAffinityOutput {
        val classDirectories = taggedDirs.filter { scope.matchesSourceSet(it.second) }.map { it.first }
        val projectClasses = scanProjectClasses(classDirectories)

        val extractResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = false, filterTargets = true)
        val skippedFileWarning = SkippedFileReporter.report(extractResult.skippedFiles, reportFile)

        val result = TypeAffinityBuilder.analyze(extractResult.data, config.targetPackage, config.threshold)
        return TypeAffinityOutput(result, skippedFileWarning)
    }
}
