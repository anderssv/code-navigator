package no.f12.codenavigator.navigation.converge

import no.f12.codenavigator.analysis.ChangeCouplingBuilder
import no.f12.codenavigator.analysis.GitCommit
import no.f12.codenavigator.analysis.HotspotBuilder
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.classinfo.ClassScanner
import no.f12.codenavigator.navigation.complexity.ClassComplexityAnalyzer
import no.f12.codenavigator.navigation.dsm.CycleDetector
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.DsmMatrixBuilder
import no.f12.codenavigator.navigation.dsm.RingDetector
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File

/**
 * Shared by ConvergeTask (Gradle) and ConvergeMojo (Maven) so both build tools run the exact same
 * pipeline. Takes already-fetched git [commits] rather than calling `GitLogRunner` itself, so the
 * cycle/ring/coupling-join logic can be unit-tested with synthetic commits instead of a real git repo.
 */
object ConvergeOrchestrator {

    fun run(
        config: ConvergeConfig,
        taggedDirs: List<Pair<File, SourceSet>>,
        commits: List<GitCommit>,
        projectDir: File,
        cacheFile: File,
        reportFile: File,
    ): ConvergeOutput {
        val classDirectories = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }.map { it.first }
        return when (config.mode) {
            ConvergeMode.INTERSECT -> ConvergeOutput.Intersect(runIntersect(config, classDirectories, commits, reportFile))
            ConvergeMode.RISK -> ConvergeOutput.Risk(runRisk(config, taggedDirs, classDirectories, commits, projectDir, cacheFile, reportFile))
        }
    }

    /**
     * Result count above which intersect output is likely inflated by DI/test-wiring noise rather than
     * real production coupling. Chosen so a clean production run (e.g. this repo, ra-backend `--scope=prod`
     * ~12 findings) stays quiet while a test-included run (ra-backend `--scope=all` ~55) trips the advisory.
     * A hint threshold, not a hard limit — the full result is always returned.
     */
    private const val NOISE_ADVISORY_THRESHOLD = 20

    private fun normalize(a: PackageName, b: PackageName): Pair<PackageName, PackageName> =
        if (a.value <= b.value) a to b else b to a

    /**
     * Constructive advisory for a large intersect result — most such explosions come from manually-wired
     * DI or shared test infrastructure creating structural cycles that don't exist in production. Adapts to
     * what the user hasn't already tried: suggests `--scope=prod` only if still including test sources, and
     * `--exclude` only if no exclusion is set yet. Null when the result is small, or when both levers
     * are already pulled (the count is then genuinely high, not obviously noise).
     */
    internal fun advisoryFor(edgeCount: Int, config: ConvergeConfig): String? {
        if (edgeCount < NOISE_ADVISORY_THRESHOLD) return null
        val suggestions = buildList {
            if (config.scope == Scope.ALL) add("--scope=prod (drops test sources)")
            if (config.exclude == null) add("--exclude=<regex> (drops packages/classes matching a substring — e.g. a DI composition root or shared test context)")
        }
        if (suggestions.isEmpty()) return null
        // Persistence example uses whichever lever is the primary suggestion so it never contradicts the current run.
        val configExample = if (config.scope == Scope.ALL) "{\"defaults\":{\"scope\":\"prod\"}}" else "{\"defaults\":{\"exclude\":\"\\\\.di\\\\.\"}}"
        return "$edgeCount findings — a large result set that often reflects manually-wired DI or test infrastructure " +
            "creating structural cycles/coupling that don't exist in production, rather than real problems. " +
            "To narrow to production architecture, add ${suggestions.joinToString(" and/or ")}. " +
            "Persist your choice in cnav-config.json under \"defaults\" (e.g. $configExample) so every run is narrowed."
    }

    private fun matchesFilter(a: PackageName, b: PackageName, packageFilter: PackageName?): Boolean =
        packageFilter == null || a.startsWith(packageFilter) || b.startsWith(packageFilter)

    private fun runIntersect(
        config: ConvergeConfig,
        classDirectories: List<File>,
        commits: List<GitCommit>,
        reportFile: File,
    ): ConvergeIntersectOutput {
        val projectClasses = scanProjectClasses(classDirectories)
        val classInfos = ClassScanner.scan(classDirectories).data

        val extractResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, config.packageFilter, includeExternal = false, filterTargets = true)
        val skippedFileWarning = SkippedFileReporter.report(extractResult.skippedFiles, reportFile)
        // Excluded packages (e.g. a DI composition root or shared test infrastructure) are dropped from
        // the dependency graph before cycle/ring detection, not just hidden from the final edge list —
        // RingDetector already auto-excludes packages it detects as composition roots (3+ rings touched),
        // but --exclude lets a user manually back that up for a hub the heuristic doesn't catch, or for
        // test-only wiring (e.g. a shared test context) that isn't a composition root at all.
        val deps = extractResult.data.filter { dep ->
            config.exclude == null ||
                (!config.exclude.containsMatchIn(dep.sourcePackage.value) && !config.exclude.containsMatchIn(dep.targetPackage.value))
        }

        // Full depth (no truncation) so package granularity matches RingDetector's, which is also untruncated.
        val matrix = DsmMatrixBuilder.build(deps, PackageName(""), Int.MAX_VALUE)
        val adjacency = CycleDetector.adjacencyMapFrom(matrix)
        val cycles = CycleDetector.findCycles(adjacency)
        val cycleDetails = CycleDetector.enrich(cycles, matrix)
        val cyclePairs = cycleDetails.flatMap { it.edges }.map { normalize(it.from, it.to) }.toSet()

        val ringAssignment = RingDetector.detect(deps)
        val ringPairs = ringAssignment.reportableViolations.map { normalize(it.sourcePackage, it.targetPackage) }.toSet()

        val structuralPairs = cyclePairs + ringPairs

        val coupledPairs = ChangeCouplingBuilder.build(commits, config.minSharedRevs, config.minCoupling, config.maxChangesetSize, top = Int.MAX_VALUE)

        val pathIndex = SourcePathIndex.from(classInfos)
        val couplingByPair = mutableMapOf<Pair<PackageName, PackageName>, Int>()
        var unresolved = 0
        for (pair in coupledPairs) {
            val pkgA = pathIndex.resolvePackage(pair.entity)
            val pkgB = pathIndex.resolvePackage(pair.coupled)
            if (pkgA == null || pkgB == null) {
                unresolved++
                continue
            }
            if (pkgA == pkgB) continue
            if (!matchesFilter(pkgA, pkgB, config.packageFilter)) continue
            if (config.exclude?.containsMatchIn(pkgA.value) == true || config.exclude?.containsMatchIn(pkgB.value) == true) continue
            val key = normalize(pkgA, pkgB)
            couplingByPair[key] = maxOf(couplingByPair[key] ?: 0, pair.degree)
        }

        val allPairs = structuralPairs + couplingByPair.keys
        val totalFindings = allPairs.size
        val edges = allPairs.map { (source, target) ->
            val hasCycle = (source to target) in cyclePairs
            val hasRingViolation = (source to target) in ringPairs
            val degree = couplingByPair[source to target]
            val structural = hasCycle || hasRingViolation
            val verdict = when {
                structural && degree != null -> ConvergeVerdict.ACT_NOW
                structural -> ConvergeVerdict.LATENT
                else -> ConvergeVerdict.MISSING_ABSTRACTION
            }
            ConvergedEdge(source, target, verdict, hasCycle, hasRingViolation, degree)
        }.sortedWith(
            compareBy<ConvergedEdge> { it.verdict.ordinal }
                .thenByDescending { it.couplingDegree ?: -1 }
                .thenBy { it.source }
                .thenBy { it.target },
        ).take(config.top)

        return ConvergeIntersectOutput(edges, unresolved, skippedFileWarning, advisoryFor(totalFindings, config))
    }

    private fun runRisk(
        config: ConvergeConfig,
        taggedDirs: List<Pair<File, SourceSet>>,
        classDirectories: List<File>,
        commits: List<GitCommit>,
        projectDir: File,
        cacheFile: File,
        reportFile: File,
    ): ConvergeRiskOutput {
        val classInfos = ClassScanner.scan(classDirectories).data
        val classInfoByName = classInfos.associateBy { it.className }

        val callGraphResult = CallGraphCache.getOrBuildTagged(cacheFile, taggedDirs)
        val skippedFileWarning = SkippedFileReporter.report(callGraphResult.skippedFiles, reportFile)
        val complexities = ClassComplexityAnalyzer.analyze(callGraphResult.data, classPattern = ".*", projectOnly = true)
            .filter { config.exclude == null || !config.exclude.containsMatchIn(it.className.value) }

        val hotspotByPath = HotspotBuilder.build(commits, minRevs = 1, top = Int.MAX_VALUE, projectDir = projectDir)
            .associateBy { it.file }

        val couplingByPath = mutableMapOf<String, Int>()
        for (pair in ChangeCouplingBuilder.build(commits, config.minSharedRevs, config.minCoupling, config.maxChangesetSize, top = Int.MAX_VALUE)) {
            couplingByPath[pair.entity] = maxOf(couplingByPath[pair.entity] ?: 0, pair.degree)
            couplingByPath[pair.coupled] = maxOf(couplingByPath[pair.coupled] ?: 0, pair.degree)
        }

        val entries = complexities.mapNotNull { complexity ->
            val reconstructedPath = classInfoByName[complexity.className]?.reconstructedSourcePath ?: return@mapNotNull null
            val gitPath = hotspotByPath.keys.firstOrNull { it.endsWith(reconstructedPath) } ?: return@mapNotNull null
            val changeFrequency = hotspotByPath[gitPath]?.revisions ?: 0
            val complexityScore = complexity.fanOut + complexity.fanIn
            val couplingDegree = couplingByPath[gitPath]
            val riskScore = changeFrequency.toLong() * maxOf(complexityScore, 1) * (couplingDegree ?: 1)
            ConvergeRiskEntry(complexity.className, gitPath, changeFrequency, complexityScore, couplingDegree, riskScore)
        }.sortedByDescending { it.riskScore }.take(config.top)

        return ConvergeRiskOutput(entries, skippedFileWarning)
    }
}
