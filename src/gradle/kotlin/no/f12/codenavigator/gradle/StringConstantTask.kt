package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.core.Scope
import no.f12.codenavigator.navigation.core.SourceSetResolver
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import no.f12.codenavigator.navigation.stringconstant.StringConstantConfig
import no.f12.codenavigator.navigation.stringconstant.StringConstantFormatter
import no.f12.codenavigator.navigation.stringconstant.StringConstantScanner

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class StringConstantTask : DefaultTask() {

    @TaskAction
    fun findStringConstants() {
        val config = StringConstantConfig.parse(
            project.buildPropertyMap(TaskRegistry.FIND_STRING_CONSTANT),
        )

        val taggedDirs = project.taggedClassDirectories()
        val resolver = SourceSetResolver.from(taggedDirs)

        val result = StringConstantScanner.scan(resolver.classDirectories, config.pattern)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val matches = result.data.filter { resolver.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }

        if (matches.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No string constants matching '${config.pattern.pattern}' found."))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { StringConstantFormatter.format(matches) },
            json = { JsonFormatter.formatStringConstants(matches) },
            llm = { LlmFormatter.formatStringConstants(matches) },
        ))
    }
}
