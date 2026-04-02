package no.f12.codenavigator.maven

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
import no.f12.codenavigator.navigation.core.SourceSet
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
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "balance")
@Execute(phase = LifecyclePhase.COMPILE)
class BalanceMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "package-filter")
    private var packageFilter: String? = null

    @Parameter(property = "include-external")
    private var includeExternal: String? = null

    @Parameter(property = "dsm-depth")
    private var dsmDepth: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "prod-only")
    private var prodOnly: String? = null

    @Parameter(property = "test-only")
    private var testOnly: String? = null

    @Parameter(property = "after")
    private var after: String? = null

    @Parameter(property = "min-revs")
    private var minRevs: String? = null

    @Parameter(property = "no-follow")
    private var noFollow: Boolean = false

    override fun execute() {
        val config = BalanceConfig.parse(TaskRegistry.BALANCE.enhanceProperties(buildPropertyMap()))

        // --- Bytecode analysis (strength + distance) ---

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = when {
            config.prodOnly -> taggedDirs.filter { it.second == SourceSet.MAIN }
            config.testOnly -> taggedDirs.filter { it.second == SourceSet.TEST }
            else -> taggedDirs
        }
        val classDirectories = filteredDirs.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val projectClasses = scanProjectClasses(classDirectories)

        val classTypeRegistry = ClassTypeCollector.collect(classDirectories, FrameworkPresets.resolveAllModelAnnotations())

        val packageFilter = config.packageFilter?.let { PackageName(it) }

        val extractResult = DsmDependencyExtractor.extract(
            classDirectories, projectClasses,
            packageFilter = packageFilter ?: PackageName(""),
            includeExternal = config.includeExternal,
            filterTargets = false,
        )
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(extractResult.skippedFiles, reportFile)?.let { log.warn(it) }

        val strengthResult = StrengthClassifier.classify(extractResult.data, classTypeRegistry, Int.MAX_VALUE, packageFilter)

        val dependencies = extractResult.data.filterByPackage(packageFilter)
        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())
        val matrix = DsmMatrixBuilder.build(dependencies, displayPrefix, config.depth)
        val distanceResult = PackageDistanceBuilder.build(matrix, Int.MAX_VALUE)

        // --- Git history analysis (volatility) ---

        val commits = GitLogRunner.run(project.basedir, config.after, followRenames = config.followRenames)
        val hotspots = HotspotBuilder.build(commits, config.minRevs)
        val volatilityResult = PackageVolatilityBuilder.build(hotspots, Int.MAX_VALUE)

        // --- Combine into balance ---

        val result = BalanceBuilder.build(strengthResult, distanceResult, volatilityResult, config.top)

        if (result.entries.isEmpty()) {
            val packageCount = projectClasses.map { it.packageName() }.distinct().size
            val hints = BalanceFormatter.noResultsHints(packageCount)
            println(OutputWrapper.emptyResult(config.format, "No balanced coupling data found.", hints))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { BalanceFormatter.format(result) },
            json = { JsonFormatter.formatBalance(result) },
            llm = { LlmFormatter.formatBalance(result) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        dsmDepth?.let { put("dsm-depth", it) }
        top?.let { put("top", it) }
        prodOnly?.let { put("prod-only", it) }
        testOnly?.let { put("test-only", it) }
        after?.let { put("after", it) }
        minRevs?.let { put("min-revs", it) }
        if (noFollow) put("no-follow", null)
    }
}
