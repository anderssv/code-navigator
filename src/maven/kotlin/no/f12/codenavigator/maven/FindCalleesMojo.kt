package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.CallDirection
import no.f12.codenavigator.navigation.CallGraphBuilder
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
    private var llm: Boolean? = null

    @Parameter(property = "method", required = true)
    private var method: String? = null

    @Parameter(property = "maxdepth", required = true)
    private var maxdepth: Int? = null

    @Parameter(property = "projectonly", defaultValue = "false")
    private var projectonly: Boolean = false

    override fun execute() {
        val methodPattern = method
            ?: throw MojoFailureException("Missing required property 'method'. Usage: mvn cnav:find-callees -Dmethod=<regex> -Dmaxdepth=3")
        val depth = maxdepth
            ?: throw MojoFailureException("Missing required property 'maxdepth'. Usage: mvn cnav:find-callees -Dmethod=<regex> -Dmaxdepth=3")

        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val outputFormat = OutputFormat.from(format, llm)
        val result = CallGraphBuilder.build(listOf(classesDir))
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = result.data
        val methods = graph.findMethods(methodPattern)

        if (methods.isEmpty()) {
            println("No methods found matching '$methodPattern'")
            return
        }

        val filter: ((MethodRef) -> Boolean)? =
            if (projectonly) graph.projectClassFilter() else null

        val trees = CallTreeBuilder.build(graph, methods, depth, CallDirection.CALLEES, filter)
        val output = when (outputFormat) {
            OutputFormat.JSON -> JsonFormatter.renderCallTrees(trees)
            OutputFormat.LLM -> LlmFormatter.renderCallTrees(trees, CallDirection.CALLEES)
            OutputFormat.TEXT -> CallTreeFormatter.renderTrees(trees, CallDirection.CALLEES)
        }
        println(OutputWrapper.wrap(output, outputFormat))
    }
}
