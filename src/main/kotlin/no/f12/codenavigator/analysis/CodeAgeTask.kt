package no.f12.codenavigator.analysis

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.time.LocalDate

@DisableCachingByDefault(because = "Produces console output only")
abstract class CodeAgeTask : DefaultTask() {

    @TaskAction
    fun showAge() {
        val after = project.findProperty("after")?.toString()?.let { LocalDate.parse(it) }
            ?: LocalDate.now().minusYears(1)
        val top = project.findProperty("top")?.toString()?.toIntOrNull() ?: 50
        val format = OutputFormat.from(project)

        val commits = GitLogRunner.run(project.projectDir, after, followRenames = !project.hasProperty("no-follow"))
        val ages = CodeAgeBuilder.build(commits, LocalDate.now(), top)

        if (ages.isEmpty()) {
            logger.lifecycle("No files found.")
            return
        }

        val output = when (format) {
            OutputFormat.JSON -> JsonFormatter.formatAge(ages)
            OutputFormat.LLM -> LlmFormatter.formatAge(ages)
            OutputFormat.TEXT -> CodeAgeFormatter.format(ages)
        }
        logger.lifecycle(OutputWrapper.wrap(output, format))
    }
}
