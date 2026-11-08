package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.relations.callgraph.CallPathConfig
import no.f12.codenavigator.navigation.relations.callgraph.CallPathFormatter
import no.f12.codenavigator.navigation.relations.callgraph.CallPathOrchestrator
import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskRegistry
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class CallPathsTask : CodeNavigatorTask() {

    @Option(option = "from", description = "Entry-point method regex (Class.method, camelCase-aware)")
    @get:Internal
    var from: String? = null

    @Option(option = "from-annotation", description = "Treat methods carrying a matching annotation as entry points")
    @get:Internal
    var fromAnnotation: String? = null

    @Option(option = "to", description = "Target method regex (Class.method, camelCase-aware)")
    @get:Internal
    var to: String? = null

    @Option(option = "guard", description = "Report only paths that skip this check (method name or annotation regex)")
    @get:Internal
    var guard: String? = null

    @Option(option = "guard-depth", description = "How many calls deep a method may reach the guard and still count as guarded")
    @get:Internal
    var guardDepth: String? = null

    @Option(option = "maxdepth", description = "Max calls along a path")
    @get:Internal
    var maxdepth: String? = null

    @Option(option = "top", description = "Max entry points listed")
    @get:Internal
    var top: String? = null

    @Option(option = "project-only", description = "Step through project classes only (default: on); the target may still be a library method")
    @get:Internal
    var projectOnly: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        from?.let { put("from", it) }
        fromAnnotation?.let { put("from-annotation", it) }
        to?.let { put("to", it) }
        guard?.let { put("guard", it) }
        guardDepth?.let { put("guard-depth", it) }
        maxdepth?.let { put("maxdepth", it) }
        top?.let { put("top", it) }
        projectOnly?.let { put("project-only", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun callPaths() {
        val taskDef = TaskRegistry.CALL_PATHS
        val config = try {
            CallPathConfig.parse(taskDef.enhanceProperties(buildOptionsMap()))
        } catch (e: IllegalArgumentException) {
            throw GradleException("${e.message}\n${taskDef.usageHint(BuildTool.GRADLE)}")
        }

        val cacheDir = File(project.layout.buildDirectory.asFile.get(), "cnav")
        val output = CallPathOrchestrator.run(config, project.taggedClassDirectories(), cacheDir)
        output.skippedFileWarning?.let { logger.warn(it) }

        val report = output.report
        if (report == null) {
            logger.quiet(OutputWrapper.emptyResult(config.format, output.noMatch ?: "No call paths"))
            return
        }
        logger.quiet(
            OutputWrapper.formatAndWrap(config.format) { format ->
                when (format) {
                    OutputFormat.TEXT, OutputFormat.DIFF -> CallPathFormatter.formatText(report)
                    OutputFormat.JSON -> CallPathFormatter.formatJson(report)
                    OutputFormat.LLM -> CallPathFormatter.formatLlm(report)
                }
            },
        )
    }
}
