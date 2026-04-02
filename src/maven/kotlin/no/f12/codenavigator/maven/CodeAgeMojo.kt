package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.analysis.CodeAgeBuilder
import no.f12.codenavigator.analysis.CodeAgeConfig
import no.f12.codenavigator.analysis.CodeAgeFormatter
import no.f12.codenavigator.analysis.GitLogRunner
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.time.LocalDate

@Mojo(name = "code-age")
class CodeAgeMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "after")
    private var after: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "no-follow")
    private var noFollow: Boolean = false

    override fun execute() {
        val config = CodeAgeConfig.parse(TaskRegistry.CODE_AGE.enhanceProperties(buildPropertyMap()))

        val commits = GitLogRunner.run(project.basedir, config.after, followRenames = config.followRenames)
        val ages = CodeAgeBuilder.build(commits, LocalDate.now(), config.top)

        if (ages.isEmpty()) {
            println("No files found.")
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { CodeAgeFormatter.format(ages) },
            json = { JsonFormatter.formatAge(ages) },
            llm = { LlmFormatter.formatAge(ages) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        after?.let { put("after", it) }
        top?.let { put("top", it) }
        if (noFollow) put("no-follow", null)
    }
}
