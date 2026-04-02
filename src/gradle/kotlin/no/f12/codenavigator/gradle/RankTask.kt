package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.SourceSet
import no.f12.codenavigator.navigation.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.SkippedFileReporter
import no.f12.codenavigator.navigation.rank.RankConfig
import no.f12.codenavigator.navigation.rank.RankFormatter
import no.f12.codenavigator.navigation.rank.TypeRanker

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class RankTask : DefaultTask() {

    @TaskAction
    fun showRank() {
        val config = RankConfig.parse(
            project.buildPropertyMap(TaskRegistry.RANK),
        )

        val taggedDirs = project.taggedClassDirectories()

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/call-graph.cache")
        val result = CallGraphCache.getOrBuildTagged(cacheFile, taggedDirs)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val graph = result.data

        val ranked = TypeRanker.rank(graph, top = config.top, projectOnly = config.projectOnly, collapseLambdas = config.collapseLambdas)
        val filtered = when {
            config.prodOnly -> ranked.filter { graph.sourceSetOf(it.className) == SourceSet.MAIN }
            config.testOnly -> ranked.filter { graph.sourceSetOf(it.className) == SourceSet.TEST }
            else -> ranked
        }

        if (filtered.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No ranked types found."))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { RankFormatter.format(filtered) },
            json = { JsonFormatter.formatRank(filtered) },
            llm = { LlmFormatter.formatRank(filtered) },
        ))
    }
}
