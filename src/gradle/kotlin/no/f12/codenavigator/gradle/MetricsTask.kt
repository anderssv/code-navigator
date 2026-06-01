package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.analysis.GitLogRunner
import no.f12.codenavigator.analysis.HotspotBuilder
import no.f12.codenavigator.navigation.bytecode.RootPackageDetector
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.classinfo.ClassScanner
import no.f12.codenavigator.navigation.deadcode.DeadCodeConfig
import no.f12.codenavigator.navigation.deadcode.DeadCodeOrchestrator
import no.f12.codenavigator.navigation.dsm.CycleDetector
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.DsmMatrixBuilder
import no.f12.codenavigator.navigation.metrics.MetricsBuilder
import no.f12.codenavigator.navigation.metrics.MetricsConfig
import no.f12.codenavigator.navigation.metrics.MetricsFormatter
import no.f12.codenavigator.navigation.dsm.PackageDependencyBuilder
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.rank.TypeRanker

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class MetricsTask : CodeNavigatorTask() {

    @Option(option = "after", description = "Only consider commits after this date")
    @get:Internal
    var after: String? = null

    @Option(option = "top", description = "Max results per section")
    @get:Internal
    var top: String? = null

    @Option(option = "no-follow", description = "Disable git rename tracking")
    @get:Internal
    var noFollow: Boolean = false

    @Option(option = "package-filter", description = "Only include packages under this prefix")
    @get:Internal
    var packageFilter: String? = null

    @Option(option = "include-external", description = "Include dependencies on classes outside the project")
    @get:Internal
    var includeExternal: String? = null

    @Option(option = "exclude-annotated", description = "Exclude classes/methods bearing these annotations (simple names, comma-separated)")
    @get:Internal
    var excludeAnnotated: String? = null

    @Option(option = "treat-as-dead", description = "Treat framework-annotated code as potentially dead. Use ALL to remove all framework protections.")
    @get:Internal
    var treatAsDead: String? = null

    @Option(option = "root-package", description = "Deprecated: use package-filter instead. Only include packages under this prefix")
    @get:Internal
    var rootPackage: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        after?.let { put("after", it) }
        top?.let { put("top", it) }
        if (noFollow) put("no-follow", "true")
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        excludeAnnotated?.let { put("exclude-annotated", it) }
        treatAsDead?.let { put("treat-as-dead", it) }
        rootPackage?.let { put("root-package", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun showMetrics() {
        val extension = project.codeNavigatorExtension()
        val props = extension.resolveProperties(TaskRegistry.METRICS.enhanceProperties(buildOptionsMap()))

        val config = MetricsConfig.parse(props)
        config.deprecations().forEach { logger.warn(it) }

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        val cacheDir = File(project.layout.buildDirectory.asFile.get(), "cnav")
        val cacheFile = File(cacheDir, "call-graph.cache")
        val graphResult = CallGraphCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(cacheDir, "skipped-files.txt")
        SkippedFileReporter.report(graphResult.skippedFiles, reportFile)?.let { logger.warn(it) }
        val graph = graphResult.data

        val classResult = ClassScanner.scan(classDirectories)
        val packages = PackageDependencyBuilder.build(graph).allPackages()
        val rankedTypes = TypeRanker.rank(graph, projectOnly = true, collapseLambdas = true)

        // Use DeadCodeConfig for consistent framework preset resolution
        val deadCodeConfig = DeadCodeConfig.parse(props)

        // Build test graph for accurate dead code detection
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val testSourceSet = sourceSets.findByName("test")
        val testClassDirectories = testSourceSet?.output?.classesDirs?.files?.filter { it.exists() }?.toList() ?: emptyList()
        val testGraph = if (testClassDirectories.isNotEmpty()) {
            CallGraphCache.getOrBuild(
                File(cacheDir, "test-call-graph.cache"),
                testClassDirectories,
            ).data
        } else {
            null
        }

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
        val dsmResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, config.packageFilter, config.includeExternal, filterTargets = true)
        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())
        val matrix = DsmMatrixBuilder.build(dsmResult.data, displayPrefix, depth = 2)
        val cyclicPairCount = CycleDetector.findCycles(CycleDetector.adjacencyMapFrom(matrix)).size

        val commits = GitLogRunner.run(project.projectDir, config.after, followRenames = config.followRenames)
        val hotspots = HotspotBuilder.build(commits, minRevs = 1, top = config.top)

        val metrics = MetricsBuilder.build(
            classes = classResult.data,
            packages = packages,
            rankedTypes = rankedTypes,
            cyclicPairCount = cyclicPairCount,
            deadCode = deadCode,
            hotspots = hotspots,
        )

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> MetricsFormatter.format(metrics)
        OutputFormat.JSON -> JsonFormatter.formatMetrics(metrics)
        OutputFormat.LLM -> LlmFormatter.formatMetrics(metrics)
    }
})
    }
}
