package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.CycleDetector
import no.f12.codenavigator.navigation.CyclesConfig
import no.f12.codenavigator.navigation.CyclesFormatter
import no.f12.codenavigator.navigation.DsmDependencyExtractor
import no.f12.codenavigator.navigation.DsmMatrixBuilder
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class CyclesTask : DefaultTask() {

    @TaskAction
    fun showCycles() {
        val extension = project.codeNavigatorExtension()
        val resolvedRootPackage = extension.resolveRootPackage(project.findProperty("root-package"))

        val props = project.buildPropertyMap(TaskRegistry.CYCLE_DETECTION) + ("root-package" to resolvedRootPackage)

        val config = CyclesConfig.parse(props)

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val result = DsmDependencyExtractor.extract(classDirectories, config.rootPackage)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val dependencies = result.data
        val matrix = DsmMatrixBuilder.build(dependencies, config.rootPackage, config.depth)

        val adjacency = CycleDetector.adjacencyMapFrom(matrix)
        val cycles = CycleDetector.findCycles(adjacency)
        val details = CycleDetector.enrich(cycles, matrix)

        val output = when (config.format) {
            OutputFormat.TEXT -> CyclesFormatter.format(details)
            OutputFormat.JSON -> JsonFormatter.formatCycles(details)
            OutputFormat.LLM -> LlmFormatter.formatCycles(details)
        }
        logger.lifecycle(OutputWrapper.wrap(output, config.format))
    }
}
