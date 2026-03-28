package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.analysis.CodeAgeBuilder
import no.f12.codenavigator.analysis.CodeAgeConfig
import no.f12.codenavigator.analysis.CodeAgeFormatter
import no.f12.codenavigator.analysis.GitLogRunner

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.time.LocalDate

@DisableCachingByDefault(because = "Produces console output only")
abstract class CodeAgeTask : DefaultTask() {

    @TaskAction
    fun showAge() {
        val config = CodeAgeConfig.parse(
            project.buildPropertyMap(TaskRegistry.CODE_AGE),
        )

        val commits = GitLogRunner.run(project.projectDir, config.after, followRenames = config.followRenames)
        val ages = CodeAgeBuilder.build(commits, LocalDate.now(), config.top)

        if (ages.isEmpty()) {
            logger.lifecycle("No files found.")
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatAge(ages)
            OutputFormat.LLM -> LlmFormatter.formatAge(ages)
            OutputFormat.TEXT -> CodeAgeFormatter.format(ages)
        }
        logger.lifecycle(OutputWrapper.wrap(output, config.format))
    }
}
