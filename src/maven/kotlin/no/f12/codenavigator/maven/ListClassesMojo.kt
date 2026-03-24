package no.f12.codenavigator.maven

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TableFormatter
import no.f12.codenavigator.navigation.ClassScanner
import no.f12.codenavigator.navigation.SkippedFileReporter
import java.io.File

@Mojo(name = "list-classes")
@Execute(phase = LifecyclePhase.COMPILE)
class ListClassesMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: Boolean? = null

    override fun execute() {
        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val outputFormat = OutputFormat.from(format, llm)
        val result = ClassScanner.scan(listOf(classesDir))
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val classes = result.data

        val output = when (outputFormat) {
            OutputFormat.JSON -> JsonFormatter.formatClasses(classes)
            OutputFormat.LLM -> LlmFormatter.formatClasses(classes)
            OutputFormat.TEXT -> TableFormatter.format(classes)
        }

        println(OutputWrapper.wrap(output, outputFormat))
    }
}
