package no.f12.codenavigator.gradle

import no.f12.codenavigator.analysis.GitLogRunner
import no.f12.codenavigator.analysis.HotspotBuilder
import no.f12.codenavigator.analysis.PackageVolatilityBuilder
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.annotation.FrameworkPresets
import no.f12.codenavigator.navigation.core.PackageName
import no.f12.codenavigator.navigation.core.RootPackageDetector
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import no.f12.codenavigator.navigation.core.Scope
import no.f12.codenavigator.navigation.core.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.BalanceBuilder
import no.f12.codenavigator.navigation.dsm.BalanceConfig
import no.f12.codenavigator.navigation.dsm.BalanceFormatter
import no.f12.codenavigator.navigation.dsm.ClassTypeCollector
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.DsmMatrixBuilder
import no.f12.codenavigator.navigation.dsm.PackageDistanceBuilder
import no.f12.codenavigator.navigation.dsm.StrengthClassifier
import no.f12.codenavigator.navigation.dsm.filterByPackage
import no.f12.codenavigator.registry.TaskRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class BalanceTask : DefaultTask() {

    @TaskAction
    fun showBalance() {
        val extension = project.codeNavigatorExtension()
        val cliProps = project.buildPropertyMap(TaskRegistry.BALANCE)
        val props = extension.resolveProperties(cliProps)

        val config = BalanceConfig.parse(props)

        // --- Bytecode analysis (strength + distance) ---

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

        val strengthResult = StrengthClassifier.classify(extractResult.data, classTypeRegistry, Int.MAX_VALUE, packageFilter)

        val dependencies = extractResult.data.filterByPackage(packageFilter)
        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())
        val matrix = DsmMatrixBuilder.build(dependencies, displayPrefix, config.depth)
        val distanceResult = PackageDistanceBuilder.build(matrix, Int.MAX_VALUE)

        // --- Git history analysis (volatility) ---

        val commits = GitLogRunner.run(project.projectDir, config.after, followRenames = config.followRenames)
        val hotspots = HotspotBuilder.build(commits, config.minRevs)
        val volatilityResult = PackageVolatilityBuilder.build(hotspots, Int.MAX_VALUE)

        // --- Combine into balance ---

        val result = BalanceBuilder.build(strengthResult, distanceResult, volatilityResult, config.top)

        if (result.entries.isEmpty()) {
            val packageCount = projectClasses.map { it.packageName() }.distinct().size
            val hints = BalanceFormatter.noResultsHints(packageCount)
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No balanced coupling data found.", hints))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { BalanceFormatter.format(result) },
            json = { JsonFormatter.formatBalance(result) },
            llm = { LlmFormatter.formatBalance(result) },
        ))
    }
}
