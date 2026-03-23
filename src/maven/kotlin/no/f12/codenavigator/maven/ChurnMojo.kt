package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.analysis.ChurnBuilder
import no.f12.codenavigator.analysis.ChurnFormatter
import no.f12.codenavigator.analysis.GitLogRunner
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.time.LocalDate

@Mojo(name = "churn")
class ChurnMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: Boolean? = null

    @Parameter(property = "after")
    private var after: String? = null

    @Parameter(property = "top", defaultValue = "50")
    private var top: Int = 50

    @Parameter(property = "no-follow")
    private var noFollow: Boolean = false

    override fun execute() {
        val afterDate = after?.let { LocalDate.parse(it) } ?: LocalDate.now().minusYears(1)
        val outputFormat = OutputFormat.from(format, llm)

        val commits = GitLogRunner.run(project.basedir, afterDate, followRenames = !noFollow)
        val churn = ChurnBuilder.build(commits, top)

        if (churn.isEmpty()) {
            println("No churn data found.")
            return
        }

        val output = when (outputFormat) {
            OutputFormat.JSON -> JsonFormatter.formatChurn(churn)
            OutputFormat.LLM -> LlmFormatter.formatChurn(churn)
            OutputFormat.TEXT -> ChurnFormatter.format(churn)
        }
        println(OutputWrapper.wrap(output, outputFormat))
    }
}
