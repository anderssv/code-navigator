package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.RootPackageDetector
import no.f12.codenavigator.navigation.SourceSet
import no.f12.codenavigator.navigation.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.CycleDetector
import no.f12.codenavigator.navigation.dsm.CyclesConfig
import no.f12.codenavigator.navigation.dsm.CyclesFormatter
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.DsmMatrixBuilder
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class CyclesTask : DefaultTask() {

    @TaskAction
    fun showCycles() {
        val extension = project.codeNavigatorExtension()
        val cliProps = project.buildPropertyMap(TaskRegistry.CYCLE_DETECTION)
        val props = extension.resolveProperties(cliProps)

        val config = CyclesConfig.parse(props)
        config.deprecations().forEach { logger.warn(it) }

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = when {
            config.prodOnly -> taggedDirs.filter { it.second == SourceSet.MAIN }
            config.testOnly -> taggedDirs.filter { it.second == SourceSet.TEST }
            else -> taggedDirs
        }
        val classDirectories = filteredDirs.map { it.first }

        val projectClasses = scanProjectClasses(classDirectories)

        val result = DsmDependencyExtractor.extract(classDirectories, projectClasses, config.packageFilter, config.includeExternal)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val dependencies = result.data

        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())
        val matrix = DsmMatrixBuilder.build(dependencies, displayPrefix, config.depth)

        val adjacency = CycleDetector.adjacencyMapFrom(matrix)
        val cycles = CycleDetector.findCycles(adjacency)
        val details = CycleDetector.enrich(cycles, matrix)

        if (details.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No package cycles detected."))
            return
        }
        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { CyclesFormatter.format(details) },
            json = { JsonFormatter.formatCycles(details) },
            llm = { LlmFormatter.formatCycles(details) },
        ))
    }
}
