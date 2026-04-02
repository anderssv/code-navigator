package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.core.SourceSet
import no.f12.codenavigator.navigation.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.complexity.ClassComplexityAnalyzer
import no.f12.codenavigator.navigation.complexity.ComplexityConfig
import no.f12.codenavigator.navigation.complexity.ComplexityFormatter
import no.f12.codenavigator.navigation.core.LambdaCollapser
import no.f12.codenavigator.navigation.core.SkippedFileReporter

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class ComplexityTask : DefaultTask() {

    @TaskAction
    fun showComplexity() {
        val config = ComplexityConfig.parse(
            project.buildPropertyMap(TaskRegistry.COMPLEXITY),
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
        val filtered = when {
            config.prodOnly -> collapsed.filter { graph.sourceSetOf(it.className) == SourceSet.MAIN }
            config.testOnly -> collapsed.filter { graph.sourceSetOf(it.className) == SourceSet.TEST }
            else -> collapsed
        }
        val truncated = filtered.take(config.top)

        if (truncated.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No matching classes found."))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { ComplexityFormatter.format(truncated) },
            json = { JsonFormatter.formatComplexity(truncated) },
            llm = { LlmFormatter.formatComplexity(truncated) },
        ))
    }
}
