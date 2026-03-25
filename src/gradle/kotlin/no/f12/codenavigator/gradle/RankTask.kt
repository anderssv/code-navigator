package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.CallGraphCache
import no.f12.codenavigator.navigation.RankConfig
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
        val config = RankConfig.parse(
            project.buildPropertyMap(
                propertyNames = listOf("top", "projectonly", "collapse-lambdas", "format", "llm"),
                flagNames = emptyList(),
            ),
        )

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/call-graph.cache")
        val result = CallGraphCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val graph = result.data

        val ranked = TypeRanker.rank(graph, top = config.top, projectOnly = config.projectOnly, collapseLambdas = config.collapseLambdas)

        if (ranked.isEmpty()) {
            logger.lifecycle("No ranked types found.")
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatRank(ranked)
            OutputFormat.LLM -> LlmFormatter.formatRank(ranked)
            OutputFormat.TEXT -> RankFormatter.format(ranked)
        }
        logger.lifecycle(OutputWrapper.wrap(output, config.format))
    }
}
