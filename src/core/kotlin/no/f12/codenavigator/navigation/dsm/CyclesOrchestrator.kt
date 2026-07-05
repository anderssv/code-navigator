package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.bytecode.RootPackageDetector
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.SourceSetResolver
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File

data class CyclesOutput(
    val details: List<CycleDetail>,
    val displayPrefix: PackageName,
    val testNotice: String?,
    val skippedFileWarning: String?,
)

/** Shared by CyclesTask (Gradle) and CyclesMojo (Maven) so both build tools run the exact same pipeline. */
object CyclesOrchestrator {

    fun run(
        config: CyclesConfig,
        taggedDirs: List<Pair<File, SourceSet>>,
        plan: List<PlanStep>,
        reportFile: File,
    ): CyclesOutput {
        val classDirectories = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }.map { it.first }
        val projectClasses = scanProjectClasses(classDirectories)

        val result = DsmDependencyExtractor.extract(classDirectories, projectClasses, config.packageFilter, config.includeExternal, filterTargets = true)
        val skippedFileWarning = SkippedFileReporter.report(result.skippedFiles, reportFile)
        val dependencies = PlanMutator.apply(result.data, plan)

        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())
        val matrix = DsmMatrixBuilder.build(dependencies, displayPrefix, config.depth)

        val adjacency = CycleDetector.adjacencyMapFrom(matrix)
        val cycles = CycleDetector.findCycles(adjacency)
        val details = CycleDetector.enrich(cycles, matrix)

        val testNotice = if (details.isNotEmpty() && config.scope == Scope.ALL) {
            val resolver = SourceSetResolver.from(taggedDirs)
            val classEdges = details.flatMap { it.edges }.flatMap { it.classEdges }
            TestInvolvement.notice(
                TestInvolvement.count(classEdges) { resolver.sourceSetOf(it) },
                "cycle edges",
            )
        } else {
            null
        }

        return CyclesOutput(details, displayPrefix, testNotice, skippedFileWarning)
    }
}
