package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.bytecode.RootPackageDetector
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.SourceSetResolver
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.bytecode.modulesOfClass
import no.f12.codenavigator.navigation.types.AnalysisWorkspace
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File

data class CyclesOutput(
    val details: List<CycleDetail>,
    val displayPrefix: PackageName,
    val testInvolvement: TestInvolvement.Counts?,
    val skippedFileWarning: String?,
    val moduleLabels: Map<PackageName, Set<String>> = emptyMap(),
)

/** Shared by CyclesTask (Gradle) and CyclesMojo (Maven) so both build tools run the exact same pipeline. */
object CyclesOrchestrator {

    fun run(
        config: CyclesConfig,
        workspace: AnalysisWorkspace,
        plan: List<PlanStep>,
        reportFile: File,
    ): CyclesOutput = run(
        config,
        workspace.taggedClassDirectories(),
        plan,
        reportFile,
        workspace.modulesOfClass(),
    )

    fun run(
        config: CyclesConfig,
        taggedDirs: List<Pair<File, SourceSet>>,
        plan: List<PlanStep>,
        reportFile: File,
        modulesOfClass: Map<ClassName, Set<String>> = emptyMap(),
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
        val moduleLabels = ModulePackageLabels.build(projectClasses, modulesOfClass, displayPrefix, config.depth)

        val testInvolvement = if (details.isNotEmpty() && config.scope == Scope.ALL) {
            val resolver = SourceSetResolver.from(taggedDirs)
            val classEdges = details.flatMap { it.edges }.flatMap { it.classEdges }
            TestInvolvement.count(classEdges) { resolver.sourceSetOf(it) }
        } else {
            null
        }

        return CyclesOutput(details, displayPrefix, testInvolvement, skippedFileWarning, moduleLabels)
    }
}
