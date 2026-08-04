package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.dsm.CyclesConfig
import no.f12.codenavigator.navigation.dsm.CyclesFormatter
import no.f12.codenavigator.navigation.dsm.CyclesOrchestrator
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "cycles")
@Execute(phase = LifecyclePhase.COMPILE)
class CyclesMojo : AbstractMojo() {

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
    private var depth: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    @Parameter(property = "plan-file")
    private var planFile: String? = null

    @Parameter(property = "fail-on-violation")
    private var failOnViolation: String? = null

    @Parameter(property = "max-cycles")
    private var maxCycles: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val props = TaskRegistry.CYCLE_DETECTION.enhanceProperties(project.applyConfigDefaults(buildPropertyMap()))
        val config = CyclesConfig.parse(props)
        config.deprecations().forEach { log.warn(it) }

        val taggedDirs = project.taggedClassDirectories()
        val classDirectories = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        val output = CyclesOrchestrator.run(config, taggedDirs, loadPlanSteps(planFile), reportFile)

        output.skippedFileWarning?.let { log.warn(it) }

        if (output.details.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No package cycles detected."))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> CyclesFormatter.format(output.details, displayPrefix = output.displayPrefix, testInvolvement = output.testInvolvement, moduleLabels = output.moduleLabels)
        OutputFormat.JSON -> CyclesFormatter.formatJson(output.details, displayPrefix = output.displayPrefix, testInvolvement = output.testInvolvement, moduleLabels = output.moduleLabels)
        OutputFormat.LLM -> CyclesFormatter.formatLlm(output.details, displayPrefix = output.displayPrefix, testInvolvement = output.testInvolvement, moduleLabels = output.moduleLabels)
    }
})

        if (config.failOnViolation && output.details.size > config.maxCycles) {
            throw MojoFailureException("cnav:cycles found ${output.details.size} cycle(s), exceeding --max-cycles=${config.maxCycles}")
        }
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        rootPackage?.let { put("root-package", it) }
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        depth?.let { put("dsm-depth", it) }
        scope?.let { put("scope", it) }
        planFile?.let { put("plan-file", it) }
        failOnViolation?.let { put("fail-on-violation", it) }
        maxCycles?.let { put("max-cycles", it) }
    }
}
