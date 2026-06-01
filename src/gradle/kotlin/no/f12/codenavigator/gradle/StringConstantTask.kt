package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.bytecode.SourceSetResolver
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.stringconstant.StringConstantConfig
import no.f12.codenavigator.navigation.stringconstant.StringConstantFormatter
import no.f12.codenavigator.navigation.stringconstant.StringConstantScanner

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class StringConstantTask : CodeNavigatorTask() {

    @Option(option = "pattern", description = "Regex to match against string constant values")
    @get:Internal
    var pattern: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        pattern?.let { put("pattern", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun findStringConstants() {
        val config = StringConstantConfig.parse(
            TaskRegistry.FIND_STRING_CONSTANT.enhanceProperties(buildOptionsMap()),
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

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> StringConstantFormatter.format(matches)
        OutputFormat.JSON -> JsonFormatter.formatStringConstants(matches)
        OutputFormat.LLM -> LlmFormatter.formatStringConstants(matches)
    }
})
    }
}
