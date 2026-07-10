package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.DsmConfig
import no.f12.codenavigator.navigation.dsm.DsmFormatter
import no.f12.codenavigator.navigation.dsm.DsmHtmlRenderer
import no.f12.codenavigator.navigation.dsm.DsmOrchestrator
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.SourceSet
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class DsmTask : CodeNavigatorTask(), MultiModuleCapable {

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

    @Option(option = "multi-module", description = "Aggregate class directories from this project's real project dependencies (siblings not on the dependency graph are excluded)")
    @get:Internal
    var multiModule: String? = null

    @get:Internal
    override val multiModuleFlag: String?
        get() = multiModule

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        dsmDepth?.let { put("dsm-depth", it) }
        dsmHtml?.let { put("dsm-html", it) }
        cycles?.let { put("cycles", it) }
        cycle?.let { put("cycle", it) }
        rootPackage?.let { put("root-package", it) }
        scope?.let { put("scope", it) }
        multiModule?.let { put("multi-module", it) }
    }

    @TaskAction
    fun showDsm() {
        val extension = project.codeNavigatorExtension()
        val cliProps = TaskRegistry.DSM.enhanceProperties(buildOptionsMap())
        val props = extension.resolveProperties(cliProps)

        val config = DsmConfig.parse(props)
        config.deprecations().forEach { logger.warn(it) }

        val isMultiModule = TaskRegistry.MULTI_MODULE.parseFrom(props)
        val taggedDirs: List<Pair<File, SourceSet>>
        val moduleOfClass: Map<ClassName, String>
        if (isMultiModule) {
            val moduleTaggedDirs = MultiModuleResolver.resolve(project)
            taggedDirs = moduleTaggedDirs.map { (dir, mss) -> dir to mss.sourceSet }
            moduleOfClass = buildMap {
                for ((dir, mss) in moduleTaggedDirs) {
                    if (!dir.exists()) continue
                    scanProjectClasses(listOf(dir)).forEach { put(it, mss.moduleName) }
                }
            }
        } else {
            taggedDirs = project.taggedClassDirectories()
            moduleOfClass = emptyMap()
        }

        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        val output = DsmOrchestrator.run(config, taggedDirs, loadPlanSteps(), reportFile, moduleOfClass)

        output.skippedFileWarning?.let { logger.warn(it) }
        val matrix = output.matrix

        if (matrix.packages.isEmpty() && config.cycleFilter == null && !config.cyclesOnly) {
            val packageCount = output.projectClasses.map { it.packageName() }.distinct().size
            val hints = DsmFormatter.noResultsHints(packageCount)
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No inter-package dependencies found.", hints))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> if (config.cyclesOnly || config.cycleFilter != null) DsmFormatter.formatCycles(matrix, config.cycleFilter) else DsmFormatter.format(matrix, output.moduleLabels)
        OutputFormat.JSON -> if (config.cyclesOnly || config.cycleFilter != null) DsmFormatter.formatCyclesJson(matrix, config.cycleFilter) else DsmFormatter.formatJson(matrix, output.moduleLabels)
        OutputFormat.LLM -> if (config.cyclesOnly || config.cycleFilter != null) DsmFormatter.formatCyclesLlm(matrix, config.cycleFilter) else DsmFormatter.formatLlm(matrix, output.moduleLabels)
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
