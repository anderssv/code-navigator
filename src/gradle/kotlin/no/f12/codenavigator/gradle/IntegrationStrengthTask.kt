package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.PackageName
import no.f12.codenavigator.navigation.SourceSet
import no.f12.codenavigator.navigation.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.ClassTypeCollector
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.StrengthClassifier
import no.f12.codenavigator.navigation.dsm.StrengthConfig
import no.f12.codenavigator.navigation.dsm.StrengthFormatter
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class IntegrationStrengthTask : DefaultTask() {

    @TaskAction
    fun showStrength() {
        val extension = project.codeNavigatorExtension()
        val cliProps = project.buildPropertyMap(TaskRegistry.STRENGTH)
        val props = extension.resolveProperties(cliProps)

        val config = StrengthConfig.parse(props)

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = when {
            config.prodOnly -> taggedDirs.filter { it.second == SourceSet.MAIN }
            config.testOnly -> taggedDirs.filter { it.second == SourceSet.TEST }
            else -> taggedDirs
        }
        val classDirectories = filteredDirs.map { it.first }

        val projectClasses = scanProjectClasses(classDirectories)

        // Pass 1: collect class kinds (interface, abstract, data class, record, concrete)
        val classTypeRegistry = ClassTypeCollector.collect(classDirectories)

        // Pass 2: extract inter-package dependencies
        val packageFilter = PackageName(config.packageFilter ?: "")
        val extractResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter, config.includeExternal)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(extractResult.skippedFiles, reportFile)?.let { logger.warn(it) }
        val dependencies = extractResult.data

        // Classify strength per package pair
        val packageFilterName = config.packageFilter?.let { PackageName(it) }
        val result = StrengthClassifier.classify(dependencies, classTypeRegistry, config.top, packageFilterName)

        if (result.entries.isEmpty()) {
            val packageCount = projectClasses.map { it.packageName() }.distinct().size
            val hints = StrengthFormatter.noResultsHints(packageCount)
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No inter-package dependencies found.", hints))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { StrengthFormatter.format(result) },
            json = { JsonFormatter.formatStrength(result) },
            llm = { LlmFormatter.formatStrength(result) },
        ))
    }
}
