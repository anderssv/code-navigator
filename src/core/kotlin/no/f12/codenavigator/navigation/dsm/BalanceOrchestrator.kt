package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.analysis.GitLogRunner
import no.f12.codenavigator.analysis.HotspotBuilder
import no.f12.codenavigator.analysis.PackageVolatilityBuilder
import no.f12.codenavigator.navigation.types.FrameworkPresets
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import java.io.File

data class BalanceOutput(
    val result: BalanceResult?,
    val noResultsHints: List<String>,
    val skippedFileWarning: String?,
)

object BalanceOrchestrator {

    fun run(config: BalanceConfig, classDirectories: List<File>, reportFile: File, projectDir: File): BalanceOutput {
        val projectClasses = scanProjectClasses(classDirectories)

        val classTypeRegistry = ClassTypeCollector.collect(classDirectories, FrameworkPresets.resolveAllModelAnnotations())

        val packageFilter = config.packageFilter?.let { PackageName(it) }

        val extractResult = DsmDependencyExtractor.extract(
            classDirectories, projectClasses,
            packageFilter = packageFilter,
            includeExternal = config.includeExternal,
            filterTargets = false,
        )
        val skippedWarning = SkippedFileReporter.report(extractResult.skippedFiles, reportFile)

        // Strength
        val strengthResult = StrengthClassifier.classify(extractResult.data, classTypeRegistry, Int.MAX_VALUE, packageFilter)

        // Ring separation (dependency-structure distance, not package-name nesting)
        val dependencies = extractResult.data.filterByPackage(packageFilter)
        val ringAssignment = RingDetector.detect(dependencies)

        // Volatility (git history)
        val commits = GitLogRunner.run(projectDir, config.after, followRenames = config.followRenames)
        val hotspots = HotspotBuilder.build(commits, config.minRevs)
        val volatilityResult = PackageVolatilityBuilder.build(hotspots, Int.MAX_VALUE)

        // Combine
        val result = BalanceBuilder.build(
            strengthResult,
            ringAssignment.rings,
            ringAssignment.compositionRoots,
            volatilityResult,
            config.top,
        )

        if (result.entries.isEmpty()) {
            val packageCount = projectClasses.map { it.packageName() }.distinct().size
            return BalanceOutput(
                result = null,
                noResultsHints = BalanceFormatter.noResultsHints(packageCount),
                skippedFileWarning = skippedWarning,
            )
        }

        return BalanceOutput(
            result = result,
            noResultsHints = emptyList(),
            skippedFileWarning = skippedWarning,
        )
    }
}
