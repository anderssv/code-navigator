package no.f12.codenavigator.maven

import no.f12.codenavigator.analysis.FileSizeConfig
import no.f12.codenavigator.analysis.FileSizeFormatter
import no.f12.codenavigator.analysis.FileSizeScanner
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "size")
class SizeMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "over")
    private var over: String? = null

    override fun execute() {
        val config = FileSizeConfig.parse(TaskRegistry.SIZE.enhanceProperties(buildPropertyMap()))

        val sourceRoots = (project.compileSourceRoots + project.testCompileSourceRoots)
            .map { File(it) }
            .filter { it.exists() }

        val entries = FileSizeScanner.scan(sourceRoots, config.over, config.top)

        if (entries.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No source files found."))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { FileSizeFormatter.format(entries) },
            json = { JsonFormatter.formatSize(entries) },
            llm = { LlmFormatter.formatSize(entries) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        top?.let { put("top", it) }
        over?.let { put("over", it) }
    }
}
