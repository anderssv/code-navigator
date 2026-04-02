package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.SourceSet
import no.f12.codenavigator.navigation.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.complexity.ClassComplexityAnalyzer
import no.f12.codenavigator.navigation.complexity.ComplexityConfig
import no.f12.codenavigator.navigation.complexity.ComplexityFormatter
import no.f12.codenavigator.navigation.LambdaCollapser
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "complexity")
@Execute(phase = LifecyclePhase.COMPILE)
class ComplexityMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "pattern")
    private var pattern: String? = null

    @Parameter(property = "project-only")
    private var projectOnly: String? = null

    @Parameter(property = "detail")
    private var detail: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "collapse-lambdas")
    private var collapseLambdas: String? = null

    @Parameter(property = "prod-only")
    private var prodOnly: String? = null

    @Parameter(property = "test-only")
    private var testOnly: String? = null

    override fun execute() {
        val taggedDirs = project.taggedClassDirectories()
        if (taggedDirs.isEmpty()) {
            log.warn("Classes directory does not exist: ${File(project.build.outputDirectory)} — run 'mvn compile' first.")
            return
        }

        val config = try {
            ComplexityConfig.parse(TaskRegistry.COMPLEXITY.enhanceProperties(buildPropertyMap()))
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(e.message)
        }

        val result = CallGraphCache.getOrBuildTagged(File(project.build.directory, "cnav/call-graph.cache"), taggedDirs)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = result.data

        val rawResults = ClassComplexityAnalyzer.analyze(
            graph = graph,
            classPattern = config.classPattern,
            projectOnly = config.projectOnly,
        )
        val collapsed = if (config.collapseLambdas) LambdaCollapser.collapseComplexity(rawResults) else rawResults
        val filtered = when {
            config.prodOnly -> collapsed.filter { graph.sourceSetOf(it.className) == SourceSet.MAIN }
            config.testOnly -> collapsed.filter { graph.sourceSetOf(it.className) == SourceSet.TEST }
            else -> collapsed
        }
        val truncated = filtered.take(config.top)

        if (truncated.isEmpty()) {
            println("No matching classes found.")
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { ComplexityFormatter.format(truncated) },
            json = { JsonFormatter.formatComplexity(truncated) },
            llm = { LlmFormatter.formatComplexity(truncated) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        pattern?.let { put("pattern", it) }
        projectOnly?.let { put("project-only", it) }
        detail?.let { put("detail", it) }
        top?.let { put("top", it) }
        collapseLambdas?.let { put("collapse-lambdas", it) }
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        prodOnly?.let { put("prod-only", it) }
        testOnly?.let { put("test-only", it) }
    }
}
