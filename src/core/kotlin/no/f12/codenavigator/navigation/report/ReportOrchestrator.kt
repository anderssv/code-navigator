package no.f12.codenavigator.navigation.report

import no.f12.codenavigator.analysis.GitCommit
import no.f12.codenavigator.analysis.HotspotBuilder
import no.f12.codenavigator.navigation.bytecode.RootPackageDetector
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.classinfo.ClassScanner
import no.f12.codenavigator.navigation.deadcode.DeadCode
import no.f12.codenavigator.navigation.deadcode.DeadCodeOrchestrator
import no.f12.codenavigator.navigation.dsm.CohesionOrchestrator
import no.f12.codenavigator.navigation.dsm.CohesionResult
import no.f12.codenavigator.navigation.dsm.CycleDetail
import no.f12.codenavigator.navigation.dsm.CycleDetector
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.DsmMatrixBuilder
import no.f12.codenavigator.navigation.dsm.MoveSuggestOrchestrator
import no.f12.codenavigator.navigation.dsm.MoveSuggestionResult
import no.f12.codenavigator.navigation.dsm.PackageDependencyBuilder
import no.f12.codenavigator.navigation.dsm.RingAssignment
import no.f12.codenavigator.navigation.dsm.RingDetector
import no.f12.codenavigator.navigation.metrics.MetricsBuilder
import no.f12.codenavigator.navigation.metrics.MetricsResult
import no.f12.codenavigator.navigation.rank.TypeRanker
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.Scope
import java.io.File

/** Typed result of the composite report, holding each sub-analysis's domain object so a formatter can render TEXT, JSON, or LLM from the same data rather than re-parsing rendered markdown. */
data class ReportData(
    val metrics: MetricsResult,
    val cycles: List<CycleDetail>,
    val displayPrefix: PackageName,
    val rings: RingAssignment,
    /** Null when there are no move suggestions (result absent or empty). */
    val moveSuggestions: MoveSuggestionResult?,
    /** Null when cohesion couldn't be computed (e.g. too few packages). */
    val cohesion: CohesionResult?,
    val deadCode: List<DeadCode>,
    val topN: Int,
    val scope: Scope,
    val skippedFileWarning: String?,
)

/**
 * Shared by ReportTask (Gradle) and ReportMojo (Maven) so both build tools run the exact same
 * composite pipeline. Test class directories and git commits are resolved by each build tool and
 * passed in (they differ per tool / are I/O), keeping the aggregation logic build-tool-neutral.
 */
object ReportOrchestrator {

    fun run(
        config: ReportConfig,
        classDirectories: List<File>,
        testClassDirectories: List<File>,
        commits: List<GitCommit>,
        cacheDir: File,
        reportFile: File,
    ): ReportData {
        val graphResult = CallGraphCache.getOrBuild(File(cacheDir, "call-graph.cache"), classDirectories)
        val skippedFileWarning = SkippedFileReporter.report(graphResult.skippedFiles, reportFile)
        val graph = graphResult.data

        val classResult = ClassScanner.scan(classDirectories)
        val packages = PackageDependencyBuilder.build(graph).allPackages()
        val rankedTypes = TypeRanker.rank(graph, projectOnly = true, collapseLambdas = true)

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
                excludeAnnotated = config.deadCode.excludeAnnotated.toSet(),
                modifierAnnotated = config.deadCode.modifierAnnotated.toSet(),
                supertypeEntryPoints = config.deadCode.supertypeEntryPoints,
                receiverTypeEntryPoints = config.deadCode.receiverTypeEntryPoints,
                scope = config.deadCode.scope,
                cacheDir = cacheDir,
            ),
        )

        val projectClasses = scanProjectClasses(classDirectories)
        val dsmResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, config.packageFilter, includeExternal = false, filterTargets = true)
        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())
        val matrix = DsmMatrixBuilder.build(dsmResult.data, displayPrefix, depth = 2)
        val adjacency = CycleDetector.adjacencyMapFrom(matrix)
        val cycles = CycleDetector.findCycles(adjacency)

        val hotspots = HotspotBuilder.build(commits, minRevs = 1, top = config.top)

        val metrics = MetricsBuilder.build(
            classes = classResult.data,
            packages = packages,
            rankedTypes = rankedTypes,
            cyclicPairCount = cycles.size,
            deadCode = deadCode,
            hotspots = hotspots,
        )

        val cycleDetails = CycleDetector.enrich(cycles, matrix)

        val ringDeps = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = false, filterTargets = true)
        val ringResult = RingDetector.detect(ringDeps.data)

        val moveOutput = MoveSuggestOrchestrator.run(config.moveSuggest, classDirectories, reportFile)
        val moveSuggestions = moveOutput.result?.takeIf { it.suggestions.isNotEmpty() }

        val cohesionOutput = CohesionOrchestrator.run(config.cohesion, classDirectories, reportFile)

        return ReportData(
            metrics = metrics,
            cycles = cycleDetails,
            displayPrefix = displayPrefix,
            rings = ringResult,
            moveSuggestions = moveSuggestions,
            cohesion = cohesionOutput.result,
            deadCode = deadCode,
            topN = config.top,
            scope = config.scope,
            skippedFileWarning = skippedFileWarning,
        )
    }
}
