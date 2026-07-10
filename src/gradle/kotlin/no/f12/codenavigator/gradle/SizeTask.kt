package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.analysis.FileSizeConfig
import no.f12.codenavigator.analysis.FileSizeFormatter
import no.f12.codenavigator.analysis.FileSizeScanner
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class SizeTask : CodeNavigatorTask() {

    @Option(option = "top", description = "Max results")
    @get:Internal
    var top: String? = null

    @Option(option = "over", description = "Only show files over N lines")
    @get:Internal
    var over: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        top?.let { put("top", it) }
        over?.let { put("over", it) }
    }

    @TaskAction
    fun showSize() {
        val config = FileSizeConfig.parse(
            TaskRegistry.SIZE.enhanceProperties(buildOptionsMap()),
        )

        val sourceRoots = project.sourceDirectories()
        val entries = FileSizeScanner.scan(sourceRoots, config.over, config.top)

        if (entries.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No source files found."))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> FileSizeFormatter.format(entries)
        OutputFormat.JSON -> FileSizeFormatter.formatJson(entries)
        OutputFormat.LLM -> FileSizeFormatter.formatLlm(entries)
    }
})
    }
}
