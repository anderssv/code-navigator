package no.f12.codenavigator.maven

import no.f12.codenavigator.analysis.DuplicateConfig
import no.f12.codenavigator.analysis.DuplicateFormatter
import no.f12.codenavigator.analysis.DuplicateScanner
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.core.SourceSet
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "duplicates")
class DuplicatesMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "min-tokens")
    private var minTokens: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        val config = DuplicateConfig.parse(TaskRegistry.DUPLICATES.enhanceProperties(buildPropertyMap()))

        val taggedSourceRoots = project.compileSourceRoots.map { File(it as String) to SourceSet.MAIN } +
            project.testCompileSourceRoots.map { File(it as String) to SourceSet.TEST }
        val existingRoots = taggedSourceRoots.filter { (file, _) -> file.exists() }

        val groups = DuplicateScanner.scan(existingRoots, config.minTokens, config.top, config.scope)

        if (groups.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No duplicates found."))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { DuplicateFormatter.format(groups) },
            json = { JsonFormatter.formatDuplicates(groups) },
            llm = { LlmFormatter.formatDuplicates(groups) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        top?.let { put("top", it) }
        minTokens?.let { put("min-tokens", it) }
        scope?.let { put("scope", it) }
    }
}
