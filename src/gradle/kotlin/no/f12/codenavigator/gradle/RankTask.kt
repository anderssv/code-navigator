package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.CallGraphCache
import no.f12.codenavigator.navigation.RankFormatter
import no.f12.codenavigator.navigation.SkippedFileReporter
import no.f12.codenavigator.navigation.TypeRanker

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class RankTask : DefaultTask() {

    @TaskAction
    fun showRank() {
        val top = project.findProperty("top")?.toString()?.toIntOrNull() ?: 50
        val projectOnly = project.findProperty("projectonly")?.toString()?.toBoolean() ?: true
        val format = project.outputFormat()

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/call-graph.cache")
        val result = CallGraphCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val graph = result.data

        val ranked = TypeRanker.rank(graph, top = top, projectOnly = projectOnly)

        if (ranked.isEmpty()) {
            logger.lifecycle("No ranked types found.")
            return
        }

        val output = when (format) {
            OutputFormat.JSON -> JsonFormatter.formatRank(ranked)
            OutputFormat.LLM -> LlmFormatter.formatRank(ranked)
            OutputFormat.TEXT -> RankFormatter.format(ranked)
        }
        logger.lifecycle(OutputWrapper.wrap(output, format))
    }
}
