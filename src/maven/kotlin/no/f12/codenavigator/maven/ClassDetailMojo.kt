package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.ClassDetailFormatter
import no.f12.codenavigator.navigation.ClassDetailScanner
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "class-detail")
class ClassDetailMojo : AbstractMojo() {

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
            ?: throw MojoFailureException("Missing required property 'pattern'. Usage: mvn cnav:class-detail -Dpattern=<regex>")

        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val outputFormat = OutputFormat.from(format, llm)
        val matchingDetails = ClassDetailScanner.scan(listOf(classesDir), pat)

        if (matchingDetails.isEmpty()) {
            println("No classes found matching '$pat'")
            return
        }

        val output = when (outputFormat) {
            OutputFormat.JSON -> JsonFormatter.formatClassDetails(matchingDetails)
            OutputFormat.LLM -> LlmFormatter.formatClassDetails(matchingDetails)
            OutputFormat.TEXT -> ClassDetailFormatter.format(matchingDetails)
        }
        println(OutputWrapper.wrap(output, outputFormat))
    }
}
