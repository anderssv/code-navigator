package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.analysis.GitLogRunner
import no.f12.codenavigator.analysis.HotspotBuilder
import no.f12.codenavigator.navigation.CallGraphCache
import no.f12.codenavigator.navigation.ClassScanner
import no.f12.codenavigator.navigation.CycleDetector
import no.f12.codenavigator.navigation.DeadCodeFinder
import no.f12.codenavigator.navigation.DsmDependencyExtractor
import no.f12.codenavigator.navigation.DsmMatrixBuilder
import no.f12.codenavigator.navigation.MetricsBuilder
import no.f12.codenavigator.navigation.MetricsConfig
import no.f12.codenavigator.navigation.MetricsFormatter
import no.f12.codenavigator.navigation.PackageDependencyBuilder
import no.f12.codenavigator.navigation.SkippedFileReporter
import no.f12.codenavigator.navigation.TypeRanker

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
        val resolvedRootPackage = extension.resolveRootPackage(project.findProperty("root-package"))

        val config = MetricsConfig.parse(
            project.buildPropertyMap(
                propertyNames = listOf("after", "top", "format", "llm"),
                flagNames = listOf("no-follow"),
            ) + ("root-package" to resolvedRootPackage),
        )

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/call-graph.cache")
        val graphResult = CallGraphCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(graphResult.skippedFiles, reportFile)?.let { logger.warn(it) }
        val graph = graphResult.data

        val classResult = ClassScanner.scan(classDirectories)
        val packages = PackageDependencyBuilder.build(graph).allPackages()
        val rankedTypes = TypeRanker.rank(graph, projectOnly = true, collapseLambdas = true)
        val deadCode = DeadCodeFinder.find(
            graph = graph,
            filter = null,
            exclude = null,
            classesOnly = false,
            excludeAnnotated = emptySet(),
            classAnnotations = emptyMap(),
            methodAnnotations = emptyMap(),
            testGraph = null,
        )

        val dsmResult = DsmDependencyExtractor.extract(classDirectories, config.rootPackage)
        val matrix = DsmMatrixBuilder.build(dsmResult.data, config.rootPackage, depth = 2)
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

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatMetrics(metrics)
            OutputFormat.LLM -> LlmFormatter.formatMetrics(metrics)
            OutputFormat.TEXT -> MetricsFormatter.format(metrics)
        }
        logger.lifecycle(OutputWrapper.wrap(output, config.format))
    }
}
