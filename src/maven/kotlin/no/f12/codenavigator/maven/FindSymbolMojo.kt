package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.SymbolScanner
import no.f12.codenavigator.navigation.SymbolFilter
import no.f12.codenavigator.navigation.SymbolTableFormatter
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "find-symbol")
@Execute(phase = LifecyclePhase.COMPILE)
class FindSymbolMojo : AbstractMojo() {

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
            ?: throw MojoFailureException("Missing required property 'pattern'. Usage: mvn cnav:find-symbol -Dpattern=<regex>")

        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val outputFormat = OutputFormat.from(format, llm)
        val result = SymbolScanner.scan(listOf(classesDir))
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val allSymbols = result.data
        val matches = SymbolFilter.filter(allSymbols, pat)

        val output = when (outputFormat) {
            OutputFormat.JSON -> JsonFormatter.formatSymbols(matches)
            OutputFormat.LLM -> LlmFormatter.formatSymbols(matches)
            OutputFormat.TEXT -> SymbolTableFormatter.format(matches)
        }
        println(OutputWrapper.wrap(output, outputFormat))
    }
}
