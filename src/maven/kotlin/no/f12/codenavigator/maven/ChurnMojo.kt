package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.analysis.ChurnBuilder
import no.f12.codenavigator.analysis.ChurnConfig
import no.f12.codenavigator.analysis.ChurnFormatter
import no.f12.codenavigator.analysis.GitLogRunner
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

@Mojo(name = "churn")
class ChurnMojo : AbstractMojo() {

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
        val config = ChurnConfig.parse(TaskRegistry.CHURN.enhanceProperties(buildPropertyMap()))

        val commits = GitLogRunner.run(project.basedir, config.after, followRenames = config.followRenames)
        val churn = ChurnBuilder.build(commits, config.top)

        if (churn.isEmpty()) {
            println("No churn data found.")
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { ChurnFormatter.format(churn) },
            json = { JsonFormatter.formatChurn(churn) },
            llm = { LlmFormatter.formatChurn(churn) },
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
