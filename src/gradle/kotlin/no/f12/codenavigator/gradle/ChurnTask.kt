package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
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
            project.buildPropertyMap(
                propertyNames = listOf("after", "top", "format", "llm"),
                flagNames = listOf("no-follow"),
            ),
        )

        val commits = GitLogRunner.run(project.projectDir, config.after, followRenames = config.followRenames)
        val churn = ChurnBuilder.build(commits, config.top)

        if (churn.isEmpty()) {
            logger.lifecycle("No churn data found.")
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatChurn(churn)
            OutputFormat.LLM -> LlmFormatter.formatChurn(churn)
            OutputFormat.TEXT -> ChurnFormatter.format(churn)
        }
        logger.lifecycle(OutputWrapper.wrap(output, config.format))
    }
}
