package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.analysis.ChurnBuilder
import no.f12.codenavigator.analysis.ChurnConfig
import no.f12.codenavigator.analysis.ChurnFormatter
import no.f12.codenavigator.analysis.GitLogRunner

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class ChurnTask : DefaultTask() {

    @TaskAction
    fun showChurn() {
        val config = ChurnConfig.parse(
            project.buildPropertyMap(TaskRegistry.CHURN),
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
