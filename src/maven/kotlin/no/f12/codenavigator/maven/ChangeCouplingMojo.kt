package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.analysis.ChangeCouplingBuilder
import no.f12.codenavigator.analysis.ChangeCouplingFormatter
import no.f12.codenavigator.analysis.GitLogRunner
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.time.LocalDate

@Mojo(name = "coupling")
class ChangeCouplingMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: Boolean? = null

    @Parameter(property = "after")
    private var after: String? = null

    @Parameter(property = "min-shared-revs", defaultValue = "5")
    private var minSharedRevs: Int = 5

    @Parameter(property = "min-coupling", defaultValue = "30")
    private var minCoupling: Int = 30

    @Parameter(property = "max-changeset-size", defaultValue = "30")
    private var maxChangesetSize: Int = 30

    @Parameter(property = "no-follow")
    private var noFollow: Boolean = false

    override fun execute() {
        val afterDate = after?.let { LocalDate.parse(it) } ?: LocalDate.now().minusYears(1)
        val outputFormat = OutputFormat.from(format, llm)

        val commits = GitLogRunner.run(project.basedir, afterDate, followRenames = !noFollow)
        val pairs = ChangeCouplingBuilder.build(commits, minSharedRevs, minCoupling, maxChangesetSize)

        if (pairs.isEmpty()) {
            println("No coupling found.")
            return
        }

        val output = when (outputFormat) {
            OutputFormat.JSON -> JsonFormatter.formatCoupling(pairs)
            OutputFormat.LLM -> LlmFormatter.formatCoupling(pairs)
            OutputFormat.TEXT -> ChangeCouplingFormatter.format(pairs)
        }
        println(OutputWrapper.wrap(output, outputFormat))
    }
}
