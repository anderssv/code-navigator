package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.analysis.AuthorAnalysisBuilder
import no.f12.codenavigator.analysis.AuthorAnalysisConfig
import no.f12.codenavigator.analysis.AuthorAnalysisFormatter
import no.f12.codenavigator.analysis.GitLogRunner

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class AuthorAnalysisTask : DefaultTask() {

    @TaskAction
    fun showAuthors() {
        val config = AuthorAnalysisConfig.parse(
            project.buildPropertyMap(TaskRegistry.AUTHORS),
        )

        val commits = GitLogRunner.run(project.projectDir, config.after, followRenames = config.followRenames)
        val modules = AuthorAnalysisBuilder.build(commits, config.minRevs, config.top)

        if (modules.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No files found."))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { AuthorAnalysisFormatter.format(modules) },
            json = { JsonFormatter.formatAuthors(modules) },
            llm = { LlmFormatter.formatAuthors(modules) },
        ))
    }
}
