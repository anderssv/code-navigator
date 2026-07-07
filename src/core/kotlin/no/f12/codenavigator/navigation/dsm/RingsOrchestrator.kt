package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.SourceSetResolver
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File

data class PackageRingsOutput(
    val assignment: RingAssignment,
    val skippedFileWarning: String?,
)

data class EmergentRingsOutput(
    val result: ClassRingAssignment,
    val ringNames: Map<Int, String>,
    val hasHints: Boolean,
    val testInvolvement: TestInvolvement.Counts?,
    val skippedFileWarning: String?,
)

sealed class RingsAnalysis {
    data class Bootstrap(val hintsConfigJson: String) : RingsAnalysis()
    data class Package(val output: PackageRingsOutput) : RingsAnalysis()
    data class Emergent(val output: EmergentRingsOutput) : RingsAnalysis()
}

/** Shared by RingsTask (Gradle) and RingsMojo (Maven) so both build tools run the exact same pipeline. */
object RingsOrchestrator {

    fun run(
        taggedDirs: List<Pair<File, SourceSet>>,
        scope: Scope,
        mode: String,
        bootstrap: Boolean,
        plan: List<PlanStep>,
        projectDir: File,
        reportFile: File,
    ): RingsAnalysis {
        val classDirectories = taggedDirs.filter { scope.matchesSourceSet(it.second) }.map { it.first }
        val projectClasses = scanProjectClasses(classDirectories)

        return when {
            bootstrap && mode == "emergent" -> RingsAnalysis.Bootstrap(bootstrapConfig(classDirectories, projectClasses))
            mode == "emergent" -> RingsAnalysis.Emergent(
                detectEmergent(classDirectories, projectClasses, taggedDirs, scope, plan, projectDir, reportFile),
            )
            else -> RingsAnalysis.Package(detectPackageLevel(classDirectories, projectClasses, plan, reportFile))
        }
    }

    private fun detectPackageLevel(
        classDirectories: List<File>,
        projectClasses: Set<ClassName>,
        plan: List<PlanStep>,
        reportFile: File,
    ): PackageRingsOutput {
        val extractResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = false, filterTargets = true)
        val skippedFileWarning = SkippedFileReporter.report(extractResult.skippedFiles, reportFile)
        val assignment = RingDetector.detect(PlanMutator.apply(extractResult.data, plan))
        return PackageRingsOutput(assignment, skippedFileWarning)
    }

    private fun bootstrapConfig(classDirectories: List<File>, projectClasses: Set<ClassName>): String {
        val allResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = true, filterTargets = false, includeSamePackage = true)
        val projectDeps = allResult.data.filter { it.targetClass in projectClasses }
        val externalDeps = allResult.data.filter { it.targetClass !in projectClasses }

        val result = EmergentRingDetector.detect(projectDeps, externalDeps, projectClasses)
        return HintsConfigGenerator.generate(result.classRings)
    }

    private fun detectEmergent(
        classDirectories: List<File>,
        projectClasses: Set<ClassName>,
        taggedDirs: List<Pair<File, SourceSet>>,
        scope: Scope,
        plan: List<PlanStep>,
        projectDir: File,
        reportFile: File,
    ): EmergentRingsOutput {
        val allResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = true, filterTargets = false, includeSamePackage = true)
        val skippedFileWarning = SkippedFileReporter.report(allResult.skippedFiles, reportFile)

        val mutatedDeps = PlanMutator.apply(allResult.data, plan, dropSamePackageEdges = false)
        val mutatedClasses = PlanMutator.applyToClassSet(projectClasses, plan)
        val projectDeps = mutatedDeps.filter { it.targetClass in mutatedClasses }
        val externalDeps = mutatedDeps.filter { it.targetClass !in mutatedClasses }

        val hintsConfig = RingsHintsConfig.loadFromDirectory(projectDir)
        val result = EmergentRingDetector.detect(projectDeps, externalDeps, mutatedClasses, hintsConfig)
        val ringNames = hintsConfig?.ringIndexNames() ?: emptyMap()

        val testInvolvement = if (scope == Scope.ALL) {
            val resolver = SourceSetResolver.from(taggedDirs)
            val edges = result.violations.map { it.sourceClass to it.targetClass }
            TestInvolvement.count(edges) { resolver.sourceSetOf(it) }
        } else {
            null
        }

        return EmergentRingsOutput(
            result = result,
            ringNames = ringNames,
            hasHints = hintsConfig != null && hintsConfig.hasHints(),
            testInvolvement = testInvolvement,
            skippedFileWarning = skippedFileWarning,
        )
    }
}
