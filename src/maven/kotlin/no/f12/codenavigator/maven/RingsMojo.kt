package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.dsm.EmergentRingFormatter
import no.f12.codenavigator.navigation.dsm.EmergentRingsOutput
import no.f12.codenavigator.navigation.dsm.PackageRingsOutput
import no.f12.codenavigator.navigation.dsm.RingFormatter
import no.f12.codenavigator.navigation.dsm.RingsAnalysis
import no.f12.codenavigator.navigation.dsm.RingsOrchestrator
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "rings")
@Execute(phase = LifecyclePhase.COMPILE)
class RingsMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    @Parameter(property = "plan-file")
    private var planFile: String? = null

    @Parameter(property = "mode")
    private var mode: String? = null

    @Parameter(property = "bootstrap-config")
    private var bootstrapConfig: String? = null

    @Parameter(property = "fail-on-violation")
    private var failOnViolation: String? = null

    @Parameter(property = "max-violations")
    private var maxViolations: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val props = TaskRegistry.RINGS.enhanceProperties(project.applyConfigDefaults(buildPropertyMap()))
        val outputFormat = ParamDef.parseFormat(props)
        val scopeFilter = Scope.parse(props["scope"])
        val modeVal = props["mode"] ?: "emergent"
        val bootstrap = props["bootstrap-config"] == "true"
        val failOnViolationVal = TaskRegistry.FAIL_ON_VIOLATION.parseFrom(props)
        val maxViolationsVal = TaskRegistry.MAX_VIOLATIONS.parseFrom(props)

        val taggedDirs = project.taggedClassDirectories()
        val classDirectories = taggedDirs.filter { scopeFilter.matchesSourceSet(it.second) }.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        val analysis = RingsOrchestrator.run(taggedDirs, scopeFilter, modeVal, bootstrap, loadPlanSteps(planFile), project.basedir, reportFile)

        val (output, violationCount) = when (analysis) {
            is RingsAnalysis.Bootstrap -> analysis.hintsConfigJson to 0
            is RingsAnalysis.Package -> renderPackage(analysis.output, outputFormat)
            is RingsAnalysis.Emergent -> renderEmergent(analysis.output, outputFormat)
        }

        println(OutputWrapper.formatAndWrap(outputFormat) { format ->
            when (format) {
                OutputFormat.TEXT, OutputFormat.DIFF -> output
                OutputFormat.JSON -> output
                OutputFormat.LLM -> output
            }
        })

        if (failOnViolationVal && violationCount > maxViolationsVal) {
            throw MojoFailureException("cnav:rings found $violationCount violation(s), exceeding --max-violations=$maxViolationsVal")
        }
    }

    private fun renderPackage(output: PackageRingsOutput, format: OutputFormat): Pair<String, Int> {
        output.skippedFileWarning?.let { log.warn(it) }
        val rings = RingFormatter.format(output.assignment, format = format)
        return "${RingFormatter.PACKAGE_MODE_NOTICE}\n\n$rings" to output.assignment.violations.size
    }

    private fun renderEmergent(output: EmergentRingsOutput, format: OutputFormat): Pair<String, Int> {
        output.skippedFileWarning?.let { log.warn(it) }
        val rings = EmergentRingFormatter.format(output.result, output.ringNames, hasHints = output.hasHints, format = format)
        val rendered = if (output.testNotice != null) "$rings\n\n${output.testNotice}" else rings
        return rendered to output.result.violations.size
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        scope?.let { put("scope", it) }
        planFile?.let { put("plan-file", it) }
        mode?.let { put("mode", it) }
        bootstrapConfig?.let { put("bootstrap-config", it) }
        failOnViolation?.let { put("fail-on-violation", it) }
        maxViolations?.let { put("max-violations", it) }
    }
}
