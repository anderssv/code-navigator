package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.CallGraphBuilder
import no.f12.codenavigator.navigation.DeadCodeConfig
import no.f12.codenavigator.navigation.DeadCodeFinder
import no.f12.codenavigator.navigation.DeadCodeFormatter
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "dead")
@Execute(phase = LifecyclePhase.COMPILE)
class DeadCodeMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "filter")
    private var filter: String? = null

    @Parameter(property = "exclude")
    private var exclude: String? = null

    override fun execute() {
        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val config = DeadCodeConfig.parse(buildPropertyMap())

        val result = CallGraphBuilder.build(listOf(classesDir))
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = result.data

        val dead = DeadCodeFinder.find(graph, filter = config.filter, exclude = config.exclude)

        if (dead.isEmpty()) {
            println("No potential dead code found.")
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatDead(dead)
            OutputFormat.LLM -> LlmFormatter.formatDead(dead)
            OutputFormat.TEXT -> DeadCodeFormatter.format(dead)
        }
        println(OutputWrapper.wrap(output, config.format))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        filter?.let { put("filter", it) }
        exclude?.let { put("exclude", it) }
    }
}
