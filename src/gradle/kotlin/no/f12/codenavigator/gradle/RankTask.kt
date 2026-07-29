package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.rank.RankConfig
import no.f12.codenavigator.navigation.rank.RankFormatter
import no.f12.codenavigator.navigation.rank.TypeRanker

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class RankTask : CodeNavigatorTask() {

    @Option(option = "top", description = "Max results")
    @get:Internal
    var top: String? = null

    @Option(option = "project-only", description = "Hide JDK/stdlib/library classes (default: on)")
    @get:Internal
    var projectOnly: String? = null

    @Option(option = "collapse-lambdas", description = "Set false to show lambda classes separately")
    @get:Internal
    var collapseLambdas: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        top?.let { put("top", it) }
        projectOnly?.let { put("project-only", it) }
        collapseLambdas?.let { put("collapse-lambdas", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun showRank() {
        val config = RankConfig.parse(
            TaskRegistry.RANK.enhanceProperties(buildOptionsMap()),
        )

        val taggedDirs = project.taggedClassDirectories()

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/call-graph.cache")
        val result = CallGraphCache.getOrBuildTagged(cacheFile, taggedDirs)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val graph = result.data

        val ranked = TypeRanker.rank(graph, top = config.top, projectOnly = config.projectOnly, collapseLambdas = config.collapseLambdas)
        val filtered = ranked.filter { graph.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }

        if (filtered.isEmpty()) {
            logger.quiet(OutputWrapper.emptyResult(config.format, "No ranked types found."))
            return
        }

        logger.quiet(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> RankFormatter.format(filtered)
        OutputFormat.JSON -> RankFormatter.formatJson(filtered)
        OutputFormat.LLM -> RankFormatter.formatLlm(filtered)
    }
})
    }
}
