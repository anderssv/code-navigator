package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.DsmConfig
import no.f12.codenavigator.navigation.DsmDependencyExtractor
import no.f12.codenavigator.navigation.DsmFormatter
import no.f12.codenavigator.navigation.DsmHtmlRenderer
import no.f12.codenavigator.navigation.DsmMatrixBuilder
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class DsmTask : DefaultTask() {

    @TaskAction
    fun showDsm() {
        val extension = project.codeNavigatorExtension()
        val resolvedRootPackage = extension.resolveRootPackage(project.findProperty("root-package"))

        val props = project.buildPropertyMap(TaskRegistry.DSM) + ("root-package" to resolvedRootPackage)

        val config = DsmConfig.parse(props)

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val result = DsmDependencyExtractor.extract(classDirectories, config.rootPackage)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val dependencies = result.data
        val matrix = DsmMatrixBuilder.build(dependencies, config.rootPackage, config.depth)

        val output = if (config.cyclesOnly || config.cycleFilter != null) {
            when (config.format) {
                OutputFormat.TEXT -> DsmFormatter.formatCycles(matrix, config.cycleFilter)
                OutputFormat.JSON -> JsonFormatter.formatDsmCycles(matrix, config.cycleFilter)
                OutputFormat.LLM -> LlmFormatter.formatDsmCycles(matrix, config.cycleFilter)
            }
        } else {
            when (config.format) {
                OutputFormat.TEXT -> DsmFormatter.format(matrix)
                OutputFormat.JSON -> JsonFormatter.formatDsm(matrix)
                OutputFormat.LLM -> LlmFormatter.formatDsm(matrix)
            }
        }
        logger.lifecycle(OutputWrapper.wrap(output, config.format))

        if (config.htmlPath != null) {
            val htmlFile = project.file(config.htmlPath)
            htmlFile.parentFile?.mkdirs()
            htmlFile.writeText(DsmHtmlRenderer.render(matrix))
            logger.lifecycle("DSM HTML written to: ${htmlFile.absolutePath}")
        }
    }
}
