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
abstract class HotspotTask : DefaultTask() {

    @TaskAction
    fun showHotspots() {
        val after = project.findProperty("after")?.toString()?.let { LocalDate.parse(it) }
            ?: LocalDate.now().minusYears(1)
        val minRevs = project.findProperty("min-revs")?.toString()?.toIntOrNull() ?: 1
        val top = project.findProperty("top")?.toString()?.toIntOrNull() ?: 50
        val format = OutputFormat.from(project)

        val commits = GitLogRunner.run(project.projectDir, after, followRenames = !project.hasProperty("no-follow"))
        val hotspots = HotspotBuilder.build(commits, minRevs, top)

        if (hotspots.isEmpty()) {
            logger.lifecycle("No hotspots found.")
            return
        }

        val output = when (format) {
            OutputFormat.JSON -> JsonFormatter.formatHotspots(hotspots)
            OutputFormat.LLM -> LlmFormatter.formatHotspots(hotspots)
            OutputFormat.TEXT -> HotspotFormatter.format(hotspots)
        }
        logger.lifecycle(OutputWrapper.wrap(output, format))
    }
}
