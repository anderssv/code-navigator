package no.f12.codenavigator.gradle

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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class MetricsTask : DefaultTask() {

    @TaskAction
    fun showMetrics() {
        val extension = project.codeNavigatorExtension()
        val cliProps = project.buildPropertyMap(TaskRegistry.METRICS)
        val props = extension.resolveProperties(cliProps)

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

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { MetricsFormatter.format(metrics) },
            json = { JsonFormatter.formatMetrics(metrics) },
            llm = { LlmFormatter.formatMetrics(metrics) },
        ))
    }
}
