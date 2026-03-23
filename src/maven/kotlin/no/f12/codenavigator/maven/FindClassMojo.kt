package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TableFormatter
import no.f12.codenavigator.navigation.ClassFilter
import no.f12.codenavigator.navigation.ClassScanner
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "find-class")
class FindClassMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: Boolean? = null

    @Parameter(property = "pattern", required = true)
    private var pattern: String? = null

    override fun execute() {
        val pat = pattern
            ?: throw MojoFailureException("Missing required property 'pattern'. Usage: mvn cnav:find-class -Dpattern=<regex>")

        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val outputFormat = OutputFormat.from(format, llm)
        val allClasses = ClassScanner.scan(listOf(classesDir))
        val matches = ClassFilter.filter(allClasses, pat)

        val output = when (outputFormat) {
            OutputFormat.JSON -> JsonFormatter.formatClasses(matches)
            OutputFormat.LLM -> LlmFormatter.formatClasses(matches)
            OutputFormat.TEXT -> TableFormatter.format(matches)
        }
        println(OutputWrapper.wrap(output, outputFormat))
    }
}
