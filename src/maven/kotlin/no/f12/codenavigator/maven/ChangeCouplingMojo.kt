package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.analysis.ChangeCouplingBuilder
import no.f12.codenavigator.analysis.ChangeCouplingConfig
import no.f12.codenavigator.analysis.ChangeCouplingFormatter
import no.f12.codenavigator.analysis.GitLogRunner
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

@Mojo(name = "coupling")
class ChangeCouplingMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "after")
    private var after: String? = null

    @Parameter(property = "min-shared-revs")
    private var minSharedRevs: String? = null

    @Parameter(property = "min-coupling")
    private var minCoupling: String? = null

    @Parameter(property = "max-changeset-size")
    private var maxChangesetSize: String? = null

    @Parameter(property = "no-follow")
    private var noFollow: Boolean = false

    @Parameter(property = "top")
    private var top: String? = null

    override fun execute() {
        val config = ChangeCouplingConfig.parse(TaskRegistry.COUPLING.enhanceProperties(buildPropertyMap()))

        val commits = GitLogRunner.run(project.basedir, config.after, followRenames = config.followRenames)
        val pairs = ChangeCouplingBuilder.build(commits, config.minSharedRevs, config.minCoupling, config.maxChangesetSize, config.top)

        if (pairs.isEmpty()) {
            println("No coupling found.")
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { ChangeCouplingFormatter.format(pairs) },
            json = { JsonFormatter.formatCoupling(pairs) },
            llm = { LlmFormatter.formatCoupling(pairs) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        after?.let { put("after", it) }
        minSharedRevs?.let { put("min-shared-revs", it) }
        minCoupling?.let { put("min-coupling", it) }
        maxChangesetSize?.let { put("max-changeset-size", it) }
        if (noFollow) put("no-follow", null)
        top?.let { put("top", it) }
    }
}
