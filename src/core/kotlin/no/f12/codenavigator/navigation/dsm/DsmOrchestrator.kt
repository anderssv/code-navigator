package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.bytecode.RootPackageDetector
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File

data class DsmAnalysisOutput(
    val matrix: DsmMatrix,
    val projectClasses: Set<ClassName>,
    val skippedFileWarning: String?,
    /** Module(s) each displayed (post-prefix-truncation) package was found in. Empty unless --multi-module is active. */
    val moduleLabels: Map<PackageName, Set<String>> = emptyMap(),
)

/** Shared by DsmTask (Gradle) and DsmMojo (Maven) so both build tools run the exact same pipeline. */
object DsmOrchestrator {

    fun run(
        config: DsmConfig,
        taggedDirs: List<Pair<File, SourceSet>>,
        plan: List<PlanStep>,
        reportFile: File,
        moduleOfClass: Map<ClassName, String> = emptyMap(),
    ): DsmAnalysisOutput {
        val classDirectories = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }.map { it.first }
        val projectClasses = scanProjectClasses(classDirectories)

        val result = DsmDependencyExtractor.extract(classDirectories, projectClasses, config.packageFilter, config.includeExternal, filterTargets = true)
        val skippedFileWarning = SkippedFileReporter.report(result.skippedFiles, reportFile)
        val dependencies = PlanMutator.apply(result.data, plan)

        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())
        val matrix = DsmMatrixBuilder.build(dependencies, displayPrefix, config.depth)

        val moduleLabels = if (moduleOfClass.isEmpty()) {
            emptyMap()
        } else {
            projectClasses
                .groupBy { it.packageName().truncate(displayPrefix, config.depth) }
                .mapValues { (_, classes) -> classes.mapNotNull { moduleOfClass[it] }.toSet() }
        }

        return DsmAnalysisOutput(matrix, projectClasses, skippedFileWarning, moduleLabels)
    }
}
