package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.SourceSetResolver
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.bytecode.modulesOfClass
import no.f12.codenavigator.navigation.types.AnalysisWorkspace
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File

data class HexRingsOutput(
    val layering: RingLayering,
    val graph: RingGraph,
    val unhonoured: List<UnhonouredDirective>,
    val expectedRingCount: Int?,
    val testInvolvement: TestInvolvement.Counts?,
    val skippedFileWarning: String?,
    val modulesOfClass: Map<ClassName, Set<String>> = emptyMap(),
    val serviceTier: Set<ClassName> = emptySet(),
    val domainServiceViolations: List<DomainServiceViolation> = emptyList(),
    val portBypassViolations: List<PortBypassViolation> = emptyList(),
) {
    /**
     * Null when no `rings.expected` pin is configured. Otherwise the pinned count, whenever it
     * disagrees with what was actually detected — ring counts move as ports are added or removed,
     * so a project that wants stable reports pins the count and gets told when reality diverges.
     */
    val unexpectedRingCount: Int?
        get() = expectedRingCount?.takeIf { it != layering.ringCount }
}

sealed class RingsAnalysis {
    data class Bootstrap(val configJson: String) : RingsAnalysis()
    data class Hexagonal(val output: HexRingsOutput) : RingsAnalysis()
}

/** Shared by RingsTask (Gradle) and RingsMojo (Maven) so both build tools run the exact same pipeline. */
object RingsOrchestrator {

    fun run(
        workspace: AnalysisWorkspace,
        scope: Scope,
        bootstrap: Boolean,
        plan: List<PlanStep>,
        projectDir: File,
        reportFile: File,
    ): RingsAnalysis = run(
        workspace.taggedClassDirectories(),
        scope,
        bootstrap,
        plan,
        projectDir,
        reportFile,
        workspace.modulesOfClass(),
    )

    fun run(
        taggedDirs: List<Pair<File, SourceSet>>,
        scope: Scope,
        bootstrap: Boolean,
        plan: List<PlanStep>,
        projectDir: File,
        reportFile: File,
        modulesOfClass: Map<ClassName, Set<String>> = emptyMap(),
    ): RingsAnalysis {
        val classDirectories = taggedDirs.filter { scope.matchesSourceSet(it.second) }.map { it.first }
        val projectClasses = scanProjectClasses(classDirectories)

        val config = if (bootstrap) RingsConfig() else RingsConfig.loadFromDirectory(projectDir)
        val output = analyse(classDirectories, projectClasses, taggedDirs, scope, plan, config, reportFile, modulesOfClass)

        return if (bootstrap) {
            RingsAnalysis.Bootstrap(RingsConfigGenerator.generate(output.graph, output.layering))
        } else {
            RingsAnalysis.Hexagonal(output)
        }
    }

    /** Exposed so cnavReport and cnavConverge report the same rings and violations cnavRings does. */
    fun analyse(
        classDirectories: List<File>,
        projectClasses: Set<ClassName>,
        taggedDirs: List<Pair<File, SourceSet>>,
        scope: Scope,
        plan: List<PlanStep>,
        config: RingsConfig,
        reportFile: File,
        modulesOfClass: Map<ClassName, Set<String>>,
    ): HexRingsOutput {
        val extracted = DsmDependencyExtractor.extract(
            classDirectories,
            projectClasses,
            packageFilter = null,
            includeExternal = true,
            filterTargets = false,
            includeSamePackage = true,
        )
        val skippedFileWarning = SkippedFileReporter.report(extracted.skippedFiles, reportFile)

        val mutatedDeps = PlanMutator.apply(extracted.data, plan, dropSamePackageEdges = false)
        val mutatedClasses = PlanMutator.applyToClassSet(projectClasses, plan)
        val mutatedModulesOfClass = PlanMutator.applyToClassMap(modulesOfClass, plan)
        val classKinds = PlanMutator.applyToClassMap(ClassTypeCollector.collect(classDirectories), plan)
        val signatureTypes = PlanMutator.applyToClassMap(SignatureTypeScanner.scan(classDirectories), plan)
        val supertypes = PlanMutator.applyToSupertypes(
            DsmDependencyExtractor.extractStructuralSupertypes(classDirectories, projectClasses),
            plan,
        )

        val overridden = RingConfigOverrides.apply(
            RingGraphBuilder.build(
                projectClasses = mutatedClasses,
                projectDeps = mutatedDeps.filter { it.targetClass in mutatedClasses },
                externalDeps = mutatedDeps.filter { it.targetClass !in mutatedClasses },
                classKinds = classKinds,
                supertypes = supertypes,
                signatureTypes = signatureTypes,
                configuredCompositionRoots = config.compositionRoots
                    .flatMapTo(mutableSetOf()) { pattern ->
                        mutatedClasses.filter { RingsConfig.matchesGlob(it.value, pattern) }
                    },
                extraValuePackages = config.valuePackages.toSet(),
                extraFrameworkPackages = config.frameworkPackages.toSet(),
            ),
            config,
        )

        val layering = InversionRingDetector.detect(overridden.graph)

        val strippedGraph = overridden.graph.withoutCompositionRoots()
        val rawServiceTier = ServiceTierDetector.detect(strippedGraph)
        val serviceTierOverride = RingConfigOverrides.applyServiceTier(rawServiceTier, strippedGraph.classes, config)
        val serviceTier = serviceTierOverride.serviceTier

        return HexRingsOutput(
            layering = layering,
            graph = overridden.graph,
            unhonoured = overridden.unhonoured + serviceTierOverride.unhonoured,
            expectedRingCount = config.expectedRingCount,
            testInvolvement = testInvolvement(scope, taggedDirs, layering),
            skippedFileWarning = skippedFileWarning,
            modulesOfClass = mutatedModulesOfClass,
            serviceTier = serviceTier,
            domainServiceViolations = ServiceTierDetector.domainServiceViolations(strippedGraph, serviceTier),
            portBypassViolations = ServiceTierDetector.portBypassViolations(strippedGraph),
        )
    }

    private fun testInvolvement(
        scope: Scope,
        taggedDirs: List<Pair<File, SourceSet>>,
        layering: RingLayering,
    ): TestInvolvement.Counts? {
        if (scope != Scope.ALL || taggedDirs.isEmpty()) return null
        val resolver = SourceSetResolver.from(taggedDirs)
        val edges = layering.violations.map { it.sourceClass to it.targetClass }
        return TestInvolvement.count(edges) { resolver.sourceSetOf(it) }
    }
}
