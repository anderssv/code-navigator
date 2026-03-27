package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.SkippedFileReporter
import no.f12.codenavigator.navigation.StringConstantConfig
import no.f12.codenavigator.navigation.StringConstantFormatter
import no.f12.codenavigator.navigation.StringConstantScanner
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "find-string-constant")
@Execute(phase = LifecyclePhase.COMPILE)
class StringConstantMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "pattern")
    private var pattern: String? = null

    override fun execute() {
        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val config = StringConstantConfig.parse(
            TaskRegistry.FIND_STRING_CONSTANT.enhanceProperties(buildPropertyMap()),
        )

        val result = StringConstantScanner.scan(listOf(classesDir), config.pattern)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val matches = result.data

        if (matches.isEmpty()) {
            println("No string constants matching '${config.pattern.pattern}' found.")
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatStringConstants(matches)
            OutputFormat.LLM -> LlmFormatter.formatStringConstants(matches)
            OutputFormat.TEXT -> StringConstantFormatter.format(matches)
        }
        println(OutputWrapper.wrap(output, config.format))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        pattern?.let { put("pattern", it) }
    }
}
