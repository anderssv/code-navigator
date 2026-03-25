package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.CallGraphCache
import no.f12.codenavigator.navigation.ClassComplexityAnalyzer
import no.f12.codenavigator.navigation.ComplexityConfig
import no.f12.codenavigator.navigation.ComplexityFormatter
import no.f12.codenavigator.navigation.LambdaCollapser
import no.f12.codenavigator.navigation.SkippedFileReporter

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class ComplexityTask : DefaultTask() {

    @TaskAction
    fun showComplexity() {
        val config = ComplexityConfig.parse(
            project.buildPropertyMap(
                propertyNames = listOf("classname", "projectonly", "detail", "collapse-lambdas", "format", "llm"),
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

        val rawResults = ClassComplexityAnalyzer.analyze(
            graph = graph,
            classPattern = config.classPattern,
            projectOnly = config.projectOnly,
        )
        val results = if (config.collapseLambdas) LambdaCollapser.collapseComplexity(rawResults) else rawResults

        if (results.isEmpty()) {
            logger.lifecycle("No matching classes found.")
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatComplexity(results)
            OutputFormat.LLM -> LlmFormatter.formatComplexity(results)
            OutputFormat.TEXT -> ComplexityFormatter.format(results)
        }
        logger.lifecycle(OutputWrapper.wrap(output, config.format))
    }
}
