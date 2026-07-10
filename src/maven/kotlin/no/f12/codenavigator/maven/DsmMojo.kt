package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.dsm.DsmConfig
import no.f12.codenavigator.navigation.dsm.DsmFormatter
import no.f12.codenavigator.navigation.dsm.DsmHtmlRenderer
import no.f12.codenavigator.navigation.dsm.DsmOrchestrator
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "dsm")
@Execute(phase = LifecyclePhase.COMPILE)
class DsmMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "root-package")
    private var rootPackage: String? = null

    @Parameter(property = "package-filter")
    private var packageFilter: String? = null

    @Parameter(property = "include-external")
    private var includeExternal: String? = null

    @Parameter(property = "dsm-depth")
    private var dsmDepth: String? = null

    @Parameter(property = "dsm-html")
    private var dsmHtml: String? = null

    @Parameter(property = "cycles")
    private var cycles: String? = null

    @Parameter(property = "cycle")
    private var cycle: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    @Parameter(property = "plan-file")
    private var planFile: String? = null

    @Parameter(property = "multi-module")
    private var multiModule: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = DsmConfig.parse(TaskRegistry.DSM.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))
        config.deprecations().forEach { log.warn(it) }

        if (TaskRegistry.MULTI_MODULE.parseFrom(project.applyConfigDefaults(buildPropertyMap()))) {
            log.warn("--multi-module is not yet implemented for Maven (Gradle only for now) — analyzing this module only.")
        }

        val taggedDirs = project.taggedClassDirectories()
        val classDirectories = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        val output = DsmOrchestrator.run(config, taggedDirs, loadPlanSteps(planFile), reportFile)

        output.skippedFileWarning?.let { log.warn(it) }
        val matrix = output.matrix

        if (matrix.packages.isEmpty() && config.cycleFilter == null && !config.cyclesOnly) {
            val packageCount = output.projectClasses.map { it.packageName() }.distinct().size
            val hints = DsmFormatter.noResultsHints(packageCount)
            println(OutputWrapper.emptyResult(config.format, "No inter-package dependencies found.", hints))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> if (config.cyclesOnly || config.cycleFilter != null) DsmFormatter.formatCycles(matrix, config.cycleFilter) else DsmFormatter.format(matrix)
        OutputFormat.JSON -> if (config.cyclesOnly || config.cycleFilter != null) DsmFormatter.formatCyclesJson(matrix, config.cycleFilter) else DsmFormatter.formatJson(matrix)
        OutputFormat.LLM -> if (config.cyclesOnly || config.cycleFilter != null) DsmFormatter.formatCyclesLlm(matrix, config.cycleFilter) else DsmFormatter.formatLlm(matrix)
    }
})

        if (config.htmlPath != null) {
            val htmlFile = File(project.basedir, config.htmlPath)
            htmlFile.parentFile?.mkdirs()
            htmlFile.writeText(DsmHtmlRenderer.render(matrix))
            println("DSM HTML written to: ${htmlFile.absolutePath}")
        }
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        rootPackage?.let { put("root-package", it) }
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        dsmDepth?.let { put("dsm-depth", it) }
        dsmHtml?.let { put("dsm-html", it) }
        cycles?.let { put("cycles", it) }
        cycle?.let { put("cycle", it) }
        scope?.let { put("scope", it) }
        planFile?.let { put("plan-file", it) }
        multiModule?.let { put("multi-module", it) }
    }
}
