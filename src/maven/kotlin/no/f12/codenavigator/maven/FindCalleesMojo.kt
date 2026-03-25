package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.CallDirection
import no.f12.codenavigator.navigation.CallGraphBuilder
import no.f12.codenavigator.navigation.CallGraphConfig
import no.f12.codenavigator.navigation.CallTreeBuilder
import no.f12.codenavigator.navigation.CallTreeFormatter
import no.f12.codenavigator.navigation.MethodRef
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "find-callees")
@Execute(phase = LifecyclePhase.COMPILE)
class FindCalleesMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "method")
    private var method: String? = null

    @Parameter(property = "maxdepth")
    private var maxdepth: String? = null

    @Parameter(property = "projectonly")
    private var projectonly: String? = null

    override fun execute() {
        val config = try {
            CallGraphConfig.parse(buildPropertyMap())
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(
                "Missing required property. Usage: mvn cnav:find-callees -Dmethod=<regex> -Dmaxdepth=3",
            )
        }

        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val result = CallGraphBuilder.build(listOf(classesDir))
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = result.data
        val methods = graph.findMethods(config.method)

        if (methods.isEmpty()) {
            println("No methods found matching '${config.method}'")
            return
        }

        val filter: ((MethodRef) -> Boolean)? =
            if (config.projectOnly) graph.projectClassFilter() else null

        val trees = CallTreeBuilder.build(graph, methods, config.maxDepth, CallDirection.CALLEES, filter)
        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.renderCallTrees(trees)
            OutputFormat.LLM -> LlmFormatter.renderCallTrees(trees, CallDirection.CALLEES)
            OutputFormat.TEXT -> CallTreeFormatter.renderTrees(trees, CallDirection.CALLEES)
        }
        println(OutputWrapper.wrap(output, config.format))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        method?.let { put("method", it) }
        maxdepth?.let { put("maxdepth", it) }
        projectonly?.let { put("projectonly", it) }
    }
}
