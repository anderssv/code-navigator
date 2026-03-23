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
abstract class ChangeCouplingTask : DefaultTask() {

    @TaskAction
    fun showCoupling() {
        val after = project.findProperty("after")?.toString()?.let { LocalDate.parse(it) }
            ?: LocalDate.now().minusYears(1)
        val minSharedRevs = project.findProperty("min-shared-revs")?.toString()?.toIntOrNull() ?: 5
        val minCoupling = project.findProperty("min-coupling")?.toString()?.toIntOrNull() ?: 30
        val maxChangesetSize = project.findProperty("max-changeset-size")?.toString()?.toIntOrNull() ?: 30
        val format = OutputFormat.from(project)

        val commits = GitLogRunner.run(project.projectDir, after, followRenames = !project.hasProperty("no-follow"))
        val pairs = ChangeCouplingBuilder.build(commits, minSharedRevs, minCoupling, maxChangesetSize)

        if (pairs.isEmpty()) {
            logger.lifecycle("No coupling found.")
            return
        }

        val output = when (format) {
            OutputFormat.JSON -> JsonFormatter.formatCoupling(pairs)
            OutputFormat.LLM -> LlmFormatter.formatCoupling(pairs)
            OutputFormat.TEXT -> ChangeCouplingFormatter.format(pairs)
        }
        logger.lifecycle(OutputWrapper.wrap(output, format))
    }
}
