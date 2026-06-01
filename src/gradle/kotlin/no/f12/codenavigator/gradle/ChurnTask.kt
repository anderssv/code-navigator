package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.analysis.ChurnBuilder
import no.f12.codenavigator.analysis.ChurnConfig
import no.f12.codenavigator.analysis.ChurnFormatter
import no.f12.codenavigator.analysis.GitLogRunner

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class ChurnTask : CodeNavigatorTask() {

    @Option(option = "after", description = "Only consider commits after this date")
    @get:Internal
    var after: String? = null

    @Option(option = "top", description = "Max results")
    @get:Internal
    var top: String? = null

    @Option(option = "no-follow", description = "Disable git rename tracking")
    @get:Internal
    var noFollow: Boolean = false

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        after?.let { put("after", it) }
        top?.let { put("top", it) }
        if (noFollow) put("no-follow", "true")
    }

    @TaskAction
    fun showChurn() {
        val config = ChurnConfig.parse(
            TaskRegistry.CHURN.enhanceProperties(buildOptionsMap()),
        )

        val commits = GitLogRunner.run(project.projectDir, config.after, followRenames = config.followRenames)
        val churn = ChurnBuilder.build(commits, config.top)

        if (churn.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No churn data found."))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { ChurnFormatter.format(churn) },
            json = { JsonFormatter.formatChurn(churn) },
            llm = { LlmFormatter.formatChurn(churn) },
        ))
    }
}
