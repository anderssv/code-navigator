package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.PlanMutator
import no.f12.codenavigator.navigation.dsm.EmergentRingDetector
import no.f12.codenavigator.navigation.dsm.EmergentRingFormatter
import no.f12.codenavigator.navigation.dsm.RingDetector
import no.f12.codenavigator.navigation.dsm.RingFormatter
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.registry.TaskRegistry
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

    @Option(option = "mode", description = "Analysis mode: package (default) or emergent (class-level ring detection)")
    @get:Internal
    var mode: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        scope?.let { put("scope", it) }
        mode?.let { put("mode", it) }
    }

    @TaskAction
    fun detectRings() {
        val extension = project.codeNavigatorExtension()
        val props = extension.resolveProperties(TaskRegistry.RINGS.enhanceProperties(buildOptionsMap()))

        val format = ParamDef.parseFormat(props)
        val scopeVal = Scope.parse(props["scope"])
        val modeVal = props["mode"] ?: "package"

        val classDirectories = project.taggedClassDirectories()
            .filter { scopeVal.matchesSourceSet(it.second) }
            .map { it.first }
        val projectClasses = scanProjectClasses(classDirectories)

        val output = when (modeVal) {
            "emergent" -> detectEmergent(classDirectories, projectClasses)
            else -> detectPackageLevel(classDirectories)
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(format) { format ->
            when (format) {
                OutputFormat.TEXT, OutputFormat.DIFF -> output
                OutputFormat.JSON -> output
                OutputFormat.LLM -> output
            }
        })
    }

    private fun detectPackageLevel(classDirectories: List<File>): String {
        val projectClasses = scanProjectClasses(classDirectories)
        val extractResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = false, filterTargets = true)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(extractResult.skippedFiles, reportFile)?.let { logger.warn(it) }
        return RingFormatter.format(RingDetector.detect(applyPlan(extractResult.data)))
    }

    private fun detectEmergent(classDirectories: List<File>, projectClasses: Set<no.f12.codenavigator.navigation.types.ClassName>): String {
        val allResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = true, filterTargets = false, includeSamePackage = true)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(allResult.skippedFiles, reportFile)?.let { logger.warn(it) }

        val mutatedDeps = applyPlan(allResult.data)
        val mutatedClasses = PlanMutator.applyToClassSet(projectClasses, loadPlanSteps())
        val projectDeps = mutatedDeps.filter { it.targetClass in mutatedClasses }
        val externalDeps = mutatedDeps.filter { it.targetClass !in mutatedClasses }

        val result = EmergentRingDetector.detect(projectDeps, externalDeps, mutatedClasses)
        return EmergentRingFormatter.format(result)
    }
}
