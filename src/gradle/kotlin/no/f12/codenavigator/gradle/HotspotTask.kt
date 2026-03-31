package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.analysis.GitLogRunner
import no.f12.codenavigator.analysis.HotspotBuilder
import no.f12.codenavigator.analysis.HotspotConfig
import no.f12.codenavigator.analysis.HotspotFormatter

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class HotspotTask : DefaultTask() {

    @TaskAction
    fun showHotspots() {
        val config = HotspotConfig.parse(
            project.buildPropertyMap(TaskRegistry.HOTSPOTS),
        )

        val commits = GitLogRunner.run(project.projectDir, config.after, followRenames = config.followRenames)
        val hotspots = HotspotBuilder.build(commits, config.minRevs, config.top)

        if (hotspots.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No hotspots found."))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { HotspotFormatter.format(hotspots) },
            json = { JsonFormatter.formatHotspots(hotspots) },
            llm = { LlmFormatter.formatHotspots(hotspots) },
        ))
    }
}
