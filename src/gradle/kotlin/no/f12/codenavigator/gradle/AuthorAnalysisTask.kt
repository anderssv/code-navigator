package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.analysis.AuthorAnalysisBuilder
import no.f12.codenavigator.analysis.AuthorAnalysisConfig
import no.f12.codenavigator.analysis.AuthorAnalysisFormatter
import no.f12.codenavigator.analysis.GitLogRunner

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class AuthorAnalysisTask : CodeNavigatorTask() {

    @Option(option = "after", description = "Only consider commits after this date")
    @get:Internal
    var after: String? = null

    @Option(option = "min-revs", description = "Min revisions to include")
    @get:Internal
    var minRevs: String? = null

    @Option(option = "top", description = "Max results")
    @get:Internal
    var top: String? = null

    @Option(option = "no-follow", description = "Disable git rename tracking")
    @get:Internal
    var noFollow: Boolean = false

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        after?.let { put("after", it) }
        minRevs?.let { put("min-revs", it) }
        top?.let { put("top", it) }
        if (noFollow) put("no-follow", "true")
    }

    @TaskAction
    fun showAuthors() {
        val config = AuthorAnalysisConfig.parse(
            TaskRegistry.AUTHORS.enhanceProperties(buildOptionsMap()),
        )

        val commits = GitLogRunner.run(project.projectDir, config.after, followRenames = config.followRenames)
        val modules = AuthorAnalysisBuilder.build(commits, config.minRevs, config.top)

        if (modules.isEmpty()) {
            logger.quiet(OutputWrapper.emptyResult(config.format, "No files found."))
            return
        }

        logger.quiet(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> AuthorAnalysisFormatter.format(modules)
        OutputFormat.JSON -> AuthorAnalysisFormatter.formatJson(modules)
        OutputFormat.LLM -> AuthorAnalysisFormatter.formatLlm(modules)
    }
})
    }
}
