package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.analysis.DuplicateConfig
import no.f12.codenavigator.analysis.DuplicateFormatter
import no.f12.codenavigator.analysis.DuplicateScanner
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.types.SourceSet
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


    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "min-tokens")
    private var minTokens: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        val config = DuplicateConfig.parse(TaskRegistry.DUPLICATES.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))

        val taggedSourceRoots = project.compileSourceRoots.map { File(it as String) to SourceSet.MAIN } +
            project.testCompileSourceRoots.map { File(it as String) to SourceSet.TEST }
        val existingRoots = taggedSourceRoots.filter { (file, _) -> file.exists() }

        val groups = DuplicateScanner.scan(existingRoots, config.minTokens, config.top, config.scope)

        if (groups.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No duplicates found."))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> DuplicateFormatter.format(groups)
        OutputFormat.JSON -> DuplicateFormatter.formatJson(groups)
        OutputFormat.LLM -> DuplicateFormatter.formatLlm(groups)
    }
})
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        top?.let { put("top", it) }
        minTokens?.let { put("min-tokens", it) }
        scope?.let { put("scope", it) }
    }
}
