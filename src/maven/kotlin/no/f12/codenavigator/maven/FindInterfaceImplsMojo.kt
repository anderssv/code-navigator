package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.InterfaceFormatter
import no.f12.codenavigator.navigation.InterfaceRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "find-interfaces")
class FindInterfaceImplsMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: Boolean? = null

    @Parameter(property = "pattern", required = true)
    private var pattern: String? = null

    @Parameter(property = "includetest", defaultValue = "false")
    private var includetest: Boolean = false

    override fun execute() {
        val pat = pattern
            ?: throw MojoFailureException("Missing required property 'pattern'. Usage: mvn cnav:find-interfaces -Dpattern=<regex>")

        val classDirectories = mutableListOf<File>()
        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }
        classDirectories.add(classesDir)

        if (includetest) {
            val testClassesDir = File(project.build.testOutputDirectory)
            if (testClassesDir.exists()) {
                classDirectories.add(testClassesDir)
            }
        }

        val outputFormat = OutputFormat.from(format, llm)
        val registry = InterfaceRegistry.build(classDirectories)
        val matchingInterfaces = registry.findInterfaces(pat)

        if (matchingInterfaces.isEmpty()) {
            println("No interfaces found matching '$pat'")
            return
        }

        val output = when (outputFormat) {
            OutputFormat.JSON -> JsonFormatter.formatInterfaces(registry, matchingInterfaces)
            OutputFormat.LLM -> LlmFormatter.formatInterfaces(registry, matchingInterfaces)
            OutputFormat.TEXT -> InterfaceFormatter.format(registry, matchingInterfaces)
        }
        println(OutputWrapper.wrap(output, outputFormat))
    }
}
