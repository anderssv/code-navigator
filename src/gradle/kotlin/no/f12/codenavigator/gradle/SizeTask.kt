package no.f12.codenavigator.gradle

import no.f12.codenavigator.analysis.FileSizeConfig
import no.f12.codenavigator.analysis.FileSizeFormatter
import no.f12.codenavigator.analysis.FileSizeScanner
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class SizeTask : DefaultTask() {

    @TaskAction
    fun showSize() {
        val config = FileSizeConfig.parse(
            project.buildPropertyMap(TaskRegistry.SIZE),
        )

        val sourceRoots = project.sourceDirectories()
        val entries = FileSizeScanner.scan(sourceRoots, config.over, config.top)

        if (entries.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No source files found."))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { FileSizeFormatter.format(entries) },
            json = { JsonFormatter.formatSize(entries) },
            llm = { LlmFormatter.formatSize(entries) },
        ))
    }
}
