package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.core.PackageName
import no.f12.codenavigator.navigation.core.RootPackageDetector
import no.f12.codenavigator.navigation.core.Scope
import no.f12.codenavigator.navigation.core.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.DsmMatrixBuilder
import no.f12.codenavigator.navigation.dsm.PackageDistanceBuilder
import no.f12.codenavigator.navigation.dsm.PackageDistanceConfig
import no.f12.codenavigator.navigation.dsm.PackageDistanceFormatter
import no.f12.codenavigator.navigation.dsm.filterByPackage
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class PackageDistanceTask : DefaultTask() {

    @TaskAction
    fun showDistance() {
        val extension = project.codeNavigatorExtension()
        val cliProps = project.buildPropertyMap(TaskRegistry.DISTANCE)
        val props = extension.resolveProperties(cliProps)

        val config = PackageDistanceConfig.parse(props)

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        val projectClasses = scanProjectClasses(classDirectories)

        val packageFilter = config.packageFilter?.let { PackageName(it) }

        val extractResult = DsmDependencyExtractor.extract(
            classDirectories, projectClasses,
            packageFilter = packageFilter ?: PackageName(""),
            includeExternal = config.includeExternal,
            filterTargets = false,
        )
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(extractResult.skippedFiles, reportFile)?.let { logger.warn(it) }

        val dependencies = extractResult.data.filterByPackage(packageFilter)

        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())
        val matrix = DsmMatrixBuilder.build(dependencies, displayPrefix, config.depth)

        val result = PackageDistanceBuilder.build(matrix, config.top)

        if (result.entries.isEmpty()) {
            val packageCount = projectClasses.map { it.packageName() }.distinct().size
            val hints = PackageDistanceFormatter.noResultsHints(packageCount)
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No inter-package dependencies found.", hints))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { PackageDistanceFormatter.format(result) },
            json = { JsonFormatter.formatDistance(result) },
            llm = { LlmFormatter.formatDistance(result) },
        ))
    }
}
