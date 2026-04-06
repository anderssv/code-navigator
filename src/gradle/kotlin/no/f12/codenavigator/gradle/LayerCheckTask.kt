package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import no.f12.codenavigator.navigation.core.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.LayerCheckConfig
import no.f12.codenavigator.navigation.dsm.LayerCheckResult
import no.f12.codenavigator.navigation.dsm.LayerChecker
import no.f12.codenavigator.navigation.dsm.LayerConfig
import no.f12.codenavigator.navigation.dsm.LayerFormatter
import no.f12.codenavigator.navigation.dsm.LayerInitGenerator
import no.f12.codenavigator.registry.TaskRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class LayerCheckTask : DefaultTask() {

    @TaskAction
    fun checkLayers() {
        val extension = project.codeNavigatorExtension()
        val cliProps = project.buildPropertyMap(TaskRegistry.LAYER_CHECK)
        val props = extension.resolveProperties(cliProps)

        val config = LayerCheckConfig.parse(props)

        val classDirectories = project.taggedClassDirectories().map { it.first }
        val projectClasses = scanProjectClasses(classDirectories)

        val extractResult = DsmDependencyExtractor.extract(classDirectories, projectClasses)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(extractResult.skippedFiles, reportFile)?.let { logger.warn(it) }
        val dependencies = extractResult.data

        if (config.init) {
            val configFile = project.file(config.configPath)
            val json = LayerInitGenerator.generateConfigJson(projectClasses.toList())
            configFile.writeText(json)
            val sampleClasses = projectClasses.sorted().toList()
            logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
                text = { LayerFormatter.formatInit(config.configPath, projectClasses.size, sampleClasses) },
                json = { JsonFormatter.formatLayerCheck(LayerCheckResult(emptyList(), emptySet())) },
                llm = { LayerFormatter.formatInit(config.configPath, projectClasses.size, sampleClasses) },
            ))
            return
        }

        val configFile = project.file(config.configPath)
        if (!configFile.exists()) {
            throw GradleException("Layer config file not found: ${config.configPath}\nRun with -Pinit=true to generate a starter config.")
        }

        val layerConfig = LayerConfig.parse(configFile.readText())
        val result = LayerChecker.check(layerConfig, dependencies)

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { LayerFormatter.format(result) },
            json = { JsonFormatter.formatLayerCheck(result) },
            llm = { LlmFormatter.formatLayerCheck(result) },
        ))

        if (result.violations.isNotEmpty()) {
            throw GradleException("${result.violations.size} layer violation(s) found. See output above for details.")
        }
    }
}
