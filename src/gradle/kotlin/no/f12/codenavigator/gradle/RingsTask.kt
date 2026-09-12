package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.navigation.dsm.HexRingFormatter
import no.f12.codenavigator.navigation.dsm.HexRingsOutput
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
abstract class RingsTask : WorkspaceAnalysisTask() {

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    @Option(option = "mode", description = "Removed: cnavRings no longer has modes")
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

    override fun analysisOptionsMap(): Map<String, String?> = buildMap {
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

        require(props["mode"] == null) {
            "cnavRings no longer has a --mode option. It now detects hexagonal rings from dependency " +
                "inversions; the old emergent/package modes were both topological depth, which is a " +
                "different measurement. Remove --mode from the invocation."
        }

        val format = ParamDef.parseFormat(props)
        val scopeVal = Scope.parse(props["scope"])
        val bootstrap = props["bootstrap-config"] == "true"
        val failOnViolationVal = TaskRegistry.FAIL_ON_VIOLATION.parseFrom(props)
        val maxViolationsVal = TaskRegistry.MAX_VIOLATIONS.parseFrom(props)

        val workspace = resolveAnalysisWorkspace()
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        val analysis = RingsOrchestrator.run(workspace, scopeVal, bootstrap, loadPlanSteps(), project.projectDir, reportFile)

        val (output, violationCount) = when (analysis) {
            is RingsAnalysis.Bootstrap -> analysis.configJson to 0
            is RingsAnalysis.Hexagonal -> render(analysis.output, format)
        }

        logger.quiet(OutputWrapper.wrap(output, format))

        if (failOnViolationVal && violationCount > maxViolationsVal) {
            throw GradleException("cnavRings found $violationCount violation(s), exceeding --max-violations=$maxViolationsVal")
        }
    }

    private fun render(output: HexRingsOutput, format: OutputFormat): Pair<String, Int> {
        output.skippedFileWarning?.let { logger.warn(it) }
        return HexRingFormatter.format(output, format) to output.layering.violations.size
    }
}
