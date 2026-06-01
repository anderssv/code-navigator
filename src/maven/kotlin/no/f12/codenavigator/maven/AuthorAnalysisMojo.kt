package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.analysis.AuthorAnalysisBuilder
import no.f12.codenavigator.analysis.AuthorAnalysisConfig
import no.f12.codenavigator.analysis.AuthorAnalysisFormatter
import no.f12.codenavigator.analysis.GitLogRunner
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

@Mojo(name = "authors")
class AuthorAnalysisMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "after")
    private var after: String? = null

    @Parameter(property = "min-revs")
    private var minRevs: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "no-follow")
    private var noFollow: Boolean = false

    override fun execute() {
        val config = AuthorAnalysisConfig.parse(TaskRegistry.AUTHORS.enhanceProperties(buildPropertyMap()))

        val commits = GitLogRunner.run(project.basedir, config.after, followRenames = config.followRenames)
        val modules = AuthorAnalysisBuilder.build(commits, config.minRevs, config.top)

        if (modules.isEmpty()) {
            println("No files found.")
            return
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> AuthorAnalysisFormatter.format(modules)
        OutputFormat.JSON -> JsonFormatter.formatAuthors(modules)
        OutputFormat.LLM -> LlmFormatter.formatAuthors(modules)
    }
})
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        after?.let { put("after", it) }
        minRevs?.let { put("min-revs", it) }
        top?.let { put("top", it) }
        if (noFollow) put("no-follow", null)
    }
}
