package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.context.ContextConfig
import no.f12.codenavigator.navigation.context.ContextFormatter
import no.f12.codenavigator.navigation.context.ContextOrchestrator
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "context")
@Execute(phase = LifecyclePhase.COMPILE)
class ContextMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "pattern")
    private var pattern: String? = null

    @Parameter(property = "maxdepth")
    private var maxdepth: String? = null

    @Parameter(property = "project-only")
    private var projectOnly: String? = null

    @Parameter(property = "filter-synthetic")
    private var filterSynthetic: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = try {
            ContextConfig.parse(TaskRegistry.CONTEXT.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(
                "Missing required property 'pattern'. Usage: mvn cnav:context -Dpattern=<regex>",
            )
        }

        val taggedDirs = project.taggedClassDirectories()
        if (taggedDirs.isEmpty()) {
            log.warn("Classes directory does not exist: ${File(project.build.outputDirectory)} — run 'mvn compile' first.")
            return
        }

        val cacheDir = File(project.build.directory, "cnav")
        val output = ContextOrchestrator.run(config, taggedDirs, cacheDir)

        output.skippedFileWarnings.forEach { log.warn(it) }

        if (output.results.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No classes found matching '${config.pattern}'"))
            return
        }

        println(
            OutputWrapper.formatAndWrap(config.format) { format ->
                when (format) {
                    OutputFormat.TEXT, OutputFormat.DIFF -> output.results.joinToString("\n\n") { ContextFormatter.format(it) }
                    OutputFormat.JSON -> "[${output.results.joinToString(",") { JsonFormatter.formatContext(it) }}]"
                    OutputFormat.LLM -> output.results.joinToString("\n\n") { LlmFormatter.formatContext(it) }
                }
            },
        )
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        pattern?.let { put("pattern", it) }
        maxdepth?.let { put("maxdepth", it) }
        projectOnly?.let { put("project-only", it) }
        filterSynthetic?.let { put("filter-synthetic", it) }
        scope?.let { put("scope", it) }
    }
}
