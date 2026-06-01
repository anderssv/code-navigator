package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.analysis.ChangeCouplingBuilder
import no.f12.codenavigator.analysis.ChangeCouplingConfig
import no.f12.codenavigator.analysis.ChangeCouplingFormatter
import no.f12.codenavigator.analysis.GitLogRunner

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class ChangeCouplingTask : CodeNavigatorTask() {

    @Option(option = "after", description = "Only consider commits after this date")
    @get:Internal
    var after: String? = null

    @Option(option = "min-shared-revs", description = "Min shared commits")
    @get:Internal
    var minSharedRevs: String? = null

    @Option(option = "min-coupling", description = "Min coupling degree %")
    @get:Internal
    var minCoupling: String? = null

    @Option(option = "max-changeset-size", description = "Skip commits touching more files")
    @get:Internal
    var maxChangesetSize: String? = null

    @Option(option = "top", description = "Max results")
    @get:Internal
    var top: String? = null

    @Option(option = "no-follow", description = "Disable git rename tracking")
    @get:Internal
    var noFollow: Boolean = false

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        after?.let { put("after", it) }
        minSharedRevs?.let { put("min-shared-revs", it) }
        minCoupling?.let { put("min-coupling", it) }
        maxChangesetSize?.let { put("max-changeset-size", it) }
        top?.let { put("top", it) }
        if (noFollow) put("no-follow", "true")
    }

    @TaskAction
    fun showCoupling() {
        val config = ChangeCouplingConfig.parse(
            TaskRegistry.COUPLING.enhanceProperties(buildOptionsMap()),
        )

        val commits = GitLogRunner.run(project.projectDir, config.after, followRenames = config.followRenames)
        val pairs = ChangeCouplingBuilder.build(commits, config.minSharedRevs, config.minCoupling, config.maxChangesetSize, config.top)

        if (pairs.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No coupling found."))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { ChangeCouplingFormatter.format(pairs) },
            json = { JsonFormatter.formatCoupling(pairs) },
            llm = { LlmFormatter.formatCoupling(pairs) },
        ))
    }
}
