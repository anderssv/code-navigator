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
abstract class ChurnTask : DefaultTask() {

    @TaskAction
    fun showChurn() {
        val after = project.findProperty("after")?.toString()?.let { LocalDate.parse(it) }
            ?: LocalDate.now().minusYears(1)
        val top = project.findProperty("top")?.toString()?.toIntOrNull() ?: 50
        val format = OutputFormat.from(project)

        val commits = GitLogRunner.run(project.projectDir, after, followRenames = !project.hasProperty("no-follow"))
        val churn = ChurnBuilder.build(commits, top)

        if (churn.isEmpty()) {
            logger.lifecycle("No churn data found.")
            return
        }

        val output = when (format) {
            OutputFormat.JSON -> JsonFormatter.formatChurn(churn)
            OutputFormat.LLM -> LlmFormatter.formatChurn(churn)
            OutputFormat.TEXT -> ChurnFormatter.format(churn)
        }
        logger.lifecycle(OutputWrapper.wrap(output, format))
    }
}
