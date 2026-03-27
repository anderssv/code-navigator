package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TableFormatter
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.ClassFilter
import no.f12.codenavigator.navigation.ClassScanner
import no.f12.codenavigator.navigation.FindClassConfig
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "find-class")
@Execute(phase = LifecyclePhase.COMPILE)
class FindClassMojo : AbstractMojo() {

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
            FindClassConfig.parse(TaskRegistry.FIND_CLASS.enhanceProperties(buildPropertyMap()))
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(
                "Missing required property 'pattern'. Usage: mvn cnav:find-class -Dpattern=<regex>",
            )
        }

        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val result = ClassScanner.scan(listOf(classesDir))
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val allClasses = result.data
        val matches = ClassFilter.filter(allClasses, config.pattern)

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatClasses(matches)
            OutputFormat.LLM -> LlmFormatter.formatClasses(matches)
            OutputFormat.TEXT -> TableFormatter.format(matches)
        }
        println(OutputWrapper.wrap(output, config.format))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        pattern?.let { put("pattern", it) }
    }
}
