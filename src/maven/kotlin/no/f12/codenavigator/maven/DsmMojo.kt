package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.DsmDependencyExtractor
import no.f12.codenavigator.navigation.DsmFormatter
import no.f12.codenavigator.navigation.DsmHtmlRenderer
import no.f12.codenavigator.navigation.DsmMatrixBuilder
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "dsm")
@Execute(phase = LifecyclePhase.COMPILE)
class DsmMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: Boolean? = null

    @Parameter(property = "root-package", defaultValue = "")
    private var rootPackage: String = ""

    @Parameter(property = "dsm-depth", defaultValue = "2")
    private var dsmDepth: Int = 2

    @Parameter(property = "dsm-html")
    private var dsmHtml: String? = null

    @Parameter(property = "cycles", defaultValue = "false")
    private var cycles: Boolean = false

    override fun execute() {
        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val outputFormat = OutputFormat.from(format, llm)
        val result = DsmDependencyExtractor.extract(listOf(classesDir), rootPackage)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val dependencies = result.data
        val matrix = DsmMatrixBuilder.build(dependencies, rootPackage, dsmDepth)

        val output = if (cycles) {
            when (outputFormat) {
                OutputFormat.TEXT -> DsmFormatter.formatCycles(matrix)
                OutputFormat.JSON -> JsonFormatter.formatDsmCycles(matrix)
                OutputFormat.LLM -> LlmFormatter.formatDsmCycles(matrix)
            }
        } else {
            when (outputFormat) {
                OutputFormat.TEXT -> DsmFormatter.format(matrix)
                OutputFormat.JSON -> JsonFormatter.formatDsm(matrix)
                OutputFormat.LLM -> LlmFormatter.formatDsm(matrix)
            }
        }
        println(OutputWrapper.wrap(output, outputFormat))

        if (dsmHtml != null) {
            val htmlFile = File(project.basedir, dsmHtml!!)
            htmlFile.parentFile?.mkdirs()
            htmlFile.writeText(DsmHtmlRenderer.render(matrix))
            println("DSM HTML written to: ${htmlFile.absolutePath}")
        }
    }
}
