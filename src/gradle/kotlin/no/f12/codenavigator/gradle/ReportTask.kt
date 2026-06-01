package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.DsmOutputFormatter
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
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
import no.f12.codenavigator.navigation.dsm.CohesionOrchestrator
import no.f12.codenavigator.navigation.dsm.CohesionConfig
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
import no.f12.codenavigator.navigation.types.Scope
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class ReportTask : CodeNavigatorTask() {

    @Option(option = "package-filter", description = "Only include packages under this prefix")
    @get:Internal
    var packageFilter: String? = null

    @Option(option = "include-external", description = "Include dependencies on classes outside the project")
    @get:Internal
    var includeExternal: String? = null

    @Option(option = "top", description = "Max results")
    @get:Internal
    var top: String? = null

    @Option(option = "after", description = "Only consider commits after this date")
    @get:Internal
    var after: String? = null

    @Option(option = "no-follow", description = "Disable git rename tracking")
    @get:Internal
    var noFollow: Boolean = false

    @Option(option = "exclude-annotated", description = "Exclude classes/methods bearing these annotations (simple names, comma-separated)")
    @get:Internal
    var excludeAnnotated: String? = null

    @Option(option = "treat-as-dead", description = "Treat framework-annotated code as potentially dead. Use ALL to remove all framework protections.")
    @get:Internal
    var treatAsDead: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        top?.let { put("top", it) }
        after?.let { put("after", it) }
        if (noFollow) put("no-follow", "true")
        excludeAnnotated?.let { put("exclude-annotated", it) }
        treatAsDead?.let { put("treat-as-dead", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun generateReport() {
        val extension = project.codeNavigatorExtension()
        val props = extension.resolveProperties(TaskRegistry.REPORT.enhanceProperties(buildOptionsMap()))

        val format = ParamDef.parseFormat(props)
        val scopeVal = Scope.parse(props["scope"])
        val packageFilterVal = props["package-filter"]
        val topVal = props["top"]?.toIntOrNull() ?: 20
        val afterVal = TaskRegistry.AFTER.parseFrom(props)
        val followRenames = props["no-follow"] != "true"

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { scopeVal.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        val cacheDir = File(project.layout.buildDirectory.asFile.get(), "cnav")
        val reportFile = File(cacheDir, "skipped-files.txt")

        val sections = mutableListOf<String>()

        // --- Metrics ---
        val cacheFile = File(cacheDir, "call-graph.cache")
        val graphResult = CallGraphCache.getOrBuild(cacheFile, classDirectories)
        SkippedFileReporter.report(graphResult.skippedFiles, reportFile)?.let { logger.warn(it) }
        val graph = graphResult.data

        val classResult = ClassScanner.scan(classDirectories)
        val packages = PackageDependencyBuilder.build(graph).allPackages()
        val rankedTypes = TypeRanker.rank(graph, projectOnly = true, collapseLambdas = true)

        val deadCodeConfig = DeadCodeConfig.parse(props)
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val testSourceSet = sourceSets.findByName("test")
        val testClassDirectories = testSourceSet?.output?.classesDirs?.files?.filter { it.exists() }?.toList() ?: emptyList()
        val testGraph = if (testClassDirectories.isNotEmpty()) {
            CallGraphCache.getOrBuild(File(cacheDir, "test-call-graph.cache"), testClassDirectories).data
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
        val filterPkg = packageFilterVal?.let { no.f12.codenavigator.navigation.types.PackageName(it) }
        val dsmResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, filterPkg, includeExternal = false, filterTargets = true)
        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())
        val matrix = DsmMatrixBuilder.build(dsmResult.data, displayPrefix, depth = 2)
        val adjacency = CycleDetector.adjacencyMapFrom(matrix)
        val cycles = CycleDetector.findCycles(adjacency)
        val cyclicPairCount = cycles.size

        val commits = GitLogRunner.run(project.projectDir, afterVal, followRenames = followRenames)
        val hotspots = HotspotBuilder.build(commits, minRevs = 1, top = topVal)

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
            sections += "## Cohesion\n\n${no.f12.codenavigator.navigation.dsm.CohesionFormatter.format(cohesionOutput.result)}"
        }

        // --- Dead Code ---
        if (deadCode.isNotEmpty()) {
            sections += "## Dead Code (top $topVal)\n\n${DeadCodeFormatter.format(deadCode.take(topVal), scopeVal)}"
        } else {
            sections += "## Dead Code\n\nNo dead code detected."
        }

        val output = sections.joinToString("\n\n---\n\n")
        logger.lifecycle(OutputWrapper.formatAndWrap(format,
            text = { output },
            json = { output },
            llm = { output },
        ))
    }
}
