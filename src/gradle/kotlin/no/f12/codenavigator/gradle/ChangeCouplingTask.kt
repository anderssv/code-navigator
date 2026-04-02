package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
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
            project.buildPropertyMap(TaskRegistry.COUPLING),
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
