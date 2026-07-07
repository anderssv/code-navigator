package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.context.ContextConfig
import no.f12.codenavigator.navigation.context.ContextFormatter
import no.f12.codenavigator.navigation.context.ContextOrchestrator

import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class ContextTask : CodeNavigatorTask() {

    @Option(option = "pattern", description = "Class/symbol name regex (camelCase-aware: MyService matches com.example.MyService)")
    @get:Internal
    var pattern: String? = null

    @Option(option = "maxdepth", description = "Max call tree depth (default: 2)")
    @get:Internal
    var maxdepth: String? = null

    @Option(option = "project-only", description = "Hide JDK/stdlib/library classes (default: on)")
    @get:Internal
    var projectOnly: String? = null

    @Option(option = "filter-synthetic", description = "Set false to include synthetic methods (equals, hashCode, copy, componentN, etc.)")
    @get:Internal
    var filterSynthetic: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        pattern?.let { put("pattern", it) }
        maxdepth?.let { put("maxdepth", it) }
        projectOnly?.let { put("project-only", it) }
        filterSynthetic?.let { put("filter-synthetic", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun gatherContext() {
        val config = try {
            ContextConfig.parse(TaskRegistry.CONTEXT.enhanceProperties(buildOptionsMap()))
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.CONTEXT.usageHint(BuildTool.GRADLE)}",
            )
        }

        val taggedDirs = project.taggedClassDirectories()
        val cacheDir = File(project.layout.buildDirectory.asFile.get(), "cnav")
        val output = ContextOrchestrator.run(config, taggedDirs, cacheDir)

        output.skippedFileWarnings.forEach { logger.warn(it) }

        if (output.results.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No classes found matching '${config.pattern}'"))
            return
        }

        logger.lifecycle(
            OutputWrapper.formatAndWrap(config.format) { format ->
                when (format) {
                    OutputFormat.TEXT, OutputFormat.DIFF -> output.results.joinToString("\n\n") { ContextFormatter.format(it) }
                    OutputFormat.JSON -> "[${output.results.joinToString(",") { JsonFormatter.formatContext(it) }}]"
                    OutputFormat.LLM -> output.results.joinToString("\n\n") { LlmFormatter.formatContext(it) }
                }
            },
        )
    }
}
