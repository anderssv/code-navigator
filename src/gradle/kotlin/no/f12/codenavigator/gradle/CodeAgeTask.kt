package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.analysis.CodeAgeBuilder
import no.f12.codenavigator.analysis.CodeAgeConfig
import no.f12.codenavigator.analysis.CodeAgeFormatter
import no.f12.codenavigator.analysis.GitLogRunner

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.time.LocalDate

@DisableCachingByDefault(because = "Produces console output only")
abstract class CodeAgeTask : CodeNavigatorTask() {

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
    fun showAge() {
        val config = CodeAgeConfig.parse(
            TaskRegistry.CODE_AGE.enhanceProperties(buildOptionsMap()),
        )

        val commits = GitLogRunner.run(project.projectDir, config.after, followRenames = config.followRenames)
        val ages = CodeAgeBuilder.build(commits, LocalDate.now(), config.top)

        if (ages.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No files found."))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> CodeAgeFormatter.format(ages)
        OutputFormat.JSON -> CodeAgeFormatter.formatJson(ages)
        OutputFormat.LLM -> CodeAgeFormatter.formatLlm(ages)
    }
})
    }
}
