package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.bytecode.RootPackageDetector
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.DsmConfig
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.DsmFormatter
import no.f12.codenavigator.navigation.dsm.DsmHtmlRenderer
import no.f12.codenavigator.navigation.dsm.DsmMatrixBuilder
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class DsmTask : CodeNavigatorTask() {

    @Option(option = "package-filter", description = "Only include packages under this prefix")
    @get:Internal
    var packageFilter: String? = null

    @Option(option = "include-external", description = "Include dependencies on classes outside the project")
    @get:Internal
    var includeExternal: String? = null

    @Option(option = "dsm-depth", description = "Package grouping depth")
    @get:Internal
    var dsmDepth: String? = null

    @Option(option = "dsm-html", description = "Write interactive HTML matrix to file")
    @get:Internal
    var dsmHtml: String? = null

    @Option(option = "cycles", description = "Show only cyclic dependencies with class-level edges")
    @get:Internal
    var cycles: String? = null

    @Option(option = "cycle", description = "Show only the cycle between two specific packages")
    @get:Internal
    var cycle: String? = null

    @Option(option = "root-package", description = "Deprecated: use package-filter instead. Only include packages under this prefix")
    @get:Internal
    var rootPackage: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        dsmDepth?.let { put("dsm-depth", it) }
        dsmHtml?.let { put("dsm-html", it) }
        cycles?.let { put("cycles", it) }
        cycle?.let { put("cycle", it) }
        rootPackage?.let { put("root-package", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun showDsm() {
        val extension = project.codeNavigatorExtension()
        val cliProps = TaskRegistry.DSM.enhanceProperties(buildOptionsMap())
        val props = extension.resolveProperties(cliProps)

        val config = DsmConfig.parse(props)
        config.deprecations().forEach { logger.warn(it) }

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        val projectClasses = scanProjectClasses(classDirectories)

        val result = DsmDependencyExtractor.extract(classDirectories, projectClasses, config.packageFilter, config.includeExternal, filterTargets = true)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val dependencies = result.data

        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())
        val matrix = DsmMatrixBuilder.build(dependencies, displayPrefix, config.depth)

        if (matrix.packages.isEmpty() && config.cycleFilter == null && !config.cyclesOnly) {
            val packageCount = projectClasses.map { it.packageName() }.distinct().size
            val hints = DsmFormatter.noResultsHints(packageCount)
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No inter-package dependencies found.", hints))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> if (config.cyclesOnly || config.cycleFilter != null) DsmFormatter.formatCycles(matrix, config.cycleFilter) else DsmFormatter.format(matrix)
        OutputFormat.JSON -> if (config.cyclesOnly || config.cycleFilter != null) JsonFormatter.formatDsmCycles(matrix, config.cycleFilter) else JsonFormatter.formatDsm(matrix)
        OutputFormat.LLM -> if (config.cyclesOnly || config.cycleFilter != null) LlmFormatter.formatDsmCycles(matrix, config.cycleFilter) else LlmFormatter.formatDsm(matrix)
    }
})

        if (config.htmlPath != null) {
            val htmlFile = project.file(config.htmlPath)
            htmlFile.parentFile?.mkdirs()
            htmlFile.writeText(DsmHtmlRenderer.render(matrix))
            logger.lifecycle("DSM HTML written to: ${htmlFile.absolutePath}")
        }
    }
}
