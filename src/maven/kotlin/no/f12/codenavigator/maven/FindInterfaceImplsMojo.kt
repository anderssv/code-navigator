package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.FindInterfaceImplsConfig
import no.f12.codenavigator.navigation.InterfaceFormatter
import no.f12.codenavigator.navigation.InterfaceRegistry
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "find-interfaces")
@Execute(phase = LifecyclePhase.COMPILE)
class FindInterfaceImplsMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "pattern", required = true)
    private var pattern: String? = null

    @Parameter(property = "includetest")
    private var includetest: String? = null

    override fun execute() {
        val config = try {
            FindInterfaceImplsConfig.parse(buildPropertyMap())
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(e.message)
        }

        val classDirectories = mutableListOf<File>()
        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }
        classDirectories.add(classesDir)

        if (config.includeTest) {
            val testClassesDir = File(project.build.testOutputDirectory)
            if (testClassesDir.exists()) {
                classDirectories.add(testClassesDir)
            }
        }

        val result = InterfaceRegistry.build(classDirectories)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val registry = result.data
        val matchingInterfaces = registry.findInterfaces(config.pattern)

        if (matchingInterfaces.isEmpty()) {
            println("No interfaces found matching '${config.pattern}'")
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatInterfaces(registry, matchingInterfaces)
            OutputFormat.LLM -> LlmFormatter.formatInterfaces(registry, matchingInterfaces)
            OutputFormat.TEXT -> InterfaceFormatter.format(registry, matchingInterfaces)
        }
        println(OutputWrapper.wrap(output, config.format))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        pattern?.let { put("pattern", it) }
        includetest?.let { put("includetest", it) }
    }
}
