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

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class PackageVolatilityTask : CodeNavigatorTask() {

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
    fun showVolatility() {
        val config = VolatilityConfig.parse(
            TaskRegistry.VOLATILITY.enhanceProperties(buildOptionsMap()),
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
