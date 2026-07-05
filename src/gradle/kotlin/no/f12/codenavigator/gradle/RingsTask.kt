package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.navigation.dsm.EmergentRingFormatter
import no.f12.codenavigator.navigation.dsm.EmergentRingsOutput
import no.f12.codenavigator.navigation.dsm.PackageRingsOutput
import no.f12.codenavigator.navigation.dsm.RingFormatter
import no.f12.codenavigator.navigation.dsm.RingsAnalysis
import no.f12.codenavigator.navigation.dsm.RingsOrchestrator
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.registry.TaskRegistry
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class RingsTask : CodeNavigatorTask() {

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    @Option(option = "mode", description = "Analysis mode: emergent (default, class-level ring detection) or package (package-level by topological depth)")
    @get:Internal
    var mode: String? = null

    @Option(option = "bootstrap-config", description = "Generate a starting cnav-config.json based on emergent ring analysis — best-effort, meant to be reviewed and tweaked")
    @get:Internal
    var bootstrapConfig: Boolean? = null

    @Option(option = "fail-on-violation", description = "Fail the build when ring violation count exceeds --max-violations")
    @get:Internal
    var failOnViolation: String? = null

    @Option(option = "max-violations", description = "Max allowed ring violations before failing the build (used with --fail-on-violation)")
    @get:Internal
    var maxViolations: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        scope?.let { put("scope", it) }
        mode?.let { put("mode", it) }
        bootstrapConfig?.let { put("bootstrap-config", "true") }
        failOnViolation?.let { put("fail-on-violation", it) }
        maxViolations?.let { put("max-violations", it) }
    }

    @TaskAction
    fun detectRings() {
        val extension = project.codeNavigatorExtension()
        val props = extension.resolveProperties(TaskRegistry.RINGS.enhanceProperties(buildOptionsMap()))

        val format = ParamDef.parseFormat(props)
        val scopeVal = Scope.parse(props["scope"])
        val modeVal = props["mode"] ?: "emergent"
        val bootstrap = props["bootstrap-config"] == "true"
        val failOnViolationVal = TaskRegistry.FAIL_ON_VIOLATION.parseFrom(props)
        val maxViolationsVal = TaskRegistry.MAX_VIOLATIONS.parseFrom(props)

        val taggedDirs = project.taggedClassDirectories()
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        val analysis = RingsOrchestrator.run(taggedDirs, scopeVal, modeVal, bootstrap, loadPlanSteps(), project.projectDir, reportFile)

        val (output, violationCount) = when (analysis) {
            is RingsAnalysis.Bootstrap -> analysis.hintsConfigJson to 0
            is RingsAnalysis.Package -> renderPackage(analysis.output, format)
            is RingsAnalysis.Emergent -> renderEmergent(analysis.output, format)
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(format) { format ->
            when (format) {
                OutputFormat.TEXT, OutputFormat.DIFF -> output
                OutputFormat.JSON -> output
                OutputFormat.LLM -> output
            }
        })

        if (failOnViolationVal && violationCount > maxViolationsVal) {
            throw GradleException("cnavRings found $violationCount violation(s), exceeding --max-violations=$maxViolationsVal")
        }
    }

    private fun renderPackage(output: PackageRingsOutput, format: OutputFormat): Pair<String, Int> {
        output.skippedFileWarning?.let { logger.warn(it) }
        val rings = RingFormatter.format(output.assignment, format = format)
        return "${RingFormatter.PACKAGE_MODE_NOTICE}\n\n$rings" to output.assignment.violations.size
    }

    private fun renderEmergent(output: EmergentRingsOutput, format: OutputFormat): Pair<String, Int> {
        output.skippedFileWarning?.let { logger.warn(it) }
        val rings = EmergentRingFormatter.format(output.result, output.ringNames, hasHints = output.hasHints, format = format)
        val rendered = if (output.testNotice != null) "$rings\n\n${output.testNotice}" else rings
        return rendered to output.result.violations.size
    }
}
