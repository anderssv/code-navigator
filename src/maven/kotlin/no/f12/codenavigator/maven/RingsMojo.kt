package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.SourceSetResolver
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.EmergentRingDetector
import no.f12.codenavigator.navigation.dsm.EmergentRingFormatter
import no.f12.codenavigator.navigation.dsm.RingDetector
import no.f12.codenavigator.navigation.dsm.RingFormatter
import no.f12.codenavigator.navigation.dsm.HintsConfigGenerator
import no.f12.codenavigator.navigation.dsm.PlanMutator
import no.f12.codenavigator.navigation.dsm.RingsHintsConfig
import no.f12.codenavigator.navigation.dsm.TestInvolvement
import no.f12.codenavigator.navigation.types.ClassName
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

        val props = TaskRegistry.RINGS.enhanceProperties(buildPropertyMap())
        val outputFormat = ParamDef.parseFormat(props)
        val scopeFilter = Scope.parse(props["scope"])
        val modeVal = props["mode"] ?: "emergent"
        val bootstrap = props["bootstrap-config"] == "true"
        val failOnViolationVal = TaskRegistry.FAIL_ON_VIOLATION.parseFrom(props)
        val maxViolationsVal = TaskRegistry.MAX_VIOLATIONS.parseFrom(props)

        val classDirectories = project.taggedClassDirectories()
            .filter { scopeFilter.matchesSourceSet(it.second) }
            .map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val projectClasses = scanProjectClasses(classDirectories)

        val (output, violationCount) = when {
            bootstrap && modeVal == "emergent" -> bootstrapConfig(classDirectories, projectClasses) to 0
            modeVal == "emergent" -> detectEmergent(classDirectories, projectClasses, scopeFilter, outputFormat)
            else -> detectPackageLevel(classDirectories, projectClasses, outputFormat)
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

    private fun detectPackageLevel(classDirectories: List<File>, projectClasses: Set<ClassName>, format: OutputFormat): Pair<String, Int> {
        val extractResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = false, filterTargets = true)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(extractResult.skippedFiles, reportFile)?.let { log.warn(it) }
        // Package mode does not apply cnav-config.json ring names: a topological-depth ranking
        // can't honestly assert a semantic layer label. The names belong to --mode=emergent.
        val assignment = RingDetector.detect(applyPlanFile(extractResult.data, planFile, log))
        val rings = RingFormatter.format(assignment, format = format)
        return "${RingFormatter.PACKAGE_MODE_NOTICE}\n\n$rings" to assignment.violations.size
    }

    private fun bootstrapConfig(classDirectories: List<File>, projectClasses: Set<ClassName>): String {
        val projectResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = false, filterTargets = true, includeSamePackage = true)
        val externalResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = true, filterTargets = false, includeSamePackage = true)
        val externalOnly = externalResult.data.filter { it.targetClass !in projectClasses }

        val result = EmergentRingDetector.detect(projectResult.data, externalOnly, projectClasses)
        return HintsConfigGenerator.generate(result.classRings)
    }

    private fun detectEmergent(classDirectories: List<File>, projectClasses: Set<ClassName>, scope: Scope, format: OutputFormat): Pair<String, Int> {
        val projectResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = false, filterTargets = true, includeSamePackage = true)
        val externalResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = true, filterTargets = false, includeSamePackage = true)

        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(projectResult.skippedFiles, reportFile)?.let { log.warn(it) }

        val plan = loadPlanSteps(planFile)
        if (plan.isNotEmpty()) {
            log.info("Applying plan: ${plan.size} step(s) from $planFile")
        }
        val mutatedClasses = PlanMutator.applyToClassSet(projectClasses, plan)
        val mutatedProjectDeps = PlanMutator.apply(projectResult.data, plan, dropSamePackageEdges = false)
        val mutatedExternalDeps = PlanMutator.apply(externalResult.data, plan, dropSamePackageEdges = false)
        val externalOnly = mutatedExternalDeps.filter { it.targetClass !in mutatedClasses }

        val hintsConfig = RingsHintsConfig.loadFromDirectory(project.basedir)
        val result = EmergentRingDetector.detect(mutatedProjectDeps, externalOnly, mutatedClasses, hintsConfig)
        val ringNames = hintsConfig?.ringIndexNames() ?: emptyMap()
        val rings = EmergentRingFormatter.format(result, ringNames, hasHints = hintsConfig != null && hintsConfig.hasHints(), format = format)

        val testNotice = if (scope == Scope.ALL) {
            val resolver = SourceSetResolver.from(project.taggedClassDirectories())
            val edges = result.violations.map { it.sourceClass to it.targetClass }
            TestInvolvement.notice(TestInvolvement.count(edges) { resolver.sourceSetOf(it) }, "violations")
        } else {
            null
        }
        return (if (testNotice != null) "$rings\n\n$testNotice" else rings) to result.violations.size
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
