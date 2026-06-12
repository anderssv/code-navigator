package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.SourceSetResolver
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.PlanMutator
import no.f12.codenavigator.navigation.dsm.EmergentRingDetector
import no.f12.codenavigator.navigation.dsm.EmergentRingFormatter
import no.f12.codenavigator.navigation.dsm.RingDetector
import no.f12.codenavigator.navigation.dsm.RingFormatter
import no.f12.codenavigator.navigation.dsm.HintsConfigGenerator
import no.f12.codenavigator.navigation.dsm.RingsHintsConfig
import no.f12.codenavigator.navigation.dsm.TestInvolvement
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

    @Option(option = "mode", description = "Analysis mode: emergent (default, class-level ring detection) or package (package-level by topological depth)")
    @get:Internal
    var mode: String? = null

    @Option(option = "bootstrap-config", description = "Generate a starting cnav-config.json based on emergent ring analysis — best-effort, meant to be reviewed and tweaked")
    @get:Internal
    var bootstrapConfig: Boolean? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        scope?.let { put("scope", it) }
        mode?.let { put("mode", it) }
        bootstrapConfig?.let { put("bootstrap-config", "true") }
    }

    @TaskAction
    fun detectRings() {
        val extension = project.codeNavigatorExtension()
        val props = extension.resolveProperties(TaskRegistry.RINGS.enhanceProperties(buildOptionsMap()))

        val format = ParamDef.parseFormat(props)
        val scopeVal = Scope.parse(props["scope"])
        val modeVal = props["mode"] ?: "emergent"
        val bootstrap = props["bootstrap-config"] == "true"

        val classDirectories = project.taggedClassDirectories()
            .filter { scopeVal.matchesSourceSet(it.second) }
            .map { it.first }
        val projectClasses = scanProjectClasses(classDirectories)

        val output = when {
            bootstrap && modeVal == "emergent" -> bootstrapConfig(classDirectories, projectClasses)
            modeVal == "emergent" -> detectEmergent(classDirectories, projectClasses, scopeVal)
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
        // Package mode does not apply cnav-config.json ring names: a topological-depth ranking
        // can't honestly assert a semantic layer label. The names belong to --mode=emergent.
        val rings = RingFormatter.format(RingDetector.detect(applyPlan(extractResult.data)))
        return "${RingFormatter.PACKAGE_MODE_NOTICE}\n\n$rings"
    }

    private fun loadHintsConfig(): RingsHintsConfig? =
        RingsHintsConfig.loadFromDirectory(project.projectDir)

    private fun bootstrapConfig(classDirectories: List<File>, projectClasses: Set<no.f12.codenavigator.navigation.types.ClassName>): String {
        val allResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = true, filterTargets = false, includeSamePackage = true)
        val projectDeps = allResult.data.filter { it.targetClass in projectClasses }
        val externalDeps = allResult.data.filter { it.targetClass !in projectClasses }

        val result = EmergentRingDetector.detect(projectDeps, externalDeps, projectClasses)
        return HintsConfigGenerator.generate(result.classRings)
    }

    private fun detectEmergent(classDirectories: List<File>, projectClasses: Set<no.f12.codenavigator.navigation.types.ClassName>, scope: Scope): String {
        val allResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = true, filterTargets = false, includeSamePackage = true)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(allResult.skippedFiles, reportFile)?.let { logger.warn(it) }

        val mutatedDeps = applyPlan(allResult.data)
        val mutatedClasses = PlanMutator.applyToClassSet(projectClasses, loadPlanSteps())
        val projectDeps = mutatedDeps.filter { it.targetClass in mutatedClasses }
        val externalDeps = mutatedDeps.filter { it.targetClass !in mutatedClasses }

        val hintsConfig = loadHintsConfig()
        val result = EmergentRingDetector.detect(projectDeps, externalDeps, mutatedClasses, hintsConfig)
        val ringNames = hintsConfig?.ringIndexNames() ?: emptyMap()
        val rings = EmergentRingFormatter.format(result, ringNames, hasHints = hintsConfig != null && hintsConfig.hasHints())

        val testNotice = if (scope == Scope.ALL) {
            val resolver = SourceSetResolver.from(project.taggedClassDirectories())
            val edges = result.violations.map { it.sourceClass to it.targetClass }
            TestInvolvement.notice(TestInvolvement.count(edges) { resolver.sourceSetOf(it) }, "violations")
        } else {
            null
        }
        return if (testNotice != null) "$rings\n\n$testNotice" else rings
    }
}
