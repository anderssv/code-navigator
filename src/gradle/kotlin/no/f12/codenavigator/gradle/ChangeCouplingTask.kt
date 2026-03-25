package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.analysis.ChangeCouplingBuilder
import no.f12.codenavigator.analysis.ChangeCouplingConfig
import no.f12.codenavigator.analysis.ChangeCouplingFormatter
import no.f12.codenavigator.analysis.GitLogRunner

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class ChangeCouplingTask : DefaultTask() {

    @TaskAction
    fun showCoupling() {
        val config = ChangeCouplingConfig.parse(
            project.buildPropertyMap(
                propertyNames = listOf("after", "min-shared-revs", "min-coupling", "max-changeset-size", "format", "llm"),
                flagNames = listOf("no-follow"),
            ),
        )

        val commits = GitLogRunner.run(project.projectDir, config.after, followRenames = config.followRenames)
        val pairs = ChangeCouplingBuilder.build(commits, config.minSharedRevs, config.minCoupling, config.maxChangesetSize)

        if (pairs.isEmpty()) {
            logger.lifecycle("No coupling found.")
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatCoupling(pairs)
            OutputFormat.LLM -> LlmFormatter.formatCoupling(pairs)
            OutputFormat.TEXT -> ChangeCouplingFormatter.format(pairs)
        }
        logger.lifecycle(OutputWrapper.wrap(output, config.format))
    }
}
