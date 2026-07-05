package no.f12.codenavigator.navigation.metrics

import no.f12.codenavigator.analysis.GitLogRunner
import no.f12.codenavigator.analysis.HotspotBuilder
import no.f12.codenavigator.navigation.bytecode.RootPackageDetector
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.classinfo.ClassScanner
import no.f12.codenavigator.navigation.deadcode.DeadCodeConfig
import no.f12.codenavigator.navigation.deadcode.DeadCodeOrchestrator
import no.f12.codenavigator.navigation.dsm.CycleDetector
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.DsmMatrixBuilder
import no.f12.codenavigator.navigation.dsm.PackageDependencyBuilder
import no.f12.codenavigator.navigation.dsm.PlanMutator
import no.f12.codenavigator.navigation.dsm.PlanStep
import no.f12.codenavigator.navigation.rank.TypeRanker
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File

data class MetricsOutput(
    val result: MetricsResult,
    val skippedFileWarning: String?,
)

/** Shared by MetricsTask (Gradle) and MetricsMojo (Maven) so both build tools run the exact same pipeline. */
object MetricsOrchestrator {

    fun run(
        config: MetricsConfig,
        deadCodeConfig: DeadCodeConfig,
        taggedDirs: List<Pair<File, SourceSet>>,
        plan: List<PlanStep>,
        cacheDir: File,
        projectDir: File,
    ): MetricsOutput {
        val classDirectories = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }.map { it.first }

        val graphResult = CallGraphCache.getOrBuild(File(cacheDir, "call-graph.cache"), classDirectories)
        val reportFile = File(cacheDir, "skipped-files.txt")
        val skippedFileWarning = SkippedFileReporter.report(graphResult.skippedFiles, reportFile)
        val graph = graphResult.data

        val classResult = ClassScanner.scan(classDirectories)
        val packages = PackageDependencyBuilder.build(graph).allPackages()
        val rankedTypes = TypeRanker.rank(graph, projectOnly = true, collapseLambdas = true)

        val testClassDirectories = taggedDirs.filter { it.second == SourceSet.TEST }.map { it.first }
        val testGraph = if (testClassDirectories.isNotEmpty()) {
            CallGraphCache.getOrBuild(File(cacheDir, "test-call-graph.cache"), testClassDirectories).data
        } else {
            null
        }

        val deadCode = DeadCodeOrchestrator.findDeadCode(
            DeadCodeOrchestrator.DeadCodeInput(
                graph = graph,
                classDirectories = classDirectories,
                testGraph = testGraph,
                excludeAnnotated = deadCodeConfig.excludeAnnotated.toSet(),
                modifierAnnotated = deadCodeConfig.modifierAnnotated.toSet(),
                supertypeEntryPoints = deadCodeConfig.supertypeEntryPoints,
                receiverTypeEntryPoints = deadCodeConfig.receiverTypeEntryPoints,
                scope = deadCodeConfig.scope,
                cacheDir = cacheDir,
            ),
        )

        val projectClasses = scanProjectClasses(classDirectories)
        val dsmResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, config.packageFilter, config.includeExternal, filterTargets = true)
        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())
        val matrix = DsmMatrixBuilder.build(PlanMutator.apply(dsmResult.data, plan), displayPrefix, depth = 2)
        val cyclicPairCount = CycleDetector.findCycles(CycleDetector.adjacencyMapFrom(matrix)).size

        val commits = GitLogRunner.run(projectDir, config.after, followRenames = config.followRenames)
        val hotspots = HotspotBuilder.build(commits, minRevs = 1, top = config.top)

        val metrics = MetricsBuilder.build(
            classes = classResult.data,
            packages = packages,
            rankedTypes = rankedTypes,
            cyclicPairCount = cyclicPairCount,
            deadCode = deadCode,
            hotspots = hotspots,
        )

        return MetricsOutput(metrics, skippedFileWarning)
    }
}
