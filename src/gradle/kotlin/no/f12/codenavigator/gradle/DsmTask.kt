package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.RootPackageDetector
import no.f12.codenavigator.navigation.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.DsmConfig
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.DsmFormatter
import no.f12.codenavigator.navigation.dsm.DsmHtmlRenderer
import no.f12.codenavigator.navigation.dsm.DsmMatrixBuilder
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
        val cliProps = project.buildPropertyMap(TaskRegistry.DSM)
        val props = extension.resolveProperties(cliProps)

        val config = DsmConfig.parse(props)
        config.deprecations().forEach { logger.warn(it) }

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val projectClasses = scanProjectClasses(classDirectories)

        val result = DsmDependencyExtractor.extract(classDirectories, projectClasses, config.packageFilter, config.includeExternal)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val dependencies = result.data

        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())
        val matrix = DsmMatrixBuilder.build(dependencies, displayPrefix, config.depth)

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { if (config.cyclesOnly || config.cycleFilter != null) DsmFormatter.formatCycles(matrix, config.cycleFilter) else DsmFormatter.format(matrix) },
            json = { if (config.cyclesOnly || config.cycleFilter != null) JsonFormatter.formatDsmCycles(matrix, config.cycleFilter) else JsonFormatter.formatDsm(matrix) },
            llm = { if (config.cyclesOnly || config.cycleFilter != null) LlmFormatter.formatDsmCycles(matrix, config.cycleFilter) else LlmFormatter.formatDsm(matrix) },
        ))

        if (config.htmlPath != null) {
            val htmlFile = project.file(config.htmlPath)
            htmlFile.parentFile?.mkdirs()
            htmlFile.writeText(DsmHtmlRenderer.render(matrix))
            logger.lifecycle("DSM HTML written to: ${htmlFile.absolutePath}")
        }
    }
}
