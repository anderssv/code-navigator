package no.f12.codenavigator.gradle

import no.f12.codenavigator.analysis.DuplicateConfig
import no.f12.codenavigator.analysis.DuplicateFormatter
import no.f12.codenavigator.analysis.DuplicateScanner
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class DuplicatesTask : DefaultTask() {

    @TaskAction
    fun findDuplicates() {
        val config = DuplicateConfig.parse(
            project.buildPropertyMap(TaskRegistry.DUPLICATES),
        )

        val taggedSourceRoots = project.taggedSourceDirectories()
        val groups = DuplicateScanner.scan(taggedSourceRoots, config.minTokens, config.top, config.scope)

        if (groups.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No duplicates found."))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { DuplicateFormatter.format(groups) },
            json = { JsonFormatter.formatDuplicates(groups) },
            llm = { LlmFormatter.formatDuplicates(groups) },
        ))
    }
}
