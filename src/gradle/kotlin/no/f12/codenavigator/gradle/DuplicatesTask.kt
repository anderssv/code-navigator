package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.analysis.DuplicateConfig
import no.f12.codenavigator.analysis.DuplicateFormatter
import no.f12.codenavigator.analysis.DuplicateScanner
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class DuplicatesTask : CodeNavigatorTask() {

    @Option(option = "top", description = "Max results")
    @get:Internal
    var top: String? = null

    @Option(option = "min-tokens", description = "Minimum duplicate token sequence length")
    @get:Internal
    var minTokens: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        top?.let { put("top", it) }
        minTokens?.let { put("min-tokens", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun findDuplicates() {
        val config = DuplicateConfig.parse(
            TaskRegistry.DUPLICATES.enhanceProperties(buildOptionsMap()),
        )

        val taggedSourceRoots = project.taggedSourceDirectories()
        val groups = DuplicateScanner.scan(taggedSourceRoots, config.minTokens, config.top, config.scope)

        if (groups.isEmpty()) {
            logger.quiet(OutputWrapper.emptyResult(config.format, "No duplicates found."))
            return
        }

        logger.quiet(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> DuplicateFormatter.format(groups)
        OutputFormat.JSON -> DuplicateFormatter.formatJson(groups)
        OutputFormat.LLM -> DuplicateFormatter.formatLlm(groups)
    }
})
    }
}
