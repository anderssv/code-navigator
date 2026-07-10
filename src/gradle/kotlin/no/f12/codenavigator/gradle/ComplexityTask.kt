package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.complexity.ClassComplexityAnalyzer
import no.f12.codenavigator.navigation.complexity.ComplexityConfig
import no.f12.codenavigator.navigation.complexity.ComplexityFormatter
import no.f12.codenavigator.navigation.bytecode.LambdaCollapser
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class ComplexityTask : CodeNavigatorTask() {

    @Option(option = "pattern", description = "Class/symbol name regex (camelCase-aware: MyService matches com.example.MyService)")
    @get:Internal
    var pattern: String? = null

    @Option(option = "project-only", description = "Hide JDK/stdlib/library classes (default: on)")
    @get:Internal
    var projectOnly: String? = null

    @Option(option = "detail", description = "Show individual call details")
    @get:Internal
    var detail: String? = null

    @Option(option = "collapse-lambdas", description = "Set false to show lambda classes separately")
    @get:Internal
    var collapseLambdas: String? = null

    @Option(option = "top", description = "Max results")
    @get:Internal
    var top: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        pattern?.let { put("pattern", it) }
        projectOnly?.let { put("project-only", it) }
        detail?.let { put("detail", it) }
        collapseLambdas?.let { put("collapse-lambdas", it) }
        top?.let { put("top", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun showComplexity() {
        val config = ComplexityConfig.parse(
            TaskRegistry.COMPLEXITY.enhanceProperties(buildOptionsMap()),
        )

        val taggedDirs = project.taggedClassDirectories()

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/call-graph.cache")
        val result = CallGraphCache.getOrBuildTagged(cacheFile, taggedDirs)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val graph = result.data

        val rawResults = ClassComplexityAnalyzer.analyze(
            graph = graph,
            classPattern = config.classPattern,
            projectOnly = config.projectOnly,
        )
        val collapsed = if (config.collapseLambdas) LambdaCollapser.collapseComplexity(rawResults) else rawResults
        val filtered = collapsed.filter { graph.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }
        val truncated = filtered.take(config.top)

        if (truncated.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No matching classes found."))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> ComplexityFormatter.format(truncated)
        OutputFormat.JSON -> ComplexityFormatter.formatJson(truncated)
        OutputFormat.LLM -> ComplexityFormatter.formatLlm(truncated)
    }
})
    }
}
