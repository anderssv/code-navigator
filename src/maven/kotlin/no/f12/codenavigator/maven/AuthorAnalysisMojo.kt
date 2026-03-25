package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
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

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "after")
    private var after: String? = null

    @Parameter(property = "min-revs")
    private var minRevs: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "no-follow")
    private var noFollow: Boolean = false

    override fun execute() {
        val config = AuthorAnalysisConfig.parse(buildPropertyMap())

        val commits = GitLogRunner.run(project.basedir, config.after, followRenames = config.followRenames)
        val modules = AuthorAnalysisBuilder.build(commits, config.minRevs, config.top)

        if (modules.isEmpty()) {
            println("No files found.")
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatAuthors(modules)
            OutputFormat.LLM -> LlmFormatter.formatAuthors(modules)
            OutputFormat.TEXT -> AuthorAnalysisFormatter.format(modules)
        }
        println(OutputWrapper.wrap(output, config.format))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        after?.let { put("after", it) }
        minRevs?.let { put("min-revs", it) }
        top?.let { put("top", it) }
        if (noFollow) put("no-follow", null)
    }
}
