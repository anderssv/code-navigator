package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.analysis.GitLogRunner
import no.f12.codenavigator.analysis.HotspotBuilder
import no.f12.codenavigator.navigation.bytecode.RootPackageDetector
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.classinfo.ClassScanner
import no.f12.codenavigator.navigation.deadcode.DeadCodeConfig
import no.f12.codenavigator.navigation.deadcode.DeadCodeFormatter
import no.f12.codenavigator.navigation.deadcode.DeadCodeOrchestrator
import no.f12.codenavigator.navigation.dsm.CohesionConfig
import no.f12.codenavigator.navigation.dsm.CohesionFormatter
import no.f12.codenavigator.navigation.dsm.CohesionOrchestrator
import no.f12.codenavigator.navigation.dsm.CycleDetector
import no.f12.codenavigator.navigation.dsm.CyclesFormatter
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.DsmMatrixBuilder
import no.f12.codenavigator.navigation.dsm.MoveSuggestConfig
import no.f12.codenavigator.navigation.dsm.MoveSuggestFormatter
import no.f12.codenavigator.navigation.dsm.MoveSuggestOrchestrator
import no.f12.codenavigator.navigation.dsm.PackageDependencyBuilder
import no.f12.codenavigator.navigation.dsm.RingDetector
import no.f12.codenavigator.navigation.dsm.RingFormatter
import no.f12.codenavigator.navigation.metrics.MetricsBuilder
import no.f12.codenavigator.navigation.metrics.MetricsFormatter
import no.f12.codenavigator.navigation.rank.TypeRanker
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.Scope
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "report")
@Execute(phase = LifecyclePhase.COMPILE)
class ReportMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "after")
    private var after: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "package-filter")
    private var packageFilter: String? = null

    @Parameter(property = "include-external")
    private var includeExternal: String? = null

    @Parameter(property = "no-follow")
    private var noFollow: Boolean = false

    @Parameter(property = "exclude-annotated")
    private var excludeAnnotated: String? = null

    @Parameter(property = "treat-as-dead")
    private var treatAsDead: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val props = TaskRegistry.REPORT.enhanceProperties(project.applyConfigDefaults(buildPropertyMap()))
        val outputFormat = ParamDef.parseFormat(props)
        val scopeFilter = Scope.parse(props["scope"])
        val topN = props["top"]?.toIntOrNull() ?: 20
        val afterDate = TaskRegistry.AFTER.parseFrom(props)
        val followRenames = props["no-follow"] != "true"

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { scopeFilter.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val cacheDir = File(project.build.directory, "cnav")
        val reportFile = File(cacheDir, "skipped-files.txt")
        val sections = mutableListOf<String>()

        // --- Metrics ---
        val cacheFile = File(cacheDir, "call-graph.cache")
        val graphResult = CallGraphCache.getOrBuild(cacheFile, classDirectories)
        SkippedFileReporter.report(graphResult.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = graphResult.data

        val classResult = ClassScanner.scan(classDirectories)
        val packages = PackageDependencyBuilder.build(graph).allPackages()
        val rankedTypes = TypeRanker.rank(graph, projectOnly = true, collapseLambdas = true)

        val deadCodeConfig = DeadCodeConfig.parse(props)
        val testClassesDir = File(project.build.testOutputDirectory)
        val testGraph = if (testClassesDir.exists()) {
            CallGraphCache.getOrBuild(File(cacheDir, "test-call-graph.cache"), listOf(testClassesDir)).data
        } else null

        val deadCode = DeadCodeOrchestrator.findDeadCode(DeadCodeOrchestrator.DeadCodeInput(
            graph = graph,
            classDirectories = classDirectories,
            testGraph = testGraph,
            excludeAnnotated = deadCodeConfig.excludeAnnotated.toSet(),
            modifierAnnotated = deadCodeConfig.modifierAnnotated.toSet(),
            supertypeEntryPoints = deadCodeConfig.supertypeEntryPoints,
            receiverTypeEntryPoints = deadCodeConfig.receiverTypeEntryPoints,
            scope = deadCodeConfig.scope,
            cacheDir = cacheDir,
        ))

        val projectClasses = scanProjectClasses(classDirectories)
        val filterPkg = props["package-filter"]?.let { PackageName(it) }
        val dsmResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, filterPkg, includeExternal = false, filterTargets = true)
        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())
        val matrix = DsmMatrixBuilder.build(dsmResult.data, displayPrefix, depth = 2)
        val adjacency = CycleDetector.adjacencyMapFrom(matrix)
        val cycles = CycleDetector.findCycles(adjacency)
        val cyclicPairCount = cycles.size

        val commits = GitLogRunner.run(project.basedir, afterDate, followRenames = followRenames)
        val hotspots = HotspotBuilder.build(commits, minRevs = 1, top = topN)

        val metrics = MetricsBuilder.build(
            classes = classResult.data,
            packages = packages,
            rankedTypes = rankedTypes,
            cyclicPairCount = cyclicPairCount,
            deadCode = deadCode,
            hotspots = hotspots,
        )
        sections += "## Metrics\n\n${MetricsFormatter.format(metrics)}"

        // --- Cycles ---
        val cycleDetails = CycleDetector.enrich(cycles, matrix)
        if (cycleDetails.isNotEmpty()) {
            sections += "## Cycles\n\n${CyclesFormatter.format(cycleDetails, displayPrefix = displayPrefix)}"
        } else {
            sections += "## Cycles\n\nNo package cycles detected."
        }

        // --- Rings ---
        val ringDeps = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = false, filterTargets = true)
        val ringResult = RingDetector.detect(ringDeps.data)
        sections += "## Rings\n\n${RingFormatter.format(ringResult)}"

        // --- Move Suggestions ---
        val moveSuggestConfig = MoveSuggestConfig.parse(props)
        val moveOutput = MoveSuggestOrchestrator.run(moveSuggestConfig, classDirectories, reportFile)
        if (moveOutput.result != null && moveOutput.result.suggestions.isNotEmpty()) {
            sections += "## Move Suggestions\n\n${MoveSuggestFormatter.format(moveOutput.result)}"
        } else {
            sections += "## Move Suggestions\n\nNo misplaced classes detected."
        }

        // --- Cohesion ---
        val cohesionConfig = CohesionConfig.parse(props)
        val cohesionOutput = CohesionOrchestrator.run(cohesionConfig, classDirectories, reportFile)
        if (cohesionOutput.result != null) {
            sections += "## Cohesion\n\n${CohesionFormatter.format(cohesionOutput.result)}"
        }

        // --- Dead Code ---
        if (deadCode.isNotEmpty()) {
            sections += "## Dead Code (top $topN)\n\n${DeadCodeFormatter.format(deadCode.take(topN), scopeFilter)}"
        } else {
            sections += "## Dead Code\n\nNo dead code detected."
        }

        val output = sections.joinToString("\n\n---\n\n")
        println(OutputWrapper.formatAndWrap(outputFormat) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> output
        OutputFormat.JSON -> output
        OutputFormat.LLM -> output
    }
})
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        after?.let { put("after", it) }
        top?.let { put("top", it) }
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        excludeAnnotated?.let { put("exclude-annotated", it) }
        treatAsDead?.let { put("treat-as-dead", it) }
        if (noFollow) put("no-follow", null)
        scope?.let { put("scope", it) }
    }
}
