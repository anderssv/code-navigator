package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.core.PackageName
import no.f12.codenavigator.navigation.core.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.ClassTypeCollector
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.StrengthClassifier
import no.f12.codenavigator.navigation.dsm.StrengthConfig
import no.f12.codenavigator.navigation.dsm.StrengthFormatter
import no.f12.codenavigator.navigation.annotation.FrameworkPresets
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "strength")
@Execute(phase = LifecyclePhase.COMPILE)
class IntegrationStrengthMojo : AbstractMojo() {

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

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        val config = StrengthConfig.parse(TaskRegistry.STRENGTH.enhanceProperties(buildPropertyMap()))

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
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

        val result = StrengthClassifier.classify(extractResult.data, classTypeRegistry, config.top, packageFilter)

        if (result.entries.isEmpty()) {
            val packageCount = projectClasses.map { it.packageName() }.distinct().size
            val hints = StrengthFormatter.noResultsHints(packageCount)
            println(OutputWrapper.emptyResult(config.format, "No inter-package dependencies found.", hints))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { StrengthFormatter.format(result) },
            json = { JsonFormatter.formatStrength(result) },
            llm = { LlmFormatter.formatStrength(result) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        dsmDepth?.let { put("dsm-depth", it) }
        top?.let { put("top", it) }
        scope?.let { put("scope", it) }
    }
}
