package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.CallGraphCache
import no.f12.codenavigator.navigation.DeadCodeConfig
import no.f12.codenavigator.navigation.DeadCodeFinder
import no.f12.codenavigator.navigation.DeadCodeFormatter
import no.f12.codenavigator.navigation.SkippedFileReporter

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class DeadCodeTask : DefaultTask() {

    @TaskAction
    fun showDeadCode() {
        val config = DeadCodeConfig.parse(
            project.buildPropertyMap(
                propertyNames = listOf("filter", "exclude", "format", "llm"),
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

        val dead = DeadCodeFinder.find(graph, filter = config.filter, exclude = config.exclude)

        if (dead.isEmpty()) {
            logger.lifecycle("No potential dead code found.")
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatDead(dead)
            OutputFormat.LLM -> LlmFormatter.formatDead(dead)
            OutputFormat.TEXT -> DeadCodeFormatter.format(dead)
        }
        logger.lifecycle(OutputWrapper.wrap(output, config.format))
    }
}
