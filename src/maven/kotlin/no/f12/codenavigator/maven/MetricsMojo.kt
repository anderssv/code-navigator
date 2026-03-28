package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.analysis.GitLogRunner
import no.f12.codenavigator.analysis.HotspotBuilder
import no.f12.codenavigator.navigation.AnnotationExtractor
import no.f12.codenavigator.navigation.CallGraphBuilder
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
import no.f12.codenavigator.navigation.rank.TypeRanker
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "metrics")
@Execute(phase = LifecyclePhase.COMPILE)
class MetricsMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "after")
    private var after: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "root-package")
    private var rootPackage: String? = null

    @Parameter(property = "no-follow")
    private var noFollow: Boolean = false

    @Parameter(property = "exclude-annotated")
    private var excludeAnnotated: String? = null

    @Parameter(property = "framework")
    private var framework: String? = null

    override fun execute() {
        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val config = MetricsConfig.parse(buildPropertyMap())
        val classDirectories = listOf(classesDir)

        val graphResult = CallGraphBuilder.build(classDirectories)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(graphResult.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = graphResult.data

        val classResult = ClassScanner.scan(classDirectories)
        val packages = PackageDependencyBuilder.build(graph).allPackages()
        val rankedTypes = TypeRanker.rank(graph, projectOnly = true, collapseLambdas = true)

        val excludeAnnotatedSet = config.excludeAnnotated.toSet()
        val (classAnnotations, methodAnnotations) = AnnotationExtractor.scanAll(classDirectories)

        val deadCode = DeadCodeFinder.find(
            graph = graph,
            filter = null,
            exclude = null,
            classesOnly = false,
            excludeAnnotated = excludeAnnotatedSet,
            classAnnotations = classAnnotations,
            methodAnnotations = methodAnnotations,
            testGraph = null,
        )

        val dsmResult = DsmDependencyExtractor.extract(classDirectories, config.rootPackage)
        val matrix = DsmMatrixBuilder.build(dsmResult.data, config.rootPackage, depth = 2)
        val cyclicPairCount = CycleDetector.findCycles(CycleDetector.adjacencyMapFrom(matrix)).size

        val commits = GitLogRunner.run(project.basedir, config.after, followRenames = config.followRenames)
        val hotspots = HotspotBuilder.build(commits, minRevs = 1, top = config.top)

        val metrics = MetricsBuilder.build(
            classes = classResult.data,
            packages = packages,
            rankedTypes = rankedTypes,
            cyclicPairCount = cyclicPairCount,
            deadCode = deadCode,
            hotspots = hotspots,
        )

        println(OutputWrapper.formatAndWrap(config.format,
            text = { MetricsFormatter.format(metrics) },
            json = { JsonFormatter.formatMetrics(metrics) },
            llm = { LlmFormatter.formatMetrics(metrics) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        after?.let { put("after", it) }
        top?.let { put("top", it) }
        rootPackage?.let { put("root-package", it) }
        excludeAnnotated?.let { put("exclude-annotated", it) }
        framework?.let { put("framework", it) }
        if (noFollow) put("no-follow", null)
    }
}
