package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.CallGraphCache
import no.f12.codenavigator.navigation.SkippedFileReporter
import no.f12.codenavigator.navigation.rank.RankConfig
import no.f12.codenavigator.navigation.rank.RankFormatter
import no.f12.codenavigator.navigation.rank.TypeRanker

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
            project.buildPropertyMap(TaskRegistry.RANK),
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

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { RankFormatter.format(ranked) },
            json = { JsonFormatter.formatRank(ranked) },
            llm = { LlmFormatter.formatRank(ranked) },
        ))
    }
}
