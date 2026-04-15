package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.core.PackageName
import no.f12.codenavigator.navigation.core.Scope
import no.f12.codenavigator.navigation.core.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.ClassTypeCollector
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.StrengthClassifier
import no.f12.codenavigator.navigation.dsm.StrengthConfig
import no.f12.codenavigator.navigation.dsm.StrengthFormatter
import no.f12.codenavigator.navigation.annotation.FrameworkPresets
import no.f12.codenavigator.navigation.core.SkippedFileReporter
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
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        val projectClasses = scanProjectClasses(classDirectories)

        val classTypeRegistry = ClassTypeCollector.collect(classDirectories, FrameworkPresets.resolveAllModelAnnotations())

        val packageFilter = config.packageFilter?.let { PackageName(it) }

        val extractResult = DsmDependencyExtractor.extract(
            classDirectories, projectClasses,
            packageFilter = packageFilter ?: PackageName(""),
            includeExternal = config.includeExternal,
            filterTargets = false,
        )
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(extractResult.skippedFiles, reportFile)?.let { logger.warn(it) }

        val result = StrengthClassifier.classify(extractResult.data, classTypeRegistry, config.top, packageFilter)

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
