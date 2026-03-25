package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.ClassDetailFormatter
import no.f12.codenavigator.navigation.ClassDetailScanner
import no.f12.codenavigator.navigation.FindClassDetailConfig
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "class-detail")
@Execute(phase = LifecyclePhase.COMPILE)
class ClassDetailMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "pattern")
    private var pattern: String? = null

    override fun execute() {
        val config = try {
            FindClassDetailConfig.parse(buildPropertyMap())
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(
                "Missing required property 'pattern'. Usage: mvn cnav:class-detail -Dpattern=<regex>",
            )
        }

        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val result = ClassDetailScanner.scan(listOf(classesDir), config.pattern)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val matchingDetails = result.data

        if (matchingDetails.isEmpty()) {
            println("No classes found matching '${config.pattern}'")
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatClassDetails(matchingDetails)
            OutputFormat.LLM -> LlmFormatter.formatClassDetails(matchingDetails)
            OutputFormat.TEXT -> ClassDetailFormatter.format(matchingDetails)
        }
        println(OutputWrapper.wrap(output, config.format))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        pattern?.let { put("pattern", it) }
    }
}
