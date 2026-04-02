package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.analysis.GitLogRunner
import no.f12.codenavigator.analysis.HotspotBuilder
import no.f12.codenavigator.analysis.PackageVolatilityBuilder
import no.f12.codenavigator.analysis.PackageVolatilityFormatter
import no.f12.codenavigator.analysis.VolatilityConfig

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class PackageVolatilityTask : DefaultTask() {

    @TaskAction
    fun showVolatility() {
        val config = VolatilityConfig.parse(
            project.buildPropertyMap(TaskRegistry.VOLATILITY),
        )

        val commits = GitLogRunner.run(project.projectDir, config.after, followRenames = config.followRenames)
        val hotspots = HotspotBuilder.build(commits, config.minRevs)
        val result = PackageVolatilityBuilder.build(hotspots, config.top)

        if (result.entries.isEmpty()) {
            val hints = PackageVolatilityFormatter.noResultsHints()
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No package volatility data found.", hints))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { PackageVolatilityFormatter.format(result) },
            json = { JsonFormatter.formatVolatility(result) },
            llm = { LlmFormatter.formatVolatility(result) },
        ))
    }
}
